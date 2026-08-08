const pool = require('../config/db');
const { fallar } = require('../middleware/errores');

function generarCodigo(id) {
  const anio = new Date().getFullYear();
  return `TH-${anio}-${String(id).padStart(6, '0')}`;
}

/** Diferencia en dias entre dos fechas 'YYYY-MM-DD'. */
function diasEntre(inicio, fin) {
  const ms = new Date(`${fin}T00:00:00Z`) - new Date(`${inicio}T00:00:00Z`);
  return Math.round(ms / 86400000);
}

/** Lista de fechas 'YYYY-MM-DD' entre dos limites, sin incluir `fin`. */
function rangoFechas(inicio, fin) {
  const fechas = [];
  const actual = new Date(`${inicio}T00:00:00Z`);
  const limite = new Date(`${fin}T00:00:00Z`);
  while (actual < limite) {
    fechas.push(actual.toISOString().slice(0, 10));
    actual.setUTCDate(actual.getUTCDate() + 1);
  }
  return fechas;
}

/**
 * Calcula cuantas unidades se cobran segun la unidad de precio del servicio.
 */
function calcularCantidad(unidad, { fecha_inicio, fecha_fin, num_personas, horas, km }) {
  switch (unidad) {
    case 'por_noche': {
      if (!fecha_fin) throw fallar(400, 'Este servicio se cobra por noche: indica fecha_fin');
      const noches = diasEntre(fecha_inicio, fecha_fin);
      if (noches < 1) throw fallar(400, 'La estadia debe ser de al menos una noche');
      return noches;
    }
    case 'por_dia': {
      if (!fecha_fin) return 1;
      return Math.max(1, diasEntre(fecha_inicio, fecha_fin));
    }
    case 'por_persona':
      return num_personas;
    case 'por_hora':
      if (!horas) throw fallar(400, 'Este servicio se cobra por hora: indica "horas"');
      return Number(horas);
    case 'por_km':
      if (!km) throw fallar(400, 'Este servicio se cobra por km: indica "km"');
      return Number(km);
    case 'por_servicio':
    default:
      return 1;
  }
}

/**
 * POST /api/reservas
 */
async function crear(req, res) {
  const { servicio_id, fecha_inicio, fecha_fin = null, num_personas = 1, notas = null, horas, km } = req.body;

  const conexion = await pool.getConnection();
  try {
    await conexion.beginTransaction();

    const [servicios] = await conexion.query(
      `SELECT id, precio, unidad_precio, moneda, capacidad_maxima, activo
       FROM servicios WHERE id = ?`,
      [servicio_id]
    );
    if (servicios.length === 0) throw fallar(404, 'El servicio no existe');

    const servicio = servicios[0];
    if (!servicio.activo) throw fallar(400, 'Este servicio no esta disponible');
    if (num_personas > servicio.capacidad_maxima) {
      throw fallar(400, `Este servicio admite un maximo de ${servicio.capacidad_maxima} personas`);
    }

    const fechasOcupadas = servicio.unidad_precio === 'por_noche' && fecha_fin
      ? rangoFechas(fecha_inicio, fecha_fin)
      : [fecha_inicio];

    const [disponibles] = await conexion.query(
      `SELECT id, fecha, cupos_totales, cupos_ocupados, precio_especial, bloqueado
       FROM disponibilidad
       WHERE servicio_id = ? AND fecha IN (?)
       FOR UPDATE`,
      [servicio_id, fechasOcupadas]
    );

    const porFecha = new Map(disponibles.map((d) => [String(d.fecha), d]));

    for (const fecha of fechasOcupadas) {
      const d = porFecha.get(fecha);
      if (!d) throw fallar(409, `El servicio no tiene disponibilidad publicada para el ${fecha}`);
      if (d.bloqueado) throw fallar(409, `El ${fecha} no esta disponible`);
      if (d.cupos_ocupados + num_personas > d.cupos_totales) {
        throw fallar(409, `No hay cupo suficiente para el ${fecha}`);
      }
    }

    // Si alguna fecha tiene precio especial se usa el mas alto del rango;
    // si no, el precio base del servicio.
    const especiales = fechasOcupadas
      .map((f) => porFecha.get(f).precio_especial)
      .filter((p) => p !== null && p !== undefined);
    const precioUnitario = especiales.length ? Math.max(...especiales.map(Number)) : Number(servicio.precio);

    const cantidad = calcularCantidad(servicio.unidad_precio, {
      fecha_inicio, fecha_fin, num_personas, horas, km
    });
    const subtotal = Number((precioUnitario * cantidad).toFixed(2));

    const [resultado] = await conexion.query(
      `INSERT INTO reservas
        (codigo, turista_id, servicio_id, fecha_inicio, fecha_fin, num_personas,
         precio_unitario, cantidad, subtotal, moneda, estado, notas)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pendiente', ?)`,
      ['TEMP', req.usuario.id, servicio_id, fecha_inicio, fecha_fin, num_personas,
        precioUnitario, cantidad, subtotal, servicio.moneda, notas]
    );
    const reservaId = resultado.insertId;

    // El codigo depende del id autoincremental, asi que se actualiza despues.
    await conexion.query('UPDATE reservas SET codigo = ? WHERE id = ?',
      [generarCodigo(reservaId), reservaId]);

    for (const fecha of fechasOcupadas) {
      await conexion.query(
        'UPDATE disponibilidad SET cupos_ocupados = cupos_ocupados + ? WHERE id = ?',
        [num_personas, porFecha.get(fecha).id]
      );
    }

    await conexion.commit();

    res.status(201).json({
      id: reservaId,
      codigo: generarCodigo(reservaId),
      servicio_id,
      fecha_inicio,
      fecha_fin,
      num_personas,
      precio_unitario: precioUnitario,
      cantidad,
      subtotal,
      moneda: servicio.moneda,
      estado: 'pendiente'
    });
  } catch (err) {
    await conexion.rollback();
    throw err;
  } finally {
    conexion.release();
  }
}

/** GET /api/reservas?estado= — historial del turista (punto 4.2). */
async function mias(req, res) {
  const { estado } = req.query;

  const condiciones = ['r.turista_id = ?'];
  const parametros = [req.usuario.id];

  if (estado) {
    condiciones.push('r.estado = ?');
    parametros.push(estado);
  }

  const [filas] = await pool.query(
    `SELECT r.*, s.titulo AS servicio_titulo, s.ciudad, s.latitud, s.longitud,
            c.slug AS categoria_slug, c.nombre AS categoria_nombre,
            u.nombre AS prestador_nombre,
            (SELECT f.url FROM servicio_fotos f
              WHERE f.servicio_id = s.id ORDER BY f.orden LIMIT 1) AS foto,
            EXISTS(SELECT 1 FROM resenas re WHERE re.reserva_id = r.id) AS tiene_resena
     FROM reservas r
     JOIN servicios s           ON s.id = r.servicio_id
     JOIN categorias_servicio c ON c.id = s.categoria_id
     JOIN prestadores p         ON p.id = s.prestador_id
     JOIN usuarios u            ON u.id = p.usuario_id
     WHERE ${condiciones.join(' AND ')}
     ORDER BY r.fecha_inicio DESC`,
    parametros
  );

  res.json(filas);
}

/** GET /api/reservas/recibidas — panel del prestador (punto 4.6). */
async function recibidas(req, res) {
  const [filas] = await pool.query(
    `SELECT r.*, s.titulo AS servicio_titulo,
            u.nombre AS turista_nombre, u.telefono AS turista_telefono
     FROM reservas r
     JOIN servicios s   ON s.id = r.servicio_id
     JOIN prestadores p ON p.id = s.prestador_id
     JOIN usuarios u    ON u.id = r.turista_id
     WHERE p.usuario_id = ?
     ORDER BY r.creado_en DESC`,
    [req.usuario.id]
  );
  res.json(filas);
}

/**
 * PATCH /api/reservas/:id/estado
 * Body: { estado, motivo }
 *
 * Las transiciones validas se declaran explicitamente: no se puede pasar
 * de "cancelada" a "confirmada", por ejemplo.
 */
const TRANSICIONES = {
  pendiente: ['confirmada', 'cancelada'],
  confirmada: ['completada', 'cancelada'],
  completada: [],
  cancelada: []
};

async function cambiarEstado(req, res) {
  const { id } = req.params;
  const { estado, motivo = null } = req.body;

  const conexion = await pool.getConnection();
  try {
    await conexion.beginTransaction();

    const [filas] = await conexion.query(
      `SELECT r.*, p.usuario_id AS prestador_usuario_id, s.unidad_precio
       FROM reservas r
       JOIN servicios s   ON s.id = r.servicio_id
       JOIN prestadores p ON p.id = s.prestador_id
       WHERE r.id = ?
       FOR UPDATE`,
      [id]
    );
    if (filas.length === 0) throw fallar(404, 'Reserva no encontrada');

    const reserva = filas[0];
    const esTurista = reserva.turista_id === req.usuario.id;
    const esPrestador = reserva.prestador_usuario_id === req.usuario.id;

    if (!esTurista && !esPrestador && req.usuario.rol !== 'admin') {
      throw fallar(403, 'Esta reserva no te corresponde');
    }
    // El turista solo puede cancelar; confirmar y completar son del prestador.
    if (esTurista && !esPrestador && estado !== 'cancelada') {
      throw fallar(403, 'Como turista solo puedes cancelar la reserva');
    }

    const permitidos = TRANSICIONES[reserva.estado] || [];
    if (!permitidos.includes(estado)) {
      throw fallar(400, `No se puede pasar de "${reserva.estado}" a "${estado}"`);
    }

    await conexion.query(
      'UPDATE reservas SET estado = ?, motivo_cancelacion = ? WHERE id = ?',
      [estado, estado === 'cancelada' ? motivo : null, id]
    );

    // Al cancelar se devuelven los cupos que la reserva tenia tomados.
    if (estado === 'cancelada') {
      const fechas = reserva.unidad_precio === 'por_noche' && reserva.fecha_fin
        ? rangoFechas(reserva.fecha_inicio, reserva.fecha_fin)
        : [reserva.fecha_inicio];

      await conexion.query(
        `UPDATE disponibilidad
         SET cupos_ocupados = GREATEST(0, cupos_ocupados - ?)
         WHERE servicio_id = ? AND fecha IN (?)`,
        [reserva.num_personas, reserva.servicio_id, fechas]
      );
    }

    await conexion.commit();
    res.json({ id: Number(id), estado });
  } catch (err) {
    await conexion.rollback();
    throw err;
  } finally {
    conexion.release();
  }
}

/**
 * GET /api/reservas/costos/:itinerarioId
 */
async function costosItinerario(req, res) {
  const { itinerarioId } = req.params;

  const [duenos] = await pool.query(
    'SELECT id, presupuesto_estimado, moneda FROM itinerarios WHERE id = ? AND turista_id = ?',
    [itinerarioId, req.usuario.id]
  );
  if (duenos.length === 0) throw fallar(404, 'Itinerario no encontrado');

  const [desglose] = await pool.query(
    `SELECT categoria_slug, categoria_nombre, cantidad_reservas, total
     FROM v_costos_itinerario
     WHERE itinerario_id = ?
     ORDER BY total DESC`,
    [itinerarioId]
  );

  const total = desglose.reduce((suma, fila) => suma + Number(fila.total), 0);
  const presupuesto = duenos[0].presupuesto_estimado;

  res.json({
    itinerario_id: Number(itinerarioId),
    moneda: duenos[0].moneda,
    desglose,
    total: Number(total.toFixed(2)),
    presupuesto_estimado: presupuesto === null ? null : Number(presupuesto),
    diferencia: presupuesto === null ? null : Number((Number(presupuesto) - total).toFixed(2))
  });
}

module.exports = { crear, mias, recibidas, cambiarEstado, costosItinerario };
