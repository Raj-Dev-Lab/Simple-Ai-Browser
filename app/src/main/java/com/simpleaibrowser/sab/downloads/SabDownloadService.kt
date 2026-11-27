package com.simpleaibrowser.sab.downloads

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import java.util.Locale

class SabDownloadService : Service() {

    companion object {
        const val CHANNEL_PROGRESS = "sab_downloads_progress"
        const val CHANNEL_ALERTS = "sab_downloads_alerts"
        const val EXTRA_OPEN_DOWNLOADS = "com.simpleaibrowser.sab.EXTRA_OPEN_DOWNLOADS"

        private const val ACTION_UPDATE = "com.simpleaibrowser.sab.DOWNLOAD_SERVICE_UPDATE"
        private const val ACTION_STOP = "com.simpleaibrowser.sab.DOWNLOAD_SERVICE_STOP"
        private const val EXTRA_ACTIVE_COUNT = "active_count"
        private const val EXTRA_SUMMARY = "summary"
        private const val EXTRA_PROGRESS_PCT = "progress_pct"
        private const val FOREGROUND_ID = 4101

        fun syncForeground(
            context: Context,
            activeCount: Int,
            summary: String,
            progressPercent: Int
        ) {
            if (activeCount <= 0) {
                runCatching {
                    context.stopService(Intent(context, SabDownloadService::class.java))
                }
                return
            }
            val intent = Intent(context, SabDownloadService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_ACTIVE_COUNT, activeCount)
                putExtra(EXTRA_SUMMARY, summary)
                putExtra(EXTRA_PROGRESS_PCT, progressPercent)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun notifyCompletion(
            context: Context,
            notificationId: Int,
            fileName: String,
            success: Boolean,
            detail: String
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            ensureChannels(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_DOWNLOADS, true)
            }
            val pi = PendingIntent.getActivity(
                context,
                notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(
                    if (success) android.R.drawable.stat_sys_download_done
                    else android.R.drawable.stat_notify_error
                )
                .setContentTitle(if (success) "Downloaded: $fileName" else "Failed: $fileName")
                .setContentText(detail)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            nm.notify(notificationId, notification)
        }

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_PROGRESS) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_PROGRESS,
                        "Active Downloads & Torrents",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Live progress notifications for ongoing SAB downloads and torrents"
                        setShowBadge(false)
                    }
                )
            }
            if (nm.getNotificationChannel(CHANNEL_ALERTS) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ALERTS,
                        "Completed & Failed Downloads",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Alerts when downloads or torrents complete"
                    }
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val activeCount = intent?.getIntExtra(EXTRA_ACTIVE_COUNT, 1) ?: 1
        if (activeCount <= 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val summary = intent?.getStringExtra(EXTRA_SUMMARY)?.takeIf { it.isNotBlank() }
            ?: "Downloading $activeCount item(s)..."
        val pct = (intent?.getIntExtra(EXTRA_PROGRESS_PCT, -1) ?: -1).coerceIn(-1, 100)

        val notification = buildForegroundNotification(activeCount, summary, pct)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FOREGROUND_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun buildForegroundNotification(
        activeCount: Int,
        summary: String,
        progressPct: Int
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_DOWNLOADS, true)
        }
        val pi = PendingIntent.getActivity(
            this,
            FOREGROUND_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = String.format(
            Locale.US,
            "SAB Download Manager (%d active)",
            activeCount
        )
        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(summary)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPct.coerceAtLeast(0), progressPct < 0)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
