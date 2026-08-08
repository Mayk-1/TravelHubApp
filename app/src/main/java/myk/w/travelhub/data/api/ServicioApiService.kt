package myk.w.travelhub.data.api

import myk.w.travelhub.data.model.response.CategoriaResponse
import myk.w.travelhub.data.model.response.DisponibilidadResponse
import myk.w.travelhub.data.model.response.PaginaResponse
import myk.w.travelhub.data.model.response.ServicioDetalleResponse
import myk.w.travelhub.data.model.response.ServicioResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Endpoints del catalogo. Son publicos en el backend (no exigen token),
 * asi que funcionan aunque la sesion haya caducado.
 */
interface ServicioApiService {

    /**
     * Los parametros con valor null NO se envian en la URL: Retrofit los
     * omite. Por eso se pueden declarar todos los filtros aqui y mandar
     * solo los que el usuario haya elegido.
     */
    @GET("servicios")
    suspend fun listar(
        @Query("categoria") categoria: String? = null,
        @Query("ciudad") ciudad: String? = null,
        @Query("precio_min") precioMin: Double? = null,
        @Query("precio_max") precioMax: Double? = null,
        @Query("calificacion") calificacion: Double? = null,
        @Query("fecha") fecha: String? = null,
        @Query("buscar") buscar: String? = null,
        @Query("orden") orden: String? = null,
        @Query("pagina") pagina: Int = 1,
        @Query("limite") limite: Int = 20
    ): Response<PaginaResponse<ServicioResponse>>

    @GET("servicios/categorias")
    suspend fun categorias(): Response<List<CategoriaResponse>>

    @GET("servicios/{id}")
    suspend fun detalle(@Path("id") id: Int): Response<ServicioDetalleResponse>

    @GET("servicios/{id}/disponibilidad")
    suspend fun disponibilidad(
        @Path("id") id: Int,
        @Query("desde") desde: String,
        @Query("hasta") hasta: String
    ): Response<List<DisponibilidadResponse>>
}
