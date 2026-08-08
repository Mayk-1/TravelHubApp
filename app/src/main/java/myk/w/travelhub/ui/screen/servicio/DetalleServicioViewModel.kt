package myk.w.travelhub.ui.screen.servicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import myk.w.travelhub.data.model.response.DisponibilidadResponse
import myk.w.travelhub.data.model.response.ReservaResponse
import myk.w.travelhub.data.model.response.ServicioDetalleResponse
import myk.w.travelhub.data.repository.ReservaRepository
import myk.w.travelhub.data.repository.ServicioRepository
import java.time.LocalDate

sealed interface DetalleServicioUiState {
    data object Cargando : DetalleServicioUiState
    data class Exito(val servicio: ServicioDetalleResponse) : DetalleServicioUiState
    data class Error(val mensaje: String) : DetalleServicioUiState
}

/**
 * Estado del formulario de reserva.
 *
 * El subtotal se calcula aqui, en el cliente, para que el usuario lo vea
 * cambiar al instante. Pero el precio que vale es el que devuelve el
 * backend al confirmar: el servidor recalcula todo y aplica los precios
 * especiales de temporada. Este numero es una previsualizacion, no la
 * fuente de verdad.
 */
data class ReservaFormState(
    val visible: Boolean = false,
    val fechaSeleccionada: String? = null,
    val fechaFin: String? = null,
    val numPersonas: Int = 1,
    val notas: String = "",
    val disponibilidad: List<DisponibilidadResponse> = emptyList(),
    val cargandoFechas: Boolean = false,
    val enviando: Boolean = false,
    val error: String? = null,
    val reservaCreada: ReservaResponse? = null
) {
    val fechasDisponibles: List<DisponibilidadResponse>
        get() = disponibilidad.filter { it.disponible }

    val esValido: Boolean
        get() = fechaSeleccionada != null && numPersonas >= 1
}

class DetalleServicioViewModel : ViewModel() {

    private val servicioRepo = ServicioRepository()
    private val reservaRepo = ReservaRepository()

    private val _uiState = MutableStateFlow<DetalleServicioUiState>(DetalleServicioUiState.Cargando)
    val uiState: StateFlow<DetalleServicioUiState> = _uiState.asStateFlow()

    private val _reserva = MutableStateFlow(ReservaFormState())
    val reserva: StateFlow<ReservaFormState> = _reserva.asStateFlow()

    private var servicioId: Int? = null

    fun cargar(id: Int) {
        servicioId = id
        viewModelScope.launch {
            _uiState.value = DetalleServicioUiState.Cargando
            servicioRepo.detalle(id).fold(
                onSuccess = { _uiState.value = DetalleServicioUiState.Exito(it) },
                onFailure = {
                    _uiState.value = DetalleServicioUiState.Error(it.message ?: "Error desconocido")
                }
            )
        }
    }

    fun reintentar() = servicioId?.let { cargar(it) }

    /** Abre el formulario y carga el calendario de los proximos 90 dias. */
    fun abrirReserva() {
        val id = servicioId ?: return
        _reserva.value = ReservaFormState(visible = true, cargandoFechas = true)

        viewModelScope.launch {
            val hoy = LocalDate.now()
            servicioRepo.disponibilidad(
                id = id,
                desde = hoy.toString(),
                hasta = hoy.plusDays(90).toString()
            ).fold(
                onSuccess = { lista ->
                    _reserva.update { it.copy(disponibilidad = lista, cargandoFechas = false) }
                },
                onFailure = { e ->
                    _reserva.update {
                        it.copy(
                            cargandoFechas = false,
                            error = e.message ?: "No se pudo cargar la disponibilidad"
                        )
                    }
                }
            )
        }
    }

    fun cerrarReserva() {
        _reserva.value = ReservaFormState(visible = false)
    }

    fun onFechaChange(fecha: String) {
        _reserva.update { estado ->
            // En servicios por noche, elegir la entrada fija la salida al dia
            // siguiente como minimo.
            val fin = if (requiereRango()) {
                LocalDate.parse(fecha).plusDays(1).toString()
            } else {
                null
            }
            estado.copy(fechaSeleccionada = fecha, fechaFin = fin, error = null)
        }
    }

    fun onFechaFinChange(fecha: String) = _reserva.update { it.copy(fechaFin = fecha, error = null) }

    fun onPersonasChange(delta: Int) {
        val maximo = (_uiState.value as? DetalleServicioUiState.Exito)
            ?.servicio?.capacidadMaxima ?: 1
        _reserva.update {
            it.copy(numPersonas = (it.numPersonas + delta).coerceIn(1, maximo), error = null)
        }
    }

    fun onNotasChange(v: String) = _reserva.update { it.copy(notas = v) }

    private fun requiereRango(): Boolean =
        (_uiState.value as? DetalleServicioUiState.Exito)?.servicio?.requiereRangoDeFechas == true

    /**
     * Estimacion local del subtotal, solo para mostrarla mientras el usuario
     * elige. No sustituye al calculo del backend.
     */
    fun subtotalEstimado(): Double {
        val servicio = (_uiState.value as? DetalleServicioUiState.Exito)?.servicio ?: return 0.0
        val f = _reserva.value
        val fecha = f.fechaSeleccionada ?: return 0.0

        // Si ese dia tiene precio especial, manda sobre el precio base.
        val precio = f.disponibilidad
            .firstOrNull { it.fecha == fecha }
            ?.precioEspecial ?: servicio.precio

        val cantidad = when (servicio.unidadPrecio) {
            "por_noche" -> {
                val fin = f.fechaFin ?: return 0.0
                val noches = LocalDate.parse(fin).toEpochDay() - LocalDate.parse(fecha).toEpochDay()
                if (noches < 1) return 0.0 else noches.toDouble()
            }
            "por_persona" -> f.numPersonas.toDouble()
            else -> 1.0
        }
        return precio * cantidad
    }

    fun confirmarReserva() {
        val id = servicioId ?: return
        val f = _reserva.value
        if (!f.esValido || f.enviando) return

        viewModelScope.launch {
            _reserva.update { it.copy(enviando = true, error = null) }

            reservaRepo.crear(
                servicioId = id,
                fechaInicio = f.fechaSeleccionada!!,
                fechaFin = if (requiereRango()) f.fechaFin else null,
                numPersonas = f.numPersonas,
                notas = f.notas
            ).fold(
                onSuccess = { creada ->
                    _reserva.update { it.copy(enviando = false, reservaCreada = creada) }
                },
                onFailure = { e ->
                    // El formulario no se cierra: conserva lo elegido y
                    // muestra por que fallo (sin cupo, fecha no publicada...).
                    _reserva.update {
                        it.copy(enviando = false, error = e.message ?: "No se pudo reservar")
                    }
                }
            )
        }
    }
}
