const pool = require('../config/db');
const { fallar } = require('../middleware/errores');

/**
 * Panel de administrador.
 *
 * Todas las rutas de este controller estan detras de exigirRol('admin'),
 * que se aplica una sola vez en admin.routes.js. Aun asi, cada operacion
 * destructiva vuelve a comprobar sus propias reglas: un admin tampoco
 * deberia poder desactivarse a si mismo ni dejar al sistema sin admins.
 */

/**
 * GET /api/admin/metricas
 * Cifras del tablero. Se resuelven en una sola ida a la base de datos con
 * subconsultas en vez de siete SELECT sueltos.
 */
async function metricas(req, res) {
  const [[generales]] = await pool.query(
    `SELECT
      (SELECT COUNT(*) FROM usuarios WHERE rol = 'turista')                    AS turistas,
      (SELECT COUNT(*) FROM usuarios WHERE rol = 'prestador')                  AS prestadores,
      (SELECT COUNT(*) FROM usuarios WHERE activo = FALSE)                     AS usuarios_inactivos,
      (SELECT COUNT(*) FROM prestadores WHERE estado_verificacion = 'pendiente') AS prestadores_pendientes,
      (SELECT COUNT(*) FROM servicios WHERE activo = TRUE)                     AS servicios_activos,
      (SELECT COUNT(*) FROM reservas)                                          AS reservas_totales,
      (SELECT COUNT(*) FROM reservas WHERE estado = 'confirmada')              AS reservas_confirmadas,
      (SELECT COUNT(*) FROM itinerarios)                                       AS itinerarios,
      (SELECT COALESCE(ROUND(AVG(calificacion), 2), 0) FROM resenas)           AS calificacion_media`
  );

  // Volumen transado: solo cuenta lo que no esta cancelado.
  const [[{ volumen }]] = await pool.query(
    `SELECT COALESCE(SUM(subtotal), 0) AS volumen
     FROM reservas
     WHERE estado IN ('confirmada','completada')`
  );

  const [porCategoria] = await pool.query(
    `SELECT c.slug, c.nombre,
            COUNT(DISTINCT s.id) AS servicios,
            COUNT(r.id)          AS reservas
     FROM categorias_servicio c
     LEFT JOIN servicios s ON s.categoria_id = c.id AND s.activo = TRUE
     LEFT JOIN reservas r  ON r.servicio_id = s.id
     GROUP BY c.id, c.slug, c.nombre
     ORDER BY reservas DESC`
  );

  // Reservas de los ultimos 30 dias, para la grafica de tendencia.
  const [porDia] = await pool.query(
    `SELECT DATE(creado_en) AS fecha, COUNT(*) AS cantidad
     FROM reservas
     WHERE creado_en >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
     GROUP BY DATE(creado_en)
     ORDER BY fecha`
  );

  res.json({
    ...generales,
    volumen_transado: Number(volumen),
    por_categoria: porCategoria,
    reservas_ultimos_30_dias: porDia
  });
}


/**
 * GET /api/admin/prestadores?estado=pendiente&buscar=
 * Bandeja de solicitudes. Por defecto muestra las pendientes, que es lo
 * que el admin necesita atender.
 */
async function listarPrestadores(req, res) {
  const { estado, buscar } = req.query;
  const pagina = Math.max(1, Number(req.query.pagina) || 1);
  const limite = Math.min(100, Math.max(1, Number(req.query.limite) || 20));
  const offset = (pagina - 1) * limite;

  const condiciones = [];
  const parametros = [];

  if (estado) {
    condiciones.push('p.estado_verificacion = ?');
    parametros.push(estado);
  }
  if (buscar) {
    condiciones.push('(u.nombre LIKE ? OR u.email LIKE ? OR p.documento_numero LIKE ?)');
    parametros.push(`%${buscar}%`, `%${buscar}%`, `%${buscar}%`);
  }

  const where = condiciones.length ? `WHERE ${condiciones.join(' AND ')}` : '';

  const [filas] = await pool.query(
    `SELECT p.id, p.usuario_id, p.razon_social, p.descripcion,
            p.documento_tipo, p.documento_numero, p.ciudad_base,
            p.estado_verificacion, p.motivo_rechazo, p.verificado_en, p.creado_en,
            u.nombre, u.email, u.telefono, u.activo,
            revisor.nombre AS revisado_por,
            (SELECT COUNT(*) FROM servicios s WHERE s.prestador_id = p.id) AS total_servicios
     FROM prestadores p
     JOIN usuarios u            ON u.id = p.usuario_id
     LEFT JOIN usuarios revisor ON revisor.id = p.verificado_por
     ${where}
     ORDER BY (p.estado_verificacion = 'pendiente') DESC, p.creado_en DESC
     LIMIT ? OFFSET ?`,
    [...parametros, limite, offset]
  );

  const [[{ total }]] = await pool.query(
    `SELECT COUNT(*) AS total
     FROM prestadores p
     JOIN usuarios u ON u.id = p.usuario_id
     ${where}`,
    parametros
  );

  res.json({
    datos: filas,
    paginacion: { pagina, limite, total, paginas: Math.ceil(total / limite) }
  });
}


/**
 * PATCH /api/admin/prestadores/:id/verificacion
 * Body: { estado: 'aprobado' | 'rechazado' | 'pendiente', motivo }
 *
 * Al rechazar se exige un motivo: el prestador lo vera al intentar publicar
 * un servicio, y sin el no sabria que corregir.
 *
 * Rechazar tambien desactiva los servicios que ya tuviera publicados; si no,
 * un prestador aprobado por error seguiria apareciendo en el catalogo
 * despues de revocarle la aprobacion.
 */
async function cambiarVerificacion(req, res) {
  const { id } = req.params;
  const { estado, motivo = null } = req.body;

  if (estado === 'rechazado' && !motivo) {
    throw fallar(400, 'Para rechazar a un prestador debes indicar el motivo');
  }

  const conexion = await pool.getConnection();
  try {
    await conexion.beginTransaction();

    const [filas] = await conexion.query(
      `SELECT p.id, p.estado_verificacion, u.nombre, u.email
       FROM prestadores p
       JOIN usuarios u ON u.id = p.usuario_id
       WHERE p.id = ?
       FOR UPDATE`,
      [id]
    );
    if (filas.length === 0) throw fallar(404, 'Prestador no encontrado');

    const prestador = filas[0];
    if (prestador.estado_verificacion === estado) {
      throw fallar(409, `El prestador ya esta en estado "${estado}"`);
    }

    await conexion.query(
      `UPDATE prestadores
       SET estado_verificacion = ?,
           motivo_rechazo      = ?,
           verificado_por      = ?,
           verificado_en       = CURRENT_TIMESTAMP
       WHERE id = ?`,
      [estado, estado === 'rechazado' ? motivo : null, req.usuario.id, id]
    );

    let serviciosAfectados = 0;
    if (estado !== 'aprobado') {
      const [resultado] = await conexion.query(
        'UPDATE servicios SET activo = FALSE WHERE prestador_id = ? AND activo = TRUE',
        [id]
      );
      serviciosAfectados = resultado.affectedRows;
    }

    await conexion.commit();

    res.json({
      id: Number(id),
      prestador: prestador.nombre,
      estado_verificacion: estado,
      motivo_rechazo: estado === 'rechazado' ? motivo : null,
      servicios_desactivados: serviciosAfectados
    });
  } catch (err) {
    await conexion.rollback();
    throw err;
  } finally {
    conexion.release();
  }
}


/** GET /api/admin/usuarios?rol=&activo=&buscar= */
async function listarUsuarios(req, res) {
  const { rol, activo, buscar } = req.query;
  const pagina = Math.max(1, Number(req.query.pagina) || 1);
  const limite = Math.min(100, Math.max(1, Number(req.query.limite) || 20));
  const offset = (pagina - 1) * limite;

  const condiciones = [];
  const parametros = [];

  if (rol) {
    condiciones.push('u.rol = ?');
    parametros.push(rol);
  }
  if (activo !== undefined && activo !== '') {
    condiciones.push('u.activo = ?');
    parametros.push(activo === 'true' || activo === '1');
  }
  if (buscar) {
    condiciones.push('(u.nombre LIKE ? OR u.email LIKE ?)');
    parametros.push(`%${buscar}%`, `%${buscar}%`);
  }

  const where = condiciones.length ? `WHERE ${condiciones.join(' AND ')}` : '';

  // Nunca se selecciona password_hash, ni siquiera para el admin.
  const [filas] = await pool.query(
    `SELECT u.id, u.nombre, u.email, u.telefono, u.rol, u.activo, u.creado_en,
            (SELECT COUNT(*) FROM reservas r WHERE r.turista_id = u.id) AS total_reservas
     FROM usuarios u
     ${where}
     ORDER BY u.creado_en DESC
     LIMIT ? OFFSET ?`,
    [...parametros, limite, offset]
  );

  const [[{ total }]] = await pool.query(
    `SELECT COUNT(*) AS total FROM usuarios u ${where}`,
    parametros
  );

  res.json({
    datos: filas,
    paginacion: { pagina, limite, total, paginas: Math.ceil(total / limite) }
  });
}


/**
 * PATCH /api/admin/usuarios/:id/activo
 * Body: { activo: boolean }
 *
 * Desactivar es preferible a borrar: conserva el historial de reservas y
 * las resenas escritas. El login ya rechaza a los usuarios inactivos.
 */
async function cambiarActivo(req, res) {
  const { id } = req.params;
  const { activo } = req.body;

  if (Number(id) === req.usuario.id) {
    throw fallar(400, 'No puedes desactivar tu propia cuenta');
  }

  const conexion = await pool.getConnection();
  try {
    await conexion.beginTransaction();

    const [filas] = await conexion.query(
      'SELECT id, nombre, rol, activo FROM usuarios WHERE id = ? FOR UPDATE',
      [id]
    );
    if (filas.length === 0) throw fallar(404, 'Usuario no encontrado');

    const usuario = filas[0];

    // Nunca dejar el sistema sin ningun administrador activo.
    if (usuario.rol === 'admin' && activo === false) {
      const [[{ activos }]] = await conexion.query(
        "SELECT COUNT(*) AS activos FROM usuarios WHERE rol = 'admin' AND activo = TRUE"
      );
      if (activos <= 1) {
        throw fallar(409, 'No puedes desactivar al ultimo administrador activo');
      }
    }

    await conexion.query('UPDATE usuarios SET activo = ? WHERE id = ?', [activo, id]);

    await conexion.commit();
    res.json({ id: Number(id), nombre: usuario.nombre, activo });
  } catch (err) {
    await conexion.rollback();
    throw err;
  } finally {
    conexion.release();
  }
}


/**
 * GET /api/admin/servicios?activo=&categoria=&buscar=
 * Moderacion del catalogo. A diferencia de GET /api/servicios, aqui SI se
 * ven los servicios desactivados, que es justo lo que el admin necesita.
 */
async function listarServicios(req, res) {
  const { activo, categoria, buscar } = req.query;
  const pagina = Math.max(1, Number(req.query.pagina) || 1);
  const limite = Math.min(100, Math.max(1, Number(req.query.limite) || 20));
  const offset = (pagina - 1) * limite;

  const condiciones = [];
  const parametros = [];

  if (activo !== undefined && activo !== '') {
    condiciones.push('s.activo = ?');
    parametros.push(activo === 'true' || activo === '1');
  }
  if (categoria) {
    condiciones.push('c.slug = ?');
    parametros.push(categoria);
  }
  if (buscar) {
    condiciones.push('(s.titulo LIKE ? OR u.nombre LIKE ?)');
    parametros.push(`%${buscar}%`, `%${buscar}%`);
  }

  const where = condiciones.length ? `WHERE ${condiciones.join(' AND ')}` : '';

  const [filas] = await pool.query(
    `SELECT s.id, s.titulo, s.precio, s.moneda, s.ciudad, s.activo,
            s.calificacion_promedio, s.total_resenas, s.creado_en,
            c.slug AS categoria_slug, c.nombre AS categoria_nombre,
            p.id AS prestador_id, p.estado_verificacion,
            u.nombre AS prestador_nombre,
            (SELECT COUNT(*) FROM reservas r WHERE r.servicio_id = s.id) AS total_reservas
     FROM servicios s
     JOIN categorias_servicio c ON c.id = s.categoria_id
     JOIN prestadores p         ON p.id = s.prestador_id
     JOIN usuarios u            ON u.id = p.usuario_id
     ${where}
     ORDER BY s.creado_en DESC
     LIMIT ? OFFSET ?`,
    [...parametros, limite, offset]
  );

  const [[{ total }]] = await pool.query(
    `SELECT COUNT(*) AS total
     FROM servicios s
     JOIN categorias_servicio c ON c.id = s.categoria_id
     JOIN prestadores p         ON p.id = s.prestador_id
     JOIN usuarios u            ON u.id = p.usuario_id
     ${where}`,
    parametros
  );

  res.json({
    datos: filas,
    paginacion: { pagina, limite, total, paginas: Math.ceil(total / limite) }
  });
}


/**
 * PATCH /api/admin/servicios/:id/activo
 * Retira del catalogo un servicio inapropiado sin borrarlo, para no romper
 * las reservas que ya lo referencian (la FK es ON DELETE RESTRICT).
 */
async function cambiarActivoServicio(req, res) {
  const { id } = req.params;
  const { activo } = req.body;

  const [filas] = await pool.query('SELECT id, titulo FROM servicios WHERE id = ?', [id]);
  if (filas.length === 0) throw fallar(404, 'Servicio no encontrado');

  await pool.query('UPDATE servicios SET activo = ? WHERE id = ?', [activo, id]);

  res.json({ id: Number(id), titulo: filas[0].titulo, activo });
}


module.exports = {
  metricas,
  listarPrestadores,
  cambiarVerificacion,
  listarUsuarios,
  cambiarActivo,
  listarServicios,
  cambiarActivoServicio
};
