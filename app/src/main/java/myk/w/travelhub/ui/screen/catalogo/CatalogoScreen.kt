package myk.w.travelhub.ui.screen.catalogo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import myk.w.travelhub.data.model.response.CategoriaResponse
import myk.w.travelhub.data.model.response.ServicioResponse
import myk.w.travelhub.ui.common.PreviewClaroOscuro
import myk.w.travelhub.ui.theme.TravelHubTheme

/**
 * Catalogo de servicios (punto 4.2 del enunciado).
 *
 * Igual que las demas pantallas: una parte con estado que habla con el
 * ViewModel, y otra sin estado que solo dibuja y se puede previsualizar.
 */
@Composable
fun CatalogoScreen(
    onServicioClick: (Int) -> Unit = {},
    viewModel: CatalogoViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val filtros by viewModel.filtros.collectAsStateWithLifecycle()

    CatalogoContenido(
        estado = estado,
        filtros = filtros,
        onBusquedaChange = viewModel::onBusquedaChange,
        onCategoriaChange = viewModel::onCategoriaChange,
        onOrdenChange = viewModel::onOrdenChange,
        onLimpiarFiltros = viewModel::limpiarFiltros,
        onReintentar = viewModel::cargar,
        onServicioClick = onServicioClick
    )
}


@Composable
fun CatalogoContenido(
    estado: CatalogoUiState,
    filtros: FiltrosState,
    onBusquedaChange: (String) -> Unit = {},
    onCategoriaChange: (String?) -> Unit = {},
    onOrdenChange: (String) -> Unit = {},
    onLimpiarFiltros: () -> Unit = {},
    onReintentar: () -> Unit = {},
    onServicioClick: (Int) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {

        BarraFiltros(
            filtros = filtros,
            onBusquedaChange = onBusquedaChange,
            onCategoriaChange = onCategoriaChange,
            onOrdenChange = onOrdenChange
        )

        // El `when` es exhaustivo porque CatalogoUiState es un sealed
        // interface: si se anade un estado nuevo, esto deja de compilar
        // hasta que se contemple.
        when (estado) {
            is CatalogoUiState.Cargando -> Centrado { CircularProgressIndicator() }

            is CatalogoUiState.Vacio -> Centrado {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No hay servicios que coincidan",
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Prueba con otros filtros o cambia la busqueda",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (filtros.hayFiltrosActivos) {
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onLimpiarFiltros) { Text("Quitar filtros") }
                    }
                }
            }

            is CatalogoUiState.Error -> Centrado {
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

            is CatalogoUiState.Exito -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // La `key` es importante: sin ella, al reordenar la lista
                // Compose reutiliza las tarjetas por posicion y se ven
                // parpadeos de imagenes que no corresponden.
                items(estado.servicios, key = { it.id }) { servicio ->
                    TarjetaServicio(servicio, onClick = { onServicioClick(servicio.id) })
                }
            }
        }
    }
}


@Composable
private fun BarraFiltros(
    filtros: FiltrosState,
    onBusquedaChange: (String) -> Unit,
    onCategoriaChange: (String?) -> Unit,
    onOrdenChange: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

        OutlinedTextField(
            value = filtros.busqueda,
            onValueChange = onBusquedaChange,
            placeholder = { Text("Buscar guias, hospedajes...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (filtros.busqueda.isNotEmpty()) {
                    IconButton(onClick = { onBusquedaChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Limpiar busqueda")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filtros.categoriaSlug == null,
                onClick = { onCategoriaChange(null) },
                label = { Text("Todos") }
            )
            filtros.categorias.forEach { categoria ->
                FilterChip(
                    selected = filtros.categoriaSlug == categoria.slug,
                    onClick = { onCategoriaChange(categoria.slug) },
                    label = { Text(categoria.nombre) }
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OpcionOrden.entries.forEach { opcion ->
                FilterChip(
                    selected = filtros.orden == opcion.valor,
                    onClick = { onOrdenChange(opcion.valor) },
                    label = { Text(opcion.etiqueta, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}


@Composable
private fun TarjetaServicio(servicio: ServicioResponse, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            // Las URLs del seed apuntan a un dominio que no existe todavia,
            // asi que Coil fallara y se vera el fondo gris. Es lo esperado
            // hasta que haya almacenamiento de imagenes en la nube.
            Box(
                modifier = Modifier
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
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(bottomEnd = 12.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        servicio.categoriaNombre,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Column(Modifier.padding(14.dp)) {
                Text(
                    servicio.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        servicio.ciudad,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(12.dp))

                    if (servicio.tieneCalificacion) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFF5A623)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "%.1f (${servicio.totalResenas})".format(servicio.calificacion),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            "Sin resenas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            servicio.prestadorNombre,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (servicio.prestadorVerificado) {
                            Spacer(Modifier.size(4.dp))
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = "Prestador verificado",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            servicio.precioFormateado,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (servicio.unidadLegible.isNotEmpty()) {
                            Text(
                                servicio.unidadLegible,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

private val categoriasDemo = listOf(
    CategoriaResponse(1, "guia", "Guia turistico", "hiking"),
    CategoriaResponse(2, "hospedaje", "Hospedaje", "hotel"),
    CategoriaResponse(3, "alimentacion", "Alimentacion", "restaurant")
)

private val serviciosDemo = listOf(
    ServicioResponse(
        id = 1,
        titulo = "Tour Islas Uros y Taquile - dia completo",
        descripcion = "Recorrido en lancha por las islas flotantes.",
        precio = 120.0,
        unidadPrecio = "por_persona",
        ciudad = "Puno",
        calificacion = 5.0,
        totalResenas = 1,
        categoriaSlug = "guia",
        categoriaNombre = "Guia turistico",
        prestadorId = 1,
        prestadorNombre = "Julio Mamani",
        prestadorVerificado = true
    ),
    ServicioResponse(
        id = 3,
        titulo = "Habitacion doble - Hostal Casa Rosa",
        precio = 90.0,
        unidadPrecio = "por_noche",
        ciudad = "Puno",
        categoriaSlug = "hospedaje",
        categoriaNombre = "Hospedaje",
        prestadorId = 2,
        prestadorNombre = "Rosa Quispe",
        prestadorVerificado = true
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
private fun CatalogoConResultadosPreview() = Envoltorio {
    CatalogoContenido(
        estado = CatalogoUiState.Exito(serviciosDemo),
        filtros = FiltrosState(categorias = categoriasDemo)
    )
}

@PreviewClaroOscuro
@Composable
private fun CatalogoCargandoPreview() = Envoltorio {
    CatalogoContenido(
        estado = CatalogoUiState.Cargando,
        filtros = FiltrosState(categorias = categoriasDemo)
    )
}

@PreviewClaroOscuro
@Composable
private fun CatalogoVacioPreview() = Envoltorio {
    CatalogoContenido(
        estado = CatalogoUiState.Vacio,
        filtros = FiltrosState(busqueda = "kayak", categorias = categoriasDemo)
    )
}

@PreviewClaroOscuro
@Composable
private fun CatalogoErrorPreview() = Envoltorio {
    CatalogoContenido(
        estado = CatalogoUiState.Error("No se pudo conectar con el servidor. Revisa tu conexion."),
        filtros = FiltrosState(categorias = categoriasDemo)
    )
}
