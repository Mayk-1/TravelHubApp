const express = require('express');
const { body, param } = require('express-validator');
const controlador = require('../controllers/itinerarios.controller');
const { verificarToken } = require('../middleware/auth');
const { asyncHandler, validar } = require('../middleware/errores');

const router = express.Router();

router.use(verificarToken);

router.patch(
  '/items/:itemId',
  [param('itemId').isInt({ min: 1 })],
  validar,
  asyncHandler(controlador.actualizarItem)
);

router.delete(
  '/items/:itemId',
  [param('itemId').isInt({ min: 1 })],
  validar,
  asyncHandler(controlador.eliminarItem)
);

router.get('/', asyncHandler(controlador.listar));

router.post(
  '/',
  [
    body('titulo').trim().notEmpty().withMessage('El titulo es obligatorio')
      .isLength({ max: 150 }).withMessage('El titulo es demasiado largo'),
    body('fecha_inicio').isDate().withMessage('fecha_inicio debe tener formato YYYY-MM-DD'),
    body('fecha_fin').isDate().withMessage('fecha_fin debe tener formato YYYY-MM-DD'),
    body('presupuesto_estimado').optional({ nullable: true })
      .isFloat({ min: 0 }).withMessage('El presupuesto debe ser un numero positivo')
  ],
  validar,
  asyncHandler(controlador.crear)
);

router.get('/:id', [param('id').isInt({ min: 1 })], validar, asyncHandler(controlador.detalle));

router.put(
  '/:id',
  [
    param('id').isInt({ min: 1 }),
    body('titulo').optional().trim().notEmpty().withMessage('El titulo no puede quedar vacio'),
    body('fecha_inicio').optional().isDate(),
    body('fecha_fin').optional().isDate()
  ],
  validar,
  asyncHandler(controlador.actualizar)
);

router.delete('/:id', [param('id').isInt({ min: 1 })], validar, asyncHandler(controlador.eliminar));

router.post(
  '/:id/dias/:diaNumero/items',
  [
    param('id').isInt({ min: 1 }),
    param('diaNumero').isInt({ min: 1 }),
    body('reserva_id').optional({ nullable: true }).isInt({ min: 1 }),
    body('latitud').optional({ nullable: true }).isFloat({ min: -90, max: 90 }),
    body('longitud').optional({ nullable: true }).isFloat({ min: -180, max: 180 })
  ],
  validar,
  asyncHandler(controlador.agregarItem)
);

router.put(
  '/:id/dias/:diaNumero/orden',
  [
    param('id').isInt({ min: 1 }),
    param('diaNumero').isInt({ min: 1 }),
    body('items').isArray({ min: 1 }).withMessage('Envia el arreglo "items" con los ids')
  ],
  validar,
  asyncHandler(controlador.reordenarItems)
);

module.exports = router;
