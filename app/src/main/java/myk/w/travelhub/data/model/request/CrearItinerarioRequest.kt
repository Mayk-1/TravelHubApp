package myk.w.travelhub.data.model.request

import com.google.gson.annotations.SerializedName

/**
 * Cuerpo de POST /api/itinerarios
 *
 * Las fechas van como texto "YYYY-MM-DD", que es lo que valida el backend
 * con `isDate()` y lo que espera MySQL para una columna DATE.
 */
data class CrearItinerarioRequest(
    @SerializedName("titulo") val titulo: String,
    @SerializedName("destino") val destino: String = "Puno",
    @SerializedName("fecha_inicio") val fechaInicio: String,
    @SerializedName("fecha_fin") val fechaFin: String,
    @SerializedName("presupuesto_estimado") val presupuesto: Double? = null
)
