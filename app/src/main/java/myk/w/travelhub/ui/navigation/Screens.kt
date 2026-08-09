package myk.w.travelhub.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screens(val route: String) {
    data object Login : Screens("login")

    // Pestanas de la barra inferior
    data object Home : Screens("home")
    data object Catalogo : Screens("catalogo")
    data object Itinerarios : Screens("itinerarios")
    data object Chat : Screens("chat")
    data object Perfil : Screens("perfil")

    // Pantallas de detalle (fuera de la barra)
    data object DetalleServicio : Screens("servicio/{id}") {
        fun con(id: Int) = "servicio/$id"
    }

    data object DetalleViaje : Screens("viaje/{id}") {
        fun con(id: Int) = "viaje/$id"
    }
}

enum class Pestana(
    val screen: Screens,
    val etiqueta: String,
    val icono: ImageVector
) {
    INICIO(Screens.Home, "Inicio", Icons.Filled.Home),
    CATALOGO(Screens.Catalogo, "Catalogo", Icons.Filled.Search),
    VIAJES(Screens.Itinerarios, "Viajes", Icons.Filled.Map),
    CHAT(Screens.Chat, "Chat", Icons.AutoMirrored.Filled.Chat),
    PERFIL(Screens.Perfil, "Perfil", Icons.Filled.Person)
}
