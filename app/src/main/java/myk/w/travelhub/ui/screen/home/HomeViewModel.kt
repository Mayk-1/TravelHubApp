package myk.w.travelhub.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import myk.w.travelhub.data.local.TokenStore

/**
 * Solo lee el nombre y el rol de la sesion guardada. No consulta al servidor:
 * la pantalla de Inicio se ve igual con el backend caido.
 *
 * Cerrar sesion ya no vive aqui, sino en PerfilViewModel.
 */
class HomeViewModel : ViewModel() {

    // stateIn convierte el Flow del DataStore en un StateFlow con valor
    // inicial, que es lo que Compose necesita para pintar de inmediato.
    val nombre: StateFlow<String?> = TokenStore.nombre
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val rol: StateFlow<String?> = TokenStore.rol
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
