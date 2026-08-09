package myk.w.travelhub.data.api

import com.google.gson.GsonBuilder
import myk.w.travelhub.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = GsonBuilder()
        .registerTypeAdapter(Boolean::class.java, BooleanFlexible())
        .registerTypeAdapter(Boolean::class.javaObjectType, BooleanFlexible())
        .create()

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
}
