package myk.w.travelhub.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// Extension a nivel de archivo: crea (una sola vez) el DataStore "sesion".
private val Context.dataStore by preferencesDataStore(name = "sesion")

/**
 * Guarda el token JWT y los datos basicos del usuario en disco, para que la
 * sesion sobreviva al cierre de la app.
 *
 * Se inicializa una sola vez desde TravelHubApp.onCreate().
 */
object TokenStore {

    private val KEY_TOKEN = stringPreferencesKey("token")
    private val KEY_NOMBRE = stringPreferencesKey("nombre")
    private val KEY_EMAIL = stringPreferencesKey("email")
    private val KEY_ROL = stringPreferencesKey("rol")

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Token actual como flujo observable; null si no hay sesion. */
    val token: Flow<String?>
        get() = appContext.dataStore.data.map { it[KEY_TOKEN] }

    val nombre: Flow<String?>
        get() = appContext.dataStore.data.map { it[KEY_NOMBRE] }

    val rol: Flow<String?>
        get() = appContext.dataStore.data.map { it[KEY_ROL] }

    suspend fun guardarSesion(token: String, nombre: String, email: String, rol: String) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_NOMBRE] = nombre
            prefs[KEY_EMAIL] = email
            prefs[KEY_ROL] = rol
        }
    }

    suspend fun cerrarSesion() {
        appContext.dataStore.edit { it.clear() }
    }

    /** true si ya hay un token guardado (para decidir la pantalla inicial). */
    suspend fun haySesion(): Boolean = token.first() != null

    /**
     * Lectura bloqueante, usada UNICAMENTE por AuthInterceptor.
     * OkHttp ejecuta los interceptores en un hilo de fondo propio, nunca en el
     * hilo principal, por eso aqui si es seguro bloquear.
     */
    fun tokenBloqueante(): String? = runBlocking { token.first() }
}
