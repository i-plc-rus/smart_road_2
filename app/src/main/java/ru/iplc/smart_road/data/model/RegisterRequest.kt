package ru.iplc.smart_road.data.model

data class RegisterRequest(
    val username: String,
    val fio: String,
    val phone: String,
    val email: String,
    val password: String
)
