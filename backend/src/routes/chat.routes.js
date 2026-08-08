const express = require('express');
const { body, param } = require('express-validator');
const controlador = require('../controllers/chat.controller');
const { verificarToken } = require('../middleware/auth');
const { asyncHandler, validar } = require('../middleware/errores');

const router = express.Router();

router.use(verificarToken);

router.get('/no-leidos', asyncHandler(controlador.totalNoLeidos));

router.get('/conversaciones', asyncHandler(controlador.listarConversaciones));

router.post(
  '/conversaciones',
  [
    body('prestador_id').optional().isInt({ min: 1 }),
    body('servicio_id').optional().isInt({ min: 1 })
  ],
  validar,
  asyncHandler(controlador.abrirConversacion)
);

router.get(
  '/conversaciones/:id/mensajes',
  [param('id').isInt({ min: 1 })],
  validar,
  asyncHandler(controlador.historial)
);

router.post(
  '/conversaciones/:id/mensajes',
  [
    param('id').isInt({ min: 1 }),
    body('contenido').trim().notEmpty().withMessage('El mensaje no puede estar vacio')
      .isLength({ max: 2000 }).withMessage('El mensaje es demasiado largo')
  ],
  validar,
  asyncHandler(controlador.enviarMensaje)
);

router.patch(
  '/conversaciones/:id/leidos',
  [param('id').isInt({ min: 1 })],
  validar,
  asyncHandler(controlador.marcarLeidos)
);

module.exports = router;
