package myk.w.travelhub.ui.screen.login

import myk.w.travelhub.data.model.response.UsuarioResponse

sealed interface LoginUiState {
    data object Inicial : LoginUiState
    data object Cargando : LoginUiState
    data class Exito(val usuario: UsuarioResponse) : LoginUiState
    data class Error(val mensaje: String) : LoginUiState
}
