package myk.w.travelhub.ui.screen.login

import myk.w.travelhub.data.model.response.UsuarioResponse

/**
 * Estados posibles de la pantalla de login.
 *
 * Un sealed interface obliga a cubrir todos los casos en el `when` de la UI,
 * asi no se olvida manejar el error o la carga.
 */
sealed interface LoginUiState {
    data object Inicial : LoginUiState
    data object Cargando : LoginUiState
    data class Exito(val usuario: UsuarioResponse) : LoginUiState
    data class Error(val mensaje: String) : LoginUiState
}
