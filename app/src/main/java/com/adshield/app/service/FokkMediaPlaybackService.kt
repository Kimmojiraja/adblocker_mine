package com.adshield.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.adshield.app.AdShieldApp
import com.adshield.app.R
import com.adshield.app.ui.MediaHubActivity

/**
 * Foreground service for continuous, uninterrupted audio and video playback.
 * Running as a ForegroundService with FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
 * prevents Android OS from suspending the WebView audio renderer when the screen
 * is locked or the user navigates away from the browser.
 */
class FokkMediaPlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val NOTIFICATION_ID = 4004
        const val ACTION_START = "com.adshield.app.action.START_MEDIA_FOREGROUND"
        const val ACTION_STOP = "com.adshield.app.action.STOP_MEDIA_FOREGROUND"
        const val EXTRA_TITLE = "extra_media_title"
        const val EXTRA_HOST = "extra_media_host"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        fun start(context: Context, title: String, host: String, isPlaying: Boolean) {
            val intent = Intent(context, FokkMediaPlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {}
        }

        fun stop(context: Context) {
            val intent = Intent(context, FokkMediaPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FokkShield:ForegroundMediaWakeLock")
        try {
            wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours maximum hold
        } catch (e: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseAndStop()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Media Playing"
        val host = intent?.getStringExtra(EXTRA_HOST) ?: "Fokk Shield"
        val isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, true) ?: true

        val notification = buildMediaNotification(title, host, isPlaying)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun buildMediaNotification(title: String, host: String, isPlaying: Boolean): Notification {
        val playIntent = Intent(this, MediaHubActivity::class.java).apply {
            action = MediaHubActivity.ACTION_MEDIA_TOGGLE
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val rewIntent = Intent(this, MediaHubActivity::class.java).apply {
            action = MediaHubActivity.ACTION_MEDIA_REWIND_10
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fwdIntent = Intent(this, MediaHubActivity::class.java).apply {
            action = MediaHubActivity.ACTION_MEDIA_FORWARD_10
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = Intent(this, MediaHubActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pFlags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pPlay = PendingIntent.getActivity(this, 101, playIntent, pFlags)
        val pRew = PendingIntent.getActivity(this, 102, rewIntent, pFlags)
        val pFwd = PendingIntent.getActivity(this, 103, fwdIntent, pFlags)
        val pContent = PendingIntent.getActivity(this, 100, contentIntent, pFlags)

        return NotificationCompat.Builder(this, AdShieldApp.CHANNEL_ID_MEDIA_PLAYBACK)
            .setSmallIcon(if (isPlaying) R.drawable.ic_media_play else R.drawable.ic_media_pause)
            .setContentTitle(if (title.isNotBlank()) title else "Media Playing")
            .setContentText(host)
            .setSubText("Background Playback Active")
            .setContentIntent(pContent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_media_rewind_10, "-10s", pRew)
            .addAction(
                if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                pPlay
            )
            .addAction(R.drawable.ic_media_forward_10, "+10s", pFwd)
            .build()
    }

    private fun releaseAndStop() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        releaseAndStop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
