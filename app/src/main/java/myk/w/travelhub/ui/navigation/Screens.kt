package myk.w.travelhub.ui.navigation

/**
 * Rutas de navegacion. Usar un sealed class en vez de strings sueltos evita
 * errores de tipeo: si escribes mal el nombre, no compila.
 */
sealed class Screens(val route: String) {
    data object Login : Screens("login")
    data object Home : Screens("home")

    // Pendientes de implementar (MVP del enunciado):
    data object Catalogo : Screens("catalogo")
    data object Itinerario : Screens("itinerario")
    data object Costos : Screens("costos")
    data object Chat : Screens("chat")
    data object Perfil : Screens("perfil")
}
