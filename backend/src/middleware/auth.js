const jwt = require('jsonwebtoken');

function verificarToken(req, res, next) {
  const cabecera = req.headers.authorization || '';

  if (!cabecera.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Falta el token de autenticacion' });
  }

  const token = cabecera.slice(7).trim();

  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET);
    req.usuario = {
      id: payload.usuarioId,
      rol: payload.rol
    };
    next();
  } catch (err) {
    if (err.name === 'TokenExpiredError') {
      return res.status(401).json({ error: 'La sesion expiro, inicia sesion de nuevo' });
    }
    return res.status(401).json({ error: 'Token invalido' });
  }
}

function exigirRol(...rolesPermitidos) {
  return (req, res, next) => {
    if (!req.usuario) {
      return res.status(401).json({ error: 'No autenticado' });
    }
    if (!rolesPermitidos.includes(req.usuario.rol)) {
      return res.status(403).json({ error: 'No tienes permiso para realizar esta accion' });
    }
    next();
  };
}

module.exports = { verificarToken, exigirRol };
