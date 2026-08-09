package myk.w.travelhub.data.repository

import myk.w.travelhub.data.api.RetrofitClient
import myk.w.travelhub.data.api.ejecutarLlamada
import myk.w.travelhub.data.local.TokenStore
import myk.w.travelhub.data.model.response.MiServicioResponse
import myk.w.travelhub.data.model.response.PerfilResponse
import myk.w.travelhub.data.model.response.ReservaRecibidaResponse
import myk.w.travelhub.data.model.response.ReservaResponse

/**
 * Reúne lo que hace falta para la pantalla de perfil.
 *
 * No tiene endpoint propio: compone datos de tres controllers distintos del
 * backend. Por eso vive aquí y no dentro de AuthRepository.
 */
class PerfilRepository {

    private val auth = RetrofitClient.auth
    private val servicios = RetrofitClient.servicios
    private val reservas = RetrofitClient.reservas

    suspend fun perfil(): Result<PerfilResponse> = ejecutarLlamada { auth.usuarioActual() }

    /** Solo para prestadores; el backend responde 403 a los demás. */
    suspend fun misServicios(): Result<List<MiServicioResponse>> =
        ejecutarLlamada { servicios.mios() }

    suspend fun reservasRecibidas(): Result<List<ReservaRecibidaResponse>> =
        ejecutarLlamada { reservas.recibidas() }

    /** Reservas hechas por el turista, para el contador de su perfil. */
    suspend fun misReservas(): Result<List<ReservaResponse>> =
        ejecutarLlamada { reservas.mias(null) }

    suspend fun cerrarSesion() = TokenStore.cerrarSesion()
}
