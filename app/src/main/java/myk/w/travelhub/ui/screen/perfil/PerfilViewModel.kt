package myk.w.travelhub.ui.screen.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import myk.w.travelhub.data.model.response.MiServicioResponse
import myk.w.travelhub.data.model.response.PerfilResponse
import myk.w.travelhub.data.model.response.ReservaRecibidaResponse
import myk.w.travelhub.data.repository.PerfilRepository

sealed interface PerfilUiState {
    data object Cargando : PerfilUiState

    /**
     * Un solo estado para los dos roles. Las listas del prestador llegan
     * vacías en un turista, y la pantalla decide qué secciones pintar
     * segun `perfil.esPrestador`.
     */
    data class Exito(
        val perfil: PerfilResponse,
        val misServicios: List<MiServicioResponse> = emptyList(),
        val reservasRecibidas: List<ReservaRecibidaResponse> = emptyList(),
        val totalReservas: Int = 0
    ) : PerfilUiState

    data class Error(val mensaje: String) : PerfilUiState
}

class PerfilViewModel : ViewModel() {

    private val repository = PerfilRepository()

    private val _uiState = MutableStateFlow<PerfilUiState>(PerfilUiState.Cargando)
    val uiState: StateFlow<PerfilUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            _uiState.value = PerfilUiState.Cargando

            val perfil = repository.perfil().getOrElse { e ->
                _uiState.value = PerfilUiState.Error(e.message ?: "No se pudo cargar tu perfil")
                return@launch
            }

            if (perfil.esPrestador) {
                // Las dos peticiones salen a la vez: son independientes.
                val serviciosAsync = async { repository.misServicios() }
                val reservasAsync = async { repository.reservasRecibidas() }

                _uiState.value = PerfilUiState.Exito(
                    perfil = perfil,
                    // Si alguna falla se muestra el perfil igual, con esa
                    // sección vacía. Perder toda la pantalla por un listado
                    // secundario sería desproporcionado.
                    misServicios = serviciosAsync.await().getOrDefault(emptyList()),
                    reservasRecibidas = reservasAsync.await().getOrDefault(emptyList())
                )
            } else {
                val reservas = repository.misReservas().getOrDefault(emptyList())
                _uiState.value = PerfilUiState.Exito(
                    perfil = perfil,
                    totalReservas = reservas.size
                )
            }
        }
    }

    fun cerrarSesion(alTerminar: () -> Unit) {
        viewModelScope.launch {
            repository.cerrarSesion()
            alTerminar()
        }
    }
}
