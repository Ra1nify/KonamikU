package org.cf0x.konamiku.system

import android.util.Log
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
 *       "https://raw.githubusercontent.com/C-F0x/KonamikU/main/updates/update.json"
 *   mirrorPrefix — prepended to githubUrl as proxy, e.g.
 *       "https://gh-proxy.com/" → final: "https://ghproxy.com/https://github.com/..."
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

            // Always fetch changelog (for display even when up-to-date)
            val clUrl = if (info.latest_changelog.isNotBlank()) info.latest_changelog
                       else info.changelog_url
            val changelogUrl = if (mirrorPrefix.isNotBlank() && customUrl.isBlank()) {
                "${mirrorPrefix.trimEnd('/')}/${clUrl.trimStart('/')}"
            } else clUrl
            val changelog = fetchChangelog(changelogUrl)

            if (info.version_code <= currentCode) {
                return@withContext UpdateState(
                    hasUpdate = false,
                    changelog = changelog
                )
            }

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

    /** Download + measure latency + validate JSON structure. Returns Pair(ms, isValid) or null on timeout. */
    suspend fun testAndValidate(url: String): Pair<Long, Boolean>? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TIMEOUT_MS) {
            val start = System.currentTimeMillis()
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = TIMEOUT_MS.toInt()
                conn.readTimeout = TIMEOUT_MS.toInt()
                conn.connect()
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                Log.i("KonamikU", "testAndValidate OK [$url] -> ${text.take(500)}")
                val ms = System.currentTimeMillis() - start
                // Validate JSON by attempting to parse as UpdateInfo
                val valid = runCatching {
                    updateJson.decodeFromString<UpdateInfo>(text)
                    true
                }.getOrDefault(false)
                if (!valid) Log.w("KonamikU", "testAndValidate invalid JSON [$url]")
                Pair(ms, valid)
            } catch (e: Exception) {
                Log.w("KonamikU", "testAndValidate failed [$url]: ${e.message}")
                Pair(System.currentTimeMillis() - start, false)
            }
        }
    }

    // ── Internal helpers ──

    private fun fetchString(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS.toInt()
        conn.readTimeout = TIMEOUT_MS.toInt()
        return try {
            conn.connect()
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                Log.i("KonamikU", "fetchString OK [$url] -> ${text.take(500)}")
                text
            } else {
                Log.w("KonamikU", "fetchString HTTP ${conn.responseCode} [$url]")
                null
            }
        } catch (e: Exception) {
            Log.e("KonamikU", "fetchString failed [$url]: ${e.message}")
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Fetch and parse a changelog from a full URL (mirror applied externally). */
    fun fetchChangelog(url: String): Changelog? {
        return try {
            val text = fetchString(url) ?: return null
            updateJson.decodeFromString<Changelog>(text)
        } catch (_: Exception) { null }
    }
}
