package myk.w.travelhub.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import myk.w.travelhub.ui.screen.home.HomeScreen
import myk.w.travelhub.ui.screen.login.LoginScreen

@Composable
fun NavGraph(sesionActiva: Boolean) {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (sesionActiva) Screens.Home.route else Screens.Login.route
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
                        popUpTo(Screens.Home.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
