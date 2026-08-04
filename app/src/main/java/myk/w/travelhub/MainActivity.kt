package myk.w.travelhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import myk.w.travelhub.data.local.TokenStore
import myk.w.travelhub.ui.navigation.NavGraph
import myk.w.travelhub.ui.theme.TravelHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TravelHubTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RaizApp()
                }
            }
        }
    }
}

/**
 * Decide la pantalla inicial segun si ya hay un token guardado.
 *
 * Leer el DataStore es una operacion suspendida, asi que mientras responde
 * se muestra un indicador de carga en lugar de parpadear el login.
 */
@Composable
private fun RaizApp() {
    val sesionActiva by produceState<Boolean?>(initialValue = null) {
        value = TokenStore.haySesion()
    }

    when (sesionActiva) {
        null -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        else -> NavGraph(sesionActiva = sesionActiva == true)
    }
}
