package ru.iplc.smart_road.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import ru.iplc.smart_road.SmartRoadApp
import ru.iplc.smart_road.data.local.TokenManager
import ru.iplc.smart_road.data.model.PotholeData
import ru.iplc.smart_road.data.model.PotholeDataRequest
import ru.iplc.smart_road.data.model.S3UploadUrlRequest
import ru.iplc.smart_road.data.remote.ApiService
import ru.iplc.smart_road.db.AppDatabase
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PotholeUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val dao = AppDatabase.getInstance(appContext).potholeDao()
    //private val api = ApiService.create()
    private val api: ApiService by lazy {
        (applicationContext as SmartRoadApp).apiService
    }

    private val tokenManager = TokenManager(appContext)

    override suspend fun doWork(): Result = coroutineScope {
        try {
            val userId = tokenManager.getUserId()?.toIntOrNull() ?: 0
            if (userId == 0) {
                Log.w(TAG, "user_id = 0, но продолжаем отправку")
            }

            var totalSent = 0

            while (true) {
                val batch = dao.getUnsentBatch(BATCH_SIZE, 0) // всегда берём с offset=0, т.к. после удаления offset сдвигается
                if (batch.isEmpty()) {
                    Log.d(TAG, "🎉 Все данные отправлены. Всего: $totalSent записей")
                    break
                }

                val filename = System.currentTimeMillis().toString()

                // 1. формируем zip
                val zipFile = FileUtils.writeCsvAndZip(applicationContext, filename, batch)

                // 2. получаем S3 upload url

                val response = api.getS3UploadUrl(S3UploadUrlRequest(userId, "$filename.zip"))
                if (!response.isSuccessful || response.body() == null) {
                    Log.e(TAG, "Ошибка получения S3 ссылки: ${response.code()}")
                    return@coroutineScope Result.retry()
                }
                val s3data = response.body()!!

                // 3. PUT zip в S3
                val okHttp = OkHttpClient()
                val req = Request.Builder()
                    .url(s3data.upload_url)
                    .put(zipFile.asRequestBody(s3data.content_type.toMediaType()))
                    .build()

                okHttp.newCall(req).execute().use { putResp ->
                    if (!putResp.isSuccessful) {
                        Log.e(TAG, "Ошибка PUT в S3: ${putResp.code}")
                        return@coroutineScope Result.retry()
                    }
                }

                // 4. успех → удаляем записи
                //dao.deleteByIds(batch.map { it.id })
                markAsSentChunked(batch.map { it.id })
                totalSent += batch.size
                Log.d(TAG, "✅ Отправлено и удалено ${batch.size}, всего $totalSent")
            }
            //dao.deleteSent()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Фатальная ошибка", e)
            Result.failure(workDataOf("error_message" to (e.localizedMessage ?: "Неизвестная ошибка")))
        }
    }


    private suspend fun markAsSentChunked(ids: List<Long>) {
        ids.chunked(SQL_SAFE_CHUNK).forEach { chunk ->
            dao.markAsSent(chunk)
        }
    }



    object FileUtils {
        fun writeCsvAndZip(context: Context, filename: String, rows: List<PotholeData>): File {
            val csvFile = File(context.cacheDir, "$filename.csv")
            csvFile.bufferedWriter().use { writer ->
                // Заголовок CSV
                writer.write(
                    "id,timestamp,lat,lon," +
                            "accelX,accelY,accelZ," +
                            "gyroX,gyroY,gyroZ," +
                            "magX,magY,magZ," +
                            "light,isSent\n"
                )
                // Данные
                for (r in rows) {
                    writer.write(
                        "${r.id},${r.timestamp},${r.latitude},${r.longitude}," +
                                "${r.accelX},${r.accelY},${r.accelZ}," +
                                "${r.gyroX},${r.gyroY},${r.gyroZ}," +
                                "${r.magX},${r.magY},${r.magZ}," +
                                "${r.light},${r.isSent}\n"
                    )
                }
            }

            val zipFile = File(context.cacheDir, "$filename.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val entry = ZipEntry(csvFile.name)
                zos.putNextEntry(entry)
                csvFile.inputStream().copyTo(zos)
                zos.closeEntry()
            }
            return zipFile
        }
    }



    companion object {
        private const val TAG = "PotholeUploadWorker"
        private const val BATCH_SIZE = 10000

        private const val SQL_SAFE_CHUNK = 100

        //private const val MAX_PARALLEL_REQUESTS = 3

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PotholeUploadWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "pothole_upload_work",
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }
    }
}
