USE travelhub;

CREATE OR REPLACE VIEW v_catalogo AS
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
