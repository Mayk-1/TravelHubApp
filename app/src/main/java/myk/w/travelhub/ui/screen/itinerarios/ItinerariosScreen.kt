package myk.w.travelhub.ui.screen.itinerarios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import myk.w.travelhub.data.model.response.ItinerarioResponse
import myk.w.travelhub.ui.common.PreviewClaroOscuro
import myk.w.travelhub.ui.theme.TravelHubTheme

/**
 * Lista de viajes del turista (punto 4.3 del enunciado).
 */
@Composable
fun ItinerariosScreen(
    onViajeClick: (Int) -> Unit = {},
    viewModel: ItinerariosViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val formulario by viewModel.nuevoViaje.collectAsStateWithLifecycle()
    val porEliminar by viewModel.porEliminar.collectAsStateWithLifecycle()
    val eliminando by viewModel.eliminando.collectAsStateWithLifecycle()

    ItinerariosContenido(
        estado = estado,
        formulario = formulario,
        porEliminar = porEliminar,
        eliminando = eliminando,
        onViajeClick = onViajeClick,
        onReintentar = viewModel::cargar,
        onNuevoViaje = viewModel::abrirFormulario,
        onCerrarFormulario = viewModel::cerrarFormulario,
        onTituloChange = viewModel::onTituloChange,
        onDestinoChange = viewModel::onDestinoChange,
        onFechaInicioChange = viewModel::onFechaInicioChange,
        onFechaFinChange = viewModel::onFechaFinChange,
        onPresupuestoChange = viewModel::onPresupuestoChange,
        onGuardar = viewModel::guardarViaje,
        onPedirEliminar = viewModel::pedirEliminar,
        onCancelarEliminar = viewModel::cancelarEliminar,
        onConfirmarEliminar = viewModel::confirmarEliminar
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItinerariosContenido(
    estado: ItinerariosUiState,
    formulario: NuevoViajeState,
    porEliminar: ItinerarioResponse? = null,
    eliminando: Boolean = false,
    onViajeClick: (Int) -> Unit = {},
    onReintentar: () -> Unit = {},
    onNuevoViaje: () -> Unit = {},
    onCerrarFormulario: () -> Unit = {},
    onTituloChange: (String) -> Unit = {},
    onDestinoChange: (String) -> Unit = {},
    onFechaInicioChange: (String) -> Unit = {},
    onFechaFinChange: (String) -> Unit = {},
    onPresupuestoChange: (String) -> Unit = {},
    onGuardar: () -> Unit = {},
    onPedirEliminar: (ItinerarioResponse) -> Unit = {},
    onCancelarEliminar: () -> Unit = {},
    onConfirmarEliminar: () -> Unit = {}
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Mis viajes") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNuevoViaje) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo viaje")
            }
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (estado) {
                is ItinerariosUiState.Cargando -> Centrado { CircularProgressIndicator() }

                is ItinerariosUiState.Vacio -> Centrado {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Place,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Aun no tienes viajes", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Crea uno para empezar a armar tu itinerario",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = onNuevoViaje) { Text("Crear mi primer viaje") }
                    }
                }

                is ItinerariosUiState.Error -> Centrado {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            estado.mensaje,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onReintentar) { Text("Reintentar") }
                    }
                }

                is ItinerariosUiState.Exito -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(estado.viajes, key = { it.id }) { viaje ->
                        TarjetaViaje(
                            viaje = viaje,
                            onClick = { onViajeClick(viaje.id) },
                            onEliminar = { onPedirEliminar(viaje) }
                        )
                    }
                }
            }
        }
    }

    if (porEliminar != null) {
        DialogoEliminar(
            viaje = porEliminar,
            eliminando = eliminando,
            onCancelar = onCancelarEliminar,
            onConfirmar = onConfirmarEliminar
        )
    }

    if (formulario.visible) {
        DialogoNuevoViaje(
            formulario = formulario,
            onCerrar = onCerrarFormulario,
            onTituloChange = onTituloChange,
            onDestinoChange = onDestinoChange,
            onFechaInicioChange = onFechaInicioChange,
            onFechaFinChange = onFechaFinChange,
            onPresupuestoChange = onPresupuestoChange,
            onGuardar = onGuardar
        )
    }
}


@Composable
private fun TarjetaViaje(
    viaje: ItinerarioResponse,
    onClick: () -> Unit,
    onEliminar: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    viaje.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onEliminar,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Eliminar ${viaje.titulo}",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    viaje.destino,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(12.dp))
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "${viaje.fechaInicio} al ${viaje.fechaFin}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    "${viaje.dias} dias · ${viaje.totalParadas} paradas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        viaje.costoFormateado,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        // Rojo si ya se paso de lo presupuestado: es la
                        // senal mas util de un planificador de viajes.
                        color = if (viaje.excedePresupuesto) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    if (viaje.presupuesto != null) {
                        Text(
                            "de ${viaje.simbolo} %.2f".format(viaje.presupuesto),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}


/**
 * Confirmacion de borrado.
 *
 * Dice explicitamente que se pierde y que NO: las reservas sobreviven
 * porque la clave foranea de itinerario_items hacia reservas es
 * ON DELETE SET NULL. Sin esa aclaracion, mucha gente no borraria un viaje
 * por miedo a perder lo que ya pago.
 */
@Composable
private fun DialogoEliminar(
    viaje: ItinerarioResponse,
    eliminando: Boolean,
    onCancelar: () -> Unit,
    onConfirmar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!eliminando) onCancelar() },
        icon = {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Eliminar viaje") },
        text = {
            Column {
                Text("Se eliminara \"${viaje.titulo}\" con sus ${viaje.dias} dias y ${viaje.totalParadas} paradas.")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tus reservas NO se cancelan: seguiran en tu historial y " +
                            "podras anadirlas a otro viaje.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmar, enabled = !eliminando) {
                if (eliminando) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, enabled = !eliminando) { Text("Cancelar") }
        }
    )
}


@Composable
private fun DialogoNuevoViaje(
    formulario: NuevoViajeState,
    onCerrar: () -> Unit,
    onTituloChange: (String) -> Unit,
    onDestinoChange: (String) -> Unit,
    onFechaInicioChange: (String) -> Unit,
    onFechaFinChange: (String) -> Unit,
    onPresupuestoChange: (String) -> Unit,
    onGuardar: () -> Unit
) {
    Dialog(onDismissRequest = { if (!formulario.guardando) onCerrar() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Nuevo viaje", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = formulario.titulo,
                    onValueChange = onTituloChange,
                    label = { Text("Titulo") },
                    placeholder = { Text("Puno en 3 dias") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = formulario.destino,
                    onValueChange = onDestinoChange,
                    label = { Text("Destino") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                // Campos de texto y no un selector de calendario: mantiene
                // la pantalla simple y el formato coincide con lo que espera
                // el backend. El selector se puede anadir despues.
                OutlinedTextField(
                    value = formulario.fechaInicio,
                    onValueChange = onFechaInicioChange,
                    label = { Text("Fecha de inicio") },
                    placeholder = { Text("2026-08-10") },
                    isError = formulario.fechaInicio.isNotBlank() && !formulario.fechaInicioValida,
                    supportingText = { Text("Formato AAAA-MM-DD") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = formulario.fechaFin,
                    onValueChange = onFechaFinChange,
                    label = { Text("Fecha de fin") },
                    placeholder = { Text("2026-08-12") },
                    isError = (formulario.fechaFin.isNotBlank() && !formulario.fechaFinValida) ||
                            !formulario.ordenCorrecto,
                    supportingText = {
                        Text(
                            if (!formulario.ordenCorrecto) "Debe ser posterior al inicio"
                            else "Formato AAAA-MM-DD"
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = formulario.presupuesto,
                    onValueChange = onPresupuestoChange,
                    label = { Text("Presupuesto (opcional)") },
                    placeholder = { Text("800") },
                    prefix = { Text("S/ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (formulario.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        formulario.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onCerrar,
                        enabled = !formulario.guardando
                    ) { Text("Cancelar") }

                    Spacer(Modifier.size(8.dp))

                    Button(
                        onClick = onGuardar,
                        enabled = formulario.esValido && !formulario.guardando
                    ) {
                        if (formulario.guardando) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Crear")
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun Centrado(contenido: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) { contenido() }
}


// ---------------------------------------------------------------------------
// Vistas previas
// ---------------------------------------------------------------------------

private val viajesDemo = listOf(
    ItinerarioResponse(
        id = 1,
        titulo = "Puno y el Titicaca en 3 dias",
        destino = "Puno",
        fechaInicio = "2026-08-10",
        fechaFin = "2026-08-12",
        presupuesto = 800.0,
        dias = 3,
        totalParadas = 6,
        costoActual = 580.0
    ),
    ItinerarioResponse(
        id = 2,
        titulo = "Escapada a Sillustani",
        destino = "Puno",
        fechaInicio = "2026-09-01",
        fechaFin = "2026-09-02",
        presupuesto = 200.0,
        dias = 2,
        totalParadas = 3,
        costoActual = 260.0   // por encima del presupuesto
    )
)

@Composable
private fun Envoltorio(contenido: @Composable () -> Unit) {
    TravelHubTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) { contenido() }
    }
}

@PreviewClaroOscuro
@Composable
private fun ViajesConDatosPreview() = Envoltorio {
    ItinerariosContenido(
        estado = ItinerariosUiState.Exito(viajesDemo),
        formulario = NuevoViajeState()
    )
}

@PreviewClaroOscuro
@Composable
private fun ViajesVacioPreview() = Envoltorio {
    ItinerariosContenido(
        estado = ItinerariosUiState.Vacio,
        formulario = NuevoViajeState()
    )
}

@PreviewClaroOscuro
@Composable
private fun ViajesErrorPreview() = Envoltorio {
    ItinerariosContenido(
        estado = ItinerariosUiState.Error("Tu sesion expiro. Inicia sesion de nuevo."),
        formulario = NuevoViajeState()
    )
}

@PreviewClaroOscuro
@Composable
private fun FormularioNuevoViajePreview() = Envoltorio {
    ItinerariosContenido(
        estado = ItinerariosUiState.Exito(viajesDemo),
        formulario = NuevoViajeState(
            visible = true,
            titulo = "Puno en 3 dias",
            fechaInicio = "2026-08-10",
            fechaFin = "2026-08-12",
            presupuesto = "800"
        )
    )
}

@PreviewClaroOscuro
@Composable
private fun ConfirmarEliminarPreview() = Envoltorio {
    ItinerariosContenido(
        estado = ItinerariosUiState.Exito(viajesDemo),
        formulario = NuevoViajeState(),
        porEliminar = viajesDemo.first()
    )
}

@PreviewClaroOscuro
@Composable
private fun FormularioConErrorPreview() = Envoltorio {
    ItinerariosContenido(
        estado = ItinerariosUiState.Exito(viajesDemo),
        formulario = NuevoViajeState(
            visible = true,
            titulo = "Viaje mal fechado",
            fechaInicio = "2026-08-20",
            fechaFin = "2026-08-10",
            error = "La fecha de fin debe ser igual o posterior a la de inicio"
        )
    )
}
