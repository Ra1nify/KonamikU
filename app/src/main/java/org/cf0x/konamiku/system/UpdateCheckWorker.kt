package org.cf0x.konamiku.system

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.cf0x.konamiku.MainActivity
import org.cf0x.konamiku.R
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.UpdateInterval
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val UNIQUE_WORK_NAME = "konamiku_update_check"
        const val NOTIF_ID_UPDATE = 2001

        /**
         * Schedule periodic update checks at the given interval.
         * If [interval] is [UpdateInterval.OFF], cancels any existing schedule.
         */
        fun schedule(context: Context, interval: UpdateInterval) {
            val wm = WorkManager.getInstance(context)
            if (interval == UpdateInterval.OFF) {
                Log.i(TAG, "Update check disabled — cancelling periodic work")
                wm.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                interval.millis, TimeUnit.MILLISECONDS
            )
                .setConstraints(constraints)
                .build()

            wm.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.i(TAG, "Scheduled update check every ${interval.millis}ms (${interval.name})")
        }

        /** Cancel any scheduled update checks. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val dataStore = AppDataStore(applicationContext)
        val interval = runCatching { dataStore.updateInterval.first() }.getOrDefault(UpdateInterval.OFF)

        if (interval == UpdateInterval.OFF) {
            Log.d(TAG, "Interval is OFF, skipping check")
            return Result.success()
        }

        // Check if enough time has elapsed since last manual check
        val lastCheck = runCatching { dataStore.updateLastCheck.first() }.getOrDefault(0L)
        val elapsed = System.currentTimeMillis() - lastCheck
        if (elapsed < interval.millis * 0.8) {
            Log.d(TAG, "Skipping — only ${elapsed}ms since last check (interval=${interval.millis}ms)")
            return Result.success()
        }

        Log.i(TAG, "Running update check…")
        val githubUrl = runCatching { dataStore.updateGithubBase.first() }.getOrDefault("")
        val mirrorUrl = runCatching { dataStore.updateMirrorBase.first() }.getOrDefault("")
        val customUrl = runCatching { dataStore.updateCustomBase.first() }.getOrDefault("")

        val state = UpdateChecker.check(
            githubUrl = githubUrl,
            mirrorPrefix = mirrorUrl,
            customUrl = customUrl
        )

        if (state.hasUpdate) {
            Log.i(TAG, "Update found: ${state.latestVersion}")
            showUpdateNotification(state.latestVersion ?: "")
            dataStore.saveUpdateLastCheck(System.currentTimeMillis())
        } else if (state.error != null) {
            Log.w(TAG, "Update check error: ${state.error}")
        } else {
            Log.i(TAG, "No update available")
            dataStore.saveUpdateLastCheck(System.currentTimeMillis())
        }

        return Result.success()
    }

    private fun showUpdateNotification(version: String) {
        val context = applicationContext
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "konamiku_live_important")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(context.getString(R.string.setting_update_new_title))
            .setContentText(context.getString(R.string.setting_update_new_msg, version))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_UPDATE, notification)
    }
}
