USE travelhub;

SET @pw = '$2b$10$jpiy3tN2e.ElqMRt1by9JuTuDpm8jB/k5epkcHtz3UgZr4YScwiSG';

-- contraseñas de prueba: travelhub2026

INSERT INTO usuarios (id, nombre, email, password_hash, telefono, rol) VALUES
  (1, 'Administrador TravelHub', 'admin@travelhub.pe',    @pw, '951000000', 'admin'),
  -- turistas
  (2, 'Camila Rojas',            'camila@example.com',    @pw, '951111111', 'turista'),
  (3, 'Diego Fernandez',         'diego@example.com',     @pw, '951222222', 'turista'),
  (4, 'Sofia Mendoza',           'sofia@example.com',     @pw, '951333333', 'turista'),
  -- prestadores
  (5, 'Julio Mamani',            'julio.guia@example.com', @pw, '952111111', 'prestador'),
  (6, 'Rosa Quispe',             'rosa.hostal@example.com',@pw, '952222222', 'prestador'),
  (7, 'Hotel Titicaca SAC',      'contacto@hoteltiticaca.pe', @pw, '952333333', 'prestador'),
  (8, 'Marco Condori',           'marco.guia@example.com', @pw, '952444444', 'prestador');

INSERT INTO prestadores
  (id, usuario_id, razon_social, descripcion, documento_tipo, documento_numero,
   ciudad_base, estado_verificacion, verificado_por, verificado_en) VALUES
  (1, 5, NULL, 'Guia oficial de turismo con 12 anios recorriendo el lago Titicaca y las islas.',
   'DNI', '01234567', 'Puno', 'aprobado', 1, '2026-06-15 10:00:00'),
  (2, 6, 'Hostal Casa Rosa', 'Hospedaje familiar a tres cuadras de la plaza de armas.',
   'RUC', '20123456781', 'Puno', 'aprobado', 1, '2026-06-16 11:30:00'),
  (3, 7, 'Hotel Titicaca SAC', 'Hotel 3 estrellas con vista al lago.',
   'RUC', '20987654321', 'Puno', 'aprobado', 1, '2026-06-18 09:15:00'),
  -- Pendiente de revision: sirve para demostrar el flujo del admin.
  (4, 8, NULL, 'Guia independiente, rutas de Sillustani y Chucuito.',
   'DNI', '07654321', 'Puno', 'pendiente', NULL, NULL);

INSERT INTO servicios
  (id, prestador_id, categoria_id, titulo, descripcion, precio, unidad_precio,
   direccion, ciudad, latitud, longitud, capacidad_maxima) VALUES
  (1, 1, 1, 'Tour Islas Uros y Taquile - dia completo',
   'Recorrido en lancha por las islas flotantes de los Uros y la isla Taquile, con almuerzo tipico incluido.',
   120.00, 'por_persona', 'Puerto de Puno', 'Puno', -15.84020000, -70.02190000, 15),

  (2, 1, 1, 'City tour Puno y Sillustani',
   'Medio dia: mirador del Condor, catedral, y las chullpas preincaicas de Sillustani.',
   80.00, 'por_persona', 'Plaza de Armas', 'Puno', -15.84040000, -70.02830000, 12),

  (3, 2, 2, 'Habitacion doble - Hostal Casa Rosa',
   'Habitacion doble con bano privado, wifi y desayuno continental incluido.',
   90.00, 'por_noche', 'Jr. Deustua 458', 'Puno', -15.83920000, -70.02710000, 2),

  (4, 3, 2, 'Habitacion matrimonial con vista al lago',
   'Habitacion matrimonial en tercer piso, ventanal con vista al Titicaca. Incluye desayuno buffet.',
   210.00, 'por_noche', 'Av. Costanera 1010', 'Puno', -15.83100000, -70.01500000, 2),

  (5, 3, 2, 'Suite familiar - Hotel Titicaca',
   'Suite para cuatro personas, dos ambientes, calefaccion y estacionamiento.',
   380.00, 'por_noche', 'Av. Costanera 1010', 'Puno', -15.83100000, -70.01500000, 4),

  (6, 4, 1, 'Ruta Chucuito y Aramu Muru',
   'Excursion de medio dia al templo de la fertilidad y la puerta de Aramu Muru.',
   95.00, 'por_persona', 'Terminal zonal', 'Puno', -15.88900000, -69.88700000, 8);

INSERT INTO servicios_guia
  (servicio_id, anios_experiencia, duracion_horas, tamano_max_grupo,
   incluye_transporte, punto_encuentro) VALUES
  (1, 12, 9.0, 15, TRUE,  'Muelle turistico de Puno, 06:45 h'),
  (2, 12, 4.5, 12, TRUE,  'Plaza de Armas, frente a la catedral'),
  (6, 5,  5.0,  8, FALSE, 'Terminal zonal, anden 3');

INSERT INTO servicios_hospedaje
  (servicio_id, tipo_alojamiento, habitaciones, camas, banos,
   wifi, desayuno_incluido, estacionamiento) VALUES
  (3, 'hostal', 1, 2, 1, TRUE, TRUE,  FALSE),
  (4, 'hotel',  1, 1, 1, TRUE, TRUE,  TRUE),
  (5, 'hotel',  2, 3, 2, TRUE, TRUE,  TRUE);

INSERT INTO servicio_idiomas (servicio_id, idioma_id, nivel) VALUES
  (1, 1, 'nativo'), (1, 2, 'avanzado'), (1, 3, 'nativo'), (1, 4, 'intermedio'),
  (2, 1, 'nativo'), (2, 2, 'avanzado'),
  (6, 1, 'nativo'), (6, 3, 'avanzado');


INSERT INTO servicio_fotos (servicio_id, url, orden) VALUES
  (1, 'https://storage.travelhub.pe/servicios/1_uros_01.jpg', 0),
  (1, 'https://storage.travelhub.pe/servicios/1_taquile_02.jpg', 1),
  (2, 'https://storage.travelhub.pe/servicios/2_sillustani_01.jpg', 0),
  (3, 'https://storage.travelhub.pe/servicios/3_casarosa_01.jpg', 0),
  (4, 'https://storage.travelhub.pe/servicios/4_titicaca_01.jpg', 0),
  (5, 'https://storage.travelhub.pe/servicios/5_suite_01.jpg', 0);

INSERT INTO disponibilidad (servicio_id, fecha, cupos_totales, cupos_ocupados) VALUES
  (1, '2026-08-10', 15, 2), (1, '2026-08-11', 15, 0), (1, '2026-08-12', 15, 0),
  (2, '2026-08-10', 12, 0), (2, '2026-08-11', 12, 1), (2, '2026-08-12', 12, 0),
  (3, '2026-08-10',  1, 1), (3, '2026-08-11',  1, 1), (3, '2026-08-12',  1, 0),
  (4, '2026-08-10',  1, 0), (4, '2026-08-11',  1, 0), (4, '2026-08-12',  1, 0),
  (5, '2026-08-10',  1, 0), (5, '2026-08-11',  1, 0), (5, '2026-08-12',  1, 0);

INSERT INTO disponibilidad (servicio_id, fecha, cupos_totales, cupos_ocupados, precio_especial) VALUES
  (3, '2027-02-02', 1, 0, 150.00),
  (4, '2027-02-02', 1, 0, 350.00);

INSERT INTO reservas
  (id, codigo, turista_id, servicio_id, fecha_inicio, fecha_fin,
   num_personas, precio_unitario, cantidad, subtotal, estado, notas) VALUES
  (1, 'TH-2026-000001', 2, 3, '2026-08-10', '2026-08-12', 2,  90.00, 2.00, 180.00, 'confirmada', 'Llegada 21:00 aprox.'),
  (2, 'TH-2026-000002', 2, 1, '2026-08-10', NULL,         2, 120.00, 2.00, 240.00, 'confirmada', 'Dos personas, dieta vegetariana.'),
  (3, 'TH-2026-000003', 2, 2, '2026-08-11', NULL,         2,  80.00, 2.00, 160.00, 'pendiente',  NULL),
  (4, 'TH-2026-000004', 3, 1, '2026-07-05', NULL,         1, 120.00, 1.00, 120.00, 'completada', NULL),
  (5, 'TH-2026-000005', 3, 4, '2026-07-04', '2026-07-06', 1, 210.00, 2.00, 420.00, 'completada', NULL),
  (6, 'TH-2026-000006', 4, 2, '2026-07-20', NULL,         3,  80.00, 3.00, 240.00, 'cancelada',  NULL);

UPDATE reservas SET motivo_cancelacion = 'El turista cambio de fechas' WHERE id = 6;

INSERT INTO itinerarios
  (id, turista_id, titulo, destino, fecha_inicio, fecha_fin, presupuesto_estimado) VALUES
  (1, 2, 'Puno y el Titicaca en 3 dias', 'Puno', '2026-08-10', '2026-08-12', 800.00);

INSERT INTO itinerario_dias (id, itinerario_id, dia_numero, fecha, notas) VALUES
  (1, 1, 1, '2026-08-10', 'Llegada y check-in. Descansar por la altura.'),
  (2, 1, 2, '2026-08-11', 'Dia completo en las islas.'),
  (3, 1, 3, '2026-08-12', 'City tour y retorno.');

INSERT INTO itinerario_items
  (dia_id, orden, reserva_id, titulo_libre, latitud, longitud,
   hora_inicio, hora_fin, distancia_metros, duracion_segundos) VALUES
  -- Dia 1
  (1, 1, NULL, 'Llegada aeropuerto Inca Manco Capac (Juliaca)', -15.46710000, -70.15820000, '18:30:00', '19:00:00', NULL, NULL),
  (1, 2, 1,    NULL, -15.83920000, -70.02710000, '21:00:00', '21:30:00', 44200, 3300),
  -- Dia 2
  (2, 1, 2,    NULL, -15.84020000, -70.02190000, '06:45:00', '16:30:00',  850,  700),
  (2, 2, NULL, 'Cena en el Jiron Lima',          -15.84130000, -70.02760000, '19:30:00', '21:00:00', 1100, 900),
  -- Dia 3
  (3, 1, 3,    NULL, -15.84040000, -70.02830000, '09:00:00', '13:30:00',  400,  350),
  (3, 2, NULL, 'Mirador Kuntur Wasi',            -15.83480000, -70.03360000, '14:30:00', '15:30:00', 1800, 1200);

INSERT INTO conversaciones (id, turista_id, prestador_id, ultimo_mensaje_en) VALUES
  (1, 2, 1, '2026-08-04 09:12:00'),
  (2, 2, 2, '2026-08-03 17:40:00');

INSERT INTO mensajes (conversacion_id, emisor_id, contenido, leido, enviado_en) VALUES
  (1, 2, 'Hola Julio, reserve el tour a los Uros para el 10. A que hora es el punto de encuentro?', TRUE,  '2026-08-04 09:05:00'),
  (1, 5, 'Hola Camila! Nos vemos 06:45 en el muelle turistico. Lleva bloqueador y algo de abrigo.', TRUE,  '2026-08-04 09:10:00'),
  (1, 2, 'Perfecto, ahi estare. Gracias!',                                                          FALSE, '2026-08-04 09:12:00'),
  (2, 2, 'Buenas tardes, tienen estacionamiento en el hostal?',                                     TRUE,  '2026-08-03 17:35:00'),
  (2, 6, 'Hola! No contamos con estacionamiento propio, pero hay una playa a media cuadra.',        FALSE, '2026-08-03 17:40:00');

INSERT INTO resenas (reserva_id, turista_id, servicio_id, calificacion, comentario) VALUES
  (4, 3, 1, 5, 'Julio conoce muchisimo la zona y explica con calma. El almuerzo en Taquile estuvo excelente.'),
  (5, 3, 4, 4, 'Habitacion comoda y la vista al lago es real. El desayuno podria tener mas variedad.');
