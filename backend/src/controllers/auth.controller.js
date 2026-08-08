const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const pool = require('../config/db');
const { fallar } = require('../middleware/errores');

const RONDAS_SALT = 10;

function firmarToken(usuario) {
  return jwt.sign(
    { usuarioId: usuario.id, rol: usuario.rol },
    process.env.JWT_SECRET,
    { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
  );
}

function usuarioPublico(u) {
  return {
    id: u.id,
    nombre: u.nombre,
    email: u.email,
    rol: u.rol,
    telefono: u.telefono ?? null,
    foto_url: u.foto_url ?? null
  };
}

async function registrar(req, res) {
  const { nombre, email, password, rol = 'turista', telefono = null } = req.body;

  if (rol === 'admin') {
    throw fallar(403, 'No es posible registrarse como administrador');
  }

  const conexion = await pool.getConnection();
  try {
    await conexion.beginTransaction();

    const [existentes] = await conexion.query(
      'SELECT id FROM usuarios WHERE email = ?',
      [email]
    );
    if (existentes.length > 0) {
      throw fallar(409, 'Ese correo ya esta registrado');
    }

    const passwordHash = await bcrypt.hash(password, RONDAS_SALT);

    const [resultado] = await conexion.query(
      `INSERT INTO usuarios (nombre, email, password_hash, telefono, rol)
       VALUES (?, ?, ?, ?, ?)`,
      [nombre, email, passwordHash, telefono, rol]
    );
    const usuarioId = resultado.insertId;

    if (rol === 'prestador') {
      const { documento_tipo = 'DNI', documento_numero, ciudad_base = 'Puno' } = req.body;
      if (!documento_numero) {
        throw fallar(400, 'Un prestador debe indicar su numero de documento');
      }
      await conexion.query(
        `INSERT INTO prestadores (usuario_id, documento_tipo, documento_numero, ciudad_base)
         VALUES (?, ?, ?, ?)`,
        [usuarioId, documento_tipo, documento_numero, ciudad_base]
      );
    }

    await conexion.commit();

    const usuario = { id: usuarioId, nombre, email, rol, telefono, foto_url: null };
    res.status(201).json({ token: firmarToken(usuario), usuario: usuarioPublico(usuario) });
  } catch (err) {
    await conexion.rollback();
    throw err;
  } finally {
    conexion.release();
  }
}

/** POST /api/auth/login */
async function login(req, res) {
  const { email, password } = req.body;

  const [filas] = await pool.query(
    `SELECT id, nombre, email, password_hash, rol, telefono, foto_url, activo
     FROM usuarios WHERE email = ?`,
    [email]
  );

  if (filas.length === 0) {
    throw fallar(401, 'Correo o contraseña incorrectos');
  }

  const usuario = filas[0];

  if (!usuario.activo) {
    throw fallar(403, 'Esta cuenta esta desactivada');
  }

  const passwordValida = await bcrypt.compare(password, usuario.password_hash);
  if (!passwordValida) {
    throw fallar(401, 'Correo o contraseña incorrectos');
  }

  res.json({ token: firmarToken(usuario), usuario: usuarioPublico(usuario) });
}

/** GET /api/auth/me */
async function usuarioActual(req, res) {
  const [filas] = await pool.query(
    `SELECT id, nombre, email, rol, telefono, foto_url, creado_en
     FROM usuarios WHERE id = ?`,
    [req.usuario.id]
  );

  if (filas.length === 0) {
    throw fallar(404, 'Usuario no encontrado');
  }

  const usuario = filas[0];

  if (usuario.rol === 'prestador') {
    const [prestadores] = await pool.query(
      `SELECT id, razon_social, descripcion, ciudad_base,
              estado_verificacion, motivo_rechazo
       FROM prestadores WHERE usuario_id = ?`,
      [usuario.id]
    );
    usuario.prestador = prestadores[0] || null;
  }

  res.json(usuario);
}

async function registrarDispositivo(req, res) {
  const { token_fcm, plataforma = 'android' } = req.body;

  await pool.query(
    `INSERT INTO dispositivos (usuario_id, token_fcm, plataforma)
     VALUES (?, ?, ?)
     ON DUPLICATE KEY UPDATE usuario_id = VALUES(usuario_id),
                             actualizado_en = CURRENT_TIMESTAMP`,
    [req.usuario.id, token_fcm, plataforma]
  );

  res.status(204).send();
}

module.exports = { registrar, login, usuarioActual, registrarDispositivo };
