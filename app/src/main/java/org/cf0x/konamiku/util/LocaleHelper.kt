package org.cf0x.konamiku.util

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * 应用 App 语言设置。
 * - API 33+：直接使用 LocaleManager（不会触发 Activity 重建）
 * - 以下：回退到 AppCompatDelegate（可能重建 Activity）
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
