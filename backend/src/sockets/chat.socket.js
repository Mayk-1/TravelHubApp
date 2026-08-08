const { Server } = require('socket.io');
const jwt = require('jsonwebtoken');
const pool = require('../config/db');
const servicio = require('../services/chat.service');

/**
 * Chat en tiempo real (punto 4.5 del enunciado).
 *
 * MODELO DE SALAS
 *   usuario:<id>       cada usuario entra a la suya al conectarse. Sirve para
 *                      avisarle de mensajes nuevos aunque no tenga el chat
 *                      abierto (para pintar la insignia de no leidos).
 *   conversacion:<id>  se entra al abrir un chat concreto. Recibe los eventos
 *                      de "escribiendo" y los mensajes de ese hilo.
 *
 * Los mensajes se guardan SIEMPRE en MySQL antes de reenviarse. El socket
 * solo transporta; la fuente de verdad es la base de datos. Si se hiciera al
 * reves, un mensaje enviado con el receptor desconectado se perderia.
 */

const MAX_LONGITUD = 2000;

function configurarChat(servidorHttp, app) {
  const origenes = (process.env.CORS_ORIGINS || '')
    .split(',').map((o) => o.trim()).filter(Boolean);

  const io = new Server(servidorHttp, {
    cors: { origin: origenes.length ? origenes : '*' },
    // La app movil pierde red a menudo; se le da margen antes de darla por
    // desconectada para no cortar el chat en cada bache de senal.
    pingTimeout: 30000
  });

  /**
   * Autenticacion del handshake.
   *
   * El token se valida UNA vez, al conectar. Sin esto, cualquiera con la URL
   * del servidor podria escuchar los eventos. Nunca se confia en un usuario_id
   * que venga en el payload de un evento: siempre se usa socket.usuario, que
   * sale del JWT verificado aqui.
   */
  io.use((socket, next) => {
    const token = socket.handshake.auth?.token
      || (socket.handshake.headers.authorization || '').replace(/^Bearer /, '');

    if (!token) return next(new Error('Falta el token de autenticacion'));

    try {
      const payload = jwt.verify(token, process.env.JWT_SECRET);
      socket.usuario = { id: payload.usuarioId, rol: payload.rol };
      next();
    } catch (err) {
      next(new Error(err.name === 'TokenExpiredError'
        ? 'La sesion expiro'
        : 'Token invalido'));
    }
  });

  io.on('connection', (socket) => {
    const usuarioId = socket.usuario.id;
    socket.join(`usuario:${usuarioId}`);
    console.log(`[socket] conectado usuario ${usuarioId} (${socket.id})`);

    /**
     * unirse — el usuario abre un chat.
     * Se comprueba que participe en el hilo ANTES de meterlo en la sala:
     * si no, podria unirse a cualquier conversacion pasando un id al azar.
     */
    socket.on('unirse', async (datos, respuesta) => {
      try {
        const conversacionId = Number(datos?.conversacion_id);
        if (!conversacionId) throw new Error('Falta conversacion_id');

        const acceso = await servicio.accesoAConversacion(pool, conversacionId, usuarioId);
        if (!acceso) throw new Error('No tienes acceso a esa conversacion');

        socket.join(`conversacion:${conversacionId}`);

        const marcados = await servicio.marcarLeidos(conversacionId, usuarioId);
        if (marcados > 0) {
          // Se avisa al otro para que sus mensajes pasen a "leido".
          io.to(`usuario:${acceso.destinatarioUsuarioId}`).emit('mensajes_leidos', {
            conversacion_id: conversacionId,
            por_usuario_id: usuarioId
          });
        }

        respuesta?.({ ok: true, conversacion_id: conversacionId });
      } catch (err) {
        respuesta?.({ ok: false, error: err.message });
        socket.emit('error_chat', { evento: 'unirse', mensaje: err.message });
      }
    });

    /** salir — el usuario cierra el chat pero sigue conectado a la app. */
    socket.on('salir', (datos) => {
      const conversacionId = Number(datos?.conversacion_id);
      if (conversacionId) socket.leave(`conversacion:${conversacionId}`);
    });

    /**
     * mensaje — envio.
     * El callback `respuesta` le confirma al emisor que quedo guardado, con
     * el id real que le asigno MySQL. La app lo usa para reemplazar el
     * mensaje "enviando..." que pinto de forma optimista.
     */
    socket.on('mensaje', async (datos, respuesta) => {
      try {
        const conversacionId = Number(datos?.conversacion_id);
        const contenido = String(datos?.contenido ?? '').trim();

        if (!conversacionId) throw new Error('Falta conversacion_id');
        if (!contenido) throw new Error('El mensaje no puede estar vacio');
        if (contenido.length > MAX_LONGITUD) {
          throw new Error(`El mensaje supera los ${MAX_LONGITUD} caracteres`);
        }

        const acceso = await servicio.accesoAConversacion(pool, conversacionId, usuarioId);
        if (!acceso) throw new Error('No tienes acceso a esa conversacion');

        const mensaje = await servicio.guardarMensaje(conversacionId, usuarioId, contenido);

        // A la sala del hilo (quien lo tenga abierto) y a la sala personal
        // del destinatario (para la insignia de no leidos si no lo tiene abierto).
        io.to(`conversacion:${conversacionId}`).emit('mensaje_nuevo', mensaje);
        io.to(`usuario:${acceso.destinatarioUsuarioId}`).emit('mensaje_nuevo', mensaje);

        // TODO: si el destinatario no tiene ningun socket conectado, enviar
        // una notificacion push por FCM usando la tabla `dispositivos`.
        respuesta?.({ ok: true, mensaje });
      } catch (err) {
        respuesta?.({ ok: false, error: err.message });
        socket.emit('error_chat', { evento: 'mensaje', mensaje: err.message });
      }
    });

    /**
     * escribiendo — indicador de "esta escribiendo...".
     * No toca la base de datos: es efimero por definicion. Se usa
     * socket.to(...) y no io.to(...) para no reenviarselo al propio emisor.
     */
    socket.on('escribiendo', (datos) => {
      const conversacionId = Number(datos?.conversacion_id);
      if (!conversacionId) return;
      // No hace falta comprobar permisos: solo llega a quienes ya estan en la
      // sala, y para entrar en la sala ya se verifico el acceso en 'unirse'.
      socket.to(`conversacion:${conversacionId}`).emit('escribiendo', {
        conversacion_id: conversacionId,
        usuario_id: usuarioId,
        activo: datos?.activo !== false
      });
    });

    /** marcar_leidos — cuando el usuario vuelve a la pantalla del chat. */
    socket.on('marcar_leidos', async (datos, respuesta) => {
      try {
        const conversacionId = Number(datos?.conversacion_id);
        const acceso = await servicio.accesoAConversacion(pool, conversacionId, usuarioId);
        if (!acceso) throw new Error('No tienes acceso a esa conversacion');

        const marcados = await servicio.marcarLeidos(conversacionId, usuarioId);
        io.to(`usuario:${acceso.destinatarioUsuarioId}`).emit('mensajes_leidos', {
          conversacion_id: conversacionId,
          por_usuario_id: usuarioId
        });

        respuesta?.({ ok: true, marcados });
      } catch (err) {
        respuesta?.({ ok: false, error: err.message });
      }
    });

    socket.on('disconnect', (motivo) => {
      console.log(`[socket] desconectado usuario ${usuarioId} (${motivo})`);
    });
  });

  // Se guarda en la app para que los controllers REST puedan emitir eventos
  // (ver enviarMensaje en chat.controller.js).
  app.set('io', io);

  return io;
}

module.exports = { configurarChat };
