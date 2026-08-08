const pool = require('../config/db');
const { fallar } = require('../middleware/errores');

/** Lista de fechas 'YYYY-MM-DD' entre dos limites, incluyendo ambos. */
function rangoInclusivo(inicio, fin) {
  const fechas = [];
  const actual = new Date(`${inicio}T00:00:00Z`);
  const limite = new Date(`${fin}T00:00:00Z`);
  while (actual <= limite) {
    fechas.push(actual.toISOString().slice(0, 10));
    actual.setUTCDate(actual.getUTCDate() + 1);
  }
  return fechas;
}

/**
 * Comprueba que el itinerario exista y sea del usuario autenticado.
 * Se usa al inicio de casi todos los endpoints: sin esto, cualquiera podria
 * leer o modificar el viaje de otra persona pasando un id distinto.
 */
async function exigirPropiedad(conexion, itinerarioId, usuarioId) {
  const [filas] = await conexion.query(
    'SELECT * FROM itinerarios WHERE id = ? AND turista_id = ?',
    [itinerarioId, usuarioId]
  );
  if (filas.length === 0) {
    // 404 en vez de 403: no se le confirma a un extrano que el itinerario existe.
    throw fallar(404, 'Itinerario no encontrado');
  }
  return filas[0];
}

/** Igual, pero partiendo de un item: sube hasta el itinerario por los JOIN. */
async function exigirPropiedadItem(conexion, itemId, usuarioId) {
  const [filas] = await conexion.query(
    `SELECT it.*, d.itinerario_id
     FROM itinerario_items it
     JOIN itinerario_dias d ON d.id = it.dia_id
     JOIN itinerarios i     ON i.id = d.itinerario_id
     WHERE it.id = ? AND i.turista_id = ?`,
    [itemId, usuarioId]
  );
  if (filas.length === 0) throw fallar(404, 'Elemento del itinerario no encontrado');
  return filas[0];
}


/** GET /api/itinerarios — lista resumida de los viajes del turista. */
async function listar(req, res) {
  const [filas] = await pool.query(
    `SELECT i.*,
            DATEDIFF(i.fecha_fin, i.fecha_inicio) + 1 AS dias,
            (SELECT COUNT(*) FROM itinerario_dias d
               JOIN itinerario_items it ON it.dia_id = d.id
              WHERE d.itinerario_id = i.id) AS total_paradas,
            (SELECT COALESCE(SUM(r.subtotal), 0)
               FROM itinerario_dias d
               JOIN itinerario_items it ON it.dia_id = d.id
               JOIN reservas r          ON r.id = it.reserva_id
              WHERE d.itinerario_id = i.id
                AND r.estado IN ('pendiente','confirmada','completada')) AS costo_actual
     FROM itinerarios i
     WHERE i.turista_id = ?
     ORDER BY i.fecha_inicio DESC`,
    [req.usuario.id]
  );
  res.json(filas);
}


/**
 * POST /api/itinerarios
 *
 * Al crear el viaje se generan automaticamente las filas de
 * `itinerario_dias` para todo el rango de fechas. Asi la app puede pintar
 * el calendario dia por dia de inmediato, sin que el usuario tenga que
 * crearlos a mano uno por uno.
 */
async function crear(req, res) {
  const {
    titulo, destino = 'Puno', fecha_inicio, fecha_fin,
    presupuesto_estimado = null, moneda = 'PEN'
  } = req.body;

  const fechas = rangoInclusivo(fecha_inicio, fecha_fin);
  if (fechas.length === 0) {
    throw fallar(400, 'La fecha de fin debe ser igual o posterior a la de inicio');
  }
  if (fechas.length > 60) {
    throw fallar(400, 'Un itinerario no puede abarcar mas de 60 dias');
  }

  const conexion = await pool.getConnection();
  try {
    await conexion.beginTransaction();

    const [resultado] = await conexion.query(
      `INSERT INTO itinerarios
        (turista_id, titulo, destino, fecha_inicio, fecha_fin, presupuesto_estimado, moneda)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      [req.usuario.id, titulo, destino, fecha_inicio, fecha_fin, presupuesto_estimado, moneda]
    );
    const itinerarioId = resultado.insertId;

    // Insercion en lote: una sola consulta en vez de N.
    const valores = fechas.map((f, i) => [itinerarioId, i + 1, f]);
    await conexion.query(
      'INSERT INTO itinerario_dias (itinerario_id, dia_numero, fecha) VALUES ?',
      [valores]
    );

    await conexion.commit();

    res.status(201).json({
      id: itinerarioId,
      titulo,
      destino,
      fecha_inicio,
      fecha_fin,
      presupuesto_estimado,
      moneda,
      dias_generados: fechas.length
    });
  } catch (err) {
    await conexion.rollback();
    throw err;
  } finally {
    conexion.release();
  }
}


/**
 * GET /api/itinerarios/:id
 * Detalle completo: el viaje, sus dias y las paradas de cada dia, con los
 * datos del servicio reservado cuando la parada corresponde a una reserva.
 */
async function detalle(req, res) {
  const itinerario = await exigirPropiedad(pool, req.params.id, req.usuario.id);

  const [dias] = await pool.query(
    'SELECT * FROM itinerario_dias WHERE itinerario_id = ? ORDER BY dia_numero',
    [itinerario.id]
  );

  const [items] = await pool.query(
    `SELECT it.*,
            r.codigo        AS reserva_codigo,
            r.estado        AS reserva_estado,
            r.subtotal      AS reserva_subtotal,
            r.num_personas,
            s.id            AS servicio_id,
            s.titulo        AS servicio_titulo,
            c.slug          AS categoria_slug,
            c.icono         AS categoria_icono,
            (SELECT f.url FROM servicio_fotos f
              WHERE f.servicio_id = s.id ORDER BY f.orden LIMIT 1) AS foto
     FROM itinerario_items it
     JOIN itinerario_dias d       ON d.id = it.dia_id
     LEFT JOIN reservas r         ON r.id = it.reserva_id
     LEFT JOIN servicios s        ON s.id = r.servicio_id
     LEFT JOIN categorias_servicio c ON c.id = s.categoria_id
     WHERE d.itinerario_id = ?
     ORDER BY d.dia_numero, it.orden`,
    [itinerario.id]
  );

  // Se agrupan las paradas dentro de su dia para que la app reciba el
  // arbol ya armado y no tenga que cruzarlo en el cliente.
  const porDia = new Map(dias.map((d) => [d.id, { ...d, items: [] }]));
  for (const item of items) {
    const dia = porDia.get(item.dia_id);
    if (dia) dia.items.push(item);
  }

  res.json({ ...itinerario, dias: [...porDia.values()] });
}


/**
 * PUT /api/itinerarios/:id
 *
 * Si cambian las fechas hay que reconstruir los dias: se agregan los que
 * faltan y se borran los que quedaron fuera del nuevo rango. Borrar un dia
 * arrastra sus paradas por el ON DELETE CASCADE, asi que se avisa cuantas
 * se perderian antes de hacerlo.
 */
async function actualizar(req, res) {
  const conexion = await pool.getConnection();
  try {
    await conexion.beginTransaction();

    const actual = await exigirPropiedad(conexion, req.params.id, req.usuario.id);

    const {
      titulo = actual.titulo,
      destino = actual.destino,
      fecha_inicio = actual.fecha_inicio,
      fecha_fin = actual.fecha_fin,
      presupuesto_estimado = actual.presupuesto_estimado,
      forzar = false
    } = req.body;

    const cambianFechas = fecha_inicio !== actual.fecha_inicio || fecha_fin !== actual.fecha_fin;

    if (cambianFechas) {
      const nuevas = rangoInclusivo(fecha_inicio, fecha_fin);
      if (nuevas.length === 0) throw fallar(400, 'Rango de fechas invalido');
      if (nuevas.length > 60) throw fallar(400, 'Un itinerario no puede abarcar mas de 60 dias');

      const [sobrantes] = await conexion.query(
        `SELECT d.id, COUNT(it.id) AS paradas
         FROM itinerario_dias d
         LEFT JOIN itinerario_items it ON it.dia_id = d.id
         WHERE d.itinerario_id = ? AND d.fecha NOT IN (?)
         GROUP BY d.id`,
        [actual.id, nuevas]
      );

      const paradasEnPeligro = sobrantes.reduce((s, f) => s + Number(f.paradas), 0);
      if (paradasEnPeligro > 0 && !forzar) {
        throw fallar(409,
          `El nuevo rango de fechas eliminaria ${paradasEnPeligro} parada(s). ` +
          'Vuelve a enviar la peticion con "forzar": true para confirmar.');
      }

      if (sobrantes.length > 0) {
        await conexion.query(
          'DELETE FROM itinerario_dias WHERE id IN (?)',
          [sobrantes.map((f) => f.id)]
        );
      }

      // Renumera y crea los dias que falten, sin tocar los que ya existian
      // (asi conservan sus paradas).
      for (let i = 0; i < nuevas.length; i++) {
        await conexion.query(
          `INSERT INTO itinerario_dias (itinerario_id, dia_numero, fecha)
           VALUES (?, ?, ?)
           ON DUPLICATE KEY UPDATE fecha = VALUES(fecha)`,
          [actual.id, i + 1, nuevas[i]]
        );
      }
    }

    await conexion.query(
      `UPDATE itinerarios
       SET titulo = ?, destino = ?, fecha_inicio = ?, fecha_fin = ?, presupuesto_estimado = ?
       WHERE id = ?`,
      [titulo, destino, fecha_inicio, fecha_fin, presupuesto_estimado, actual.id]
    );

    await conexion.commit();
    res.json({ id: actual.id, titulo, destino, fecha_inicio, fecha_fin, presupuesto_estimado });
  } catch (err) {
    await conexion.rollback();
    throw err;
  } finally {
    conexion.release();
  }
}


/** DELETE /api/itinerarios/:id */
async function eliminar(req, res) {
  await exigirPropiedad(pool, req.params.id, req.usuario.id);
  // Los dias y sus paradas se van por ON DELETE CASCADE. Las reservas NO:
  // el itinerario_items tiene ON DELETE SET NULL hacia reservas, asi que el
  // historial de reservas del turista queda intacto.
  await pool.query('DELETE FROM itinerarios WHERE id = ?', [req.params.id]);
  res.status(204).send();
}


/**
 * POST /api/itinerarios/:id/dias/:diaNumero/items
 *
 * Agrega una parada al dia. Puede ser una reserva ya hecha (reserva_id) o
 * un punto libre del mapa (titulo_libre + coordenadas).
 */
async function agregarItem(req, res) {
  const { id, diaNumero } = req.params;
  const {
    reserva_id = null, titulo_libre = null,
    latitud = null, longitud = null,
    hora_inicio = null, hora_fin = null
  } = req.body;

  if (!reserva_id && !titulo_libre) {
    throw fallar(400, 'Indica una reserva_id o un titulo_libre para la parada');
  }

  const conexion = await pool.getConnection();
  try {
    await conexion.beginTransaction();

    await exigirPropiedad(conexion, id, req.usuario.id);

    const [dias] = await conexion.query(
      'SELECT id FROM itinerario_dias WHERE itinerario_id = ? AND dia_numero = ?',
      [id, diaNumero]
    );
    if (dias.length === 0) throw fallar(404, `El itinerario no tiene un dia ${diaNumero}`);
    const diaId = dias[0].id;

    // La reserva tiene que ser del mismo turista: si no, cualquiera podria
    // meter la reserva de otra persona en su itinerario y ver su costo.
    let lat = latitud;
    let lng = longitud;
    let titulo = titulo_libre;
    if (reserva_id) {
      const [reservas] = await conexion.query(
        `SELECT r.id, s.titulo, s.latitud, s.longitud
         FROM reservas r
         JOIN servicios s ON s.id = r.servicio_id
         WHERE r.id = ? AND r.turista_id = ?`,
        [reserva_id, req.usuario.id]
      );
      if (reservas.length === 0) throw fallar(404, 'Reserva no encontrada');

      const [repetidas] = await conexion.query(
        `SELECT it.id FROM itinerario_items it
         JOIN itinerario_dias d ON d.id = it.dia_id
         WHERE d.itinerario_id = ? AND it.reserva_id = ?`,
        [id, reserva_id]
      );
      if (repetidas.length > 0) {
        throw fallar(409, 'Esa reserva ya esta en el itinerario');
      }

      // Si no mandaron coordenadas, se heredan las del servicio.
      lat = lat ?? reservas[0].latitud;
      lng = lng ?? reservas[0].longitud;

      // Y se guarda tambien el titulo del servicio. Asi, si algun dia se
      // borra la reserva (la FK hace SET NULL), la parada conserva su
      // nombre en el mapa en vez de quedarse en blanco.
      titulo = titulo ?? reservas[0].titulo;
    }

    const [[{ siguiente }]] = await conexion.query(
      'SELECT COALESCE(MAX(orden), 0) + 1 AS siguiente FROM itinerario_items WHERE dia_id = ?',
      [diaId]
    );

    const [resultado] = await conexion.query(
      `INSERT INTO itinerario_items
        (dia_id, orden, reserva_id, titulo_libre, latitud, longitud, hora_inicio, hora_fin)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [diaId, siguiente, reserva_id, titulo, lat, lng, hora_inicio, hora_fin]
    );

    await conexion.commit();
    res.status(201).json({ id: resultado.insertId, dia_id: diaId, orden: siguiente });
  } catch (err) {
    await conexion.rollback();
    throw err;
  } finally {
    conexion.release();
  }
}


/**
 * PATCH /api/itinerarios/items/:itemId
 *
 * Sirve para mover la parada de hora, y tambien para que la app guarde el
 * resultado de la Directions API (distancia y duracion desde la parada
 * anterior). Cachearlo evita volver a llamar a Google cada vez que se abre
 * el itinerario y permite verlo sin conexion.
 */
async function actualizarItem(req, res) {
  const item = await exigirPropiedadItem(pool, req.params.itemId, req.usuario.id);

  const {
    titulo_libre = item.titulo_libre,
    latitud = item.latitud,
    longitud = item.longitud,
    hora_inicio = item.hora_inicio,
    hora_fin = item.hora_fin,
    distancia_metros = item.distancia_metros,
    duracion_segundos = item.duracion_segundos
  } = req.body;

  await pool.query(
    `UPDATE itinerario_items
     SET titulo_libre = ?, latitud = ?, longitud = ?, hora_inicio = ?, hora_fin = ?,
         distancia_metros = ?, duracion_segundos = ?
     WHERE id = ?`,
    [titulo_libre, latitud, longitud, hora_inicio, hora_fin,
      distancia_metros, duracion_segundos, item.id]
  );

  res.json({ id: item.id, mensaje: 'Parada actualizada' });
}


/**
 * PUT /api/itinerarios/:id/dias/:diaNumero/orden
 * Body: { items: [12, 8, 15] }  <- ids en el orden deseado
 *
 * Es lo que se dispara cuando el usuario arrastra las paradas en la app.
 */
async function reordenarItems(req, res) {
  const { id, diaNumero } = req.params;
  const { items } = req.body;

  if (!Array.isArray(items) || items.length === 0) {
    throw fallar(400, 'Envia un arreglo "items" con los ids en el orden deseado');
  }

  const conexion = await pool.getConnection();
  try {
    await conexion.beginTransaction();

    await exigirPropiedad(conexion, id, req.usuario.id);

    const [dias] = await conexion.query(
      'SELECT id FROM itinerario_dias WHERE itinerario_id = ? AND dia_numero = ?',
      [id, diaNumero]
    );
    if (dias.length === 0) throw fallar(404, `El itinerario no tiene un dia ${diaNumero}`);
    const diaId = dias[0].id;

    // Todos los ids enviados tienen que pertenecer a ese dia. Sin esta
    // comprobacion se podria reordenar (y por tanto tocar) items ajenos.
    const [existentes] = await conexion.query(
      'SELECT id FROM itinerario_items WHERE dia_id = ?',
      [diaId]
    );
    const idsValidos = new Set(existentes.map((f) => f.id));

    if (items.length !== idsValidos.size || !items.every((i) => idsValidos.has(Number(i)))) {
      throw fallar(400, 'La lista debe contener exactamente los ids de las paradas de ese dia');
    }

    for (let i = 0; i < items.length; i++) {
      await conexion.query(
        'UPDATE itinerario_items SET orden = ? WHERE id = ?',
        [i + 1, items[i]]
      );
    }

    await conexion.commit();
    res.json({ mensaje: 'Orden actualizado', total: items.length });
  } catch (err) {
    await conexion.rollback();
    throw err;
  } finally {
    conexion.release();
  }
}


/** DELETE /api/itinerarios/items/:itemId */
async function eliminarItem(req, res) {
  const item = await exigirPropiedadItem(pool, req.params.itemId, req.usuario.id);

  const conexion = await pool.getConnection();
  try {
    await conexion.beginTransaction();

    await conexion.query('DELETE FROM itinerario_items WHERE id = ?', [item.id]);

    // Se renumera lo que queda para que no queden huecos en el orden.
    const [restantes] = await conexion.query(
      'SELECT id FROM itinerario_items WHERE dia_id = ? ORDER BY orden',
      [item.dia_id]
    );
    for (let i = 0; i < restantes.length; i++) {
      await conexion.query('UPDATE itinerario_items SET orden = ? WHERE id = ?',
        [i + 1, restantes[i].id]);
    }

    await conexion.commit();
    res.status(204).send();
  } catch (err) {
    await conexion.rollback();
    throw err;
  } finally {
    conexion.release();
  }
}


module.exports = {
  listar, crear, detalle, actualizar, eliminar,
  agregarItem, actualizarItem, reordenarItems, eliminarItem
};
