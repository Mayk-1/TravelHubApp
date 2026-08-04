package myk.w.travelhub.data.model.response

import com.google.gson.annotations.SerializedName

/**
 * Respuesta de /auth/login y /auth/registro.
 *
 * OJO con @SerializedName: el backend devuelve la llave "usuario" (en espanol).
 * Sin esta anotacion, Gson no encuentra el campo y lo deja en null, aunque el
 * tipo en Kotlin sea no-nulo. Es un error silencioso que revienta despues.
 */
data class AuthResponse(
    @SerializedName("token") val token: String,
    @SerializedName("usuario") val usuario: UsuarioResponse
)
