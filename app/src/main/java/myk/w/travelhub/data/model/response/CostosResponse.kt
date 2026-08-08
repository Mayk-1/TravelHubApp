package myk.w.travelhub.data.model.response

import com.google.gson.annotations.SerializedName

/**
 * Calculadora de costos del viaje (punto 4.4):
 * GET /api/reservas/costos/:itinerarioId
 *
 * El backend lo resuelve con la vista v_costos_itinerario, que agrupa las
 * reservas del itinerario por categoria de servicio.
 */
data class CostosResponse(
    @SerializedName("itinerario_id") val itinerarioId: Int,
    @SerializedName("moneda") val moneda: String = "PEN",
    @SerializedName("desglose") val desglose: List<CostoCategoria> = emptyList(),
    @SerializedName("total") val total: Double = 0.0,
    @SerializedName("presupuesto_estimado") val presupuesto: Double? = null,
    // Positivo = queda margen; negativo = se paso del presupuesto.
    @SerializedName("diferencia") val diferencia: Double? = null
) {
    val simbolo: String get() = if (moneda == "PEN") "S/" else moneda

    fun formatear(monto: Double) = "$simbolo %.2f".format(monto)

    val hayPresupuesto: Boolean get() = presupuesto != null

    val excedido: Boolean get() = diferencia != null && diferencia < 0
}


data class CostoCategoria(
    @SerializedName("categoria_slug") val slug: String,
    @SerializedName("categoria_nombre") val nombre: String,
    @SerializedName("cantidad_reservas") val cantidadReservas: Int = 0,
    @SerializedName("total") val total: Double = 0.0
)
