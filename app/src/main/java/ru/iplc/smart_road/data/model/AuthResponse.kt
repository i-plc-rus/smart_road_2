package ru.iplc.smart_road.data.model

data class AuthResponse(
    val access_token: String,
    val refresh_token: String,
    val user_id: Number
)
