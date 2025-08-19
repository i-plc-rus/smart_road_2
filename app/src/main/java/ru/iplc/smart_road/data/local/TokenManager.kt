package ru.iplc.smart_road.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_data")

class TokenManager(private val context: Context) {

    companion object {
        private val JWT_TOKEN_KEY = stringPreferencesKey("jwt_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val TOKEN_EXPIRATION_KEY = stringPreferencesKey("token_expiration") // Добавлено для проверки срока действия
    }

    suspend fun getToken(): String? {
        return context.authDataStore.data
            .map { it[JWT_TOKEN_KEY] }
            .firstOrNull()
    }

    val jwtToken: Flow<String?> = context.authDataStore.data
        .map { it[JWT_TOKEN_KEY] }

    suspend fun saveTokens(
        jwtToken: String,
        userId: String,
        refreshToken: String? = null,
        expiresIn: Long? = null // Добавлен параметр срока действия
    ) {
        context.authDataStore.edit {
            it[JWT_TOKEN_KEY] = jwtToken
            it[USER_ID_KEY] = userId
            refreshToken?.let { token -> it[REFRESH_TOKEN_KEY] = token }
            expiresIn?.let { exp ->
                it[TOKEN_EXPIRATION_KEY] = (System.currentTimeMillis() + exp * 1000).toString()
            }
        }
    }

    suspend fun clearTokens() {
        context.authDataStore.edit {
            it.remove(JWT_TOKEN_KEY)
            it.remove(USER_ID_KEY)
            it.remove(REFRESH_TOKEN_KEY)
            it.remove(TOKEN_EXPIRATION_KEY)
        }
    }

    suspend fun getUserId(): String? {
        return context.authDataStore.data
            .map { it[USER_ID_KEY] }
            .firstOrNull()
    }

    // Новый метод для проверки аутентификации
    suspend fun isLoggedIn(): Boolean {
        val token = getToken()
        if (token == null) return false

        // Дополнительная проверка срока действия токена (если есть информация)
        val expirationTime = context.authDataStore.data
            .map { it[TOKEN_EXPIRATION_KEY]?.toLongOrNull() }
            .firstOrNull()

        return expirationTime == null || System.currentTimeMillis() < expirationTime
    }

    // Метод для получения refresh token (если нужен)
    suspend fun getRefreshToken(): String? {
        return context.authDataStore.data
            .map { it[REFRESH_TOKEN_KEY] }
            .firstOrNull()
    }
}