package myk.w.travelhub.data.model.response

import com.google.gson.annotations.SerializedName

data class ServicioResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("titulo") val titulo: String,
    @SerializedName("descripcion") val descripcion: String? = null,

    @SerializedName("precio") val precio: Double,
    @SerializedName("moneda") val moneda: String = "PEN",
    @SerializedName("unidad_precio") val unidadPrecio: String = "por_servicio",

    @SerializedName("ciudad") val ciudad: String,
    @SerializedName("latitud") val latitud: Double? = null,
    @SerializedName("longitud") val longitud: Double? = null,
    @SerializedName("capacidad_maxima") val capacidadMaxima: Int = 1,

    @SerializedName("calificacion_promedio") val calificacion: Double = 0.0,
    @SerializedName("total_resenas") val totalResenas: Int = 0,

    @SerializedName("categoria_slug") val categoriaSlug: String,
    @SerializedName("categoria_nombre") val categoriaNombre: String,
    @SerializedName("categoria_icono") val categoriaIcono: String? = null,

    @SerializedName("prestador_id") val prestadorId: Int,
    @SerializedName("prestador_nombre") val prestadorNombre: String,
    @SerializedName("prestador_foto") val prestadorFoto: String? = null,
    @SerializedName("prestador_verificado") val prestadorVerificado: Boolean = false,

    @SerializedName("foto_principal") val fotoPrincipal: String? = null
) {
    val precioFormateado: String
        get() {
            val simbolo = if (moneda == "PEN") "S/" else moneda
            return "$simbolo %.2f".format(precio)
        }

    val unidadLegible: String
        get() = when (unidadPrecio) {
            "por_persona" -> "por persona"
            "por_noche" -> "por noche"
            "por_dia" -> "por dia"
            "por_hora" -> "por hora"
            "por_km" -> "por km"
            else -> ""
        }

    val tieneCalificacion: Boolean get() = totalResenas > 0
}
