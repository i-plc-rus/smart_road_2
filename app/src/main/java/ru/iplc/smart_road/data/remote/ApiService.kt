package ru.iplc.smart_road.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import ru.iplc.smart_road.data.model.AuthRequest
import ru.iplc.smart_road.data.model.AuthResponse
import ru.iplc.smart_road.data.model.BatchPayload
import ru.iplc.smart_road.data.model.PotholeData
import ru.iplc.smart_road.data.model.PotholeDataRequest
import ru.iplc.smart_road.data.model.RegisterRequest
import ru.iplc.smart_road.data.model.User

interface ApiService {
    @POST("d4ecg0afm0a6m8547i10")
    suspend fun login(@Body authRequest: AuthRequest): Response<AuthResponse>

    @POST("d4esv050svbpfgltj7nt")
    suspend fun register(@Body registerRequest: RegisterRequest): Response<AuthResponse>

    @GET("users/me")
    suspend fun getProfile(): Response<User>

    @POST("d4edlqujb8517a04ajif")
    suspend fun uploadPotholeData(@Body request: PotholeDataRequest): Response<Unit>

    companion object {
        fun create(): ApiService {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://functions.yandexcloud.net/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(
                    OkHttpClient.Builder()
                        .addInterceptor(HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        })
                        .build()
                )
                .build()
            return retrofit.create(ApiService::class.java)
        }
    }
}