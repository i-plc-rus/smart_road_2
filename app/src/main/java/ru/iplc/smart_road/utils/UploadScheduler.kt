package ru.iplc.smart_road.utils

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.work.*
import ru.iplc.smart_road.worker.PotholeUploadWorker
import java.util.concurrent.TimeUnit

fun schedulePotholeUpload(context: Context, owner: LifecycleOwner) {
    val workRequest = PeriodicWorkRequestBuilder<PotholeUploadWorker>(
        15, TimeUnit.MINUTES
    )
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            30, TimeUnit.SECONDS
        )
        .build()

    WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData("PotholeUploadWork")
        .observe(owner) { works ->
            works.forEach {
                Log.d("WorkCheck", "Work ${it.id} status: ${it.state}")
            }
        }

    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(
            "PotholeUploadWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
}