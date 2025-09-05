package ru.iplc.smart_road.data.remote

import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import ru.iplc.smart_road.data.model.AuthRequest
import ru.iplc.smart_road.data.model.AuthResponse
import ru.iplc.smart_road.data.model.AvatarResponse
import ru.iplc.smart_road.data.model.BatchPayload
import ru.iplc.smart_road.data.model.PatternStat
import ru.iplc.smart_road.data.model.PotholeData
import ru.iplc.smart_road.data.model.PotholeDataRequest
import ru.iplc.smart_road.data.model.RegisterRequest
import ru.iplc.smart_road.data.model.S3UploadUrlRequest
import ru.iplc.smart_road.data.model.S3UploadUrlResponse
import ru.iplc.smart_road.data.model.User
import ru.iplc.smart_road.data.model.UserProfile
import java.util.concurrent.TimeUnit

interface ApiService {
    @POST("login")
    suspend fun login(@Body authRequest: AuthRequest): Response<AuthResponse>

    @POST("registration")
    suspend fun register(@Body registerRequest: RegisterRequest): Response<AuthResponse>

    @GET("profile")
    suspend fun getProfile(): Response<UserProfile>

    @POST("indata")
    suspend fun uploadPotholeData(@Body request: PotholeDataRequest): Response<Unit>

    @Multipart
    @POST("user/avatar")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): Response<AvatarResponse>

    @PUT("user/profile")
    suspend fun updateProfile(@Body user: UserProfile): Response<UserProfile>

    @POST("indatas3geturl")
    suspend fun getS3UploadUrl(@Body request: S3UploadUrlRequest): Response<S3UploadUrlResponse>

    @GET("pattern_stat")
    suspend fun getPatternStat(): Response<List<PatternStat>>

}