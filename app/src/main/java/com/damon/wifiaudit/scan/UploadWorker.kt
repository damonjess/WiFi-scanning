package com.damon.wifiaudit.scan

import android.content.Context
import androidx.work.*
import com.damon.wifiaudit.data.AppDatabase
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val queue = db.apiQueueDao()
        val items = queue.getPendingItems()

        if (items.isEmpty()) return Result.success()

        android.util.Log.i("UploadWorker", "Processing ${items.size} queued items")

        for (item in items) {
            try {
                // Simulate network upload
                val success = simulateUpload(item.payload)
                if (success) {
                    queue.delete(item.id)
                } else {
                    handleFailure(queue, item)
                }
            } catch (e: Exception) {
                handleFailure(queue, item)
            }
        }

        return if (queue.getPendingItems().isNotEmpty()) Result.retry() else Result.success()
    }

    private suspend fun simulateUpload(payload: String): Boolean {
        // Mocking a successful upload
        delay(500)
        android.util.Log.d("UploadWorker", "Successfully uploaded: ${payload.take(50)}...")
        return true
    }

    private suspend fun handleFailure(queue: com.damon.wifiaudit.data.ApiQueueDao, item: com.damon.wifiaudit.data.ApiQueueItem) {
        if (item.retryCount < 3) {
            queue.update(item.copy(retryCount = item.retryCount + 1))
        } else {
            queue.markProcessed(item.id)
            android.util.Log.e("UploadWorker", "Giving up on item ${item.id} after 3 retries")
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "api_upload",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
