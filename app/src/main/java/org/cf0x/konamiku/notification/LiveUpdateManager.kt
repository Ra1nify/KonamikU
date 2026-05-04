package org.cf0x.konamiku.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import kotlinx.coroutines.*
import org.cf0x.konamiku.MainActivity
import org.cf0x.konamiku.R

object LiveUpdateManager {

    const val CHANNEL_ID             = "konamiku_live"
    const val NOTIF_ID               = 1001
    const val ACTION_TOGGLE_ACTIVATE = "org.cf0x.konamiku.ACTION_TOGGLE_ACTIVATE"
    const val ACTION_TOGGLE_MODE     = "org.cf0x.konamiku.ACTION_TOGGLE_MODE"
    const val ACTION_DISMISSED       = "org.cf0x.konamiku.ACTION_NOTIF_DISMISSED"

    private var pulseJob: Job? = null

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun postActive(context: Context, cardName: String, compatMode: Boolean) {
        val progress = if (compatMode) 30 else 20
        val modeText = context.getString(
            if (compatMode) R.string.notif_progress_compat
            else            R.string.notif_progress_normal
        )
        notify(context, cardName, modeText, progress)
    }

    fun pulse(context: Context, cardName: String, compatMode: Boolean, scope: CoroutineScope) {
        notify(context, cardName, context.getString(R.string.notif_progress_simulated), 95)
        pulseJob?.cancel()
        pulseJob = scope.launch {
            delay(3000)
            postActive(context, cardName, compatMode)
        }
    }

    fun cancel(context: Context) {
        pulseJob?.cancel()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }

    private fun notify(context: Context, title: String, contentText: String, progress: Int) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggleActivateIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(ACTION_TOGGLE_ACTIVATE).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggleModeIntent = PendingIntent.getBroadcast(
            context, 2,
            Intent(ACTION_TOGGLE_MODE).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val deleteIntent = PendingIntent.getBroadcast(
            context, 3,
            Intent(ACTION_DISMISSED).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_nfc))
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(tapIntent)
            .setDeleteIntent(deleteIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setProgress(100, progress, false)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_media_pause),
                    context.getString(R.string.notif_action_stop),
                    toggleActivateIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_manage),
                    context.getString(R.string.notif_action_switch_mode),
                    toggleModeIntent
                ).build()
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        nm.notify(NOTIF_ID, builder.build())
    }
}