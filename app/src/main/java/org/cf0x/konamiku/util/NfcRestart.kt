package org.cf0x.konamiku.util

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object NfcRestart {

    suspend fun restartNfcService(context: Context, status: org.cf0x.konamiku.system.StatusDetector.AllStatus): Boolean = withContext(Dispatchers.IO) {
        val oldPid = getNfcPid()

        val executed = when {
            status.root.available -> executeRootKill()
            else -> false
        }

        if (!executed) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No privileged access available", Toast.LENGTH_SHORT).show()
            }
            return@withContext false
        }

        delay(800)

        val newPid = getNfcPid()
        val success = newPid != -1 && newPid != oldPid

        withContext(Dispatchers.Main) {
            if (success) {
                VibratorUtil.successTick(context)
                Toast.makeText(context, "NFC Service Restarted", Toast.LENGTH_SHORT).show()
            } else {
                VibratorUtil.doubleTick(context)
                Toast.makeText(context, "Restart failed or timeout", Toast.LENGTH_SHORT).show()
            }
        }

        return@withContext success
    }

    private fun getNfcPid(): Int {
        return try {
            val process = Runtime.getRuntime().exec("pidof com.android.nfc")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val pidStr = reader.readLine()
            process.waitFor()
            pidStr?.trim()?.toIntOrNull() ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    private fun executeRootKill(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -f com.android.nfc"))
            process.waitFor()
            true
        } catch (e: Exception) {
            false
        }
    }
}