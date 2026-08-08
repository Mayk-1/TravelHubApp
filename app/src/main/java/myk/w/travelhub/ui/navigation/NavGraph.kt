package myk.w.travelhub.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import myk.w.travelhub.ui.screen.catalogo.CatalogoScreen
import myk.w.travelhub.ui.screen.home.HomeScreen
import myk.w.travelhub.ui.screen.itinerarios.DetalleViajeScreen
import myk.w.travelhub.ui.screen.itinerarios.ItinerariosScreen
import myk.w.travelhub.ui.screen.login.LoginScreen
import myk.w.travelhub.ui.screen.servicio.DetalleServicioScreen

@Composable
fun NavGraph(sesionActiva: Boolean) {

    val navController = rememberNavController()

    // La barra inferior solo aparece en las pestanas, nunca en el login ni
    // en las pantallas de detalle.
    val entrada by navController.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route
    val mostrarBarra = Pestana.entries.any { it.screen.route == rutaActual }

    Scaffold(
        bottomBar = {
            if (mostrarBarra) BarraInferior(navController, entrada?.destination)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (sesionActiva) Screens.Home.route else Screens.Login.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screens.Login.route) {
                LoginScreen(
                    onLoginExitoso = {
                        navController.navigate(Screens.Home.route) {
                            // Saca el login del historial: si el usuario pulsa
                            // "atras" desde Home, sale de la app en vez de volver
                            // al formulario ya estando autenticado.
                            popUpTo(Screens.Login.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screens.Home.route) {
                HomeScreen(
                    onCerrarSesion = {
                        navController.navigate(Screens.Login.route) {
                            // Se limpia TODO el historial: tras cerrar sesion no
                            // debe quedar ninguna pantalla con datos del usuario
                            // accesible con el boton atras.
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    // Los atajos llevan a las pestanas con las mismas opciones
                    // que la barra inferior, para que el estado se conserve y
                    // la pestana quede marcada correctamente.
                    onIrACatalogo = { navController.irAPestana(Screens.Catalogo) },
                    onIrAViajes = { navController.irAPestana(Screens.Itinerarios) }
                )
            }

            composable(Screens.Catalogo.route) {
                CatalogoScreen(
                    onServicioClick = { id ->
                        navController.navigate(Screens.DetalleServicio.con(id))
                    }
                )
            }

            composable(Screens.Itinerarios.route) {
                ItinerariosScreen(
                    onViajeClick = { id ->
                        navController.navigate(Screens.DetalleViaje.con(id))
                    }
                )
            }

            composable(Screens.Chat.route) { EnConstruccion("Chat") }

            composable(Screens.DetalleServicio.route) { entradaRuta ->
                val id = entradaRuta.arguments?.getString("id")?.toIntOrNull()
                if (id == null) {
                    EnConstruccion("Servicio no valido")
                } else {
                    DetalleServicioScreen(
                        servicioId = id,
                        onVolver = { navController.popBackStack() },
                        onReservaCreada = {
                            // Tras reservar se lleva al usuario a sus viajes,
                            // que es donde tiene que anadir la reserva para
                            // que cuente en la calculadora de costos.
                            navController.irAPestana(Screens.Itinerarios)
                        }
                    )
                }
            }

            composable(Screens.DetalleViaje.route) { entradaRuta ->
                // El argumento llega como texto en la ruta; si no se puede
                // convertir, se vuelve atras en vez de reventar.
                val id = entradaRuta.arguments?.getString("id")?.toIntOrNull()
                if (id == null) {
                    EnConstruccion("Viaje no valido")
                } else {
                    DetalleViajeScreen(
                        itinerarioId = id,
                        onVolver = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}


@Composable
private fun BarraInferior(
    navController: NavHostController,
    destinoActual: NavDestination?
) {
    NavigationBar {
        Pestana.entries.forEach { pestana ->
            // Se mira toda la jerarquia y no solo la ruta exacta para que la
            // pestana siga marcada si mas adelante se anidan subpantallas
            // dentro de ella.
            val seleccionada = destinoActual?.hierarchy
                ?.any { it.route == pestana.screen.route } == true

            NavigationBarItem(
                selected = seleccionada,
                onClick = { navController.irAPestana(pestana.screen) },
                icon = { Icon(pestana.icono, contentDescription = pestana.etiqueta) },
                label = { Text(pestana.etiqueta) }
            )
        }
    }
}


/**
 * Navega a una pestana de la barra inferior.
 *
 * Estas tres opciones son las que hacen que una barra inferior se comporte
 * como se espera:
 *
 *   popUpTo(inicio) + saveState -> no se apila una pantalla nueva cada vez
 *       que se cambia de pestana, pero cada una recuerda donde estaba
 *       (scroll, filtros del catalogo, etc).
 *   launchSingleTop -> pulsar la pestana actual no la vuelve a crear.
 *   restoreState -> al volver, se recupera lo guardado.
 *
 * Esta extraido a una funcion porque lo usan tres sitios: la barra, los
 * atajos de Home y el salto a Viajes tras crear una reserva. Repetirlo
 * invitaba a que uno de los tres se quedara desincronizado.
 */
private fun NavHostController.irAPestana(destino: Screens) {
    navigate(destino.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}


/** Marcador para las pestanas que todavia no tienen pantalla. */
@Composable
private fun EnConstruccion(nombre: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$nombre — en construccion",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
