package org.cf0x.konamiku.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import org.cf0x.konamiku.MainActivity
import org.cf0x.konamiku.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the update APK and hands it to [UpdateInstaller].
 *
 * It runs as a foreground service because the download can take minutes on a
 * cold mirror and the app may be backgrounded meanwhile. Progress lives in the
 * notification rather than a dialog for the same reason — the Activity can be
 * destroyed while the transfer continues.
 */
class UpdateDownloadService : Service() {

    /** Why a transfer stopped, so the UI can phrase the recovery correctly. */
    enum class FailureKind { TIMEOUT, NOT_FOUND, NETWORK, HTTP, PERMISSION, INSTALL }

    enum class Phase { IDLE, DOWNLOADING, INSTALLING, FAILED }

    /**
     * Observable download state.
     *
     * A notification alone cannot offer "retry / dismiss / open in browser",
     * so the service publishes outcomes here and the Activity renders a dialog.
     */
    data class State(
        val phase: Phase = Phase.IDLE,
        val percent: Int = 0,
        val kind: FailureKind? = null,
        val message: String? = null,
        /** The URL that was attempted last — used by "open in browser". */
        val sourceUrl: String? = null
    )

    companion object {
        private const val TAG = "KonamikU-Download"

        private const val CHANNEL_ID = "konamiku_update_download"
        private const val NOTIF_ID = 2002
        private const val NOTIF_ID_FAILED = 2003

        private const val ACTION_START = "org.cf0x.konamiku.action.START_DOWNLOAD"
        private const val ACTION_CANCEL = "org.cf0x.konamiku.action.CANCEL_DOWNLOAD"

        const val EXTRA_URL = "url"
        const val EXTRA_FALLBACK_URL = "fallback_url"
        const val EXTRA_VERSION = "version"

        private const val CONNECT_TIMEOUT_MS = 15_000
        /** Abort when the progress bar has not advanced for this long. */
        private const val STALL_TIMEOUT_MS = 30_000L
        /** Hard ceiling for a single transfer attempt. */
        private const val TOTAL_TIMEOUT_MS = 10 * 60_000L
        private const val BUFFER_SIZE = 64 * 1024

        /** Read by the settings screen to drive the progress and failure dialogs. */
        val state = MutableStateFlow(State())

        /** Clears a terminal failure so the dialog does not reappear on recompose. */
        fun consumeFailure() {
            state.update { if (it.phase == Phase.FAILED) it.copy(phase = Phase.IDLE) else it }
        }

        /** Started by the UI with the resolved (possibly mirrored) URL. */
        fun start(context: Context, url: String, fallbackUrl: String, version: String) {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FALLBACK_URL, fallbackUrl)
                putExtra(EXTRA_VERSION, version)
            }
            context.startForegroundService(intent)
        }

        fun cancel(context: Context) {
            context.stopService(Intent(context, UpdateDownloadService::class.java))
        }

        /** Posts a failure notification that survives the service being stopped. */
        fun notifyInstallFailure(context: Context, message: String?) {
            ensureChannel(context)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(context.getString(R.string.update_install_failed))
                .setContentText(message ?: context.getString(R.string.update_install_failed_desc))
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        message ?: context.getString(R.string.update_install_failed_desc)
                    )
                )
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            runCatching {
                NotificationManagerCompat.from(context).notify(NOTIF_ID_FAILED, builder.build())
            }
        }

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notif_channel_update),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { setShowBadge(false) }
                )
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Timestamp of the last successfully read chunk, for the stall watchdog. */
    @Volatile
    private var lastDataAt: Long = 0L

    /** Why the last attempt failed; read by [fail] to pick a message. */
    @Volatile
    private var lastFailure: FailureKind? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                Log.i(TAG, "Cancelled by user")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val fallback = intent.getStringExtra(EXTRA_FALLBACK_URL).orEmpty()
                val version = intent.getStringExtra(EXTRA_VERSION).orEmpty()
                if (url.isNullOrBlank()) {
                    Log.e(TAG, "No URL supplied")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundCompat(getString(R.string.update_downloading, 0))
                job?.cancel()
                job = scope.launch { run(url, fallback, version) }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun run(url: String, fallbackUrl: String, version: String) {
        val dest = File(cacheDir, "update-${version.ifBlank { "latest" }}.apk")
        runCatching { if (dest.exists()) dest.delete() }
        lastDataAt = System.currentTimeMillis()
        lastFailure = null

        val ok = download(url, dest)
            || (fallbackUrl.isNotBlank() && fallbackUrl != url && run {
                // Mirror unreachable (or truncated): retry once against the
                // direct GitHub link before surfacing the failure.
                Log.w(TAG, "Primary URL failed, retrying fallback: $fallbackUrl")
                updateProgress(getString(R.string.update_downloading_retry, 0), 0, true)
                download(fallbackUrl, dest)
            })

        if (!ok) {
            Log.e(TAG, "Download failed: $lastFailure")
            // Publish a failure the UI can act on (retry / dismiss / browser).
            fail(lastFailure ?: FailureKind.NETWORK, url, dest)
            return
        }

        if (!UpdateInstaller.canInstall(this)) {
            Log.w(TAG, "Install permission missing")
            fail(FailureKind.PERMISSION, url, dest)
            UpdateInstaller.requestInstallPermission(this)
            return
        }

        updateProgress(getString(R.string.update_installing), 0, true)
        val committed = UpdateInstaller.install(this, dest.absolutePath)
        if (!committed) {
            UpdateInstaller.cleanup(this, dest.absolutePath)
            fail(FailureKind.INSTALL, url, dest)
            return
        }
        // The process is normally replaced on success; stop regardless.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    /** Publishes a terminal failure and drops the foreground notification. */
    private fun fail(kind: FailureKind, url: String, dest: File) {
        runCatching { dest.delete() }
        state.update {
            it.copy(
                phase = Phase.FAILED,
                kind = kind,
                sourceUrl = url,
                message = messageFor(kind)
            )
        }
        NotificationManagerCompat.from(this).cancel(NOTIF_ID)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun messageFor(kind: FailureKind): String = when (kind) {
        FailureKind.TIMEOUT   -> getString(R.string.update_err_timeout)
        FailureKind.NOT_FOUND -> getString(R.string.setting_update_err_not_found)
        FailureKind.PERMISSION -> getString(R.string.update_err_permission)
        FailureKind.INSTALL   -> getString(R.string.update_err_install)
        FailureKind.HTTP      -> getString(R.string.update_err_download)
        FailureKind.NETWORK   -> getString(R.string.update_err_download)
    }

    /** @return true when the whole file arrived and the length matches. */
    private fun download(url: String, dest: File): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = CONNECT_TIMEOUT_MS
                // Per-read timeout: only catches a fully stalled socket.
                readTimeout = CONNECT_TIMEOUT_MS
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = conn.responseCode
            if (code != 200) {
                Log.w(TAG, "HTTP $code for $url")
                lastFailure = if (code == 404 || code == 410) FailureKind.NOT_FOUND
                               else FailureKind.HTTP
                return false
            }

            val total = conn.contentLengthLong.takeIf { it > 0L }
            var written = 0L
            var lastPercent = -1
            val startedAt = System.currentTimeMillis()
            // Watch the *displayed* progress, not raw byte arrival: a mirror
            // that trickles a few bytes at a time keeps the socket alive but
            // leaves the bar frozen, which is exactly what the user sees.
            var lastProgressAt = startedAt

            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val now = System.currentTimeMillis()
                        if (now - lastProgressAt > STALL_TIMEOUT_MS) {
                            Log.e(
                                TAG,
                                "Progress stalled: ${now - lastProgressAt}ms since ${lastPercent}% " +
                                    "($written bytes)"
                            )
                            lastFailure = FailureKind.TIMEOUT
                            runCatching { dest.delete() }
                            return false
                        }
                        if (now - startedAt > TOTAL_TIMEOUT_MS) {
                            Log.e(TAG, "Overall timeout after ${TOTAL_TIMEOUT_MS}ms")
                            lastFailure = FailureKind.TIMEOUT
                            runCatching { dest.delete() }
                            return false
                        }

                        val n = input.read(buffer)
                        if (n <= 0) break
                        lastDataAt = System.currentTimeMillis()
                        out.write(buffer, 0, n)
                        written += n

                        // Percentage drives both the notification and the stall
                        // watchdog. Without a Content-Length there is no
                        // percentage to watch, so byte arrival is the fallback.
                        val percent = total?.let { ((written * 100) / it).toInt() } ?: -1
                        if (total == null) {
                            lastProgressAt = lastDataAt
                        } else if (percent != lastPercent) {
                            lastPercent = percent
                            lastProgressAt = lastDataAt
                            updateProgress(
                                getString(R.string.update_downloading, percent),
                                percent,
                                false
                            )
                            state.update { it.copy(phase = Phase.DOWNLOADING, percent = percent) }
                        }
                    }
                }
            }

            if (total != null && written != total) {
                Log.e(TAG, "Truncated: $written of $total bytes")
                lastFailure = FailureKind.TIMEOUT
                runCatching { dest.delete() }
                return false
            }
            Log.i(TAG, "Downloaded $written bytes to ${dest.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.javaClass.simpleName}: ${e.message}")
            runCatching { dest.delete() }
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun startForegroundCompat(text: String) {
        val notification = buildNotification(text, 0, true)
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun updateProgress(text: String, percent: Int, indeterminate: Boolean) {
        val nm = NotificationManagerCompat.from(this)
        runCatching { nm.notify(NOTIF_ID, buildNotification(text, percent, indeterminate)) }
    }

    private fun buildNotification(text: String, percent: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(getString(R.string.update_title))
            .setContentText(text)
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                0,
                getString(R.string.update_cancel),
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, UpdateDownloadService::class.java).setAction(ACTION_CANCEL),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
