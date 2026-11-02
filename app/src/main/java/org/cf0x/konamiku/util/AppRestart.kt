package org.cf0x.konamiku.util

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log

private const val TAG = "KonamikU-AppRestart"

/**
 * Restarts the whole process: launch the launcher activity with a fresh task
 * stack, then exit the current process.
 *
 * `finishAffinity()` + `exit(0)` alone does not reliably drop state held by
 * static/companion objects; relaunching with CLEAR_TASK gives the new instance
 * a clean component tree before the old process goes away.
 *
 * Exit is called even when the relaunch intent could not be resolved, because
 * the callers reach this point only after the NFC stack has been torn down and
 * a half-dead process is worse than a restart.
 */
fun Context.restartApp() {
    runCatching {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalStateException("no launcher intent for $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }.onFailure {
        Log.e(TAG, "Could not relaunch before exiting: ${it.message}")
    }

    Process.killProcess(Process.myPid())
    Runtime.getRuntime().exit(0)
}
