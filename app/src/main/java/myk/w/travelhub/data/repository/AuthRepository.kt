package myk.w.travelhub.data.repository

import com.google.gson.Gson
import myk.w.travelhub.data.api.RetrofitClient
import myk.w.travelhub.data.local.TokenStore
import myk.w.travelhub.data.model.request.LoginRequest
import myk.w.travelhub.data.model.request.RegistroRequest
import myk.w.travelhub.data.model.response.AuthResponse
import myk.w.travelhub.data.model.response.ErrorResponse
import retrofit2.Response
import java.io.IOException

/**
 * Capa que aisla al ViewModel de los detalles de la red.
 *
 * El ViewModel no sabe que existe Retrofit ni que hay codigos HTTP: solo
 * recibe un Result con exito o con un mensaje de error ya legible.
 */
class AuthRepository {

    private val api = RetrofitClient.auth

    suspend fun login(email: String, password: String): Result<AuthResponse> =
        ejecutar { api.login(LoginRequest(email.trim(), password)) }
            .onSuccess { guardar(it) }

    suspend fun registro(
        nombre: String,
        email: String,
        password: String,
        rol: String
    ): Result<AuthResponse> =
        ejecutar { api.registro(RegistroRequest(nombre.trim(), email.trim(), password, rol)) }
            .onSuccess { guardar(it) }

    suspend fun cerrarSesion() = TokenStore.cerrarSesion()

    private suspend fun guardar(auth: AuthResponse) {
        TokenStore.guardarSesion(
            token = auth.token,
            nombre = auth.usuario.nombre,
            email = auth.usuario.email,
            rol = auth.usuario.rol
        )
    }

    /**
     * Envuelve una llamada a la API y traduce cualquier fallo a un mensaje
     * entendible para el usuario. Evita repetir este try/catch en cada metodo.
     */
    private suspend fun <T> ejecutar(bloque: suspend () -> Response<T>): Result<T> {
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

    private fun <T> mensajeDeError(response: Response<T>): String {
        // El backend manda { "error": "..." }. Si por alguna razon no viene
        // asi, caemos a un mensaje generico segun el codigo HTTP.
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
            401 -> "Correo o contrasena incorrectos"
            403 -> "No tienes permiso para hacer esto"
            404 -> "Recurso no encontrado"
            409 -> "Ese correo ya esta registrado"
            in 500..599 -> "Error del servidor. Intenta mas tarde."
            else -> "Error desconocido (${response.code()})"
        }
    }
}
