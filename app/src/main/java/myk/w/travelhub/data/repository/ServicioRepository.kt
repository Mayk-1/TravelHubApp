package myk.w.travelhub.data.repository

import myk.w.travelhub.data.api.RetrofitClient
import myk.w.travelhub.data.api.ejecutarLlamada
import myk.w.travelhub.data.model.response.CategoriaResponse
import myk.w.travelhub.data.model.response.DisponibilidadResponse
import myk.w.travelhub.data.model.response.PaginaResponse
import myk.w.travelhub.data.model.response.ServicioDetalleResponse
import myk.w.travelhub.data.model.response.ServicioResponse

class ServicioRepository {

    private val api = RetrofitClient.servicios

    /**
     * Los filtros vacios se envian como null para que Retrofit los omita
     * de la URL. Mandar `buscar=` vacio haria que el backend filtrase por
     * cadena vacia en vez de no filtrar.
     */
    suspend fun listar(
        categoria: String? = null,
        buscar: String? = null,
        orden: String? = null,
        precioMax: Double? = null,
        pagina: Int = 1
    ): Result<PaginaResponse<ServicioResponse>> = ejecutarLlamada {
        api.listar(
            categoria = categoria?.takeIf { it.isNotBlank() },
            buscar = buscar?.takeIf { it.isNotBlank() },
            orden = orden?.takeIf { it.isNotBlank() },
            precioMax = precioMax,
            pagina = pagina
        )
    }

    suspend fun categorias(): Result<List<CategoriaResponse>> = ejecutarLlamada {
        api.categorias()
    }

    suspend fun detalle(id: Int): Result<ServicioDetalleResponse> = ejecutarLlamada {
        api.detalle(id)
    }

    suspend fun disponibilidad(
        id: Int,
        desde: String,
        hasta: String
    ): Result<List<DisponibilidadResponse>> = ejecutarLlamada {
        api.disponibilidad(id, desde, hasta)
    }
}
