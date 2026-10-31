package org.cf0x.konamiku.system

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.cf0x.konamiku.BuildConfig
import org.cf0x.konamiku.data.Changelog
import org.cf0x.konamiku.data.UpdateFailure
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
 *       "https://raw.githubusercontent.com/Ra1nify/KonamikU/main/updates/update.json"
 *   mirrorPrefix — prepended to githubUrl as proxy, e.g.
 *       "https://gh-proxy.com/" → final: "https://ghproxy.com/https://github.com/..."
 *   customUrl — fully independent URL, no mirror applied
 *
 * Callers decide whether a mirror applies at all (see
 * [UpdateManager.resolveEndpoints]); passing a blank [mirrorPrefix] means a
 * direct request.
 */
object UpdateChecker {

    private const val TAG = "KonamikU-Update"

    /**
     * 3s was too tight for gh-proxy round-trips on mainland networks, which
     * produced spurious "network failed" reports. 8s still fails fast enough
     * to keep the dialog responsive.
     */
    private const val TIMEOUT_MS = 8_000L

    /**
     * Error kinds surfaced to the UI. They are deliberately distinct so the
     * user can tell a network problem (retry) from a publishing problem
     * (never resolves on their side).
     */
    typealias Failure = UpdateFailure

    /**
     * Run an update check using the best available URL.
     * Priority: customUrl > mirrored githubUrl > direct githubUrl.
     */
    suspend fun check(
        githubUrl: String,
        mirrorPrefix: String = "",
        customUrl: String = ""
    ): UpdateState = withContext(Dispatchers.IO) {
        val url = resolveUrl(githubUrl, mirrorPrefix, customUrl)
            ?: return@withContext UpdateState(errorKind = Failure.NO_URL)

        val jsonText = when (val r = fetchString(url)) {
            is Fetch.Ok -> r.body
            is Fetch.Fail -> return@withContext UpdateState(errorKind = r.kind)
        }

        val info = runCatching { updateJson.decodeFromString<UpdateInfo>(jsonText) }
            .getOrElse { return@withContext UpdateState(errorKind = Failure.MALFORMED) }

        // A released build always has a positive version_code. Zero or negative
        // means the publishing pipeline produced a broken file — never report
        // that as "up to date", or the failure becomes invisible forever.
        if (info.version_code <= 0L) {
            Log.e(TAG, "update.json has invalid version_code=${info.version_code} (url=$url)")
            return@withContext UpdateState(errorKind = Failure.INVALID)
        }

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

    /** Result of a raw fetch, keeping the failure reason instead of collapsing to null. */
    private sealed class Fetch {
        data class Ok(val body: String) : Fetch()
        data class Fail(val kind: Failure) : Fetch()
    }

    private fun fetchString(url: String): Fetch {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS.toInt()
            conn.readTimeout = TIMEOUT_MS.toInt()
            conn.connect()
            val code = conn.responseCode
            when {
                code == 200 -> {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    Log.i(TAG, "fetchString OK [$url] -> ${text.take(500)}")
                    Fetch.Ok(text)
                }
                code == 404 || code == 410 -> {
                    Log.e(TAG, "fetchString HTTP $code [$url] — update manifest is missing")
                    Fetch.Fail(Failure.NOT_FOUND)
                }
                code == 401 || code == 403 || code == 429 -> {
                    Log.w(TAG, "fetchString HTTP $code [$url] — blocked or rate-limited")
                    Fetch.Fail(Failure.BLOCKED)
                }
                else -> {
                    Log.w(TAG, "fetchString HTTP $code [$url]")
                    Fetch.Fail(Failure.HTTP)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchString failed [$url]: ${e.javaClass.simpleName}: ${e.message}")
            Fetch.Fail(Failure.NETWORK)
        } finally {
            conn?.disconnect()
        }
    }

    /** Fetch and parse a changelog from a full URL (mirror applied externally). */
    suspend fun fetchChangelog(url: String): Changelog? = withContext(Dispatchers.IO) {
        val text = when (val r = fetchString(url)) {
            is Fetch.Ok -> r.body
            is Fetch.Fail -> return@withContext null
        }
        runCatching { updateJson.decodeFromString<Changelog>(text) }
            .onFailure { Log.w(TAG, "Changelog parse failed [$url]: ${it.message}") }
            .getOrNull()
    }
}
