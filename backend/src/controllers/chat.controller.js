const pool = require('../config/db');
const { fallar } = require('../middleware/errores');
const servicio = require('../services/chat.service');

/*
 * GET /api/chat/conversaciones
 */
async function listarConversaciones(req, res) {
  const [filas] = await pool.query(
    `SELECT c.id, c.turista_id, c.prestador_id, c.ultimo_mensaje_en, c.creado_en,
            ut.nombre   AS turista_nombre,
            ut.foto_url AS turista_foto,
            up.id       AS prestador_usuario_id,
            up.nombre   AS prestador_nombre,
            up.foto_url AS prestador_foto,
            (SELECT m.contenido FROM mensajes m
              WHERE m.conversacion_id = c.id
              ORDER BY m.enviado_en DESC LIMIT 1) AS ultimo_mensaje,
            (SELECT m.emisor_id FROM mensajes m
              WHERE m.conversacion_id = c.id
              ORDER BY m.enviado_en DESC LIMIT 1) AS ultimo_emisor_id,
            (SELECT COUNT(*) FROM mensajes m
              WHERE m.conversacion_id = c.id
                AND m.emisor_id <> ?
                AND m.leido = FALSE) AS no_leidos
     FROM conversaciones c
     JOIN usuarios    ut ON ut.id = c.turista_id
     JOIN prestadores p  ON p.id  = c.prestador_id
     JOIN usuarios    up ON up.id = p.usuario_id
     WHERE c.turista_id = ? OR p.usuario_id = ?
     ORDER BY COALESCE(c.ultimo_mensaje_en, c.creado_en) DESC`,
    [req.usuario.id, req.usuario.id, req.usuario.id]
  );

  const conversaciones = filas.map((c) => {
    const esTurista = c.turista_id === req.usuario.id;
    return {
      id: c.id,
      ultimo_mensaje: c.ultimo_mensaje,
      ultimo_mensaje_en: c.ultimo_mensaje_en,
      ultimo_mensaje_mio: c.ultimo_emisor_id === req.usuario.id,
      no_leidos: Number(c.no_leidos),
      interlocutor: esTurista
        ? { usuario_id: c.prestador_usuario_id, nombre: c.prestador_nombre, foto_url: c.prestador_foto, rol: 'prestador' }
        : { usuario_id: c.turista_id, nombre: c.turista_nombre, foto_url: c.turista_foto, rol: 'turista' }
    };
  });

  res.json(conversaciones);
}


/**
 * POST /api/chat/conversaciones
 */
async function abrirConversacion(req, res) {
  const { prestador_id, servicio_id } = req.body;

  if (!prestador_id && !servicio_id) {
    throw fallar(400, 'Indica prestador_id o servicio_id');
  }

  // Un prestador no inicia hilos: el modelo asume turista -> prestador.
  if (req.usuario.rol === 'prestador') {
    throw fallar(403, 'Las conversaciones las inicia el turista');
  }

  let destino = prestador_id;
  if (!destino) {
    const [servicios] = await pool.query(
      'SELECT prestador_id FROM servicios WHERE id = ?',
      [servicio_id]
    );
    if (servicios.length === 0) throw fallar(404, 'Servicio no encontrado');
    destino = servicios[0].prestador_id;
  }

  const [prestadores] = await pool.query(
    `SELECT p.id, p.usuario_id, u.nombre, u.foto_url
     FROM prestadores p
     JOIN usuarios u ON u.id = p.usuario_id
     WHERE p.id = ?`,
    [destino]
  );
  if (prestadores.length === 0) throw fallar(404, 'Prestador no encontrado');

  if (prestadores[0].usuario_id === req.usuario.id) {
    throw fallar(400, 'No puedes abrir una conversacion contigo mismo');
  }

  await pool.query(
    `INSERT INTO conversaciones (turista_id, prestador_id)
     VALUES (?, ?)
     ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)`,
    [req.usuario.id, destino]
  );

  const [filas] = await pool.query(
    'SELECT id, creado_en, ultimo_mensaje_en FROM conversaciones WHERE turista_id = ? AND prestador_id = ?',
    [req.usuario.id, destino]
  );

  res.status(201).json({
    id: filas[0].id,
    creado_en: filas[0].creado_en,
    ultimo_mensaje_en: filas[0].ultimo_mensaje_en,
    interlocutor: {
      usuario_id: prestadores[0].usuario_id,
      nombre: prestadores[0].nombre,
      foto_url: prestadores[0].foto_url,
      rol: 'prestador'
    }
  });
}


/**
 * GET /api/chat/conversaciones/:id/mensajes?antes=<id>&limite=
 */
async function historial(req, res) {
  const { id } = req.params;
  const limite = Math.min(100, Math.max(1, Number(req.query.limite) || 30));
  const antes = req.query.antes ? Number(req.query.antes) : null;

  const acceso = await servicio.accesoAConversacion(pool, id, req.usuario.id);
  if (!acceso) throw fallar(404, 'Conversacion no encontrada');

  const condiciones = ['m.conversacion_id = ?'];
  const parametros = [id];
  if (antes) {
    condiciones.push('m.id < ?');
    parametros.push(antes);
  }

  const [mensajes] = await pool.query(
    `SELECT m.id, m.conversacion_id, m.emisor_id, m.contenido, m.leido, m.enviado_en,
            u.nombre AS emisor_nombre, u.foto_url AS emisor_foto
     FROM mensajes m
     JOIN usuarios u ON u.id = m.emisor_id
     WHERE ${condiciones.join(' AND ')}
     ORDER BY m.id DESC
     LIMIT ?`,
    [...parametros, limite]
  );

  mensajes.reverse();

  res.json({
    datos: mensajes,
    cursor_anterior: mensajes.length === limite ? mensajes[0].id : null
  });
}


/** PATCH /api/chat/conversaciones/:id/leidos */
async function marcarLeidos(req, res) {
  const acceso = await servicio.accesoAConversacion(pool, req.params.id, req.usuario.id);
  if (!acceso) throw fallar(404, 'Conversacion no encontrada');

  const marcados = await servicio.marcarLeidos(req.params.id, req.usuario.id);
  res.json({ conversacion_id: Number(req.params.id), marcados });
}


/**
 * POST /api/chat/conversaciones/:id/mensajes
 */
async function enviarMensaje(req, res) {
  const { id } = req.params;
  const { contenido } = req.body;

  const acceso = await servicio.accesoAConversacion(pool, id, req.usuario.id);
  if (!acceso) throw fallar(404, 'Conversacion no encontrada');

  const mensaje = await servicio.guardarMensaje(id, req.usuario.id, contenido.trim());

  const io = req.app.get('io');
  if (io) {
    io.to(`conversacion:${id}`).emit('mensaje_nuevo', mensaje);
    io.to(`usuario:${acceso.destinatarioUsuarioId}`).emit('mensaje_nuevo', mensaje);
  }

  res.status(201).json(mensaje);
}


/** GET /api/chat/no-leidos — total para la insignia del icono de chat. */
async function totalNoLeidos(req, res) {
  const [[{ total }]] = await pool.query(
    `SELECT COUNT(*) AS total
     FROM mensajes m
     JOIN conversaciones c ON c.id = m.conversacion_id
     JOIN prestadores p    ON p.id = c.prestador_id
     WHERE m.emisor_id <> ?
       AND m.leido = FALSE
       AND (c.turista_id = ? OR p.usuario_id = ?)`,
    [req.usuario.id, req.usuario.id, req.usuario.id]
  );
  res.json({ total: Number(total) });
}


module.exports = {
  listarConversaciones,
  abrirConversacion,
  historial,
  marcarLeidos,
  enviarMensaje,
  totalNoLeidos
};
