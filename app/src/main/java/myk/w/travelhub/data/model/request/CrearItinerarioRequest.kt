package myk.w.travelhub.data.model.request

import com.google.gson.annotations.SerializedName

/**
 * Cuerpo de POST /api/itinerarios
 */
data class CrearItinerarioRequest(
    @SerializedName("titulo") val titulo: String,
    @SerializedName("destino") val destino: String = "Puno",
    @SerializedName("fecha_inicio") val fechaInicio: String,
    @SerializedName("fecha_fin") val fechaFin: String,
    @SerializedName("presupuesto_estimado") val presupuesto: Double? = null
)
