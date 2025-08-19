package ru.iplc.smart_road.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "batches")
data class BatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val jsonPayload: String, // сериализуем список SensorRecord в JSON для простоты
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0
)
