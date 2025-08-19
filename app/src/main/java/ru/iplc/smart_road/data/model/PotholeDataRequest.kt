package ru.iplc.smart_road.data.model

data class PotholeDataRequest(
    val user_id: Int,
    val data: List<PotholeData>
)
