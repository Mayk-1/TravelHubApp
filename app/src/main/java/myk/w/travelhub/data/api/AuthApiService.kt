package myk.w.travelhub.data.api

import myk.w.travelhub.data.model.request.LoginRequest
import myk.w.travelhub.data.model.request.RegistroRequest
import myk.w.travelhub.data.model.response.AuthResponse
import myk.w.travelhub.data.model.response.UsuarioResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Contrato de los endpoints de autenticacion.
 *
 * Retrofit genera la implementacion en tiempo de ejecucion: cada funcion
 * se traduce a una llamada HTTP contra BASE_URL + la ruta de la anotacion.
 */
interface AuthApiService {

    @POST("auth/registro")
    suspend fun registro(@Body request: RegistroRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("auth/me")
    suspend fun usuarioActual(): Response<UsuarioResponse>
}
