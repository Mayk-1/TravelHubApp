package myk.w.travelhub.data.api

import myk.w.travelhub.data.model.request.CrearItinerarioRequest
import myk.w.travelhub.data.model.response.CostosResponse
import myk.w.travelhub.data.model.response.ItinerarioDetalleResponse
import myk.w.travelhub.data.model.response.ItinerarioResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Endpoints de itinerarios. Todos exigen sesion: el AuthInterceptor adjunta
 * el token automaticamente.
 *
 * Ojo con `costos`: cuelga de /reservas, no de /itinerarios. Es asi en el
 * backend porque el calculo se hace sobre las reservas del viaje.
 */
interface ItinerarioApiService {

    @GET("itinerarios")
    suspend fun listar(): Response<List<ItinerarioResponse>>

    @GET("itinerarios/{id}")
    suspend fun detalle(@Path("id") id: Int): Response<ItinerarioDetalleResponse>

    @POST("itinerarios")
    suspend fun crear(@Body request: CrearItinerarioRequest): Response<ItinerarioResponse>

    @DELETE("itinerarios/{id}")
    suspend fun eliminar(@Path("id") id: Int): Response<Unit>

    @GET("reservas/costos/{itinerarioId}")
    suspend fun costos(@Path("itinerarioId") itinerarioId: Int): Response<CostosResponse>
}
