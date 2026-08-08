package myk.w.travelhub.data.model.response

import com.google.gson.annotations.SerializedName

/**
 * Respuesta de POST /api/reservas y de GET /api/reservas.
 */
data class ReservaResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("codigo") val codigo: String,
    @SerializedName("servicio_id") val servicioId: Int,

    @SerializedName("fecha_inicio") val fechaInicio: String,
    @SerializedName("fecha_fin") val fechaFin: String? = null,
    @SerializedName("num_personas") val numPersonas: Int = 1,

    @SerializedName("precio_unitario") val precioUnitario: Double,
    @SerializedName("cantidad") val cantidad: Double = 1.0,
    @SerializedName("subtotal") val subtotal: Double,
    @SerializedName("moneda") val moneda: String = "PEN",
    @SerializedName("estado") val estado: String = "pendiente",

    // Solo llegan en GET /api/reservas
    @SerializedName("servicio_titulo") val servicioTitulo: String? = null,
    @SerializedName("categoria_slug") val categoriaSlug: String? = null,
    @SerializedName("categoria_nombre") val categoriaNombre: String? = null,
    @SerializedName("prestador_nombre") val prestadorNombre: String? = null,
    @SerializedName("foto") val foto: String? = null,
    @SerializedName("tiene_resena") val tieneResena: Boolean = false
) {
    val simbolo: String get() = if (moneda == "PEN") "S/" else moneda

    val subtotalFormateado: String get() = "$simbolo %.2f".format(subtotal)

    val estadoLegible: String
        get() = when (estado) {
            "pendiente" -> "Pendiente"
            "confirmada" -> "Confirmada"
            "completada" -> "Completada"
            "cancelada" -> "Cancelada"
            else -> estado
        }

    val cancelable: Boolean get() = estado == "pendiente" || estado == "confirmada"
}
