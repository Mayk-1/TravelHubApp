package myk.w.travelhub.data.model.request

import com.google.gson.annotations.SerializedName

/**
 * Cuerpo de POST /api/reservas
 *
 * `fecha_fin` solo se manda en servicios que se cobran por noche. En los
 * demas va null y Gson lo omite del JSON, que es lo que espera el backend.
 */
data class CrearReservaRequest(
    @SerializedName("servicio_id") val servicioId: Int,
    @SerializedName("fecha_inicio") val fechaInicio: String,
    @SerializedName("fecha_fin") val fechaFin: String? = null,
    @SerializedName("num_personas") val numPersonas: Int = 1,
    @SerializedName("notas") val notas: String? = null
)


/** Cuerpo de POST /api/itinerarios/:id/dias/:dia/items */
data class AgregarParadaRequest(
    @SerializedName("reserva_id") val reservaId: Int? = null,
    @SerializedName("titulo_libre") val tituloLibre: String? = null,
    @SerializedName("hora_inicio") val horaInicio: String? = null,
    @SerializedName("hora_fin") val horaFin: String? = null
)
