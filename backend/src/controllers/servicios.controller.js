const pool = require('../config/db');
const { fallar } = require('../middleware/errores');

/**
 * GET /api/servicios
 */
async function listar(req, res) {
  const {
    categoria, ciudad, precio_min, precio_max,
    calificacion, fecha, buscar, orden = 'calificacion'
  } = req.query;

  const pagina = Math.max(1, Number(req.query.pagina) || 1);
  const limite = Math.min(50, Math.max(1, Number(req.query.limite) || 20));
  const offset = (pagina - 1) * limite;

  const condiciones = [];
  const parametros = [];

  if (categoria) {
    condiciones.push('categoria_slug = ?');
    parametros.push(categoria);
  }
  if (ciudad) {
    condiciones.push('ciudad = ?');
    parametros.push(ciudad);
  }
  if (precio_min) {
    condiciones.push('precio >= ?');
    parametros.push(Number(precio_min));
  }
  if (precio_max) {
    condiciones.push('precio <= ?');
    parametros.push(Number(precio_max));
  }
  if (calificacion) {
    condiciones.push('calificacion_promedio >= ?');
    parametros.push(Number(calificacion));
  }
  if (buscar) {
    condiciones.push('(titulo LIKE ? OR descripcion LIKE ?)');
    parametros.push(`%${buscar}%`, `%${buscar}%`);
  }
  if (fecha) {
    condiciones.push(`EXISTS (
      SELECT 1 FROM disponibilidad d
      WHERE d.servicio_id = v_catalogo.id
        AND d.fecha = ?
        AND d.bloqueado = FALSE
        AND d.cupos_ocupados < d.cupos_totales
    )`);
    parametros.push(fecha);
  }

  const where = condiciones.length ? `WHERE ${condiciones.join(' AND ')}` : '';

  const ordenamientos = {
    precio: 'precio ASC',
    precio_desc: 'precio DESC',
    calificacion: 'calificacion_promedio DESC, total_resenas DESC',
    reciente: 'id DESC'
  };
  const orderBy = ordenamientos[orden] || ordenamientos.calificacion;

  const [filas] = await pool.query(
    `SELECT * FROM v_catalogo ${where} ORDER BY ${orderBy} LIMIT ? OFFSET ?`,
    [...parametros, limite, offset]
  );

  const [[{ total }]] = await pool.query(
    `SELECT COUNT(*) AS total FROM v_catalogo ${where}`,
    parametros
  );

  res.json({
    datos: filas,
    paginacion: { pagina, limite, total, paginas: Math.ceil(total / limite) }
  });
}

/**
 * GET /api/servicios/:id
 */
async function detalle(req, res) {
  const { id } = req.params;

  const [filas] = await pool.query('SELECT * FROM v_catalogo WHERE id = ?', [id]);
  if (filas.length === 0) {
    throw fallar(404, 'Servicio no encontrado');
  }
  const servicio = filas[0];

  const tablasSatelite = {
    guia: 'servicios_guia',
    hospedaje: 'servicios_hospedaje',
    alimentacion: 'servicios_alimentacion',
    transporte: 'servicios_transporte',
    traduccion: 'servicios_traduccion'
  };

  const tabla = tablasSatelite[servicio.categoria_slug];
  if (tabla) {
    const [detalles] = await pool.query(
      `SELECT * FROM ${tabla} WHERE servicio_id = ?`,
      [id]
    );
    servicio.detalle = detalles[0] || null;
  }

  const [fotos] = await pool.query(
    'SELECT url, orden FROM servicio_fotos WHERE servicio_id = ? ORDER BY orden',
    [id]
  );
  servicio.fotos = fotos;

  const [idiomas] = await pool.query(
    `SELECT i.codigo, i.nombre, si.nivel
     FROM servicio_idiomas si
     JOIN idiomas i ON i.id = si.idioma_id
     WHERE si.servicio_id = ?`,
    [id]
  );
  servicio.idiomas = idiomas;

  const [resenas] = await pool.query(
    `SELECT r.calificacion, r.comentario, r.creado_en, u.nombre AS turista_nombre
     FROM resenas r
     JOIN usuarios u ON u.id = r.turista_id
     WHERE r.servicio_id = ?
     ORDER BY r.creado_en DESC
     LIMIT 10`,
    [id]
  );
  servicio.resenas = resenas;

  res.json(servicio);
}

/**
 * GET /api/servicios/:id/disponibilidad?desde=&hasta=
 * Calendario para la pantalla de reserva.
 */
async function disponibilidad(req, res) {
  const { id } = req.params;
  const { desde, hasta } = req.query;

  if (!desde || !hasta) {
    throw fallar(400, 'Debes indicar los parametros desde y hasta (YYYY-MM-DD)');
  }

  const [filas] = await pool.query(
    `SELECT fecha, cupos_totales, cupos_ocupados,
            (cupos_totales - cupos_ocupados) AS cupos_libres,
            precio_especial, bloqueado
     FROM disponibilidad
     WHERE servicio_id = ? AND fecha BETWEEN ? AND ?
     ORDER BY fecha`,
    [id, desde, hasta]
  );

  res.json(filas);
}

/** GET /api/servicios/categorias */
async function categorias(req, res) {
  const [filas] = await pool.query(
    'SELECT id, slug, nombre, icono FROM categorias_servicio WHERE activo = TRUE ORDER BY id'
  );
  res.json(filas);
}

/**
 * POST /api/servicios
 */
async function crear(req, res) {
  const {
    categoria_id, titulo, descripcion, precio, unidad_precio = 'por_servicio',
    direccion, ciudad = 'Puno', latitud, longitud, capacidad_maxima = 1,
    detalle = {}
  } = req.body;

  const [prestadores] = await pool.query(
    'SELECT id, estado_verificacion, motivo_rechazo FROM prestadores WHERE usuario_id = ?',
    [req.usuario.id]
  );
  if (prestadores.length === 0) {
    throw fallar(403, 'No tienes un perfil de prestador');
  }
  if (prestadores[0].estado_verificacion === 'rechazado') {
    throw fallar(403,
      `Tu solicitud de prestador fue rechazada${
        prestadores[0].motivo_rechazo ? `: ${prestadores[0].motivo_rechazo}` : ''}`);
  }
  if (prestadores[0].estado_verificacion !== 'aprobado') {
    throw fallar(403, 'Tu cuenta de prestador aun esta pendiente de aprobacion');
  }
  const prestadorId = prestadores[0].id;

  const conexion = await pool.getConnection();
  try {
    await conexion.beginTransaction();

    const [resultado] = await conexion.query(
      `INSERT INTO servicios
        (prestador_id, categoria_id, titulo, descripcion, precio, unidad_precio,
         direccion, ciudad, latitud, longitud, capacidad_maxima)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [prestadorId, categoria_id, titulo, descripcion ?? null, precio, unidad_precio,
        direccion ?? null, ciudad, latitud ?? null, longitud ?? null, capacidad_maxima]
    );
    const servicioId = resultado.insertId;

    // Fila en la tabla satelite segun la categoria elegida.
    const [cat] = await conexion.query(
      'SELECT slug FROM categorias_servicio WHERE id = ?',
      [categoria_id]
    );
    if (cat.length === 0) {
      throw fallar(400, 'La categoria indicada no existe');
    }

    const slug = cat[0].slug;
    if (slug === 'guia') {
      await conexion.query(
        `INSERT INTO servicios_guia
          (servicio_id, anios_experiencia, duracion_horas, tamano_max_grupo,
           incluye_transporte, punto_encuentro)
         VALUES (?, ?, ?, ?, ?, ?)`,
        [servicioId, detalle.anios_experiencia ?? 0, detalle.duracion_horas ?? 4.0,
          detalle.tamano_max_grupo ?? 10, detalle.incluye_transporte ?? false,
          detalle.punto_encuentro ?? null]
      );
    } else if (slug === 'hospedaje') {
      await conexion.query(
        `INSERT INTO servicios_hospedaje
          (servicio_id, tipo_alojamiento, habitaciones, camas, banos,
           wifi, desayuno_incluido, estacionamiento)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
        [servicioId, detalle.tipo_alojamiento ?? 'hostal', detalle.habitaciones ?? 1,
          detalle.camas ?? 1, detalle.banos ?? 1, detalle.wifi ?? false,
          detalle.desayuno_incluido ?? false, detalle.estacionamiento ?? false]
      );
    }
    // Las demas categorias quedan como trabajo futuro.

    await conexion.commit();
    res.status(201).json({ id: servicioId, mensaje: 'Servicio creado' });
  } catch (err) {
    await conexion.rollback();
    throw err;
  } finally {
    conexion.release();
  }
}

/**
 * PUT /api/servicios/:id/disponibilidad
 * Body: { fechas: [{ fecha, cupos_totales, precio_especial, bloqueado }] }
 */
async function guardarDisponibilidad(req, res) {
  const { id } = req.params;
  const { fechas } = req.body;

  if (!Array.isArray(fechas) || fechas.length === 0) {
    throw fallar(400, 'Debes enviar un arreglo "fechas" con al menos un elemento');
  }

  const [duenos] = await pool.query(
    `SELECT s.id FROM servicios s
     JOIN prestadores p ON p.id = s.prestador_id
     WHERE s.id = ? AND p.usuario_id = ?`,
    [id, req.usuario.id]
  );
  if (duenos.length === 0) {
    throw fallar(403, 'Este servicio no te pertenece');
  }

  const conexion = await pool.getConnection();
  try {
    await conexion.beginTransaction();

    for (const f of fechas) {
      await conexion.query(
        `INSERT INTO disponibilidad (servicio_id, fecha, cupos_totales, precio_especial, bloqueado)
         VALUES (?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE
           cupos_totales   = VALUES(cupos_totales),
           precio_especial = VALUES(precio_especial),
           bloqueado       = VALUES(bloqueado)`,
        [id, f.fecha, f.cupos_totales ?? 1, f.precio_especial ?? null, f.bloqueado ?? false]
      );
    }

    await conexion.commit();
    res.json({ mensaje: `Disponibilidad guardada (${fechas.length} fechas)` });
  } catch (err) {
    await conexion.rollback();
    throw err;
  } finally {
    conexion.release();
  }
}

/** GET /api/servicios/mios  (panel del prestador) */
async function mios(req, res) {
  const [filas] = await pool.query(
    `SELECT s.*, c.slug AS categoria_slug, c.nombre AS categoria_nombre,
            (SELECT COUNT(*) FROM reservas r
              WHERE r.servicio_id = s.id AND r.estado IN ('pendiente','confirmada')
            ) AS reservas_activas
     FROM servicios s
     JOIN categorias_servicio c ON c.id = s.categoria_id
     JOIN prestadores p ON p.id = s.prestador_id
     WHERE p.usuario_id = ?
     ORDER BY s.creado_en DESC`,
    [req.usuario.id]
  );
  res.json(filas);
}

module.exports = {
  listar, detalle, disponibilidad, categorias, crear, guardarDisponibilidad, mios
};
