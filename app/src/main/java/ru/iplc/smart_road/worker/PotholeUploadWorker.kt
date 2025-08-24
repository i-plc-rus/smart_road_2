package ru.iplc.smart_road.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import retrofit2.HttpException
import ru.iplc.smart_road.data.local.TokenManager
import ru.iplc.smart_road.data.model.PotholeDataRequest
import ru.iplc.smart_road.data.remote.ApiService
import ru.iplc.smart_road.db.AppDatabase
import java.io.IOException
import java.util.concurrent.TimeUnit

class PotholeUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val dao = AppDatabase.getInstance(appContext).potholeDao()
    private val api = ApiService.create()
    private val tokenManager = TokenManager(appContext)

    override suspend fun doWork(): Result = coroutineScope {
        try {
            val userId = tokenManager.getUserId()?.toIntOrNull() ?: 0
            if (userId == 0) {
                Log.e(TAG, "Не удалось получить user_id")
                /*return@coroutineScope Result.failure(
                    workDataOf("error_message" to "Не удалось получить user_id")
                )*/
            }

            val totalCount = dao.getNewCount()
            if (totalCount == 0) {
                Log.d(TAG, "Нет данных для отправки")
                return@coroutineScope Result.success()
            }

            Log.d(TAG, "Всего к отправке: $totalCount записей")

            val semaphore = Semaphore(MAX_PARALLEL_REQUESTS)
            val failedIds = mutableListOf<Long>()
            var offset = 0

            while (true) {
                val batch = dao.getUnsentBatch(BATCH_SIZE, offset)
                if (batch.isEmpty()) break

                val success = semaphore.withPermit {
                    try {
                        val request = PotholeDataRequest(user_id = userId, data = batch)
                        val response = api.uploadPotholeData(request)
                        if (response.isSuccessful) {
                            dao.markAsSent(batch.map { it.id })
                            Log.d(TAG, "✅ Успешно отправлено ${batch.size} записей")
                            true
                        } else {
                            Log.w(TAG, "❌ Ошибка отправки: ${response.code()} ${response.message()}")
                            failedIds.addAll(batch.map { it.id })
                            false
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Сетевая ошибка", e)
                        failedIds.addAll(batch.map { it.id })
                        false
                    } catch (e: HttpException) {
                        Log.e(TAG, "HTTP ошибка", e)
                        failedIds.addAll(batch.map { it.id })
                        false
                    } catch (e: Exception) {
                        Log.e(TAG, "Неизвестная ошибка", e)
                        failedIds.addAll(batch.map { it.id })
                        false
                    }
                }

                offset += BATCH_SIZE
            }

            return@coroutineScope if (failedIds.isEmpty()) {
                Log.d(TAG, "🎉 Все данные успешно отправлены")
                Result.success()
            } else {
                val errorMessage = "Не удалось отправить записи с ID: ${failedIds.joinToString(",")}"
                Log.w(TAG, errorMessage)
                Result.failure(workDataOf("error_message" to errorMessage))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Фатальная ошибка", e)
            return@coroutineScope Result.failure(
                workDataOf("error_message" to (e.localizedMessage ?: "Неизвестная ошибка"))
            )
        }
    }

    companion object {
        private const val TAG = "PotholeUploadWorker"
        private const val BATCH_SIZE = 5000
        private const val MAX_PARALLEL_REQUESTS = 3

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
