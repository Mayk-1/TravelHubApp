package myk.w.travelhub

import android.app.Application
import myk.w.travelhub.data.local.TokenStore

/**
 * Clase Application: se crea una sola vez, antes que cualquier Activity.
 *
 * La usamos para inicializar el TokenStore, que necesita un Context para
 * abrir el DataStore. Asi evitamos tener que pasar el Context a mano por
 * todas las capas (Repository, Interceptor, etc).
 *
 * Esta registrada en el AndroidManifest con android:name=".TravelHubApp".
 */
class TravelHubApp : Application() {

    override fun onCreate() {
        super.onCreate()
        TokenStore.init(this)
    }
}
