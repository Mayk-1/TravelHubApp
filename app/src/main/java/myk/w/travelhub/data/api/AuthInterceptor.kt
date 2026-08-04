package myk.w.travelhub.data.api

import myk.w.travelhub.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adjunta automaticamente el header "Authorization: Bearer <token>" a cada
 * peticion, si es que hay una sesion activa.
 *
 * Sin esto, todos los endpoints protegidos del backend responden 401.
 */
class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Login y registro son publicos: no llevan token.
        val esPublico = original.url.encodedPath.contains("/auth/login") ||
                original.url.encodedPath.contains("/auth/registro")

        val token = if (esPublico) null else TokenStore.tokenBloqueante()

        val request = if (token.isNullOrBlank()) {
            original
        } else {
            original.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        }

        return chain.proceed(request)
    }
}
