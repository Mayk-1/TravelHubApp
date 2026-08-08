package myk.w.travelhub.data.model.response

import com.google.gson.annotations.SerializedName

/** Categoria de servicio: GET /api/servicios/categorias */
data class CategoriaResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("slug") val slug: String,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("icono") val icono: String? = null
)
