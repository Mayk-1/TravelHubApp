package myk.w.travelhub.data.repository

import myk.w.travelhub.data.api.RetrofitClient
import myk.w.travelhub.data.api.ejecutarLlamada
import myk.w.travelhub.data.local.TokenStore
import myk.w.travelhub.data.model.request.LoginRequest
import myk.w.travelhub.data.model.request.RegistroRequest
import myk.w.travelhub.data.model.response.AuthResponse

class AuthRepository {

    private val api = RetrofitClient.auth

    suspend fun login(email: String, password: String): Result<AuthResponse> =
        ejecutarLlamada { api.login(LoginRequest(email.trim(), password)) }
            .onSuccess { guardar(it) }

    suspend fun registro(
        nombre: String,
        email: String,
        password: String,
        rol: String
    ): Result<AuthResponse> =
        ejecutarLlamada { api.registro(RegistroRequest(nombre.trim(), email.trim(), password, rol)) }
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
}
