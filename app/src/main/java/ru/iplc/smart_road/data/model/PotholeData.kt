package ru.iplc.smart_road.data.model

import androidx.room.Entity

import androidx.room.PrimaryKey

@Entity(tableName = "pothole_data_v2")
data class PotholeData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    // Акселерометр
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    // Гироскоп
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    // Магнитометр
    val magX: Float = 0f,
    val magY: Float = 0f,
    val magZ: Float = 0f,
    // Освещенность
    val light: Float = 0f,
    val isSent: Boolean = false
)
