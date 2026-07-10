package org.cf0x.konamiku.system

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Cross-process ContentProvider that exposes the PMm Tool enabled state.
 *
 * The Xposed module (running in `com.android.nfc` process) queries this
 * provider to decide whether to inject [libpmm.so] into the NFC process.
 *
 * URI: `content://org.cf0x.konamiku.pmmconfig/pmm_enabled`
 * Returns a single row with column "enabled" (int: 1 or 0).
 */
class PmmConfigProvider : ContentProvider() {

    companion object {
        private const val AUTHORITY = "org.cf0x.konamiku.pmmconfig"
        private const val PATH_PMM_ENABLED = "pmm_enabled"
        private const val CODE_PMM_ENABLED = 1

        private val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_PMM_ENABLED, CODE_PMM_ENABLED)
        }

        /** Current PMm enabled state, updated by the main app. */
        @Volatile
        var pmmEnabled: Boolean = true

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_PMM_ENABLED")
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (URI_MATCHER.match(uri)) {
            CODE_PMM_ENABLED -> {
                val cursor = MatrixCursor(arrayOf("enabled"))
                cursor.addRow(arrayOf(if (pmmEnabled) 1 else 0))
                cursor
            }
            else -> null
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return when (URI_MATCHER.match(uri)) {
            CODE_PMM_ENABLED -> {
                values?.getAsInteger("enabled")?.let {
                    pmmEnabled = it == 1
                }
                1 // number of rows affected
            }
            else -> 0
        }
    }

    override fun getType(uri: Uri): String? =
        if (URI_MATCHER.match(uri) == CODE_PMM_ENABLED)
            "vnd.android.cursor.item/vnd.konamiku.pmm_enabled"
        else null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
