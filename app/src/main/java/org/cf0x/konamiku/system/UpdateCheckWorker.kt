package org.cf0x.konamiku.system

import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.cf0x.konamiku.data.AppDataStore

/**
 * WorkManager worker that delegates to [UpdateManager.performCheck].
 *
 * Scheduling is managed by [UpdateManager.schedule] / [UpdateManager.cancel];
 * this class exists only because WorkManager requires a concrete Worker class.
 */
class UpdateCheckWorker(
    appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "UpdateCheckWorker"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Worker triggered — delegating to UpdateManager")
        val dataStore = AppDataStore(applicationContext)
        return try {
            UpdateManager.performCheck(
                context = applicationContext,
                dataStore = dataStore,
                showNotification = true
            )
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Check failed: ${e.message}")
            Result.retry()
        }
    }
}
