package myk.w.travelhub.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import myk.w.travelhub.data.repository.AuthRepository

/** Datos que el usuario va escribiendo en el formulario. */
data class LoginFormState(
    val nombre: String = "",
    val email: String = "",
    val password: String = "",
    val rol: String = "turista",
    val modoRegistro: Boolean = false
) {
    /** Habilita el boton solo si el formulario tiene sentido. */
    val esValido: Boolean
        get() = email.contains("@") &&
                password.length >= 8 &&
                (!modoRegistro || nombre.isNotBlank())
}

class LoginViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Inicial)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _form = MutableStateFlow(LoginFormState())
    val form: StateFlow<LoginFormState> = _form.asStateFlow()

    fun onNombreChange(v: String) = _form.update { it.copy(nombre = v) }
    fun onEmailChange(v: String) = _form.update { it.copy(email = v) }
    fun onPasswordChange(v: String) = _form.update { it.copy(password = v) }
    fun onRolChange(v: String) = _form.update { it.copy(rol = v) }

    fun alternarModo() {
        _form.update { it.copy(modoRegistro = !it.modoRegistro) }
        _uiState.value = LoginUiState.Inicial   // limpia el error anterior
    }

    /** Un solo boton: segun el modo, hace login o registro. */
    fun enviar() {
        val f = _form.value
        if (!f.esValido) return

        viewModelScope.launch {
            _uiState.value = LoginUiState.Cargando

            val resultado = if (f.modoRegistro) {
                repository.registro(f.nombre, f.email, f.password, f.rol)
            } else {
                repository.login(f.email, f.password)
            }

            _uiState.value = resultado.fold(
                onSuccess = { LoginUiState.Exito(it.usuario) },
                onFailure = { LoginUiState.Error(it.message ?: "Error desconocido") }
            )
        }
    }

    /** Se llama despues de navegar, para que el estado no se re-dispare. */
    fun consumirEstado() {
        _uiState.value = LoginUiState.Inicial
    }
}
