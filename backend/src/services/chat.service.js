const pool = require('../config/db');

async function accesoAConversacion(ejecutor, conversacionId, usuarioId) {
  const [filas] = await ejecutor.query(
    `SELECT c.id, c.turista_id, c.prestador_id,
            p.usuario_id AS prestador_usuario_id
     FROM conversaciones c
     JOIN prestadores p ON p.id = c.prestador_id
     WHERE c.id = ?`,
    [conversacionId]
  );
  if (filas.length === 0) return null;

  const c = filas[0];
  const esTurista = c.turista_id === usuarioId;
  const esPrestador = c.prestador_usuario_id === usuarioId;

  if (!esTurista && !esPrestador) return null;

  return {
    conversacion: c,
    esTurista,
    destinatarioUsuarioId: esTurista ? c.prestador_usuario_id : c.turista_id
  };
}

async function guardarMensaje(conversacionId, emisorId, contenido) {
  const conexion = await pool.getConnection();
  try {
    await conexion.beginTransaction();

    const [resultado] = await conexion.query(
      'INSERT INTO mensajes (conversacion_id, emisor_id, contenido) VALUES (?, ?, ?)',
      [conversacionId, emisorId, contenido]
    );

    await conexion.query(
      'UPDATE conversaciones SET ultimo_mensaje_en = CURRENT_TIMESTAMP WHERE id = ?',
      [conversacionId]
    );

    const [filas] = await conexion.query(
      `SELECT m.id, m.conversacion_id, m.emisor_id, m.contenido, m.leido, m.enviado_en,
              u.nombre AS emisor_nombre, u.foto_url AS emisor_foto
       FROM mensajes m
       JOIN usuarios u ON u.id = m.emisor_id
       WHERE m.id = ?`,
      [resultado.insertId]
    );

    await conexion.commit();
    return filas[0];
  } catch (err) {
    await conexion.rollback();
    throw err;
  } finally {
    conexion.release();
  }
}

async function marcarLeidos(conversacionId, usuarioId) {
  const [resultado] = await pool.query(
    `UPDATE mensajes
     SET leido = TRUE
     WHERE conversacion_id = ? AND emisor_id <> ? AND leido = FALSE`,
    [conversacionId, usuarioId]
  );
  return resultado.affectedRows;
}

module.exports = {
  accesoAConversacion,
  guardarMensaje,
  marcarLeidos
};
