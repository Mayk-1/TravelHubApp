const express = require('express');
const { body, param } = require('express-validator');
const controlador = require('../controllers/resenas.controller');
const { verificarToken } = require('../middleware/auth');
const { asyncHandler, validar } = require('../middleware/errores');

const router = express.Router();

router.get(
  '/servicio/:servicioId',
  [param('servicioId').isInt({ min: 1 })],
  validar,
  asyncHandler(controlador.porServicio)
);

router.use(verificarToken);

router.get('/mias', asyncHandler(controlador.mias));
router.get('/pendientes', asyncHandler(controlador.pendientes));

router.post(
  '/',
  [
    body('reserva_id').isInt({ min: 1 }).withMessage('Reserva invalida'),
    body('calificacion').isInt({ min: 1, max: 5 })
      .withMessage('La calificacion debe estar entre 1 y 5'),
    body('comentario').optional({ nullable: true }).isLength({ max: 1000 })
      .withMessage('El comentario no puede superar los 1000 caracteres')
  ],
  validar,
  asyncHandler(controlador.crear)
);

router.put(
  '/:id',
  [
    param('id').isInt({ min: 1 }),
    body('calificacion').isInt({ min: 1, max: 5 })
      .withMessage('La calificacion debe estar entre 1 y 5'),
    body('comentario').optional({ nullable: true }).isLength({ max: 1000 })
  ],
  validar,
  asyncHandler(controlador.actualizar)
);

router.delete('/:id', [param('id').isInt({ min: 1 })], validar, asyncHandler(controlador.eliminar));

module.exports = router;
