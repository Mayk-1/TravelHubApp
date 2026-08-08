package myk.w.travelhub.data.model.response

import com.google.gson.annotations.SerializedName

/**
 * Respuesta de /auth/login y /auth/registro.
 */
data class AuthResponse(
    @SerializedName("token") val token: String,
    @SerializedName("usuario") val usuario: UsuarioResponse
)
