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
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
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
                            navController.irAPestana(Screens.Itinerarios)
                        }
                    )
                }
            }

            composable(Screens.DetalleViaje.route) { entradaRuta ->
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

private fun NavHostController.irAPestana(destino: Screens) {
    navigate(destino.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

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
