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
            .addInterceptor(AuthInterceptor(tokenManager))
            .build()

        apiService = Retrofit.Builder()
            .baseUrl("https://d5dqbuds89dfpltkrqd7.fary004x.apigw.yandexcloud.net/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(AuthInterceptor(tokenManager))
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                    .connectionSpecs(listOf(ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
                    .protocols(listOf(Protocol.HTTP_1_1))
                    .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                    .build()
            )
            .build()
            .create(ApiService::class.java)

        authRepository = AuthRepository(apiService, tokenManager)
    }
}


