-- ============================================================================
-- TravelHub - Esquema MySQL
-- Plataforma movil integral para la gestion de servicios turisticos
-- Universidad Nacional del Altiplano - Desarrollo de Plataformas 2026
-- ============================================================================
--
-- ESTRATEGIA DE MODELADO
--
-- Los tipos de servicio (guia, hospedaje, alimentacion, transporte,
-- traduccion) comparten muchos atributos (precio, ubicacion, prestador,
-- calificacion) pero cada uno tiene los suyos propios. Se aplica el patron
-- "Class Table Inheritance":
--
--   servicios                <- tabla base con lo comun
--     +-- servicios_hospedaje  <- 1:1, atributos propios de un alojamiento
--     +-- servicios_guia       <- 1:1, atributos propios de un guia
--     +-- servicios_transporte <- 1:1, etc.
--     ...
--
-- Ventaja: el catalogo se consulta con filtros unificados sobre una sola
-- tabla, y agregar un tipo nuevo no obliga a tocar lo existente (requisito
-- no funcional de escalabilidad del enunciado).
--
-- ALCANCE: el MVP implementa guias y hospedaje. Las demas tablas satelite
-- quedan creadas para que el modelo este "preparado para escalar", como
-- pide la seccion 7 del enunciado.
-- ============================================================================

DROP DATABASE IF EXISTS travelhub;
CREATE DATABASE travelhub
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE travelhub;


-- ============================================================================
-- 1. USUARIOS Y ROLES
-- ============================================================================

-- Un solo tabla de usuarios con un rol discriminador. Los datos extra de
-- un prestador viven en `prestadores` (1:1), no aqui, para no dejar
-- columnas nulas en todas las filas de turistas.
CREATE TABLE usuarios (
  id             INT AUTO_INCREMENT PRIMARY KEY,
  nombre         VARCHAR(120)  NOT NULL,
  email          VARCHAR(150)  NOT NULL UNIQUE,
  password_hash  VARCHAR(255)  NOT NULL,
  telefono       VARCHAR(20)   NULL,
  foto_url       VARCHAR(500)  NULL,
  rol            ENUM('turista','prestador','admin') NOT NULL DEFAULT 'turista',
  activo         BOOLEAN       NOT NULL DEFAULT TRUE,
  creado_en      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_usuarios_rol (rol)
) ENGINE=InnoDB;

-- Perfil extendido de quien ofrece servicios.
-- `verificado` lo activa un administrador: es el flujo de aprobacion de
-- prestadores del punto 4.1 del enunciado.
CREATE TABLE prestadores (
  id                INT AUTO_INCREMENT PRIMARY KEY,
  usuario_id        INT           NOT NULL UNIQUE,
  razon_social      VARCHAR(150)  NULL,
  descripcion       TEXT          NULL,
  documento_tipo    ENUM('DNI','RUC','CE','PASAPORTE') NOT NULL DEFAULT 'DNI',
  documento_numero  VARCHAR(20)   NOT NULL,
  ciudad_base       VARCHAR(100)  NOT NULL DEFAULT 'Puno',
  verificado        BOOLEAN       NOT NULL DEFAULT FALSE,
  verificado_por    INT           NULL,
  verificado_en     TIMESTAMP     NULL,
  creado_en         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_prestador_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  -- Si se borra el admin que verifico, el prestador NO se borra: solo se
  -- pierde la referencia de quien lo aprobo.
  CONSTRAINT fk_prestador_verificador
    FOREIGN KEY (verificado_por) REFERENCES usuarios(id) ON DELETE SET NULL,
  UNIQUE KEY uq_prestador_documento (documento_tipo, documento_numero),
  INDEX idx_prestadores_verificado (verificado)
) ENGINE=InnoDB;

-- Tokens de Firebase Cloud Messaging. Un usuario puede tener varios
-- dispositivos, por eso es tabla aparte y no una columna en `usuarios`.
CREATE TABLE dispositivos (
  id             INT AUTO_INCREMENT PRIMARY KEY,
  usuario_id     INT          NOT NULL,
  token_fcm      VARCHAR(255) NOT NULL UNIQUE,
  plataforma     ENUM('android','ios','web') NOT NULL DEFAULT 'android',
  actualizado_en TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_dispositivo_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  INDEX idx_dispositivos_usuario (usuario_id)
) ENGINE=InnoDB;


-- ============================================================================
-- 2. CATALOGO DE SERVICIOS
-- ============================================================================

-- Tabla de consulta en vez de un ENUM: agregar una categoria nueva es un
-- INSERT, no un ALTER TABLE.
CREATE TABLE categorias_servicio (
  id      INT AUTO_INCREMENT PRIMARY KEY,
  slug    VARCHAR(30)  NOT NULL UNIQUE,  -- 'guia', 'hospedaje', ...
  nombre  VARCHAR(60)  NOT NULL,
  icono   VARCHAR(40)  NOT NULL DEFAULT 'place',
  activo  BOOLEAN      NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- Tabla base de la jerarquia de servicios.
CREATE TABLE servicios (
  id                     INT AUTO_INCREMENT PRIMARY KEY,
  prestador_id           INT            NOT NULL,
  categoria_id           INT            NOT NULL,
  titulo                 VARCHAR(150)   NOT NULL,
  descripcion            TEXT           NULL,

  -- Precio y unidad. La unidad importa para la calculadora de costos:
  -- no es lo mismo 80 soles "por noche" que "por persona".
  precio                 DECIMAL(10,2)  NOT NULL,
  moneda                 CHAR(3)        NOT NULL DEFAULT 'PEN',
  unidad_precio          ENUM('por_persona','por_noche','por_dia',
                              'por_servicio','por_hora','por_km')
                         NOT NULL DEFAULT 'por_servicio',

  -- Geolocalizacion para el mapa y el filtro por ubicacion.
  direccion              VARCHAR(255)   NULL,
  ciudad                 VARCHAR(100)   NOT NULL DEFAULT 'Puno',
  latitud                DECIMAL(10,8)  NULL,
  longitud               DECIMAL(11,8)  NULL,

  capacidad_maxima       INT            NOT NULL DEFAULT 1,

  -- Promedio cacheado. Se recalcula al insertar una resena (ver trigger
  -- al final). Se guarda para no hacer un AVG sobre toda la tabla de
  -- resenas cada vez que se lista el catalogo.
  calificacion_promedio  DECIMAL(3,2)   NOT NULL DEFAULT 0.00,
  total_resenas          INT            NOT NULL DEFAULT 0,

  activo                 BOOLEAN        NOT NULL DEFAULT TRUE,
  creado_en              TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP,

  CONSTRAINT fk_servicio_prestador
    FOREIGN KEY (prestador_id) REFERENCES prestadores(id) ON DELETE CASCADE,
  CONSTRAINT fk_servicio_categoria
    FOREIGN KEY (categoria_id) REFERENCES categorias_servicio(id),

  CONSTRAINT chk_servicio_precio       CHECK (precio >= 0),
  CONSTRAINT chk_servicio_capacidad    CHECK (capacidad_maxima > 0),
  CONSTRAINT chk_servicio_calificacion CHECK (calificacion_promedio BETWEEN 0 AND 5),
  CONSTRAINT chk_servicio_latitud      CHECK (latitud  IS NULL OR latitud  BETWEEN  -90 AND  90),
  CONSTRAINT chk_servicio_longitud     CHECK (longitud IS NULL OR longitud BETWEEN -180 AND 180),

  -- Indice compuesto pensado para la consulta mas frecuente del catalogo:
  -- "servicios activos de tal categoria en tal ciudad".
  INDEX idx_servicios_busqueda (activo, categoria_id, ciudad),
  INDEX idx_servicios_precio (precio),
  INDEX idx_servicios_calificacion (calificacion_promedio),
  INDEX idx_servicios_prestador (prestador_id)
) ENGINE=InnoDB;

-- Galeria de fotos. Las imagenes viven en almacenamiento en la nube
-- (S3 / Firebase Storage); aqui solo se guarda la URL.
CREATE TABLE servicio_fotos (
  id           INT AUTO_INCREMENT PRIMARY KEY,
  servicio_id  INT          NOT NULL,
  url          VARCHAR(500) NOT NULL,
  orden        TINYINT      NOT NULL DEFAULT 0,
  CONSTRAINT fk_foto_servicio
    FOREIGN KEY (servicio_id) REFERENCES servicios(id) ON DELETE CASCADE,
  INDEX idx_fotos_servicio (servicio_id, orden)
) ENGINE=InnoDB;

-- Idiomas: los usan tanto guias como traductores, por eso es una relacion
-- M:N contra `servicios` y no una columna en cada tabla satelite.
CREATE TABLE idiomas (
  id      INT AUTO_INCREMENT PRIMARY KEY,
  codigo  CHAR(2)     NOT NULL UNIQUE,   -- ISO 639-1: 'es', 'en', 'qu', 'ay'
  nombre  VARCHAR(50) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE servicio_idiomas (
  servicio_id  INT NOT NULL,
  idioma_id    INT NOT NULL,
  nivel        ENUM('basico','intermedio','avanzado','nativo')
               NOT NULL DEFAULT 'intermedio',
  PRIMARY KEY (servicio_id, idioma_id),
  CONSTRAINT fk_servidioma_servicio
    FOREIGN KEY (servicio_id) REFERENCES servicios(id) ON DELETE CASCADE,
  CONSTRAINT fk_servidioma_idioma
    FOREIGN KEY (idioma_id) REFERENCES idiomas(id) ON DELETE CASCADE
) ENGINE=InnoDB;


-- ----------------------------------------------------------------------------
-- 2.1 Tablas satelite por tipo de servicio
--     La PK es tambien FK: garantiza la relacion 1:1 con `servicios`.
-- ----------------------------------------------------------------------------

-- [MVP]
CREATE TABLE servicios_hospedaje (
  servicio_id         INT PRIMARY KEY,
  tipo_alojamiento    ENUM('hotel','hostal','casa_familiar','departamento','camping')
                      NOT NULL DEFAULT 'hostal',
  habitaciones        INT     NOT NULL DEFAULT 1,
  camas               INT     NOT NULL DEFAULT 1,
  banos               INT     NOT NULL DEFAULT 1,
  wifi                BOOLEAN NOT NULL DEFAULT FALSE,
  desayuno_incluido   BOOLEAN NOT NULL DEFAULT FALSE,
  estacionamiento     BOOLEAN NOT NULL DEFAULT FALSE,
  hora_check_in       TIME    NOT NULL DEFAULT '14:00:00',
  hora_check_out      TIME    NOT NULL DEFAULT '10:00:00',
  CONSTRAINT fk_hospedaje_servicio
    FOREIGN KEY (servicio_id) REFERENCES servicios(id) ON DELETE CASCADE,
  CONSTRAINT chk_hospedaje_habitaciones CHECK (habitaciones > 0)
) ENGINE=InnoDB;

-- [MVP]
CREATE TABLE servicios_guia (
  servicio_id        INT PRIMARY KEY,
  anios_experiencia  INT          NOT NULL DEFAULT 0,
  certificado_url    VARCHAR(500) NULL,
  duracion_horas     DECIMAL(4,1) NOT NULL DEFAULT 4.0,
  tamano_max_grupo   INT          NOT NULL DEFAULT 10,
  incluye_transporte BOOLEAN      NOT NULL DEFAULT FALSE,
  punto_encuentro    VARCHAR(255) NULL,
  CONSTRAINT fk_guia_servicio
    FOREIGN KEY (servicio_id) REFERENCES servicios(id) ON DELETE CASCADE,
  CONSTRAINT chk_guia_experiencia CHECK (anios_experiencia >= 0),
  CONSTRAINT chk_guia_duracion    CHECK (duracion_horas > 0)
) ENGINE=InnoDB;

-- [Trabajo futuro] Creadas para que el modelo escale sin rediseno.
CREATE TABLE servicios_alimentacion (
  servicio_id       INT PRIMARY KEY,
  tipo_cocina       VARCHAR(80)  NOT NULL DEFAULT 'Regional',
  hora_apertura     TIME         NOT NULL DEFAULT '08:00:00',
  hora_cierre       TIME         NOT NULL DEFAULT '22:00:00',
  menu_url          VARCHAR(500) NULL,
  opcion_vegetariana BOOLEAN     NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_alimentacion_servicio
    FOREIGN KEY (servicio_id) REFERENCES servicios(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE servicios_transporte (
  servicio_id          INT PRIMARY KEY,
  tipo_vehiculo        ENUM('auto','van','minibus','bus','lancha','moto')
                       NOT NULL DEFAULT 'auto',
  placa                VARCHAR(15)  NOT NULL,
  asientos             INT          NOT NULL DEFAULT 4,
  aire_acondicionado   BOOLEAN      NOT NULL DEFAULT FALSE,
  con_conductor        BOOLEAN      NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_transporte_servicio
    FOREIGN KEY (servicio_id) REFERENCES servicios(id) ON DELETE CASCADE,
  CONSTRAINT chk_transporte_asientos CHECK (asientos > 0)
) ENGINE=InnoDB;

CREATE TABLE servicios_traduccion (
  servicio_id     INT PRIMARY KEY,
  modalidad       ENUM('presencial','remota','ambas') NOT NULL DEFAULT 'presencial',
  especialidad    VARCHAR(100) NULL,   -- 'turistica', 'medica', 'legal'
  CONSTRAINT fk_traduccion_servicio
    FOREIGN KEY (servicio_id) REFERENCES servicios(id) ON DELETE CASCADE
) ENGINE=InnoDB;


-- ============================================================================
-- 3. DISPONIBILIDAD Y RESERVAS
-- ============================================================================

-- Calendario del punto 4.2. Una fila por servicio y dia.
-- `cupos_ocupados` se incrementa al confirmar una reserva.
CREATE TABLE disponibilidad (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  servicio_id     INT           NOT NULL,
  fecha           DATE          NOT NULL,
  cupos_totales   INT           NOT NULL DEFAULT 1,
  cupos_ocupados  INT           NOT NULL DEFAULT 0,
  -- Permite precios por temporada sin tocar el precio base del servicio.
  precio_especial DECIMAL(10,2) NULL,
  bloqueado       BOOLEAN       NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_disponibilidad_servicio
    FOREIGN KEY (servicio_id) REFERENCES servicios(id) ON DELETE CASCADE,
  -- Evita duplicar el mismo dia para el mismo servicio y permite hacer
  -- upsert con ON DUPLICATE KEY UPDATE.
  UNIQUE KEY uq_disponibilidad (servicio_id, fecha),
  CONSTRAINT chk_cupos CHECK (cupos_ocupados >= 0 AND cupos_ocupados <= cupos_totales),
  INDEX idx_disponibilidad_fecha (fecha)
) ENGINE=InnoDB;

CREATE TABLE reservas (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  -- Codigo legible que se le muestra al usuario, ej. 'TH-2026-000123'.
  codigo           VARCHAR(20)   NOT NULL UNIQUE,
  turista_id       INT           NOT NULL,
  servicio_id      INT           NOT NULL,

  fecha_inicio     DATE          NOT NULL,
  fecha_fin        DATE          NULL,     -- NULL en servicios de un solo dia
  num_personas     INT           NOT NULL DEFAULT 1,

  -- IMPORTANTE: se congela el precio del momento de reservar. Si el
  -- prestador sube su tarifa manana, esta reserva conserva lo pactado.
  precio_unitario  DECIMAL(10,2) NOT NULL,
  cantidad         DECIMAL(6,2)  NOT NULL DEFAULT 1,  -- noches, horas, km...
  subtotal         DECIMAL(10,2) NOT NULL,
  moneda           CHAR(3)       NOT NULL DEFAULT 'PEN',

  estado           ENUM('pendiente','confirmada','cancelada','completada')
                   NOT NULL DEFAULT 'pendiente',
  notas            VARCHAR(500)  NULL,
  motivo_cancelacion VARCHAR(255) NULL,
  creado_en        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                 ON UPDATE CURRENT_TIMESTAMP,

  CONSTRAINT fk_reserva_turista
    FOREIGN KEY (turista_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  -- RESTRICT: no se permite borrar un servicio que tiene reservas; primero
  -- hay que desactivarlo. Protege el historial del turista.
  CONSTRAINT fk_reserva_servicio
    FOREIGN KEY (servicio_id) REFERENCES servicios(id) ON DELETE RESTRICT,

  CONSTRAINT chk_reserva_personas CHECK (num_personas > 0),
  CONSTRAINT chk_reserva_subtotal CHECK (subtotal >= 0),
  CONSTRAINT chk_reserva_fechas   CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio),

  INDEX idx_reservas_turista (turista_id, estado),
  INDEX idx_reservas_servicio (servicio_id, fecha_inicio)
) ENGINE=InnoDB;


-- ============================================================================
-- 4. ITINERARIOS (constructor de ruta de viaje, punto 4.3)
-- ============================================================================

CREATE TABLE itinerarios (
  id                  INT AUTO_INCREMENT PRIMARY KEY,
  turista_id          INT           NOT NULL,
  titulo              VARCHAR(150)  NOT NULL,
  destino             VARCHAR(100)  NOT NULL DEFAULT 'Puno',
  fecha_inicio        DATE          NOT NULL,
  fecha_fin           DATE          NOT NULL,
  presupuesto_estimado DECIMAL(10,2) NULL,
  moneda              CHAR(3)       NOT NULL DEFAULT 'PEN',
  creado_en           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_itinerario_turista
    FOREIGN KEY (turista_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  CONSTRAINT chk_itinerario_fechas CHECK (fecha_fin >= fecha_inicio),
  INDEX idx_itinerarios_turista (turista_id)
) ENGINE=InnoDB;

-- El viaje se organiza dia por dia, como pide el enunciado.
CREATE TABLE itinerario_dias (
  id             INT AUTO_INCREMENT PRIMARY KEY,
  itinerario_id  INT          NOT NULL,
  dia_numero     INT          NOT NULL,
  fecha          DATE         NOT NULL,
  notas          VARCHAR(500) NULL,
  CONSTRAINT fk_dia_itinerario
    FOREIGN KEY (itinerario_id) REFERENCES itinerarios(id) ON DELETE CASCADE,
  UNIQUE KEY uq_dia (itinerario_id, dia_numero),
  CONSTRAINT chk_dia_numero CHECK (dia_numero > 0)
) ENGINE=InnoDB;

-- Cada parada del dia. Puede ser:
--   a) un servicio ya reservado  -> reserva_id NOT NULL
--   b) un punto libre en el mapa -> reserva_id NULL + titulo_libre
CREATE TABLE itinerario_items (
  id                   INT AUTO_INCREMENT PRIMARY KEY,
  dia_id               INT           NOT NULL,
  orden                INT           NOT NULL DEFAULT 1,
  reserva_id           INT           NULL,
  titulo_libre         VARCHAR(150)  NULL,
  latitud              DECIMAL(10,8) NULL,
  longitud             DECIMAL(11,8) NULL,
  hora_inicio          TIME          NULL,
  hora_fin             TIME          NULL,

  -- Resultado cacheado de la Directions API, para no volver a llamarla
  -- cada vez que se abre el itinerario (ahorra cuota y funciona offline,
  -- que es el requisito no funcional de disponibilidad parcial sin red).
  distancia_metros     INT           NULL,
  duracion_segundos    INT           NULL,

  CONSTRAINT fk_item_dia
    FOREIGN KEY (dia_id) REFERENCES itinerario_dias(id) ON DELETE CASCADE,
  -- Si se borra la reserva, el punto sigue en el mapa pero deja de estar
  -- vinculado a un servicio contratado.
  CONSTRAINT fk_item_reserva
    FOREIGN KEY (reserva_id) REFERENCES reservas(id) ON DELETE SET NULL,
  -- Un item tiene que ser una cosa o la otra, no puede estar vacio.
  CONSTRAINT chk_item_contenido
    CHECK (reserva_id IS NOT NULL OR titulo_libre IS NOT NULL),
  INDEX idx_items_dia (dia_id, orden)
) ENGINE=InnoDB;


-- ============================================================================
-- 5. MENSAJERIA (punto 4.5) - persistida en MySQL, entregada por Socket.io
-- ============================================================================

CREATE TABLE conversaciones (
  id             INT AUTO_INCREMENT PRIMARY KEY,
  turista_id     INT       NOT NULL,
  prestador_id   INT       NOT NULL,
  creado_en      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ultimo_mensaje_en TIMESTAMP NULL,
  CONSTRAINT fk_conversacion_turista
    FOREIGN KEY (turista_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  CONSTRAINT fk_conversacion_prestador
    FOREIGN KEY (prestador_id) REFERENCES prestadores(id) ON DELETE CASCADE,
  -- Un solo hilo por par turista-prestador.
  UNIQUE KEY uq_conversacion (turista_id, prestador_id),
  -- Para listar las conversaciones de alguien ordenadas por actividad.
  INDEX idx_conversaciones_actividad (ultimo_mensaje_en)
) ENGINE=InnoDB;

CREATE TABLE mensajes (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  conversacion_id  INT       NOT NULL,
  emisor_id        INT       NOT NULL,
  contenido        TEXT      NOT NULL,
  leido            BOOLEAN   NOT NULL DEFAULT FALSE,
  enviado_en       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_mensaje_conversacion
    FOREIGN KEY (conversacion_id) REFERENCES conversaciones(id) ON DELETE CASCADE,
  CONSTRAINT fk_mensaje_emisor
    FOREIGN KEY (emisor_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  -- Indice clave: cargar el historial de un chat en orden cronologico.
  INDEX idx_mensajes_conversacion (conversacion_id, enviado_en)
) ENGINE=InnoDB;


-- ============================================================================
-- 6. CALIFICACIONES Y RESENAS (punto 4.7)
-- ============================================================================

CREATE TABLE resenas (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  -- UNIQUE: una sola resena por reserva. Es la regla que impide inflar la
  -- calificacion de un servicio resenandolo muchas veces, y ademas obliga
  -- a que quien resena efectivamente haya contratado el servicio.
  reserva_id    INT           NOT NULL UNIQUE,
  turista_id    INT           NOT NULL,
  servicio_id   INT           NOT NULL,
  calificacion  TINYINT       NOT NULL,
  comentario    VARCHAR(1000) NULL,
  creado_en     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_resena_reserva
    FOREIGN KEY (reserva_id) REFERENCES reservas(id) ON DELETE CASCADE,
  CONSTRAINT fk_resena_turista
    FOREIGN KEY (turista_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  CONSTRAINT fk_resena_servicio
    FOREIGN KEY (servicio_id) REFERENCES servicios(id) ON DELETE CASCADE,
  CONSTRAINT chk_resena_calificacion CHECK (calificacion BETWEEN 1 AND 5),
  INDEX idx_resenas_servicio (servicio_id)
) ENGINE=InnoDB;


-- ============================================================================
-- 7. VISTAS DE APOYO
-- ============================================================================

-- Catalogo listo para la app: evita repetir estos JOIN en cada endpoint.
CREATE VIEW v_catalogo AS
SELECT
  s.id,
  s.titulo,
  s.descripcion,
  s.precio,
  s.moneda,
  s.unidad_precio,
  s.ciudad,
  s.latitud,
  s.longitud,
  s.capacidad_maxima,
  s.calificacion_promedio,
  s.total_resenas,
  c.slug        AS categoria_slug,
  c.nombre      AS categoria_nombre,
  c.icono       AS categoria_icono,
  p.id          AS prestador_id,
  u.nombre      AS prestador_nombre,
  u.foto_url    AS prestador_foto,
  p.verificado  AS prestador_verificado,
  (SELECT f.url FROM servicio_fotos f
    WHERE f.servicio_id = s.id
    ORDER BY f.orden LIMIT 1) AS foto_principal
FROM servicios s
JOIN categorias_servicio c ON c.id = s.categoria_id
JOIN prestadores p         ON p.id = s.prestador_id
JOIN usuarios u            ON u.id = p.usuario_id
WHERE s.activo = TRUE;

-- Calculadora de costos (punto 4.4): total del viaje desglosado por
-- categoria de servicio. Es exactamente el desglose que pide el enunciado
-- (hospedaje, alimentacion, guia, transporte, traduccion).
CREATE VIEW v_costos_itinerario AS
SELECT
  i.id            AS itinerario_id,
  i.turista_id,
  c.slug          AS categoria_slug,
  c.nombre        AS categoria_nombre,
  COUNT(r.id)     AS cantidad_reservas,
  SUM(r.subtotal) AS total
FROM itinerarios i
JOIN itinerario_dias  d  ON d.itinerario_id = i.id
JOIN itinerario_items it ON it.dia_id = d.id
JOIN reservas r          ON r.id = it.reserva_id
JOIN servicios s         ON s.id = r.servicio_id
JOIN categorias_servicio c ON c.id = s.categoria_id
WHERE r.estado IN ('pendiente','confirmada','completada')
GROUP BY i.id, i.turista_id, c.slug, c.nombre;


-- ============================================================================
-- 8. TRIGGERS: mantienen el promedio de calificacion sincronizado
-- ============================================================================
-- Se hace con triggers y no en el codigo Node para que el dato no pueda
-- quedar inconsistente si alguien inserta una resena por otra via.

DELIMITER //

CREATE TRIGGER trg_resena_insert
AFTER INSERT ON resenas
FOR EACH ROW
BEGIN
  UPDATE servicios s
  SET s.total_resenas = (
        SELECT COUNT(*) FROM resenas r WHERE r.servicio_id = NEW.servicio_id
      ),
      s.calificacion_promedio = (
        SELECT ROUND(AVG(r.calificacion), 2) FROM resenas r WHERE r.servicio_id = NEW.servicio_id
      )
  WHERE s.id = NEW.servicio_id;
END//

CREATE TRIGGER trg_resena_delete
AFTER DELETE ON resenas
FOR EACH ROW
BEGIN
  UPDATE servicios s
  SET s.total_resenas = (
        SELECT COUNT(*) FROM resenas r WHERE r.servicio_id = OLD.servicio_id
      ),
      s.calificacion_promedio = COALESCE((
        SELECT ROUND(AVG(r.calificacion), 2) FROM resenas r WHERE r.servicio_id = OLD.servicio_id
      ), 0)
  WHERE s.id = OLD.servicio_id;
END//

DELIMITER ;


-- ============================================================================
-- 9. DATOS DE CONFIGURACION (no son datos de prueba: la app los necesita)
-- ============================================================================

INSERT INTO categorias_servicio (slug, nombre, icono) VALUES
  ('guia',         'Guia turistico', 'hiking'),
  ('hospedaje',    'Hospedaje',      'hotel'),
  ('alimentacion', 'Alimentacion',   'restaurant'),
  ('transporte',   'Transporte',     'directions_bus'),
  ('traduccion',   'Traduccion',     'translate');

INSERT INTO idiomas (codigo, nombre) VALUES
  ('es', 'Espanol'),
  ('en', 'Ingles'),
  ('qu', 'Quechua'),
  ('ay', 'Aymara'),
  ('pt', 'Portugues'),
  ('fr', 'Frances');
