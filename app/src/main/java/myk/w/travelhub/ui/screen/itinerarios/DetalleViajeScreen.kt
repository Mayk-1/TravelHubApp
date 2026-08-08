package myk.w.travelhub.ui.screen.itinerarios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import myk.w.travelhub.data.model.response.CostoCategoria
import myk.w.travelhub.data.model.response.CostosResponse
import myk.w.travelhub.data.model.response.DiaResponse
import myk.w.travelhub.data.model.response.ItinerarioDetalleResponse
import myk.w.travelhub.data.model.response.ParadaResponse
import myk.w.travelhub.ui.common.PreviewClaroOscuro
import myk.w.travelhub.ui.theme.TravelHubTheme

@Composable
fun DetalleViajeScreen(
    itinerarioId: Int,
    onVolver: () -> Unit = {},
    viewModel: DetalleViajeViewModel = viewModel()
) {
    LaunchedEffect(itinerarioId) { viewModel.cargar(itinerarioId) }

    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val agregar by viewModel.agregar.collectAsStateWithLifecycle()

    DetalleViajeContenido(
        estado = estado,
        agregar = agregar,
        onVolver = onVolver,
        onReintentar = viewModel::reintentar,
        onAgregarADia = viewModel::abrirAgregar,
        onCerrarAgregar = viewModel::cerrarAgregar,
        onElegirReserva = viewModel::agregarReserva
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleViajeContenido(
    estado: DetalleViajeUiState,
    agregar: AgregarParadaState = AgregarParadaState(),
    onVolver: () -> Unit = {},
    onReintentar: () -> Unit = {},
    onAgregarADia: (Int) -> Unit = {},
    onCerrarAgregar: () -> Unit = {},
    onElegirReserva: (Int) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (estado) {
                            is DetalleViajeUiState.Exito -> estado.viaje.titulo
                            else -> "Viaje"
                        },
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (estado) {
                is DetalleViajeUiState.Cargando -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is DetalleViajeUiState.Error -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            estado.mensaje,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onReintentar) { Text("Reintentar") }
                    }
                }

                is DetalleViajeUiState.Exito -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Cabecera(estado.viaje) }

                    if (estado.costos != null) {
                        item { TarjetaCostos(estado.costos) }
                    }

                    items(estado.viaje.dias.size) { i ->
                        BloqueDia(estado.viaje.dias[i], onAgregar = onAgregarADia)
                    }
                }
            }
        }
    }

    if (agregar.visible) {
        DialogoAgregarParada(
            estado = agregar,
            onCerrar = onCerrarAgregar,
            onElegir = onElegirReserva
        )
    }
}

@Composable
private fun DialogoAgregarParada(
    estado: AgregarParadaState,
    onCerrar: () -> Unit,
    onElegir: (Int) -> Unit
) {
    Dialog(onDismissRequest = { if (!estado.enviando) onCerrar() }) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp).heightIn(max = 520.dp)) {
                Text("Anadir al dia ${estado.diaNumero}",
                    style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Elige una de tus reservas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                when {
                    estado.cargando -> Box(
                        Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    estado.reservas.isEmpty() -> Text(
                        "No tienes reservas disponibles para anadir. " +
                                "Reserva un servicio desde el catalogo primero.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 320.dp)
                    ) {
                        items(estado.reservas.size) { i ->
                            val r = estado.reservas[i]
                            Card(
                                onClick = { if (!estado.enviando) onElegir(r.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        r.servicioTitulo ?: r.codigo,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${r.fechaInicio} · ${r.estadoLegible}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            r.subtotalFormateado,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (estado.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(estado.error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCerrar, enabled = !estado.enviando) { Text("Cerrar") }
                }
            }
        }
    }
}


@Composable
private fun Cabecera(viaje: ItinerarioDetalleResponse) {
    Column {
        Text(
            "${viaje.destino} · ${viaje.fechaInicio} al ${viaje.fechaFin}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${viaje.dias.size} dias",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TarjetaCostos(costos: CostosResponse) {
    Card(colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )) {
        Column(Modifier.padding(16.dp)) {
            Text("Costo del viaje", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Text(
                costos.formatear(costos.total),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (costos.excedido) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )

            if (costos.hayPresupuesto && costos.presupuesto != null) {
                Spacer(Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { (costos.total / costos.presupuesto).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (costos.excedido) {
                        "Te pasaste ${costos.formatear(-(costos.diferencia ?: 0.0))} del presupuesto"
                    } else {
                        "Te quedan ${costos.formatear(costos.diferencia ?: 0.0)} de ${costos.formatear(costos.presupuesto)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (costos.excedido) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (costos.desglose.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                costos.desglose.forEach { FilaCategoria(it, costos) }
            }
        }
    }
}


@Composable
private fun FilaCategoria(categoria: CostoCategoria, costos: CostosResponse) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "${categoria.nombre} (${categoria.cantidadReservas})",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            costos.formatear(categoria.total),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}


@Composable
private fun BloqueDia(dia: DiaResponse, onAgregar: (Int) -> Unit = {}) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${dia.numero}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Dia ${dia.numero}", style = MaterialTheme.typography.titleSmall)
                Text(
                    dia.fecha,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onAgregar(dia.numero) }) {
                Icon(Icons.Filled.Add, contentDescription = "Anadir parada al dia ${dia.numero}")
            }
        }

        if (dia.notas != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                dia.notas,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 38.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        if (dia.items.isEmpty()) {
            Text(
                "Sin paradas todavia",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 38.dp)
            )
        } else {
            dia.items.forEach { parada -> FilaParada(parada) }
        }
    }
}


@Composable
private fun FilaParada(parada: ParadaResponse) {
    Row(modifier = Modifier.padding(start = 13.dp)) {

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (parada.esReserva) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
            )
            Box(
                Modifier
                    .width(2.dp)
                    .height(52.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.padding(bottom = 8.dp)) {
            Text(parada.nombre, style = MaterialTheme.typography.bodyMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (parada.horario.isNotEmpty()) {
                    Text(
                        parada.horario,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (parada.trayecto != null) {
                    if (parada.horario.isNotEmpty()) Spacer(Modifier.width(8.dp))
                    Text(
                        parada.trayecto!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (parada.esReserva && parada.reservaEstado != null) {
                Text(
                    "Reserva ${parada.reservaCodigo} · ${parada.reservaEstado}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


private val viajeDemo = ItinerarioDetalleResponse(
    id = 1,
    titulo = "Puno y el Titicaca en 3 dias",
    destino = "Puno",
    fechaInicio = "2026-08-10",
    fechaFin = "2026-08-12",
    presupuesto = 800.0,
    dias = listOf(
        DiaResponse(
            id = 1, numero = 1, fecha = "2026-08-10",
            notas = "Llegada y check-in. Descansar por la altura.",
            items = listOf(
                ParadaResponse(
                    id = 1, orden = 1,
                    tituloLibre = "Llegada aeropuerto Inca Manco Capac",
                    horaInicio = "18:30:00", horaFin = "19:00:00"
                ),
                ParadaResponse(
                    id = 2, orden = 2, reservaId = 1,
                    reservaCodigo = "TH-2026-000001", reservaEstado = "confirmada",
                    servicioTitulo = "Habitacion doble - Hostal Casa Rosa",
                    horaInicio = "21:00:00", horaFin = "21:30:00",
                    distanciaMetros = 44200, duracionSegundos = 3300
                )
            )
        ),
        DiaResponse(
            id = 2, numero = 2, fecha = "2026-08-11",
            notas = "Dia completo en las islas.",
            items = listOf(
                ParadaResponse(
                    id = 3, orden = 1, reservaId = 2,
                    reservaCodigo = "TH-2026-000002", reservaEstado = "confirmada",
                    servicioTitulo = "Tour Islas Uros y Taquile",
                    horaInicio = "06:45:00", horaFin = "16:30:00",
                    distanciaMetros = 850, duracionSegundos = 700
                )
            )
        ),
        DiaResponse(id = 3, numero = 3, fecha = "2026-08-12", items = emptyList())
    )
)

private val costosDemo = CostosResponse(
    itinerarioId = 1,
    desglose = listOf(
        CostoCategoria("guia", "Guia turistico", 2, 400.0),
        CostoCategoria("hospedaje", "Hospedaje", 1, 180.0)
    ),
    total = 580.0,
    presupuesto = 800.0,
    diferencia = 220.0
)

@Composable
private fun Envoltorio(contenido: @Composable () -> Unit) {
    TravelHubTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) { contenido() }
    }
}

@PreviewClaroOscuro
@Composable
private fun DetalleViajePreview() = Envoltorio {
    DetalleViajeContenido(DetalleViajeUiState.Exito(viajeDemo, costosDemo))
}

@PreviewClaroOscuro
@Composable
private fun DetalleExcedidoPreview() = Envoltorio {
    DetalleViajeContenido(
        DetalleViajeUiState.Exito(
            viajeDemo,
            costosDemo.copy(total = 950.0, diferencia = -150.0)
        )
    )
}

@PreviewClaroOscuro
@Composable
private fun DetalleSinCostosPreview() = Envoltorio {
    DetalleViajeContenido(DetalleViajeUiState.Exito(viajeDemo, null))
}

@PreviewClaroOscuro
@Composable
private fun DetalleErrorPreview() = Envoltorio {
    DetalleViajeContenido(DetalleViajeUiState.Error("No se pudo conectar con el servidor."))
}
