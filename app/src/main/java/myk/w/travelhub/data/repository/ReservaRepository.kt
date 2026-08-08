package myk.w.travelhub.data.repository

import myk.w.travelhub.data.api.RetrofitClient
import myk.w.travelhub.data.api.ejecutarLlamada
import myk.w.travelhub.data.model.request.AgregarParadaRequest
import myk.w.travelhub.data.model.request.CrearReservaRequest
import myk.w.travelhub.data.model.response.ReservaResponse

class ReservaRepository {

    private val api = RetrofitClient.reservas

    suspend fun crear(
        servicioId: Int,
        fechaInicio: String,
        fechaFin: String? = null,
        numPersonas: Int = 1,
        notas: String? = null
    ): Result<ReservaResponse> = ejecutarLlamada {
        api.crear(
            CrearReservaRequest(
                servicioId = servicioId,
                fechaInicio = fechaInicio,
                fechaFin = fechaFin,
                numPersonas = numPersonas,
                notas = notas?.takeIf { it.isNotBlank() }
            )
        )
    }

    suspend fun mias(estado: String? = null): Result<List<ReservaResponse>> =
        ejecutarLlamada { api.mias(estado) }

    suspend fun agregarAlItinerario(
        itinerarioId: Int,
        diaNumero: Int,
        reservaId: Int
    ): Result<Map<String, Int>> = ejecutarLlamada {
        api.agregarAlItinerario(
            itinerarioId,
            diaNumero,
            AgregarParadaRequest(reservaId = reservaId)
        )
    }
}
