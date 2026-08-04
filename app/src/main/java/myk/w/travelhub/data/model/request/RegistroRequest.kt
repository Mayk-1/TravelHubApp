package myk.w.travelhub.data.model.request

import com.google.gson.annotations.SerializedName

/**
 * Cuerpo de POST /auth/registro
 *
 * A diferencia de MiBolsillo, TravelHub maneja roles: el usuario elige al
 * registrarse si entra como turista o como prestador de servicios.
 */
data class RegistroRequest(
    @SerializedName("nombre") val nombre: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("rol") val rol: String = "turista"
)
