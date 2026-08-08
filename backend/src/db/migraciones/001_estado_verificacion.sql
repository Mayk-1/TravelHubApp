-- ============================================================================
-- Migracion 001 — prestadores.verificado (BOOLEAN) -> estado_verificacion (ENUM)
--
-- Motivo: un booleano no distingue "nunca revisado" de "revisado y
-- rechazado". El panel de administrador necesita esa diferencia para saber
-- que solicitudes tiene pendientes.
--
-- Solo hace falta ejecutarla si YA creaste la base con la version anterior
-- del schema. Si la creas desde cero con schema.sql, ya viene con el ENUM.
--
--   mysql -u root -p travelhub < src/db/migraciones/001_estado_verificacion.sql
-- ============================================================================

USE travelhub;

-- 1. Columnas nuevas
ALTER TABLE prestadores
  ADD COLUMN estado_verificacion ENUM('pendiente','aprobado','rechazado')
    NOT NULL DEFAULT 'pendiente' AFTER ciudad_base,
  ADD COLUMN motivo_rechazo VARCHAR(255) NULL AFTER estado_verificacion;

-- 2. Traspaso de los datos existentes.
--    Los que estaban en TRUE pasan a 'aprobado'; los que estaban en FALSE
--    se consideran 'pendiente' (no habia forma de saber si fueron rechazados).
UPDATE prestadores SET estado_verificacion = 'aprobado' WHERE verificado = TRUE;
UPDATE prestadores SET estado_verificacion = 'pendiente' WHERE verificado = FALSE;

-- 3. Se rehace el indice y se elimina la columna vieja
ALTER TABLE prestadores DROP INDEX idx_prestadores_verificado;
ALTER TABLE prestadores DROP COLUMN verificado;
ALTER TABLE prestadores ADD INDEX idx_prestadores_estado (estado_verificacion);

-- 4. La vista referenciaba p.verificado, hay que recrearla
DROP VIEW IF EXISTS v_catalogo;

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
  (p.estado_verificacion = 'aprobado') AS prestador_verificado,
  (SELECT f.url FROM servicio_fotos f
    WHERE f.servicio_id = s.id
    ORDER BY f.orden LIMIT 1) AS foto_principal
FROM servicios s
JOIN categorias_servicio c ON c.id = s.categoria_id
JOIN prestadores p         ON p.id = s.prestador_id
JOIN usuarios u            ON u.id = p.usuario_id
WHERE s.activo = TRUE;
