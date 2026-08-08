package myk.w.travelhub.data.api

import com.google.gson.GsonBuilder
import myk.w.travelhub.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Punto unico de configuracion de la red.
 *
 * La URL base NO esta escrita aqui: viene de BuildConfig.BASE_URL, que se
 * define en app/build.gradle.kts. Asi el build de debug apunta al backend
 * local y el de release al desplegado en la nube, sin tocar codigo.
 */
object RetrofitClient {

    private val logging = HttpLoggingInterceptor().apply {
        // En release no queremos volcar tokens ni contrasenas al Logcat.
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())   // primero mete el token
        .addInterceptor(logging)             // y despues loguea la peticion final
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Gson configurado para tolerar los booleanos de MySQL.
     *
     * MySQL manda 1 y 0 donde la app espera true y false (ver
     * BooleanFlexible.kt). Hay que registrar los DOS tipos: el primitivo
     * `boolean` de Kotlin y el `java.lang.Boolean` de los campos que
     * admiten null. Registrar solo uno deja el otro fallando.
     */
    private val gson = GsonBuilder()
        .registerTypeAdapter(Boolean::class.java, BooleanFlexible())
        .registerTypeAdapter(Boolean::class.javaObjectType, BooleanFlexible())
        .create()

    // "by lazy": el objeto Retrofit se construye la primera vez que se usa
    // y luego se reutiliza en toda la app.
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val auth: AuthApiService by lazy { retrofit.create(AuthApiService::class.java) }
    val servicios: ServicioApiService by lazy { retrofit.create(ServicioApiService::class.java) }
    val itinerarios: ItinerarioApiService by lazy { retrofit.create(ItinerarioApiService::class.java) }
    val reservas: ReservaApiService by lazy { retrofit.create(ReservaApiService::class.java) }

    // Conforme avance el proyecto se agregan aqui los demas servicios:
    // val chat: ChatApiService by lazy { retrofit.create(...) }
}
