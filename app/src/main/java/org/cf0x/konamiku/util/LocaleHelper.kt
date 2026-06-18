package org.cf0x.konamiku.util

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Apply App locale settings.
 * - API 33+: Use LocaleManager (no Activity reconstruction)
 * - Below: Fallback to AppCompatDelegate (may reconstruct Activity)
 */
fun Context.applyLocale(tag: String) {
    if (tag.isBlank()) return
    if (Build.VERSION.SDK_INT >= 33) {
        runCatching {
            val lm = getSystemService(android.app.LocaleManager::class.java) ?: return@runCatching
            lm.applicationLocales = android.os.LocaleList.forLanguageTags(tag)
        }
    } else {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(tag)
        )
    }
}
