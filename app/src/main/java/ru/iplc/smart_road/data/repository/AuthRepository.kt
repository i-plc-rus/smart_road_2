package ru.iplc.smart_road.data.repository

import android.content.Context
import android.net.Uri
import ru.iplc.smart_road.data.local.TokenManager
import ru.iplc.smart_road.data.model.AuthRequest
import ru.iplc.smart_road.data.model.AuthResponse
import ru.iplc.smart_road.data.model.RegisterRequest
import ru.iplc.smart_road.data.model.User
import ru.iplc.smart_road.data.remote.ApiService
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import ru.iplc.smart_road.data.model.AvatarResponse
import ru.iplc.smart_road.data.model.UserProfile
import java.io.File


//import ru.iplc.smart_road.network.xApiService

// AuthRepository.kt
class AuthRepository(private val apiService: ApiService, private val tokenManager: TokenManager) {

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            Log.i("AuthRepository", "Login started")
            val response = apiService.login(AuthRequest(login = email, password))

            if (response.isSuccessful) {
                response.body()?.let { authResponse ->
                    tokenManager.saveTokens(authResponse.access_token, (authResponse.user_id).toString())
                    Result.Success(authResponse)
                } ?: Result.Error("Empty response body")
            } else {
                Result.Error(response.message())
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Login error", e)
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun register(username: String, fio: String, phone: String, email: String, password: String): Result<AuthResponse> {
        return try {
            Log.i("AuthRepository", "Reg started")
            val response = apiService.register(RegisterRequest(username, fio, phone, email, password))
            Log.i("AuthRepository", response.toString())
            if (response.isSuccessful) {
                response.body()?.let { authResponse ->
                    tokenManager.saveTokens(authResponse.access_token, authResponse.user_id.toString())
                    Result.Success(authResponse)
                } ?: Result.Error("Empty response body")
            } else {
                Result.Error(response.message())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getProfile(): Result<UserProfile> {
        return try {
            val response = apiService.getProfile()

            if (response.isSuccessful) {
                response.body()?.let {
                    Result.Success(it)
                } ?: Result.Error("Empty response body")
            } else {
                if (response.code() == 401) {
                    tokenManager.clearTokens()
                }
                Result.Error(response.message())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }



    suspend fun logout() {
        tokenManager.clearTokens()
    }

    sealed class Result<out T> {
        data class Success<out T>(val data: T) : Result<T>()
        data class Error(val message: String) : Result<Nothing>()
        object Loading : Result<Nothing>()
    }

    suspend fun isAuthenticated(): Boolean {
        return tokenManager.isLoggedIn()
    }

    suspend fun uploadAvatar(uri: Uri, context: Context): Result<AvatarResponse> {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
                ?: return Result.Error("Не удалось открыть файл")

            val file = File(context.cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { output -> inputStream.copyTo(output) }

            val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, requestBody)

            val response = apiService.uploadAvatar(part)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Ошибка загрузки"
                Result.Error("Ошибка загрузки: ${response.code()} $errorMsg")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка загрузки")
        }
    }

    suspend fun updateProfile(userProfile: UserProfile): Result<UserProfile> {
        return try {
            val response = apiService.updateProfile(userProfile) // PUT /user
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Неизвестная ошибка"
                Result.Error("Ошибка обновления профиля: ${response.code()} $errorMsg")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }






}