
DROP DATABASE IF EXISTS travelhub;
CREATE DATABASE travelhub
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE travelhub;

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
CREATE TABLE prestadores (
  id                INT AUTO_INCREMENT PRIMARY KEY,
  usuario_id        INT           NOT NULL UNIQUE,
  razon_social      VARCHAR(150)  NULL,
  descripcion       TEXT          NULL,
  documento_tipo    ENUM('DNI','RUC','CE','PASAPORTE') NOT NULL DEFAULT 'DNI',
  documento_numero  VARCHAR(20)   NOT NULL,
  ciudad_base       VARCHAR(100)  NOT NULL DEFAULT 'Puno',
  estado_verificacion ENUM('pendiente','aprobado','rechazado')
                    NOT NULL DEFAULT 'pendiente',
  motivo_rechazo    VARCHAR(255)  NULL,
  verificado_por    INT           NULL,
  verificado_en     TIMESTAMP     NULL,
  creado_en         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_prestador_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  CONSTRAINT fk_prestador_verificador
    FOREIGN KEY (verificado_por) REFERENCES usuarios(id) ON DELETE SET NULL,
  UNIQUE KEY uq_prestador_documento (documento_tipo, documento_numero),
  INDEX idx_prestadores_estado (estado_verificacion)
) ENGINE=InnoDB;

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

CREATE TABLE categorias_servicio (
  id      INT AUTO_INCREMENT PRIMARY KEY,
  slug    VARCHAR(30)  NOT NULL UNIQUE,
  nombre  VARCHAR(60)  NOT NULL,
  icono   VARCHAR(40)  NOT NULL DEFAULT 'place',
  activo  BOOLEAN      NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

CREATE TABLE servicios (
  id                     INT AUTO_INCREMENT PRIMARY KEY,
  prestador_id           INT            NOT NULL,
  categoria_id           INT            NOT NULL,
  titulo                 VARCHAR(150)   NOT NULL,
  descripcion            TEXT           NULL,
  precio                 DECIMAL(10,2)  NOT NULL,
  moneda                 CHAR(3)        NOT NULL DEFAULT 'PEN',
  unidad_precio          ENUM('por_persona','por_noche','por_dia',
                              'por_servicio','por_hora','por_km')
                         NOT NULL DEFAULT 'por_servicio',
  direccion              VARCHAR(255)   NULL,
  ciudad                 VARCHAR(100)   NOT NULL DEFAULT 'Puno',
  latitud                DECIMAL(10,8)  NULL,
  longitud               DECIMAL(11,8)  NULL,

  capacidad_maxima       INT            NOT NULL DEFAULT 1,
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

  INDEX idx_servicios_busqueda (activo, categoria_id, ciudad),
  INDEX idx_servicios_precio (precio),
  INDEX idx_servicios_calificacion (calificacion_promedio),
  INDEX idx_servicios_prestador (prestador_id)
) ENGINE=InnoDB;

CREATE TABLE servicio_fotos (
  id           INT AUTO_INCREMENT PRIMARY KEY,
  servicio_id  INT          NOT NULL,
  url          VARCHAR(500) NOT NULL,
  orden        TINYINT      NOT NULL DEFAULT 0,
  CONSTRAINT fk_foto_servicio
    FOREIGN KEY (servicio_id) REFERENCES servicios(id) ON DELETE CASCADE,
  INDEX idx_fotos_servicio (servicio_id, orden)
) ENGINE=InnoDB;

CREATE TABLE idiomas (
  id      INT AUTO_INCREMENT PRIMARY KEY,
  codigo  CHAR(2)     NOT NULL UNIQUE,
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
  especialidad    VARCHAR(100) NULL,
  CONSTRAINT fk_traduccion_servicio
    FOREIGN KEY (servicio_id) REFERENCES servicios(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE disponibilidad (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  servicio_id     INT           NOT NULL,
  fecha           DATE          NOT NULL,
  cupos_totales   INT           NOT NULL DEFAULT 1,
  cupos_ocupados  INT           NOT NULL DEFAULT 0,
  precio_especial DECIMAL(10,2) NULL,
  bloqueado       BOOLEAN       NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_disponibilidad_servicio
    FOREIGN KEY (servicio_id) REFERENCES servicios(id) ON DELETE CASCADE,
  UNIQUE KEY uq_disponibilidad (servicio_id, fecha),
  CONSTRAINT chk_cupos CHECK (cupos_ocupados >= 0 AND cupos_ocupados <= cupos_totales),
  INDEX idx_disponibilidad_fecha (fecha)
) ENGINE=InnoDB;

CREATE TABLE reservas (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  codigo           VARCHAR(20)   NOT NULL UNIQUE,
  turista_id       INT           NOT NULL,
  servicio_id      INT           NOT NULL,

  fecha_inicio     DATE          NOT NULL,
  fecha_fin        DATE          NULL,
  num_personas     INT           NOT NULL DEFAULT 1,

  precio_unitario  DECIMAL(10,2) NOT NULL,
  cantidad         DECIMAL(6,2)  NOT NULL DEFAULT 1,
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
  CONSTRAINT fk_reserva_servicio
    FOREIGN KEY (servicio_id) REFERENCES servicios(id) ON DELETE RESTRICT,

  CONSTRAINT chk_reserva_personas CHECK (num_personas > 0),
  CONSTRAINT chk_reserva_subtotal CHECK (subtotal >= 0),
  CONSTRAINT chk_reserva_fechas   CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio),

  INDEX idx_reservas_turista (turista_id, estado),
  INDEX idx_reservas_servicio (servicio_id, fecha_inicio)
) ENGINE=InnoDB;

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

  distancia_metros     INT           NULL,
  duracion_segundos    INT           NULL,

  CONSTRAINT fk_item_dia
    FOREIGN KEY (dia_id) REFERENCES itinerario_dias(id) ON DELETE CASCADE,
  CONSTRAINT fk_item_reserva
    FOREIGN KEY (reserva_id) REFERENCES reservas(id) ON DELETE SET NULL,

  INDEX idx_items_dia (dia_id, orden)
) ENGINE=InnoDB;

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
  UNIQUE KEY uq_conversacion (turista_id, prestador_id),
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
  INDEX idx_mensajes_conversacion (conversacion_id, enviado_en)
) ENGINE=InnoDB;

CREATE TABLE resenas (
  id            INT AUTO_INCREMENT PRIMARY KEY,
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

CREATE VIEW v_catalogo AS
SELECT
  s.id,
  s.titulo,
  s.descripcion,
  s.precio,
  s.moneda,
  s.unidad_precio,
  s.direccion,
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
  (p.estado_verificacion = 'aprobado') AS prestador_verificado,
  (SELECT f.url FROM servicio_fotos f
    WHERE f.servicio_id = s.id
    ORDER BY f.orden LIMIT 1) AS foto_principal
FROM servicios s
JOIN categorias_servicio c ON c.id = s.categoria_id
JOIN prestadores p         ON p.id = s.prestador_id
JOIN usuarios u            ON u.id = p.usuario_id
WHERE s.activo = TRUE;

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

CREATE TRIGGER trg_item_contenido_insert
BEFORE INSERT ON itinerario_items
FOR EACH ROW
BEGIN
  IF NEW.reserva_id IS NULL AND (NEW.titulo_libre IS NULL OR NEW.titulo_libre = '') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Una parada necesita una reserva o un titulo';
  END IF;
END//

CREATE TRIGGER trg_item_contenido_update
BEFORE UPDATE ON itinerario_items
FOR EACH ROW
BEGIN
  IF NEW.reserva_id IS NULL AND (NEW.titulo_libre IS NULL OR NEW.titulo_libre = '') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Una parada necesita una reserva o un titulo';
  END IF;
END//

DELIMITER ;

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
