const pool = require('../config/db');

/**
 * Logica de chat compartida entre la API REST y Socket.io.
 *
 * Vive aparte a proposito: la comprobacion de "puede este usuario escribir en
 * esta conversacion" tiene que ser identica por los dos caminos. Si estuviera
 * duplicada, bastaria con arreglar un lado y olvidar el otro para dejar un
 * agujero por el que se lee el chat de otra persona.
 */

/**
 * Comprueba que el usuario sea uno de los dos participantes.
 *
 * OJO con la asimetria de la tabla: `conversaciones.turista_id` apunta a
 * usuarios.id, pero `prestador_id` apunta a prestadores.id, no a usuarios.id.
 * Compararlos directamente contra req.usuario.id seria un error silencioso
 * que dejaria entrar al usuario equivocado cuando los ids coincidan por azar.
 *
 * @returns {null|{conversacion, esTurista, destinatarioUsuarioId}}
 */
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
    // A quien hay que notificar cuando este usuario escribe.
    destinatarioUsuarioId: esTurista ? c.prestador_usuario_id : c.turista_id
  };
}

/**
 * Guarda un mensaje y actualiza la marca de actividad de la conversacion.
 *
 * Las dos escrituras van juntas en una transaccion: si se guardara el mensaje
 * pero fallara el UPDATE, la conversacion quedaria ordenada mal en la lista
 * y el ultimo mensaje no aparecería arriba.
 */
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

/** Marca como leidos los mensajes que el usuario recibio en esa conversacion. */
async function marcarLeidos(conversacionId, usuarioId) {
  // La condicion emisor_id <> ? es clave: uno marca como leidos los mensajes
  // del OTRO, nunca los propios.
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
