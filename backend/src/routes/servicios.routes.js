const express = require('express');
const { body } = require('express-validator');
const controlador = require('../controllers/servicios.controller');
const { verificarToken, exigirRol } = require('../middleware/auth');
const { asyncHandler, validar } = require('../middleware/errores');

const router = express.Router();

router.get('/categorias', asyncHandler(controlador.categorias));

router.get('/mios', verificarToken, exigirRol('prestador'), asyncHandler(controlador.mios));

router.get('/', asyncHandler(controlador.listar));
router.get('/:id', asyncHandler(controlador.detalle));
router.get('/:id/disponibilidad', asyncHandler(controlador.disponibilidad));

router.post(
  '/',
  verificarToken,
  exigirRol('prestador'),
  [
    body('categoria_id').isInt({ min: 1 }).withMessage('Categoria invalida'),
    body('titulo').trim().notEmpty().withMessage('El titulo es obligatorio')
      .isLength({ max: 150 }).withMessage('El titulo es demasiado largo'),
    body('precio').isFloat({ min: 0 }).withMessage('El precio debe ser un numero positivo'),
    body('unidad_precio').optional()
      .isIn(['por_persona', 'por_noche', 'por_dia', 'por_servicio', 'por_hora', 'por_km'])
      .withMessage('Unidad de precio invalida'),
    body('latitud').optional({ nullable: true }).isFloat({ min: -90, max: 90 }),
    body('longitud').optional({ nullable: true }).isFloat({ min: -180, max: 180 }),
    body('capacidad_maxima').optional().isInt({ min: 1 })
  ],
  validar,
  asyncHandler(controlador.crear)
);

router.put(
  '/:id/disponibilidad',
  verificarToken,
  exigirRol('prestador'),
  [body('fechas').isArray({ min: 1 }).withMessage('Debes enviar al menos una fecha')],
  validar,
  asyncHandler(controlador.guardarDisponibilidad)
);

module.exports = router;
