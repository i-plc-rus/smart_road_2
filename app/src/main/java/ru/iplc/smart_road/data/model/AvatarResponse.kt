package ru.iplc.smart_road.data.model

import com.google.gson.annotations.SerializedName

data class AvatarResponse(
    @SerializedName("avatar_url") val avatarUrl: String
)
