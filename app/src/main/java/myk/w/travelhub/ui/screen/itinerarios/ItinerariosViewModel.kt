package myk.w.travelhub.ui.screen.itinerarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import myk.w.travelhub.data.model.response.ItinerarioResponse
import myk.w.travelhub.data.repository.ItinerarioRepository

class ItinerariosViewModel : ViewModel() {

    private val repository = ItinerarioRepository()

    private val _uiState = MutableStateFlow<ItinerariosUiState>(ItinerariosUiState.Cargando)
    val uiState: StateFlow<ItinerariosUiState> = _uiState.asStateFlow()

    private val _nuevoViaje = MutableStateFlow(NuevoViajeState())
    val nuevoViaje: StateFlow<NuevoViajeState> = _nuevoViaje.asStateFlow()

    // Viaje que el usuario pidio borrar y todavia no ha confirmado.
    // Vive en el ViewModel y no en la pantalla para que sobreviva a un giro
    // de pantalla: si estuviera en un `remember`, el dialogo se cerraria solo.
    private val _porEliminar = MutableStateFlow<ItinerarioResponse?>(null)
    val porEliminar: StateFlow<ItinerarioResponse?> = _porEliminar.asStateFlow()

    private val _eliminando = MutableStateFlow(false)
    val eliminando: StateFlow<Boolean> = _eliminando.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            _uiState.value = ItinerariosUiState.Cargando
            repository.listar().fold(
                onSuccess = { lista ->
                    _uiState.value = if (lista.isEmpty()) {
                        ItinerariosUiState.Vacio
                    } else {
                        ItinerariosUiState.Exito(lista)
                    }
                },
                onFailure = {
                    _uiState.value = ItinerariosUiState.Error(it.message ?: "Error desconocido")
                }
            )
        }
    }

    // --- Formulario de creacion ---

    fun abrirFormulario() {
        // Se parte de cero cada vez, para no arrastrar lo que el usuario
        // escribio la vez anterior si cancelo a medias.
        _nuevoViaje.value = NuevoViajeState(visible = true)
    }

    fun cerrarFormulario() {
        _nuevoViaje.value = NuevoViajeState(visible = false)
    }

    fun onTituloChange(v: String) = _nuevoViaje.update { it.copy(titulo = v, error = null) }
    fun onDestinoChange(v: String) = _nuevoViaje.update { it.copy(destino = v, error = null) }
    fun onFechaInicioChange(v: String) = _nuevoViaje.update { it.copy(fechaInicio = v, error = null) }
    fun onFechaFinChange(v: String) = _nuevoViaje.update { it.copy(fechaFin = v, error = null) }
    fun onPresupuestoChange(v: String) = _nuevoViaje.update { it.copy(presupuesto = v, error = null) }

    fun guardarViaje() {
        val f = _nuevoViaje.value
        if (!f.esValido || f.guardando) return

        viewModelScope.launch {
            _nuevoViaje.update { it.copy(guardando = true, error = null) }

            repository.crear(
                titulo = f.titulo,
                destino = f.destino,
                fechaInicio = f.fechaInicio,
                fechaFin = f.fechaFin,
                presupuesto = f.presupuesto.toDoubleOrNull()
            ).fold(
                onSuccess = {
                    _nuevoViaje.value = NuevoViajeState(visible = false)
                    cargar()   // recarga la lista para que aparezca el nuevo
                },
                onFailure = { e ->
                    // El formulario sigue abierto con lo que el usuario
                    // escribio: perder los datos por un error de red seria
                    // lo peor que podria pasar aqui.
                    _nuevoViaje.update {
                        it.copy(guardando = false, error = e.message ?: "No se pudo crear el viaje")
                    }
                }
            )
        }
    }

    // --- Eliminar ---

    fun pedirEliminar(viaje: ItinerarioResponse) {
        _porEliminar.value = viaje
    }

    fun cancelarEliminar() {
        _porEliminar.value = null
    }

    fun confirmarEliminar() {
        val viaje = _porEliminar.value ?: return
        if (_eliminando.value) return

        viewModelScope.launch {
            _eliminando.value = true
            repository.eliminar(viaje.id).fold(
                onSuccess = {
                    _eliminando.value = false
                    _porEliminar.value = null
                    cargar()
                },
                onFailure = { e ->
                    _eliminando.value = false
                    _porEliminar.value = null
                    _uiState.value = ItinerariosUiState.Error(
                        e.message ?: "No se pudo eliminar el viaje"
                    )
                }
            )
        }
    }
}
