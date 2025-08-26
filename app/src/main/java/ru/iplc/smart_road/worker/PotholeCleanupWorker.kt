package ru.iplc.smart_road.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ExistingWorkPolicy
import ru.iplc.smart_road.db.AppDatabase

class PotholeCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val dao = AppDatabase.getInstance(appContext).potholeDao()

    override suspend fun doWork(): Result {
        return try {
            val deleted = dao.deleteSent()
            Log.d(TAG, "🧹 Удалено $deleted записей (status = sent)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при очистке", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "PotholeCleanupWorker"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PotholeCleanupWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "pothole_cleanup_work",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }
}
