package myk.w.travelhub.data.model.request

import com.google.gson.annotations.SerializedName

/** Cuerpo de POST /auth/login */
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)
