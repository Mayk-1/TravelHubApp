package myk.w.travelhub.data.repository

import myk.w.travelhub.data.api.RetrofitClient
import myk.w.travelhub.data.api.ejecutarLlamada
import myk.w.travelhub.data.model.request.CrearItinerarioRequest
import myk.w.travelhub.data.model.response.CostosResponse
import myk.w.travelhub.data.model.response.ItinerarioDetalleResponse
import myk.w.travelhub.data.model.response.ItinerarioResponse

class ItinerarioRepository {

    private val api = RetrofitClient.itinerarios

    suspend fun listar(): Result<List<ItinerarioResponse>> =
        ejecutarLlamada { api.listar() }

    suspend fun detalle(id: Int): Result<ItinerarioDetalleResponse> =
        ejecutarLlamada { api.detalle(id) }

    suspend fun crear(
        titulo: String,
        destino: String,
        fechaInicio: String,
        fechaFin: String,
        presupuesto: Double?
    ): Result<ItinerarioResponse> = ejecutarLlamada {
        api.crear(
            CrearItinerarioRequest(
                titulo = titulo.trim(),
                destino = destino.trim().ifBlank { "Puno" },
                fechaInicio = fechaInicio,
                fechaFin = fechaFin,
                presupuesto = presupuesto
            )
        )
    }

    suspend fun eliminar(id: Int): Result<Unit> = try {
        val response = api.eliminar(id)
        if (response.isSuccessful) Result.success(Unit)
        else Result.failure(Exception("No se pudo eliminar el viaje (${response.code()})"))
    } catch (e: Exception) {
        Result.failure(Exception("No se pudo conectar con el servidor. Revisa tu conexion."))
    }

    suspend fun costos(itinerarioId: Int): Result<CostosResponse> =
        ejecutarLlamada { api.costos(itinerarioId) }
}
