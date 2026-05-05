package org.cf0x.konamiku.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object VibratorUtil {

    fun vibrate(context: Context, duration: Long = 50, amplitude: Int = 180) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) return

        val effect = VibrationEffect.createOneShot(duration, amplitude)
        vibrator.vibrate(effect)
    }

    fun successTick(context: Context) {
        vibrate(context, 30, 150)
    }

    fun doubleTick(context: Context) {
        val timings = longArrayOf(0, 30, 50, 30)
        val amplitudes = intArrayOf(0, 120, 0, 120)

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }
}