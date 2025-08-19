package ru.iplc.smart_road

import android.app.Application
import com.yandex.mapkit.MapKitFactory
//import com.google.firebase.appdistribution.gradle.ApiService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.iplc.smart_road.data.local.PreferenceStorage
import ru.iplc.smart_road.data.local.TokenManager
import ru.iplc.smart_road.data.remote.AuthInterceptor
import ru.iplc.smart_road.data.repository.AuthRepository
import ru.iplc.smart_road.data.remote.ApiService


class SmartRoadApp: Application() {
    lateinit var authRepository: AuthRepository
    lateinit var tokenManager: TokenManager
    override fun onCreate() {
        super.onCreate()
        PreferenceStorage.init(this)

        MapKitFactory.setApiKey("863e51f7-4aea-4487-97d6-c6a29b904dba")

        tokenManager = TokenManager(this)

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .build()

        val apiService = Retrofit.Builder()
            .baseUrl("https://functions.yandexcloud.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)

        authRepository = AuthRepository(apiService, tokenManager)
    }
}