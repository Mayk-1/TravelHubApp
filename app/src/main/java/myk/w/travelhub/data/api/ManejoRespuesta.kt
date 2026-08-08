package myk.w.travelhub.data.api

import com.google.gson.Gson
import myk.w.travelhub.data.model.response.ErrorResponse
import retrofit2.Response
import java.io.IOException

/**
 * Traduce cualquier llamada a la API en un Result con exito o con un mensaje
 * ya legible para el usuario.
 *
 * Vive aqui, y no dentro de cada repositorio, porque el mapeo de errores
 * tiene que ser identico en toda la app. Si estuviera copiado en cada
 * repositorio, en cuanto se mejorara uno los demas quedarian atras.
 */
suspend fun <T> ejecutarLlamada(bloque: suspend () -> Response<T>): Result<T> {
    return try {
        val response = bloque()

        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                Result.success(body)
            } else {
                Result.failure(Exception("El servidor respondio sin contenido"))
            }
        } else {
            Result.failure(Exception(mensajeDeError(response)))
        }
    } catch (e: IOException) {
        // Sin internet, servidor apagado, DNS caido...
        Result.failure(Exception("No se pudo conectar con el servidor. Revisa tu conexion."))
    } catch (e: Exception) {
        Result.failure(Exception(e.message ?: "Error inesperado"))
    }
}

/**
 * El backend responde siempre { "error": "mensaje" }. Si por algun motivo
 * no llega asi, se cae a un mensaje generico segun el codigo HTTP.
 */
private fun <T> mensajeDeError(response: Response<T>): String {
    val crudo = try {
        response.errorBody()?.string()
    } catch (e: Exception) {
        null
    }

    val delBackend = try {
        Gson().fromJson(crudo, ErrorResponse::class.java)?.error
    } catch (e: Exception) {
        null
    }

    if (!delBackend.isNullOrBlank()) return delBackend

    return when (response.code()) {
        401 -> "Tu sesion expiro. Inicia sesion de nuevo."
        403 -> "No tienes permiso para hacer esto"
        404 -> "No se encontro lo que buscabas"
        409 -> "Ese registro ya existe"
        in 500..599 -> "Error del servidor. Intenta mas tarde."
        else -> "Error desconocido (${response.code()})"
    }
}
