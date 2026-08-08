const pool = require('../config/db');
const { fallar } = require('../middleware/errores');

/**
 * POST /api/resenas
 * Body: { reserva_id, calificacion, comentario }
 *
 * Reglas de negocio (punto 4.7 del enunciado):
 *   - Solo se resena una reserva propia.
 *   - Solo si esta en estado "completada": no se puede calificar un
 *     servicio que todavia no se recibio.
 *   - Una sola resena por reserva. La UNIQUE KEY de la tabla ya lo impide,
 *     pero se comprueba antes para devolver un 409 con mensaje claro en vez
 *     de un error de MySQL.
 *
 * El servicio_id NO se toma del body: se deriva de la reserva. Si se
 * confiara en el body, alguien podria usar su reserva del servicio A para
 * calificar el servicio B.
 */
async function crear(req, res) {
  const { reserva_id, calificacion, comentario = null } = req.body;

  const conexion = await pool.getConnection();
  try {
    await conexion.beginTransaction();

    const [reservas] = await conexion.query(
      `SELECT r.id, r.turista_id, r.servicio_id, r.estado
       FROM reservas r
       WHERE r.id = ?
       FOR UPDATE`,
      [reserva_id]
    );
    if (reservas.length === 0) throw fallar(404, 'Reserva no encontrada');

    const reserva = reservas[0];

    if (reserva.turista_id !== req.usuario.id) {
      throw fallar(403, 'Solo puedes calificar tus propias reservas');
    }
    if (reserva.estado !== 'completada') {
      throw fallar(409,
        `Solo se pueden calificar reservas completadas (esta esta "${reserva.estado}")`);
    }

    const [previas] = await conexion.query(
      'SELECT id FROM resenas WHERE reserva_id = ?',
      [reserva_id]
    );
    if (previas.length > 0) {
      throw fallar(409, 'Esta reserva ya tiene una resena');
    }

    const [resultado] = await conexion.query(
      `INSERT INTO resenas (reserva_id, turista_id, servicio_id, calificacion, comentario)
       VALUES (?, ?, ?, ?, ?)`,
      [reserva_id, req.usuario.id, reserva.servicio_id, calificacion, comentario]
    );

    await conexion.commit();

    // El promedio del servicio lo recalcula el trigger trg_resena_insert,
    // asi que se relee para devolver el valor ya actualizado.
    const [[servicio]] = await pool.query(
      'SELECT calificacion_promedio, total_resenas FROM servicios WHERE id = ?',
      [reserva.servicio_id]
    );

    res.status(201).json({
      id: resultado.insertId,
      reserva_id,
      servicio_id: reserva.servicio_id,
      calificacion,
      comentario,
      servicio: {
        calificacion_promedio: Number(servicio.calificacion_promedio),
        total_resenas: servicio.total_resenas
      }
    });
  } catch (err) {
    await conexion.rollback();
    throw err;
  } finally {
    conexion.release();
  }
}


/**
 * GET /api/resenas/servicio/:servicioId?pagina=&limite=
 * Reseñas publicas de un servicio, con el resumen de la distribucion de
 * estrellas para pintar las barras del detalle.
 */
async function porServicio(req, res) {
  const { servicioId } = req.params;
  const pagina = Math.max(1, Number(req.query.pagina) || 1);
  const limite = Math.min(50, Math.max(1, Number(req.query.limite) || 10));
  const offset = (pagina - 1) * limite;

  const [servicios] = await pool.query(
    'SELECT id, calificacion_promedio, total_resenas FROM servicios WHERE id = ?',
    [servicioId]
  );
  if (servicios.length === 0) throw fallar(404, 'Servicio no encontrado');

  const [resenas] = await pool.query(
    `SELECT r.id, r.calificacion, r.comentario, r.creado_en,
            u.nombre AS turista_nombre, u.foto_url AS turista_foto
     FROM resenas r
     JOIN usuarios u ON u.id = r.turista_id
     WHERE r.servicio_id = ?
     ORDER BY r.creado_en DESC
     LIMIT ? OFFSET ?`,
    [servicioId, limite, offset]
  );

  const [distribucion] = await pool.query(
    `SELECT calificacion, COUNT(*) AS cantidad
     FROM resenas
     WHERE servicio_id = ?
     GROUP BY calificacion`,
    [servicioId]
  );

  // Se rellenan las estrellas sin votos para que la app siempre reciba
  // las cinco barras, incluso las que estan en cero.
  const porEstrella = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
  for (const fila of distribucion) {
    porEstrella[fila.calificacion] = Number(fila.cantidad);
  }

  res.json({
    resumen: {
      promedio: Number(servicios[0].calificacion_promedio),
      total: servicios[0].total_resenas,
      distribucion: porEstrella
    },
    datos: resenas,
    paginacion: {
      pagina,
      limite,
      total: servicios[0].total_resenas,
      paginas: Math.ceil(servicios[0].total_resenas / limite)
    }
  });
}


/** GET /api/resenas/mias — reseñas escritas por el turista. */
async function mias(req, res) {
  const [filas] = await pool.query(
    `SELECT r.id, r.calificacion, r.comentario, r.creado_en,
            r.reserva_id, res.codigo AS reserva_codigo,
            s.id AS servicio_id, s.titulo AS servicio_titulo,
            c.slug AS categoria_slug
     FROM resenas r
     JOIN reservas res          ON res.id = r.reserva_id
     JOIN servicios s           ON s.id = r.servicio_id
     JOIN categorias_servicio c ON c.id = s.categoria_id
     WHERE r.turista_id = ?
     ORDER BY r.creado_en DESC`,
    [req.usuario.id]
  );
  res.json(filas);
}


/**
 * GET /api/resenas/pendientes
 * Reservas completadas que el turista todavia no ha calificado. Es lo que
 * alimenta el aviso de "califica tu experiencia" en la app.
 */
async function pendientes(req, res) {
  const [filas] = await pool.query(
    `SELECT r.id AS reserva_id, r.codigo, r.fecha_inicio,
            s.id AS servicio_id, s.titulo AS servicio_titulo,
            c.slug AS categoria_slug,
            (SELECT f.url FROM servicio_fotos f
              WHERE f.servicio_id = s.id ORDER BY f.orden LIMIT 1) AS foto
     FROM reservas r
     JOIN servicios s           ON s.id = r.servicio_id
     JOIN categorias_servicio c ON c.id = s.categoria_id
     WHERE r.turista_id = ?
       AND r.estado = 'completada'
       AND NOT EXISTS (SELECT 1 FROM resenas re WHERE re.reserva_id = r.id)
     ORDER BY r.fecha_inicio DESC`,
    [req.usuario.id]
  );
  res.json(filas);
}


/**
 * PUT /api/resenas/:id
 * Permite corregir la propia resena. El trigger de la tabla solo cubre
 * INSERT y DELETE, asi que al editar hay que recalcular el promedio aqui.
 */
async function actualizar(req, res) {
  const { id } = req.params;
  const { calificacion, comentario = null } = req.body;

  const conexion = await pool.getConnection();
  try {
    await conexion.beginTransaction();

    const [filas] = await conexion.query(
      'SELECT id, turista_id, servicio_id FROM resenas WHERE id = ? FOR UPDATE',
      [id]
    );
    if (filas.length === 0) throw fallar(404, 'Resena no encontrada');
    if (filas[0].turista_id !== req.usuario.id) {
      throw fallar(403, 'Solo puedes editar tus propias resenas');
    }

    await conexion.query(
      'UPDATE resenas SET calificacion = ?, comentario = ? WHERE id = ?',
      [calificacion, comentario, id]
    );

    await conexion.query(
      `UPDATE servicios
       SET calificacion_promedio = COALESCE(
             (SELECT ROUND(AVG(re.calificacion), 2) FROM resenas re WHERE re.servicio_id = ?), 0)
       WHERE id = ?`,
      [filas[0].servicio_id, filas[0].servicio_id]
    );

    await conexion.commit();
    res.json({ id: Number(id), calificacion, comentario });
  } catch (err) {
    await conexion.rollback();
    throw err;
  } finally {
    conexion.release();
  }
}


/** DELETE /api/resenas/:id — el trigger trg_resena_delete ajusta el promedio. */
async function eliminar(req, res) {
  const [filas] = await pool.query(
    'SELECT id, turista_id FROM resenas WHERE id = ?',
    [req.params.id]
  );
  if (filas.length === 0) throw fallar(404, 'Resena no encontrada');

  if (filas[0].turista_id !== req.usuario.id && req.usuario.rol !== 'admin') {
    throw fallar(403, 'Solo puedes eliminar tus propias resenas');
  }

  await pool.query('DELETE FROM resenas WHERE id = ?', [req.params.id]);
  res.status(204).send();
}


module.exports = { crear, porServicio, mias, pendientes, actualizar, eliminar };
