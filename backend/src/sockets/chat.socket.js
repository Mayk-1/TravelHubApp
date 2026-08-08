const { Server } = require('socket.io');
const jwt = require('jsonwebtoken');
const pool = require('../config/db');
const servicio = require('../services/chat.service');

const MAX_LONGITUD = 2000;

function configurarChat(servidorHttp, app) {
  const origenes = (process.env.CORS_ORIGINS || '')
    .split(',').map((o) => o.trim()).filter(Boolean);

  const io = new Server(servidorHttp, {
    cors: { origin: origenes.length ? origenes : '*' },
    pingTimeout: 30000
  });

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

    socket.on('salir', (datos) => {
      const conversacionId = Number(datos?.conversacion_id);
      if (conversacionId) socket.leave(`conversacion:${conversacionId}`);
    });

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

        io.to(`conversacion:${conversacionId}`).emit('mensaje_nuevo', mensaje);
        io.to(`usuario:${acceso.destinatarioUsuarioId}`).emit('mensaje_nuevo', mensaje);

        respuesta?.({ ok: true, mensaje });
      } catch (err) {
        respuesta?.({ ok: false, error: err.message });
        socket.emit('error_chat', { evento: 'mensaje', mensaje: err.message });
      }
    });

    socket.on('escribiendo', (datos) => {
      const conversacionId = Number(datos?.conversacion_id);
      if (!conversacionId) return;
      socket.to(`conversacion:${conversacionId}`).emit('escribiendo', {
        conversacion_id: conversacionId,
        usuario_id: usuarioId,
        activo: datos?.activo !== false
      });
    });

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

  app.set('io', io);

  return io;
}

module.exports = { configurarChat };
