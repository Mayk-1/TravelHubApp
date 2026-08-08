package myk.w.travelhub.data.model.response

import com.google.gson.annotations.SerializedName

/**
 * Un servicio tal como lo devuelve GET /api/servicios.
 *
 * Corresponde 1 a 1 con las columnas de la vista `v_catalogo` del backend.
 * Los nombres tienen que coincidir EXACTAMENTE con el JSON: si no coinciden,
 * Gson deja el campo a null sin avisar, aunque el tipo sea no-nulo, y el
 * fallo aparece mucho despues en un sitio que no tiene nada que ver.
 */
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
    // MySQL manda este campo como 1 o 0, no como true/false. Gson por
    // defecto lanza "Expected a boolean but was NUMBER"; lo resuelve el
    // deserializador BooleanFlexible registrado en RetrofitClient.
    @SerializedName("prestador_verificado") val prestadorVerificado: Boolean = false,

    @SerializedName("foto_principal") val fotoPrincipal: String? = null
) {
    /** "S/ 120.00 por persona" — texto ya listo para la tarjeta. */
    val precioFormateado: String
        get() {
            val simbolo = if (moneda == "PEN") "S/" else moneda
            return "$simbolo %.2f".format(precio)
        }

    /** Traduce el ENUM del backend a algo legible. */
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
