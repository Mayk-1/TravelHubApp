const express = require('express');
const { body, param } = require('express-validator');
const controlador = require('../controllers/reservas.controller');
const { verificarToken, exigirRol } = require('../middleware/auth');
const { asyncHandler, validar } = require('../middleware/errores');

const router = express.Router();

router.use(verificarToken);

router.get('/recibidas', exigirRol('prestador'), asyncHandler(controlador.recibidas));

router.get('/costos/:itinerarioId',
  [param('itinerarioId').isInt({ min: 1 })],
  validar,
  asyncHandler(controlador.costosItinerario)
);

router.get('/', asyncHandler(controlador.mias));

router.post(
  '/',
  [
    body('servicio_id').isInt({ min: 1 }).withMessage('Servicio invalido'),
    body('fecha_inicio').isDate().withMessage('fecha_inicio debe tener formato YYYY-MM-DD'),
    body('fecha_fin').optional({ nullable: true }).isDate()
      .withMessage('fecha_fin debe tener formato YYYY-MM-DD'),
    body('num_personas').optional().isInt({ min: 1 })
      .withMessage('num_personas debe ser al menos 1')
  ],
  validar,
  asyncHandler(controlador.crear)
);

router.patch(
  '/:id/estado',
  [
    param('id').isInt({ min: 1 }),
    body('estado').isIn(['confirmada', 'cancelada', 'completada'])
      .withMessage('Estado invalido')
  ],
  validar,
  asyncHandler(controlador.cambiarEstado)
);

module.exports = router;
