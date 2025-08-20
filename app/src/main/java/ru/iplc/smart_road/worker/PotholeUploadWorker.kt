package ru.iplc.smart_road.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import retrofit2.HttpException
import ru.iplc.smart_road.data.local.TokenManager
import ru.iplc.smart_road.data.model.PotholeDataRequest
import ru.iplc.smart_road.data.remote.ApiService
import ru.iplc.smart_road.db.AppDatabase
import java.io.IOException


class PotholeUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val dao = AppDatabase.getInstance(appContext).potholeDao()
    private val api = ApiService.create()
    private val tokenManager = TokenManager(appContext)

    override suspend fun doWork(): Result {
        return try {
            val userIdStr = tokenManager.getUserId()
            val userId = userIdStr?.toIntOrNull() ?: 0

            if (userId == 0) {
                Log.e("PotholeUploadWorker", "Не удалось получить user_id")
                return Result.failure()
            }

            // Получаем первую партию данных для отправки
            val dataBatch = dao.getUnsentBatch(BATCH_SIZE)

            if (dataBatch.isEmpty()) {
                Log.d("PotholeUploadWorker", "Нет данных для отправки")
                return Result.success()
            }

            Log.d("PotholeUploadWorker", "Отправка ${dataBatch.size} записей...")

            val request = PotholeDataRequest(user_id = userId, data = dataBatch)
            val response = api.uploadPotholeData(request)

            if (response.isSuccessful) {
                // Помечаем данные как отправленные
                dao.markAsSent(dataBatch.map { it.id })
                Log.d("PotholeUploadWorker", "Успешно отправлено ${dataBatch.size} записей")

                // Если есть еще данные, планируем следующую отправку
                val hasMoreData = dao.getNewCount() > 0
                if (hasMoreData) {
                    // Создаем цепочку воркеров для отправки всех данных
                    val nextRequest = OneTimeWorkRequestBuilder<PotholeUploadWorker>()
                        .build()

                    WorkManager.getInstance(applicationContext)
                        .beginWith(nextRequest)
                        .enqueue()
                }

                Result.success()
            } else {
                Log.w("PotholeUploadWorker", "Ошибка отправки: ${response.code()} ${response.message()}")
                Result.retry()
            }
        } catch (e: IOException) {
            Log.e("PotholeUploadWorker", "Сетевая ошибка", e)
            Result.retry()
        } catch (e: HttpException) {
            Log.e("PotholeUploadWorker", "HTTP ошибка", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e("PotholeUploadWorker", "Неизвестная ошибка", e)
            Result.failure()
        }
    }

    companion object {
        private const val BATCH_SIZE = 5000 // Уменьшил размер батча для надежности
    }
}