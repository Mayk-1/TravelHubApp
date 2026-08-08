package myk.w.travelhub.ui.screen.itinerarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import myk.w.travelhub.data.model.response.CostosResponse
import myk.w.travelhub.data.model.response.ItinerarioDetalleResponse
import myk.w.travelhub.data.model.response.ReservaResponse
import myk.w.travelhub.data.repository.ItinerarioRepository
import myk.w.travelhub.data.repository.ReservaRepository

sealed interface DetalleViajeUiState {
    data object Cargando : DetalleViajeUiState
    data class Exito(
        val viaje: ItinerarioDetalleResponse,
        val costos: CostosResponse?
    ) : DetalleViajeUiState
    data class Error(val mensaje: String) : DetalleViajeUiState
}

data class AgregarParadaState(
    val visible: Boolean = false,
    val diaNumero: Int? = null,
    val reservas: List<ReservaResponse> = emptyList(),
    val cargando: Boolean = false,
    val enviando: Boolean = false,
    val error: String? = null
)

class DetalleViajeViewModel : ViewModel() {

    private val repository = ItinerarioRepository()
    private val reservaRepo = ReservaRepository()

    private val _uiState = MutableStateFlow<DetalleViajeUiState>(DetalleViajeUiState.Cargando)
    val uiState: StateFlow<DetalleViajeUiState> = _uiState.asStateFlow()

    private val _agregar = MutableStateFlow(AgregarParadaState())
    val agregar: StateFlow<AgregarParadaState> = _agregar.asStateFlow()

    private var idActual: Int? = null

    fun cargar(id: Int) {
        idActual = id
        viewModelScope.launch {
            _uiState.value = DetalleViajeUiState.Cargando

            val detalleAsync = async { repository.detalle(id) }
            val costosAsync = async { repository.costos(id) }

            val detalle = detalleAsync.await()
            val costos = costosAsync.await()

            detalle.fold(
                onSuccess = { viaje ->

                    _uiState.value = DetalleViajeUiState.Exito(viaje, costos.getOrNull())
                },
                onFailure = {
                    _uiState.value = DetalleViajeUiState.Error(it.message ?: "Error desconocido")
                }
            )
        }
    }

    fun reintentar() {
        idActual?.let { cargar(it) }
    }


    fun abrirAgregar(diaNumero: Int) {
        _agregar.value = AgregarParadaState(visible = true, diaNumero = diaNumero, cargando = true)

        viewModelScope.launch {
            reservaRepo.mias().fold(
                onSuccess = { todas ->
                    val yaEnElViaje = (_uiState.value as? DetalleViajeUiState.Exito)
                        ?.viaje?.dias
                        ?.flatMap { dia -> dia.items }
                        ?.mapNotNull { it.reservaId }
                        ?.toSet() ?: emptySet()

                    val disponibles = todas.filter {
                        it.id !in yaEnElViaje && it.estado != "cancelada"
                    }
                    _agregar.update { it.copy(reservas = disponibles, cargando = false) }
                },
                onFailure = { e ->
                    _agregar.update {
                        it.copy(
                            cargando = false,
                            error = e.message ?: "No se pudieron cargar tus reservas"
                        )
                    }
                }
            )
        }
    }

    fun cerrarAgregar() {
        _agregar.value = AgregarParadaState(visible = false)
    }

    fun agregarReserva(reservaId: Int) {
        val itinerarioId = idActual ?: return
        val dia = _agregar.value.diaNumero ?: return
        if (_agregar.value.enviando) return

        viewModelScope.launch {
            _agregar.update { it.copy(enviando = true, error = null) }

            reservaRepo.agregarAlItinerario(itinerarioId, dia, reservaId).fold(
                onSuccess = {
                    _agregar.value = AgregarParadaState(visible = false)
                    cargar(itinerarioId)
                },
                onFailure = { e ->
                    _agregar.update {
                        it.copy(enviando = false, error = e.message ?: "No se pudo anadir")
                    }
                }
            )
        }
    }
}
