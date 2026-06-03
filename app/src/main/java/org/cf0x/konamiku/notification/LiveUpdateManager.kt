package org.cf0x.konamiku.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.cf0x.konamiku.MainActivity
import org.cf0x.konamiku.R
import org.cf0x.konamiku.data.EmuMode
import java.lang.reflect.Method

object LiveUpdateManager {

    private const val CHANNEL_ID_LIVE       = "konamiku_live"
    private const val CHANNEL_ID_IMPORTANT  = "konamiku_live_important"
    const val NOTIF_ID               = 1001
    const val ACTION_TOGGLE_ACTIVATE = "org.cf0x.konamiku.ACTION_TOGGLE_ACTIVATE"
    const val ACTION_TOGGLE_MODE     = "org.cf0x.konamiku.ACTION_TOGGLE_MODE"
    const val ACTION_NEXT_CARD       = "org.cf0x.konamiku.ACTION_NEXT_CARD"
    const val ACTION_DISMISSED       = "org.cf0x.konamiku.ACTION_NOTIF_DISMISSED"

    private var pulseJob: Job? = null
    @Volatile
    private var promotedOngoingMethod: Method? = null
    @Volatile
    private var lastNotifyKey: String? = null

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val liveChannel = NotificationChannel(
            CHANNEL_ID_LIVE,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        val importantChannel = NotificationChannel(
            CHANNEL_ID_IMPORTANT,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(liveChannel)
        nm.createNotificationChannel(importantChannel)
    }

    fun postActive(context: Context, cardName: String, emuMode: EmuMode) {
        val (progress, modeTextRes) = when (emuMode) {
            EmuMode.NORMAL -> 20 to R.string.mode_normal
            EmuMode.COMPAT -> 25 to R.string.mode_compat
            EmuMode.NATIVE -> 30 to R.string.mode_native
        }
        notify(context, cardName, context.getString(modeTextRes), progress)
    }

    fun pulse(context: Context, cardName: String, emuMode: EmuMode, scope: CoroutineScope) {
        notify(context, cardName, context.getString(R.string.notif_progress_simulated), 100)
        pulseJob?.cancel()
        pulseJob = scope.launch {
            delay(2000) // Pulse duration
            postActive(context, cardName, emuMode)
        }
    }

    fun cancel(context: Context) {
        pulseJob?.cancel()
        lastNotifyKey = null
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }

    private fun notify(context: Context, title: String, contentText: String, progress: Int) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val isA15Plus = Build.VERSION.SDK_INT >= 35
        val channelId = if (isA15Plus) CHANNEL_ID_LIVE else CHANNEL_ID_IMPORTANT
        
        val notifyKey = "$channelId|$title|$contentText|$progress"
        if (lastNotifyKey == notifyKey) return
        lastNotifyKey = notifyKey

        val tapIntent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = PendingIntent.getBroadcast(context, 1, Intent(ACTION_TOGGLE_ACTIVATE).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val modeIntent = PendingIntent.getBroadcast(context, 2, Intent(ACTION_TOGGLE_MODE).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val nextIntent = PendingIntent.getBroadcast(context, 4, Intent(ACTION_NEXT_CARD).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val delIntent  = PendingIntent.getBroadcast(context, 3, Intent(ACTION_DISMISSED).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_nfc))
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(tapIntent)
            .setDeleteIntent(delIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setProgress(100, progress, false)
            .addAction(Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel), context.getString(R.string.notif_action_stop), stopIntent).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_menu_manage), context.getString(R.string.notif_action_switch_mode), modeIntent).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_media_next), context.getString(R.string.notif_action_next_card), nextIntent).build())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        
        if (isA15Plus) {
            runCatching {
                val m = promotedOngoingMethod ?: Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType).also { promotedOngoingMethod = it }
                m.invoke(builder, true)
            }
        }

        nm.notify(NOTIF_ID, builder.build())
    }
}
