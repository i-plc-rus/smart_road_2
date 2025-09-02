package ru.iplc.smart_road

import android.app.Application
import com.yandex.mapkit.MapKitFactory
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
//import com.google.firebase.appdistribution.gradle.ApiService
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.iplc.smart_road.data.local.PreferenceStorage
import ru.iplc.smart_road.data.local.TokenManager
import ru.iplc.smart_road.data.remote.AuthInterceptor
import ru.iplc.smart_road.data.repository.AuthRepository
import ru.iplc.smart_road.data.remote.ApiService
import java.util.concurrent.TimeUnit


class SmartRoadApp: Application() {
    lateinit var authRepository: AuthRepository
    lateinit var tokenManager: TokenManager
    lateinit var apiService: ApiService

    override fun onCreate() {
        super.onCreate()
        PreferenceStorage.init(this)

        MapKitFactory.setApiKey("863e51f7-4aea-4487-97d6-c6a29b904dba")

        tokenManager = TokenManager(this)

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenManager))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .addInterceptor { chain ->
                var attempt = 0
                val maxAttempts = 5
                var response: okhttp3.Response? = null
                var lastException: Exception? = null

                while (attempt < maxAttempts) {
                    try {
                        response = chain.proceed(chain.request())
                        if (response.isSuccessful) {
                            return@addInterceptor response
                        }
                    } catch (e: Exception) {
                        lastException = e
                    }
                    attempt++
                }

                throw lastException ?: RuntimeException("Unknown network error")
            }
            .build()

        apiService = Retrofit.Builder()
            .baseUrl("https://d5dqbuds89dfpltkrqd7.fary004x.apigw.yandexcloud.net/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
            .create(ApiService::class.java)

        authRepository = AuthRepository(apiService, tokenManager)
    }
}


