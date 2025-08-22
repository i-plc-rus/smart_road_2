package ru.iplc.smart_road.data.model

import com.google.gson.annotations.SerializedName

data class UserProfile(
    val username: String? = null,
    val fio: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val password: String? = null,
    //val avatarUrl: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String
)
