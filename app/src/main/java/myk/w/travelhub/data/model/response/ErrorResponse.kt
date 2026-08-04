package myk.w.travelhub.data.model.response

import com.google.gson.annotations.SerializedName

/** Formato uniforme de error del backend: { "error": "mensaje" } */
data class ErrorResponse(
    @SerializedName("error") val error: String?
)
