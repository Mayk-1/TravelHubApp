package myk.w.travelhub.ui.screen.servicio

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import myk.w.travelhub.data.model.response.DetalleEspecifico
import myk.w.travelhub.data.model.response.DisponibilidadResponse
import myk.w.travelhub.data.model.response.IdiomaResponse
import myk.w.travelhub.data.model.response.ResenaResponse
import myk.w.travelhub.data.model.response.ReservaResponse
import myk.w.travelhub.data.model.response.ServicioDetalleResponse
import myk.w.travelhub.ui.common.PreviewClaroOscuro
import myk.w.travelhub.ui.theme.TravelHubTheme

@Composable
fun DetalleServicioScreen(
    servicioId: Int,
    onVolver: () -> Unit = {},
    onReservaCreada: () -> Unit = {},
    viewModel: DetalleServicioViewModel = viewModel()
) {
    LaunchedEffect(servicioId) { viewModel.cargar(servicioId) }

    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val reserva by viewModel.reserva.collectAsStateWithLifecycle()

    DetalleServicioContenido(
        estado = estado,
        reserva = reserva,
        subtotalEstimado = viewModel.subtotalEstimado(),
        onVolver = onVolver,
        onReintentar = viewModel::reintentar,
        onAbrirReserva = viewModel::abrirReserva,
        onCerrarReserva = {
            val creada = reserva.reservaCreada
            viewModel.cerrarReserva()
            if (creada != null) onReservaCreada()
        },
        onFechaChange = viewModel::onFechaChange,
        onFechaFinChange = viewModel::onFechaFinChange,
        onPersonasChange = viewModel::onPersonasChange,
        onNotasChange = viewModel::onNotasChange,
        onConfirmar = viewModel::confirmarReserva
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleServicioContenido(
    estado: DetalleServicioUiState,
    reserva: ReservaFormState,
    subtotalEstimado: Double = 0.0,
    onVolver: () -> Unit = {},
    onReintentar: () -> Unit = {},
    onAbrirReserva: () -> Unit = {},
    onCerrarReserva: () -> Unit = {},
    onFechaChange: (String) -> Unit = {},
    onFechaFinChange: (String) -> Unit = {},
    onPersonasChange: (Int) -> Unit = {},
    onNotasChange: (String) -> Unit = {},
    onConfirmar: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Servicio", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        bottomBar = {
            if (estado is DetalleServicioUiState.Exito) {
                BarraReservar(estado.servicio, onAbrirReserva)
            }
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (estado) {
                is DetalleServicioUiState.Cargando -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is DetalleServicioUiState.Error -> Box(
                    Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(estado.mensaje, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onReintentar) { Text("Reintentar") }
                    }
                }

                is DetalleServicioUiState.Exito -> Ficha(estado.servicio)
            }
        }
    }

    if (reserva.visible && estado is DetalleServicioUiState.Exito) {
        DialogoReserva(
            servicio = estado.servicio,
            reserva = reserva,
            subtotalEstimado = subtotalEstimado,
            onCerrar = onCerrarReserva,
            onFechaChange = onFechaChange,
            onFechaFinChange = onFechaFinChange,
            onPersonasChange = onPersonasChange,
            onNotasChange = onNotasChange,
            onConfirmar = onConfirmar
        )
    }
}


@Composable
private fun Ficha(servicio: ServicioDetalleResponse) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (servicio.fotoPrincipal != null) {
                AsyncImage(
                    model = servicio.fotoPrincipal,
                    contentDescription = servicio.titulo,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(Modifier.padding(16.dp)) {
            Text(servicio.categoriaNombre, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(servicio.titulo, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (servicio.totalResenas > 0) {
                    Icon(Icons.Filled.Star, null, Modifier.size(16.dp), tint = Color(0xFFF5A623))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "%.1f · ${servicio.totalResenas} resenas".format(servicio.calificacion),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    servicio.direccion?.let { "$it, ${servicio.ciudad}" } ?: servicio.ciudad,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(servicio.prestadorNombre, style = MaterialTheme.typography.bodyMedium)
                if (servicio.prestadorVerificado) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Verified, "Verificado", Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (servicio.descripcion != null) {
                Spacer(Modifier.height(16.dp))
                Text(servicio.descripcion, style = MaterialTheme.typography.bodyMedium)
            }

            if (servicio.atributos.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("Detalles", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                servicio.atributos.forEach { (etiqueta, valor) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(etiqueta, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(valor, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (servicio.idiomas.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Idiomas", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    servicio.idiomas.forEach { idioma ->
                        AssistChip(
                            onClick = {},
                            label = { Text("${idioma.nombre} · ${idioma.nivel}") }
                        )
                    }
                }
            }

            if (servicio.resenas.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("Resenas", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                servicio.resenas.forEach { FilaResena(it) }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}


@Composable
private fun FilaResena(resena: ResenaResponse) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(resena.calificacion) {
                    Icon(Icons.Filled.Star, null, Modifier.size(12.dp), tint = Color(0xFFF5A623))
                }
                Spacer(Modifier.width(8.dp))
                Text(resena.turistaNombre, style = MaterialTheme.typography.labelMedium)
            }
            if (resena.comentario != null) {
                Spacer(Modifier.height(4.dp))
                Text(resena.comentario, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}


@Composable
private fun BarraReservar(servicio: ServicioDetalleResponse, onReservar: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(servicio.precioFormateado, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                if (servicio.unidadLegible.isNotEmpty()) {
                    Text(servicio.unidadLegible, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onReservar) { Text("Reservar") }
        }
    }
}


@Composable
private fun DialogoReserva(
    servicio: ServicioDetalleResponse,
    reserva: ReservaFormState,
    subtotalEstimado: Double,
    onCerrar: () -> Unit,
    onFechaChange: (String) -> Unit,
    onFechaFinChange: (String) -> Unit,
    onPersonasChange: (Int) -> Unit,
    onNotasChange: (String) -> Unit,
    onConfirmar: () -> Unit
) {
    Dialog(onDismissRequest = { if (!reserva.enviando) onCerrar() }) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(
                Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 560.dp)
            ) {
                if (reserva.reservaCreada != null) {
                    ConfirmacionReserva(reserva.reservaCreada, onCerrar)
                    return@Column
                }

                Text("Reservar", style = MaterialTheme.typography.titleLarge)
                Text(servicio.titulo, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(16.dp))

                when {
                    reserva.cargandoFechas -> Box(
                        Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    reserva.fechasDisponibles.isEmpty() -> Text(
                        "Este servicio no tiene fechas disponibles publicadas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    else -> {
                        Text("Fecha", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            reserva.fechasDisponibles.forEach { dia ->
                                FilterChip(
                                    selected = reserva.fechaSeleccionada == dia.fecha,
                                    onClick = { onFechaChange(dia.fecha) },
                                    label = {
                                        Column {
                                            Text(dia.fecha.substring(5),
                                                style = MaterialTheme.typography.labelMedium)
                                            Text("${dia.cuposLibres} cupos",
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                )
                            }
                        }

                        if (servicio.requiereRangoDeFechas && reserva.fechaSeleccionada != null) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = reserva.fechaFin ?: "",
                                onValueChange = onFechaFinChange,
                                label = { Text("Fecha de salida") },
                                supportingText = { Text("Formato AAAA-MM-DD") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Personas", style = MaterialTheme.typography.labelLarge)
                                Text("Maximo ${servicio.capacidadMaxima}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onPersonasChange(-1) },
                                    enabled = reserva.numPersonas > 1
                                ) { Icon(Icons.Filled.Remove, "Quitar una persona") }
                                Text("${reserva.numPersonas}",
                                    style = MaterialTheme.typography.titleMedium)
                                IconButton(
                                    onClick = { onPersonasChange(1) },
                                    enabled = reserva.numPersonas < servicio.capacidadMaxima
                                ) { Icon(Icons.Filled.Add, "Anadir una persona") }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = reserva.notas,
                            onValueChange = onNotasChange,
                            label = { Text("Notas (opcional)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (subtotalEstimado > 0) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Subtotal estimado",
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${servicio.simbolo} %.2f".format(subtotalEstimado),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                "El importe definitivo lo calcula el servidor al confirmar",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (reserva.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(reserva.error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(20.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCerrar, enabled = !reserva.enviando) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirmar,
                        enabled = reserva.esValido && !reserva.enviando
                    ) {
                        if (reserva.enviando) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Confirmar")
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ConfirmacionReserva(reserva: ReservaResponse, onCerrar: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text("Reserva creada", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(reserva.codigo, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(16.dp))
        Text(reserva.subtotalFormateado, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text(
            "${reserva.numPersonas} persona(s) · ${reserva.estadoLegible}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        Text(
            "Anade esta reserva a un viaje desde la pestana Viajes para que cuente en el total.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) { Text("Entendido") }
    }
}

private val servicioDemo = ServicioDetalleResponse(
    id = 1,
    titulo = "Tour Islas Uros y Taquile - dia completo",
    descripcion = "Recorrido en lancha por las islas flotantes de los Uros y la isla Taquile, con almuerzo tipico incluido.",
    precio = 120.0,
    unidadPrecio = "por_persona",
    ciudad = "Puno",
    capacidadMaxima = 15,
    calificacion = 5.0,
    totalResenas = 1,
    categoriaSlug = "guia",
    categoriaNombre = "Guia turistico",
    prestadorId = 1,
    prestadorNombre = "Julio Mamani",
    prestadorVerificado = true,
    detalle = DetalleEspecifico(
        aniosExperiencia = 12,
        duracionHoras = 9.0,
        tamanoMaxGrupo = 15,
        incluyeTransporte = true,
        puntoEncuentro = "Muelle turistico de Puno, 06:45 h"
    ),
    idiomas = listOf(
        IdiomaResponse("es", "Espanol", "nativo"),
        IdiomaResponse("en", "Ingles", "avanzado"),
        IdiomaResponse("qu", "Quechua", "nativo")
    ),
    resenas = listOf(
        ResenaResponse(5, "Julio conoce muchisimo la zona y explica con calma.", null, "Diego Fernandez")
    )
)

private val disponibilidadDemo = listOf(
    DisponibilidadResponse("2026-08-10", 15, 2, 13),
    DisponibilidadResponse("2026-08-11", 15, 0, 15),
    DisponibilidadResponse("2026-08-12", 15, 0, 15)
)

@Composable
private fun Envoltorio(contenido: @Composable () -> Unit) {
    TravelHubTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) { contenido() }
    }
}

@PreviewClaroOscuro
@Composable
private fun DetalleServicioPreview() = Envoltorio {
    DetalleServicioContenido(
        estado = DetalleServicioUiState.Exito(servicioDemo),
        reserva = ReservaFormState()
    )
}

@PreviewClaroOscuro
@Composable
private fun DialogoReservaPreview() = Envoltorio {
    DetalleServicioContenido(
        estado = DetalleServicioUiState.Exito(servicioDemo),
        reserva = ReservaFormState(
            visible = true,
            disponibilidad = disponibilidadDemo,
            fechaSeleccionada = "2026-08-11",
            numPersonas = 2
        ),
        subtotalEstimado = 240.0
    )
}

@PreviewClaroOscuro
@Composable
private fun ReservaSinCupoPreview() = Envoltorio {
    DetalleServicioContenido(
        estado = DetalleServicioUiState.Exito(servicioDemo),
        reserva = ReservaFormState(
            visible = true,
            disponibilidad = disponibilidadDemo,
            fechaSeleccionada = "2026-08-11",
            numPersonas = 2,
            error = "No hay cupo suficiente para el 2026-08-11"
        ),
        subtotalEstimado = 240.0
    )
}

@PreviewClaroOscuro
@Composable
private fun ReservaConfirmadaPreview() = Envoltorio {
    DetalleServicioContenido(
        estado = DetalleServicioUiState.Exito(servicioDemo),
        reserva = ReservaFormState(
            visible = true,
            reservaCreada = ReservaResponse(
                id = 7, codigo = "TH-2026-000007", servicioId = 1,
                fechaInicio = "2026-08-11", numPersonas = 2,
                precioUnitario = 120.0, cantidad = 2.0, subtotal = 240.0
            )
        )
    )
}
