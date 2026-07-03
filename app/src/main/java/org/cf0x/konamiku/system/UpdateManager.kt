package org.cf0x.konamiku.system

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import org.cf0x.konamiku.MainActivity
import org.cf0x.konamiku.R
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.UpdateInterval
import org.cf0x.konamiku.data.UpdateState
import java.util.concurrent.TimeUnit

/**
 * Unified entry point for update checks.
 *
 * Both manual ("Check Now") and automatic (WorkManager) checks go through
 * [performCheck], which reads the stored URL configuration and interval from
 * DataStore, resolves the effective URL, executes the HTTP check, persists
 * the last-check timestamp, and optionally posts a notification.
 *
 * Scheduling is handled by [schedule] / [cancel], which manage a WorkManager
 * periodic job that triggers [performCheck] at the configured interval.
 */
object UpdateManager {

    private const val TAG = "KonamikU-UpdateMgr"
    private const val UNIQUE_WORK_NAME = "konamiku_update_check"
    const val NOTIF_ID_UPDATE = 2001

    // ── Scheduling ────────────────────────────────────────────────────────

    /**
     * Schedule periodic auto-update checks via WorkManager.
     * Pass [UpdateInterval.OFF] to cancel any existing schedule.
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

    /** Cancel any scheduled periodic update check. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    // ── Unified check ------------------------------------------------------

    /**
     * Run a full update check using the configuration stored in [dataStore].
     *
     * @param showNotification  when true (auto mode), a system notification is
     *                          posted if an update is found. The caller
     *                          (e.g. SettingScreen) should pass `false` and
     *                          handle the returned [UpdateState] itself.
     * @return [UpdateState] describing the result (callers inspect `.hasUpdate`,
     *         `.error`, `.changelog`, etc.)
     */
    suspend fun performCheck(
        context: Context,
        dataStore: AppDataStore,
        showNotification: Boolean = true,
        /**
         * When true, the interval-is-OFF guard and the 80%% elapsed guard
         * are bypassed — used for manual "Check Now" so the user can always
         * trigger a check regardless of the interval setting.
         */
        force: Boolean = false
    ): UpdateState {
        // 1. Read configuration
        val interval = runCatching { dataStore.updateInterval.first() }
            .getOrDefault(UpdateInterval.OFF)

        if (!force && interval == UpdateInterval.OFF) {
            Log.d(TAG, "Interval is OFF, skipping check")
            return UpdateState()
        }

        if (!force) {
            val lastCheck = runCatching { dataStore.updateLastCheck.first() }.getOrDefault(0L)
            val elapsed = System.currentTimeMillis() - lastCheck
            if (elapsed < interval.millis * 0.8) {
                Log.d(TAG, "Skipping — only ${elapsed}ms since last check (interval=${interval.millis}ms)")
                return UpdateState()
            }
        }

        // 2. Resolve URLs (same logic as SettingScreen / legacy callers)
        val githubUrl = runCatching { dataStore.updateGithubBase.first() }.getOrDefault("")
            .ifBlank { "https://raw.githubusercontent.com/C-F0x/KonamikU/info/update.json" }
        val mirrorUrl = runCatching { dataStore.updateMirrorBase.first() }.getOrDefault("")
            .ifBlank { "https://gh-proxy.com/" }
        val customUrl = runCatching { dataStore.updateCustomBase.first() }.getOrDefault("")

        // 3. Execute HTTP check
        Log.i(TAG, "Running update check…")
        val state = UpdateChecker.check(
            githubUrl = githubUrl,
            mirrorPrefix = mirrorUrl,
            customUrl = customUrl
        )

        // 4. Persist timestamp & notify
        if (state.hasUpdate) {
            Log.i(TAG, "Update found: ${state.latestVersion}")
            if (showNotification) {
                showUpdateNotification(context, state.latestVersion ?: "")
            }
            dataStore.saveUpdateLastCheck(System.currentTimeMillis())
        } else if (state.error != null) {
            Log.w(TAG, "Update check error: ${state.error}")
        } else {
            Log.i(TAG, "No update available")
            dataStore.saveUpdateLastCheck(System.currentTimeMillis())
        }

        return state
    }

    // ── Notification helper (used by both auto and test flows) ────────────

    fun showUpdateNotification(context: Context, version: String) {
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
