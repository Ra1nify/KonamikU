package org.cf0x.konamiku.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
 * URL rules:
 *   githubUrl — full URL to update.json on github, e.g.
 *       "https://github.com/C-F0x/KonamikU/blob/main/updates/update.json"
 *   mirrorPrefix — prepended to githubUrl as proxy, e.g.
 *       "https://ghproxy.com/" → final: "https://ghproxy.com/https://github.com/..."
 *   customUrl — fully independent URL, no mirror applied
 */
object UpdateChecker {

    private const val TIMEOUT_MS = 3_000L

    /**
     * Run an update check using the best available URL.
     * Priority: customUrl > mirrored githubUrl > direct githubUrl.
     */
    suspend fun check(
        githubUrl: String,
        mirrorPrefix: String = "",
        customUrl: String = ""
    ): UpdateState = withContext(Dispatchers.IO) {
        try {
            val url = resolveUrl(githubUrl, mirrorPrefix, customUrl)
                ?: return@withContext UpdateState(error = "No URL configured")
            val jsonText = fetchString(url) ?: return@withContext UpdateState(error = "Network request failed")
            val info = updateJson.decodeFromString<UpdateInfo>(jsonText)

            val currentCode = BuildConfig.VERSION_CODE.toLong()
            if (info.version_code <= currentCode) {
                return@withContext UpdateState(hasUpdate = false)
            }

            // Fetch changelog — use latest_changelog if present, else changelog_url
            val clUrl = if (info.latest_changelog.isNotBlank()) info.latest_changelog
                       else info.changelog_url
            val changelogUrl = if (mirrorPrefix.isNotBlank() && customUrl.isBlank()) {
                "${mirrorPrefix.trimEnd('/')}/${clUrl.trimStart('/')}"
            } else clUrl
            val changelog = fetchChangelog(changelogUrl)

            UpdateState(
                hasUpdate     = true,
                latestVersion = info.version_name,
                latestCode    = info.version_code,
                downloadUrl   = info.download_url,
                changelogUrl  = clUrl,
                changelog     = changelog
            )
        } catch (e: Exception) {
            UpdateState(error = e.message ?: "Unknown error")
        }
    }

    /**
     * Resolve which URL to use.
     *   customUrl ≠ ""  → use it directly
     *   mirrorPrefix ≠ "" → prepend to githubUrl
     *   otherwise → githubUrl as-is
     */
    private fun resolveUrl(githubUrl: String, mirrorPrefix: String, customUrl: String): String? {
        val base = githubUrl.trim()
        val mirror = mirrorPrefix.trim()
        val custom = customUrl.trim()
        return when {
            custom.isNotBlank() -> custom
            mirror.isNotBlank() && base.isNotBlank() -> "${mirror.trimEnd('/')}/${base.trimStart('/')}"
            base.isNotBlank() -> base
            else -> null
        }
    }

    /** Measure real download latency (GET + read body). Returns ms, -1 on timeout/failure. */
    suspend fun measureLatency(url: String): Long = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TIMEOUT_MS) {
            val start = System.currentTimeMillis()
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = TIMEOUT_MS.toInt()
                conn.readTimeout = TIMEOUT_MS.toInt()
                conn.connect()
                // Read the full response body to measure real download speed
                conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
            } catch (_: Exception) { }
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
        } catch (_: Exception) { null }
    }
}
