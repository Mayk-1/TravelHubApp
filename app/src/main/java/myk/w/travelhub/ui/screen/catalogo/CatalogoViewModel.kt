package myk.w.travelhub.ui.screen.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import myk.w.travelhub.data.repository.ServicioRepository

class CatalogoViewModel : ViewModel() {

    private val repository = ServicioRepository()

    private val _uiState = MutableStateFlow<CatalogoUiState>(CatalogoUiState.Cargando)
    val uiState: StateFlow<CatalogoUiState> = _uiState.asStateFlow()

    private val _filtros = MutableStateFlow(FiltrosState())
    val filtros: StateFlow<FiltrosState> = _filtros.asStateFlow()

    // Se guarda la corrutina de busqueda para poder cancelarla. Sin esto,
    // escribir "guia" lanzaria cuatro peticiones y la respuesta de "gu"
    // podria llegar despues que la de "guia" y pisar los resultados buenos.
    private var busquedaJob: Job? = null

    init {
        cargarCategorias()
        cargar()
    }

    private fun cargarCategorias() {
        viewModelScope.launch {
            repository.categorias().onSuccess { lista ->
                _filtros.update { it.copy(categorias = lista) }
            }
            // Si falla, la pantalla sigue funcionando sin los chips de
            // categoria. No merece bloquear todo el catalogo por eso.
        }
    }

    fun cargar() {
        viewModelScope.launch {
            _uiState.value = CatalogoUiState.Cargando
            val f = _filtros.value

            repository.listar(
                categoria = f.categoriaSlug,
                buscar = f.busqueda,
                orden = f.orden
            ).fold(
                onSuccess = { pagina ->
                    _uiState.value = if (pagina.datos.isEmpty()) {
                        CatalogoUiState.Vacio
                    } else {
                        CatalogoUiState.Exito(pagina.datos)
                    }
                },
                onFailure = {
                    _uiState.value = CatalogoUiState.Error(it.message ?: "Error desconocido")
                }
            )
        }
    }

    /**
     * Busqueda con retardo: se espera a que el usuario deje de escribir
     * medio segundo antes de consultar. Cada tecla cancela la peticion
     * anterior, asi se hace UNA llamada en vez de una por letra.
     */
    fun onBusquedaChange(texto: String) {
        _filtros.update { it.copy(busqueda = texto) }
        busquedaJob?.cancel()
        busquedaJob = viewModelScope.launch {
            delay(500)
            cargar()
        }
    }

    /** Pulsar la categoria ya seleccionada la deselecciona (vuelve a "todas"). */
    fun onCategoriaChange(slug: String?) {
        _filtros.update {
            it.copy(categoriaSlug = if (it.categoriaSlug == slug) null else slug)
        }
        cargar()
    }

    fun onOrdenChange(orden: String) {
        _filtros.update { it.copy(orden = orden) }
        cargar()
    }

    fun limpiarFiltros() {
        _filtros.update {
            it.copy(busqueda = "", categoriaSlug = null, orden = OpcionOrden.CALIFICACION.valor)
        }
        cargar()
    }
}
