package ru.iplc.smart_road.data.model

data class SensorRecord(
    val timestampMs: Long,
    val accX: Float,
    val accY: Float,
    val accZ: Float,
    val lat: Double?,
    val lon: Double?,
    val accuracyMeters: Float?
)

// Payload DTO for server
data class BatchPayload(
    val sessionId: String,
    val records: List<SensorRecord>
)