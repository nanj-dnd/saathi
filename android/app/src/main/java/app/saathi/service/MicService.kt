package app.saathi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import app.saathi.Languages
import app.saathi.Prefs
import app.saathi.R

/**
 * Microphone-type foreground service held only while Saathi is listening, so recording is allowed
 * while another app is in front. It also shows the senior a "listening" notification.
 */
class MicService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val res = Languages.context(this, Prefs(this).language)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, res.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(res.getString(R.string.notif_listening))
            .setOngoing(true)
            .build()
        startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "listening"
        private const val ID = 7

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, MicService::class.java))
            } catch (e: Exception) {
                // Some OEMs refuse background FGS starts; recording still works on most of them.
                Log.w("Saathi", "mic service not started", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MicService::class.java))
        }
    }
}
