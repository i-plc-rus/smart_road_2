package ru.iplc.smart_road.data.remote

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlinx.coroutines.runBlocking
import ru.iplc.smart_road.data.local.TokenManager

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()

        // Run this code blocking to get the token
        val token = runBlocking {
            tokenManager.getToken()
        }

        if (token != null) {
            request.addHeader("Authorization", "Bearer $token")
        }

        return chain.proceed(request.build())
    }
}