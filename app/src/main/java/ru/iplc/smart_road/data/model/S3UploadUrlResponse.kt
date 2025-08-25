package ru.iplc.smart_road.data.model

data class S3UploadUrlResponse(
    val upload_url: String,
    val file_url: String,
    val content_type: String
)
