package myk.w.travelhub.ui.screen.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import myk.w.travelhub.data.model.response.MiServicioResponse
import myk.w.travelhub.data.model.response.PerfilPrestador
import myk.w.travelhub.data.model.response.PerfilResponse
import myk.w.travelhub.data.model.response.ReservaRecibidaResponse
import myk.w.travelhub.ui.common.PreviewClaroOscuro
import myk.w.travelhub.ui.theme.TravelHubTheme

/**
 * Perfil del usuario. Una sola pantalla que se adapta al rol:
 *
 *   Turista   -> datos de cuenta y cuántas reservas lleva.
 *   Prestador -> lo anterior, más su estado de verificación, los servicios
 *                que ha publicado y las reservas que ha recibido
 *                (modulo 4.6 del enunciado, el panel del prestador).
 *
 * Es de solo lectura: el backend todavia no expone un endpoint para
 * modificar los datos de la cuenta.
 */
@Composable
fun PerfilScreen(
    onCerrarSesion: () -> Unit = {},
    viewModel: PerfilViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    PerfilContenido(
        estado = estado,
        onReintentar = viewModel::cargar,
        onCerrarSesion = { viewModel.cerrarSesion(onCerrarSesion) }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilContenido(
    estado: PerfilUiState,
    onReintentar: () -> Unit = {},
    onCerrarSesion: () -> Unit = {}
) {
    var confirmarSalida by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mi perfil") }) }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (estado) {
                is PerfilUiState.Cargando -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is PerfilUiState.Error -> Box(
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

                is PerfilUiState.Exito -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Cabecera(estado.perfil)

                    Spacer(Modifier.height(20.dp))
                    DatosCuenta(estado.perfil, estado.totalReservas)

                    if (estado.perfil.prestador != null) {
                        Spacer(Modifier.height(16.dp))
                        EstadoVerificacion(estado.perfil.prestador)

                        Spacer(Modifier.height(16.dp))
                        SeccionServicios(estado.misServicios)

                        Spacer(Modifier.height(16.dp))
                        SeccionReservasRecibidas(estado.reservasRecibidas)
                    }

                    Spacer(Modifier.height(28.dp))
                    OutlinedButton(
                        onClick = { confirmarSalida = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Cerrar sesión")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = { confirmarSalida = false },
            title = { Text("Cerrar sesión") },
            text = { Text("Tendrás que volver a escribir tu correo y contraseña para entrar.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSalida = false
                    onCerrarSesion()
                }) { Text("Cerrar sesión") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarSalida = false }) { Text("Cancelar") }
            }
        )
    }
}


@Composable
private fun Cabecera(perfil: PerfilResponse) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (perfil.fotoUrl != null) {
                    AsyncImage(
                        model = perfil.fotoUrl,
                        contentDescription = perfil.nombre,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Sin foto se pintan las iniciales; evita el hueco gris.
                    Text(
                        perfil.iniciales,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        Column {
            Text(
                perfil.nombre,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                perfil.rolLegible,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (perfil.miembroDesde != null) {
                Text(
                    "Miembro desde ${perfil.miembroDesde}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
private fun DatosCuenta(perfil: PerfilResponse, totalReservas: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Datos de la cuenta", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))

            Dato("Correo", perfil.email)
            Dato("Teléfono", perfil.telefono ?: "No registrado")

            if (perfil.prestador != null) {
                Dato("Razón social", perfil.prestador.razonSocial ?: "Persona natural")
                Dato("Ciudad base", perfil.prestador.ciudadBase)
            } else if (!perfil.esAdmin) {
                Dato("Reservas realizadas", "$totalReservas")
            }

            if (perfil.prestador?.descripcion != null) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text(
                    perfil.prestador.descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
private fun Dato(etiqueta: String, valor: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            valor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}


/**
 * Estado de verificacion del prestador. Es la informacion mas util de su
 * perfil: explica por que puede o no puede publicar servicios.
 */
@Composable
private fun EstadoVerificacion(prestador: PerfilPrestador) {
    val (icono, color) = when {
        prestador.aprobado -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        prestador.rechazado -> Icons.Filled.Cancel to MaterialTheme.colorScheme.error
        else -> Icons.Filled.HourglassTop to Color(0xFFF5A623)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    prestador.estadoLegible,
                    style = MaterialTheme.typography.titleSmall,
                    color = color
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    prestador.explicacion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
private fun SeccionServicios(servicios: List<MiServicioResponse>) {
    Column {
        Encabezado("Mis servicios", "${servicios.size}")
        Spacer(Modifier.height(8.dp))

        if (servicios.isEmpty()) {
            Vacio("Aún no has publicado ningún servicio.")
        } else {
            servicios.forEach { s ->
                Card(Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.titulo,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${s.categoriaNombre ?: ""} · ${s.ciudad}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                s.precioFormateado,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Etiqueta(
                                if (s.activo) "Publicado" else "Oculto",
                                if (s.activo) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Etiqueta(
                                "${s.reservasActivas} reservas activas",
                                MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (s.totalResenas > 0) {
                                Etiqueta(
                                    "%.1f (${s.totalResenas})".format(s.calificacion),
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun SeccionReservasRecibidas(reservas: List<ReservaRecibidaResponse>) {
    Column {
        Encabezado("Reservas recibidas", "${reservas.size}")
        Spacer(Modifier.height(8.dp))

        if (reservas.isEmpty()) {
            Vacio("Todavía no has recibido reservas.")
        } else {
            reservas.take(10).forEach { r ->
                Card(Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            r.servicioTitulo ?: r.codigo,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${r.turistaNombre ?: "Turista"} · ${r.numPersonas} persona(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
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
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            if (reservas.size > 10) {
                Text(
                    "y ${reservas.size - 10} más",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
private fun Encabezado(titulo: String, contador: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(titulo, style = MaterialTheme.typography.titleSmall)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                contador,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}


@Composable
private fun Etiqueta(texto: String, color: Color) {
    Text(texto, style = MaterialTheme.typography.labelSmall, color = color)
}


@Composable
private fun Vacio(texto: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}


// ---------------------------------------------------------------------------
// Vistas previas
// ---------------------------------------------------------------------------

private val turistaDemo = PerfilResponse(
    id = 2, nombre = "Camila Rojas", email = "camila@example.com",
    rol = "turista", telefono = "951111111", creadoEn = "2026-06-01 09:00:00"
)

private val prestadorDemo = PerfilResponse(
    id = 5, nombre = "Julio Mamani", email = "julio.guia@example.com",
    rol = "prestador", telefono = "952111111", creadoEn = "2026-06-10 08:00:00",
    prestador = PerfilPrestador(
        id = 1,
        descripcion = "Guía oficial de turismo con 12 años recorriendo el lago Titicaca.",
        ciudadBase = "Puno",
        estado = "aprobado"
    )
)

private val serviciosDemo = listOf(
    MiServicioResponse(
        id = 1, titulo = "Tour Islas Uros y Taquile - día completo",
        precio = 120.0, unidadPrecio = "por_persona", ciudad = "Puno",
        calificacion = 5.0, totalResenas = 1,
        categoriaNombre = "Guía turístico", reservasActivas = 2
    ),
    MiServicioResponse(
        id = 2, titulo = "City tour Puno y Sillustani",
        precio = 80.0, unidadPrecio = "por_persona", ciudad = "Puno",
        activo = false, categoriaNombre = "Guía turístico", reservasActivas = 0
    )
)

private val recibidasDemo = listOf(
    ReservaRecibidaResponse(
        id = 2, codigo = "TH-2026-000002", fechaInicio = "2026-08-10",
        numPersonas = 2, subtotal = 240.0, estado = "confirmada",
        servicioTitulo = "Tour Islas Uros y Taquile",
        turistaNombre = "Camila Rojas", turistaTelefono = "951111111"
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
private fun PerfilTuristaPreview() = Envoltorio {
    PerfilContenido(PerfilUiState.Exito(perfil = turistaDemo, totalReservas = 3))
}

@PreviewClaroOscuro
@Composable
private fun PerfilPrestadorAprobadoPreview() = Envoltorio {
    PerfilContenido(
        PerfilUiState.Exito(
            perfil = prestadorDemo,
            misServicios = serviciosDemo,
            reservasRecibidas = recibidasDemo
        )
    )
}

/** Prestador sin aprobar: no puede publicar todavía. */
@PreviewClaroOscuro
@Composable
private fun PerfilPrestadorPendientePreview() = Envoltorio {
    PerfilContenido(
        PerfilUiState.Exito(
            perfil = prestadorDemo.copy(
                nombre = "Marco Condori",
                prestador = prestadorDemo.prestador?.copy(estado = "pendiente")
            )
        )
    )
}

@PreviewClaroOscuro
@Composable
private fun PerfilPrestadorRechazadoPreview() = Envoltorio {
    PerfilContenido(
        PerfilUiState.Exito(
            perfil = prestadorDemo.copy(
                prestador = prestadorDemo.prestador?.copy(
                    estado = "rechazado",
                    motivoRechazo = "El documento de identidad no coincide con el titular."
                )
            )
        )
    )
}

@PreviewClaroOscuro
@Composable
private fun PerfilErrorPreview() = Envoltorio {
    PerfilContenido(PerfilUiState.Error("Tu sesión expiró. Inicia sesión de nuevo."))
}
