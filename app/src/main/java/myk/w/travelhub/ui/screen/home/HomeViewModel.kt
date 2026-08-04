package myk.w.travelhub.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import myk.w.travelhub.data.local.TokenStore
import myk.w.travelhub.data.repository.AuthRepository

class HomeViewModel : ViewModel() {

    private val repository = AuthRepository()

    // stateIn convierte el Flow del DataStore en un StateFlow con valor
    // inicial, que es lo que Compose necesita para pintar de inmediato.
    val nombre: StateFlow<String?> = TokenStore.nombre
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val rol: StateFlow<String?> = TokenStore.rol
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun cerrarSesion(alTerminar: () -> Unit) {
        viewModelScope.launch {
            repository.cerrarSesion()
            alTerminar()
        }
    }
}
