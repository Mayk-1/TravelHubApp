const express = require('express');
const { body } = require('express-validator');
const controlador = require('../controllers/auth.controller');
const { verificarToken } = require('../middleware/auth');
const { asyncHandler, validar } = require('../middleware/errores');

const router = express.Router();

router.post(
  '/registro',
  [
    body('nombre').trim().notEmpty().withMessage('El nombre es obligatorio')
      .isLength({ max: 120 }).withMessage('El nombre es demasiado largo'),
    body('email').trim().isEmail().withMessage('El correo no es valido')
      .normalizeEmail(),
    body('password').isLength({ min: 8 })
      .withMessage('La contrasena debe tener al menos 8 caracteres'),
    body('rol').optional().isIn(['turista', 'prestador'])
      .withMessage('El rol debe ser turista o prestador')
  ],
  validar,
  asyncHandler(controlador.registrar)
);

router.post(
  '/login',
  [
    body('email').trim().isEmail().withMessage('El correo no es valido').normalizeEmail(),
    body('password').notEmpty().withMessage('La contrasena es obligatoria')
  ],
  validar,
  asyncHandler(controlador.login)
);

router.get('/me', verificarToken, asyncHandler(controlador.usuarioActual));

router.post(
  '/dispositivos',
  verificarToken,
  [body('token_fcm').trim().notEmpty().withMessage('Falta el token de FCM')],
  validar,
  asyncHandler(controlador.registrarDispositivo)
);

module.exports = router;
