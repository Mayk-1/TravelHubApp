package myk.w.travelhub.data.model.response

import com.google.gson.annotations.SerializedName

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
