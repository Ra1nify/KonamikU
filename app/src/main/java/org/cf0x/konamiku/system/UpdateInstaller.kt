package org.cf0x.konamiku.system

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a downloaded APK to [PackageInstaller] for an in-app install.
 *
 * We use a [PackageInstaller.Session] (streaming write + commit) rather than
 * `ACTION_VIEW` on a file:// URI: the latter dumps the user into a browser or
 * a file manager and loses the changelog context entirely.
 *
 * The commit result is delivered to [UpdateInstallReceiver] through a
 * broadcast PendingIntent — a session commit outlives this process, so it
 * cannot be awaited inline.
 */
object UpdateInstaller {

    const val TAG = "KonamikU-Installer"

    /** Extra carrying the human-readable reason inside the result broadcast. */
    const val EXTRA_STATUS_MESSAGE = "status_message"

    /** Broadcast action that [UpdateInstallReceiver] listens for. */
    const val ACTION_INSTALL_RESULT = "org.cf0x.konamiku.ACTION_INSTALL_RESULT"

    /**
     * Whether the user has granted this app permission to install packages.
     * Always true below API 26, where the permission is not per-app.
     */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user can allow installs from this app. */
    fun requestInstallPermission(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Cannot open unknown-sources settings: ${it.message}") }
    }

    /**
     * Installs [apk] from absolute [path].
     *
     * @return true when the session was committed (the result arrives later),
     *         false when the session could not be created or written.
     */
    fun install(context: Context, path: String): Boolean {
        val apk = File(path)
        if (!apk.isFile || apk.length() <= 0L) {
            Log.e(TAG, "APK missing or empty: $path")
            return false
        }

        var session: PackageInstaller.Session? = null
        return try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                // PackageInstaller rejects sessions without a size hint on some
                // OEM builds; the APK length is the exact payload size.
                setAppPackageName(context.packageName)
            }

            val sessionId = installer.createSession(params)
            session = installer.openSession(sessionId)

            session.openWrite("base.apk", 0L, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out, DEFAULT_BUFFER_SIZE) }
                session.fsync(out)
            }

            val resultIntent = Intent(context, UpdateInstallReceiver::class.java).apply {
                action = ACTION_INSTALL_RESULT
            }
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            session.commit(pending.intentSender)
            Log.i(TAG, "Session $sessionId committed for $path")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Install failed: ${e.javaClass.simpleName}: ${e.message}")
            runCatching { session?.abandon() }
            false
        } finally {
            runCatching { session?.close() }
        }
    }

    /** Deletes a previously downloaded APK once it is no longer needed. */
    fun cleanup(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
            .onFailure { Log.w(TAG, "Cannot delete $path: ${it.message}") }
    }

    /**
     * Content URI for [path], exposed through the cache-path provider.
     * Kept for callers that want to hand the APK to an external installer.
     */
    fun contentUriFor(context: Context, path: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.updates", File(path))
}
