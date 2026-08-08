const { validationResult } = require('express-validator');

function asyncHandler(fn) {
  return (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
}

function validar(req, res, next) {
  const errores = validationResult(req);
  if (!errores.isEmpty()) {
    return res.status(400).json({
      error: errores.array()[0].msg,
      detalles: errores.array().map((e) => ({ campo: e.path, mensaje: e.msg }))
    });
  }
  next();
}

function noEncontrado(req, res) {
  res.status(404).json({ error: `Ruta no encontrada: ${req.method} ${req.originalUrl}` });
}

function manejadorErrores(err, req, res, next) {
  console.error('[error]', err.code || '', err.message);

  const porCodigoMysql = {
    ER_DUP_ENTRY: [409, 'Ese registro ya existe'],
    ER_NO_REFERENCED_ROW_2: [400, 'Se hace referencia a un registro que no existe'],
    ER_ROW_IS_REFERENCED_2: [409, 'No se puede eliminar: hay registros que dependen de este'],
    ER_CHECK_CONSTRAINT_VIOLATED: [400, 'Los datos no cumplen una restriccion de la base de datos'],
    ECONNREFUSED: [503, 'No hay conexion con la base de datos']
  };

  if (porCodigoMysql[err.code]) {
    const [estado, mensaje] = porCodigoMysql[err.code];
    return res.status(estado).json({ error: mensaje });
  }

  const estado = err.status || 500;
  const mensaje = estado === 500 && process.env.NODE_ENV === 'production'
    ? 'Error interno del servidor'
    : err.message || 'Error interno del servidor';

  res.status(estado).json({ error: mensaje });
}

function fallar(status, mensaje) {
  const err = new Error(mensaje);
  err.status = status;
  return err;
}

module.exports = { asyncHandler, validar, noEncontrado, manejadorErrores, fallar };
