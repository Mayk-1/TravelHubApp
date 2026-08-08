package myk.w.travelhub.ui.screen.catalogo

import myk.w.travelhub.data.model.response.CategoriaResponse
import myk.w.travelhub.data.model.response.ServicioResponse

sealed interface CatalogoUiState {
    data object Cargando : CatalogoUiState
    data class Exito(val servicios: List<ServicioResponse>) : CatalogoUiState
    data object Vacio : CatalogoUiState
    data class Error(val mensaje: String) : CatalogoUiState
}

data class FiltrosState(
    val busqueda: String = "",
    val categoriaSlug: String? = null,       // null = todas
    val orden: String = "calificacion",
    val categorias: List<CategoriaResponse> = emptyList()
) {
    val hayFiltrosActivos: Boolean
        get() = busqueda.isNotBlank() || categoriaSlug != null
}

enum class OpcionOrden(val valor: String, val etiqueta: String) {
    CALIFICACION("calificacion", "Mejor valorados"),
    PRECIO_ASC("precio", "Menor precio"),
    PRECIO_DESC("precio_desc", "Mayor precio"),
    RECIENTE("reciente", "Mas recientes")
}
