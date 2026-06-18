package org.cf0x.konamiku.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.cf0x.konamiku.BuildConfig
import org.cf0x.konamiku.data.Changelog
import org.cf0x.konamiku.data.UpdateInfo
import org.cf0x.konamiku.data.UpdateState
import org.cf0x.konamiku.data.updateJson
import java.net.HttpURLConnection
import java.net.URL

/**
 * Core update-check logic.
 *
 * Usage:
 *   val state = UpdateChecker.check(githubBase, mirrorBase)
 *
 * URL construction:
 *   rawUrl = "${githubBase}/raw/main/updates/update.json"
 *   if mirrorBase is set → "${mirrorBase.trimEnd('/')}/${rawUrl}"
 */
object UpdateChecker {

    private const val UPDATE_PATH = "/raw/main/updates/update.json"
    private const val TIMEOUT_MS = 5_000L

    /**
     * Run a full update check.
     * @param githubBase  GitHub repo URL, e.g. "https://github.com/C-F0x/KonamikU"
     * @param mirrorBase  Mirror prefix (optional), e.g. "https://ghproxy.com/" or ""
     * @return UpdateState
     */
    suspend fun check(githubBase: String, mirrorBase: String = ""): UpdateState =
        withContext(Dispatchers.IO) {
            try {
                // 1. Build full update.json URL
                val baseRaw = "${githubBase.trimEnd('/')}$UPDATE_PATH"
                val url = if (mirrorBase.isNotBlank()) {
                    "${mirrorBase.trimEnd('/')}/$baseRaw"
                } else baseRaw

                // 2. Fetch update.json
                val jsonText = fetchString(url) ?: return@withContext UpdateState(error = "Network request failed")

                val info = updateJson.decodeFromString<UpdateInfo>(jsonText)

                // 3. Compare version codes
                val currentCode = BuildConfig.VERSION_CODE.toLong()
                if (info.version_code <= currentCode) {
                    return@withContext UpdateState(hasUpdate = false)
                }

                // 4. Fetch changelog when update available
                val changelogUrl = if (mirrorBase.isNotBlank()) {
                    "${mirrorBase.trimEnd('/')}/${info.changelog_url}"
                } else info.changelog_url

                val changelog = fetchChangelog(changelogUrl)

                UpdateState(
                    hasUpdate    = true,
                    latestVersion = info.version_name,
                    latestCode   = info.version_code,
                    downloadUrl  = info.download_url,
                    changelogUrl = info.changelog_url,
                    changelog    = changelog
                )
            } catch (e: Exception) {
                UpdateState(error = e.message ?: "Unknown error")
            }
        }

    /**
     * Measure latency for a URL (HEAD request).
     * @return latency in ms, -1 on timeout
     */
    suspend fun measureLatency(url: String): Long = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TIMEOUT_MS) {
            val start = System.currentTimeMillis()
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = TIMEOUT_MS.toInt()
                conn.readTimeout = TIMEOUT_MS.toInt()
                conn.connect()
                conn.responseCode // any response means reachable
                conn.disconnect()
            } catch (_: Exception) {
                // Connection failed
            }
            System.currentTimeMillis() - start
        } ?: -1L
    }

    // ── Internal helpers ──

    private fun fetchString(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS.toInt()
        conn.readTimeout = TIMEOUT_MS.toInt()
        return try {
            conn.connect()
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchChangelog(url: String): Changelog? {
        return try {
            val text = fetchString(url) ?: return null
            updateJson.decodeFromString<Changelog>(text)
        } catch (_: Exception) {
            null
        }
    }
}
