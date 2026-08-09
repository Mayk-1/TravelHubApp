package myk.w.travelhub.data.api

import myk.w.travelhub.data.model.request.LoginRequest
import myk.w.travelhub.data.model.request.RegistroRequest
import myk.w.travelhub.data.model.response.AuthResponse
import myk.w.travelhub.data.model.response.PerfilResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApiService {

    @POST("auth/registro")
    suspend fun registro(@Body request: RegistroRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    /**
     * Devuelve PerfilResponse y no UsuarioResponse porque este endpoint
     * adjunta el objeto `prestador` cuando el rol lo tiene, y ese campo no
     * existe en el usuario que devuelven login y registro.
     */
    @GET("auth/me")
    suspend fun usuarioActual(): Response<PerfilResponse>
}
