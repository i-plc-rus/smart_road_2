package ru.iplc.smart_road.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
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
    private val api = ApiService.create() // см. ниже
    private val tokenManager = TokenManager(appContext)


    override suspend fun doWork(): Result {
        try {
            val userIdStr = tokenManager.getUserId()
            var userId = userIdStr?.toIntOrNull()

            if (userId == null) {
                Log.e("PotholeUploadWorker", "Не удалось получить user_id")
                userId = 0
            }

            while (true) {
                //val dataBatch = dao.getAll().take(1000)
                val dataBatch = dao.getUnsentBatch(5000)
                if (dataBatch.isEmpty()) {
                    Log.d("PotholeUploadWorker", "База пуста, работа завершена")
                    return Result.success()
                }

                Log.d("PotholeUploadWorker", "Отправка ${dataBatch.size} записей...")

                val request = PotholeDataRequest(user_id = userId, data = dataBatch)
                val response = api.uploadPotholeData(request)

                if (response.isSuccessful) {
                    //dao.deleteByIds(dataBatch.map { it.id })
                    dao.markAsSent(dataBatch.map { it.id })
                    Log.d("PotholeUploadWorker", "Удалено ${dataBatch.size} записей, продолжаем...")
                } else {
                    Log.w("PotholeUploadWorker", "Ошибка отправки: ${response.code()} ${response.message()}")
                    return Result.retry()
                }
            }
        } catch (e: IOException) {
            Log.e("PotholeUploadWorker", "Сетевая ошибка", e)
            return Result.retry()
        } catch (e: HttpException) {
            Log.e("PotholeUploadWorker", "HTTP ошибка", e)
            return Result.retry()
        }
    }



}