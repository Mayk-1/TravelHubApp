const express = require('express');
const { body, param, query } = require('express-validator');
const controlador = require('../controllers/admin.controller');
const { verificarToken, exigirRol } = require('../middleware/auth');
const { asyncHandler, validar } = require('../middleware/errores');

const router = express.Router();

router.use(verificarToken, exigirRol('admin'));

// --- Tablero ---
router.get('/metricas', asyncHandler(controlador.metricas));

// --- Prestadores ---
router.get(
  '/prestadores',
  [query('estado').optional().isIn(['pendiente', 'aprobado', 'rechazado'])
    .withMessage('Estado invalido')],
  validar,
  asyncHandler(controlador.listarPrestadores)
);

router.patch(
  '/prestadores/:id/verificacion',
  [
    param('id').isInt({ min: 1 }),
    body('estado').isIn(['pendiente', 'aprobado', 'rechazado'])
      .withMessage('El estado debe ser pendiente, aprobado o rechazado'),
    body('motivo').optional({ nullable: true }).isLength({ max: 255 })
      .withMessage('El motivo no puede superar los 255 caracteres')
  ],
  validar,
  asyncHandler(controlador.cambiarVerificacion)
);

// --- Usuarios ---
router.get(
  '/usuarios',
  [query('rol').optional().isIn(['turista', 'prestador', 'admin'])
    .withMessage('Rol invalido')],
  validar,
  asyncHandler(controlador.listarUsuarios)
);

router.patch(
  '/usuarios/:id/activo',
  [
    param('id').isInt({ min: 1 }),
    body('activo').isBoolean().withMessage('El campo "activo" debe ser true o false')
  ],
  validar,
  asyncHandler(controlador.cambiarActivo)
);

// --- Servicios ---
router.get('/servicios', asyncHandler(controlador.listarServicios));

router.patch(
  '/servicios/:id/activo',
  [
    param('id').isInt({ min: 1 }),
    body('activo').isBoolean().withMessage('El campo "activo" debe ser true o false')
  ],
  validar,
  asyncHandler(controlador.cambiarActivoServicio)
);

module.exports = router;
