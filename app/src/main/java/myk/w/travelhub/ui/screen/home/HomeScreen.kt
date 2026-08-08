package myk.w.travelhub.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import myk.w.travelhub.ui.common.PreviewClaroOscuro
import myk.w.travelhub.ui.theme.TravelHubTheme

@Composable
fun HomeScreen(
    onCerrarSesion: () -> Unit = {},
    onIrACatalogo: () -> Unit = {},
    onIrAViajes: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val nombre by viewModel.nombre.collectAsStateWithLifecycle()
    val rol by viewModel.rol.collectAsStateWithLifecycle()

    HomeContenido(
        nombre = nombre,
        rol = rol,
        onCerrarSesion = { viewModel.cerrarSesion(onCerrarSesion) },
        onIrACatalogo = onIrACatalogo,
        onIrAViajes = onIrAViajes
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContenido(
    nombre: String?,
    rol: String?,
    onCerrarSesion: () -> Unit = {},
    onIrACatalogo: () -> Unit = {},
    onIrAViajes: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TravelHub") },
                actions = {
                    IconButton(onClick = onCerrarSesion) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Cerrar sesion"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = "Hola, ${nombre ?: "viajero"}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (rol) {
                    "prestador" -> "Entraste como prestador de servicios"
                    "admin" -> "Entraste como administrador"
                    else -> "Arma tu viaje sin intermediarios"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            Atajo(
                icono = Icons.Filled.Search,
                titulo = "Explorar servicios",
                descripcion = "Guias y hospedajes disponibles en Puno",
                onClick = onIrACatalogo
            )
            Spacer(Modifier.height(12.dp))
            Atajo(
                icono = Icons.Filled.Map,
                titulo = "Mis viajes",
                descripcion = "Arma tu itinerario y controla el presupuesto",
                onClick = onIrAViajes
            )
        }
    }
}


@Composable
private fun Atajo(
    icono: ImageVector,
    titulo: String,
    descripcion: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text(
                    descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun Envoltorio(contenido: @Composable () -> Unit) {
    TravelHubTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) { contenido() }
    }
}

@PreviewClaroOscuro
@Composable
private fun HomeTuristaPreview() = Envoltorio {
    HomeContenido(nombre = "Camila Rojas", rol = "turista")
}

@PreviewClaroOscuro
@Composable
private fun HomePrestadorPreview() = Envoltorio {
    HomeContenido(nombre = "Julio Mamani", rol = "prestador")
}

@PreviewClaroOscuro
@Composable
private fun HomeSinDatosPreview() = Envoltorio {
    HomeContenido(nombre = null, rol = null)
}
