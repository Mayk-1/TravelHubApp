package myk.w.travelhub.data.model.request

import com.google.gson.annotations.SerializedName

/**
 * Cuerpo de POST /auth/registro
 */
data class RegistroRequest(
    @SerializedName("nombre") val nombre: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("rol") val rol: String = "turista"
)
