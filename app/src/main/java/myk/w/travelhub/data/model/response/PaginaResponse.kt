package myk.w.travelhub.data.model.response

import com.google.gson.annotations.SerializedName

/**
 * Envoltorio generico de las respuestas paginadas del backend:
 *
 *   { "datos": [...], "paginacion": { "pagina": 1, "total": 6, ... } }
 *
 * Al ser generico sirve para servicios, reservas o cualquier listado futuro,
 * sin repetir la clase. Retrofit y Gson resuelven el tipo <T> sin problema.
 */
data class PaginaResponse<T>(
    @SerializedName("datos") val datos: List<T>,
    @SerializedName("paginacion") val paginacion: Paginacion
)

data class Paginacion(
    @SerializedName("pagina") val pagina: Int,
    @SerializedName("limite") val limite: Int,
    @SerializedName("total") val total: Int,
    @SerializedName("paginas") val paginas: Int
) {
    val hayMasPaginas: Boolean get() = pagina < paginas
}
