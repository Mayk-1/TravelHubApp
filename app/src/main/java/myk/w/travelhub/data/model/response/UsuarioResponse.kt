package myk.w.travelhub.data.model.response

import com.google.gson.annotations.SerializedName

data class UsuarioResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("email") val email: String,
    @SerializedName("rol") val rol: String = "turista",
    @SerializedName("foto_url") val fotoUrl: String? = null
)
