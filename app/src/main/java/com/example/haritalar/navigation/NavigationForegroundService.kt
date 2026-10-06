package com.example.haritalar.navigation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Keeps an active navigation session in Android's foreground-location execution class.
 *
 * The actual GPS stream remains owned by AppLocationManager in the same application
 * process; this service supplies the OS-visible foreground execution contract while
 * navigation is active.
 */
class NavigationForegroundService : Service() {
    companion object {
        private const val CHANNEL_ID = "lanu_active_navigation"
        private const val NOTIFICATION_ID = 7301
        private const val EXTRA_DESTINATION = "destination"

        fun start(context: Context, destination: String?) {
            val intent = Intent(context, NavigationForegroundService::class.java)
                .putExtra(EXTRA_DESTINATION, destination.orEmpty())
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NavigationForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val destination = intent?.getStringExtra(EXTRA_DESTINATION).orEmpty()
        val notification = buildNotification(destination)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(destination: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val detail = destination.takeIf { it.isNotBlank() }
            ?.let { "Hedef: $it" }
            ?: "GPS ve rota takibi etkin"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("LANU Harita • Navigasyon etkin")
            .setContentText(detail)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Aktif navigasyon",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Aktif rota sırasında konum ve navigasyon takibini sürdürür."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
