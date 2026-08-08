package myk.w.travelhub.ui.screen.itinerarios

import myk.w.travelhub.data.model.response.ItinerarioResponse

/** Estados de la lista de viajes. */
sealed interface ItinerariosUiState {
    data object Cargando : ItinerariosUiState
    data class Exito(val viajes: List<ItinerarioResponse>) : ItinerariosUiState
    data object Vacio : ItinerariosUiState
    data class Error(val mensaje: String) : ItinerariosUiState
}

/**
 * Formulario de creacion de viaje.
 *
 * Las fechas se guardan como texto "YYYY-MM-DD" para no depender de las
 * APIs de fecha, que cambian segun la version de Android. La validacion
 * comprueba tanto el formato como que el orden tenga sentido.
 */
data class NuevoViajeState(
    val visible: Boolean = false,
    val titulo: String = "",
    val destino: String = "Puno",
    val fechaInicio: String = "",
    val fechaFin: String = "",
    val presupuesto: String = "",
    val guardando: Boolean = false,
    val error: String? = null
) {
    val fechaInicioValida: Boolean get() = esFecha(fechaInicio)
    val fechaFinValida: Boolean get() = esFecha(fechaFin)

    /** El fin no puede ser anterior al inicio; con texto ISO basta comparar. */
    val ordenCorrecto: Boolean
        get() = !fechaInicioValida || !fechaFinValida || fechaFin >= fechaInicio

    val esValido: Boolean
        get() = titulo.isNotBlank() &&
                fechaInicioValida &&
                fechaFinValida &&
                ordenCorrecto &&
                (presupuesto.isBlank() || presupuesto.toDoubleOrNull() != null)

    private fun esFecha(v: String) = Regex("""\d{4}-\d{2}-\d{2}""").matches(v)
}
