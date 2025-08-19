package ru.iplc.smart_road.data.model

import androidx.room.Entity

import androidx.room.PrimaryKey

@Entity(tableName = "pothole_data")
data class PotholeData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val isSent: Boolean = false
)
