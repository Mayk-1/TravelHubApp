package myk.w.travelhub.data.api

import myk.w.travelhub.data.model.request.AgregarParadaRequest
import myk.w.travelhub.data.model.request.CrearReservaRequest
import myk.w.travelhub.data.model.response.ReservaRecibidaResponse
import myk.w.travelhub.data.model.response.ReservaResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ReservaApiService {

    @POST("reservas")
    suspend fun crear(@Body request: CrearReservaRequest): Response<ReservaResponse>

    @GET("reservas")
    suspend fun mias(@Query("estado") estado: String? = null): Response<List<ReservaResponse>>

    /** Reservas que ha recibido el prestador en sus servicios. */
    @GET("reservas/recibidas")
    suspend fun recibidas(): Response<List<ReservaRecibidaResponse>>

    @POST("itinerarios/{id}/dias/{dia}/items")
    suspend fun agregarAlItinerario(
        @Path("id") itinerarioId: Int,
        @Path("dia") diaNumero: Int,
        @Body request: AgregarParadaRequest
    ): Response<Map<String, Int>>
}
