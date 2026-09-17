package com.damon.wifiaudit.scan

import android.content.Context
import android.util.Log
import androidx.work.*
import com.damon.wifiaudit.data.ApiQueueDao
import com.damon.wifiaudit.data.ApiQueueItem
import com.damon.wifiaudit.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val queue = db.apiQueueDao()
        val items = queue.getPendingItems()

        if (items.isEmpty()) return Result.success()

        Log.i("UploadWorker", "Processing ${items.size} queued items")

        for (item in items) {
            try {
                val success = uploadPayload(applicationContext, item.payload)
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

    private suspend fun uploadPayload(context: Context, payload: String): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("wifi_audit_settings", Context.MODE_PRIVATE)
        val serverUrlStr = prefs.getString("upload_server_url", "https://httpbin.org/post") ?: "https://httpbin.org/post"

        try {
            val url = URL(serverUrlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("User-Agent", "WiFiAudit-Android/1.0")

            connection.outputStream.use { os ->
                val input = payload.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            Log.d("UploadWorker", "Uploaded payload (${payload.length} bytes) to $serverUrlStr -> Response $responseCode")
            connection.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e("UploadWorker", "Upload failed to $serverUrlStr: ${e.message}")
            false
        }
    }

    private suspend fun handleFailure(queue: ApiQueueDao, item: ApiQueueItem) {
        if (item.retryCount < 3) {
            queue.update(item.copy(retryCount = item.retryCount + 1))
        } else {
            queue.markProcessed(item.id)
            Log.e("UploadWorker", "Giving up on item ${item.id} after 3 retries")
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
