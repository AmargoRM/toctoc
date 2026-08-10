package app.toctoc.timbre.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.toctoc.timbre.MainActivity
import app.toctoc.timbre.R
import app.toctoc.timbre.TocTocApp
import app.toctoc.timbre.data.Links
import app.toctoc.timbre.data.SettingsRepository
import app.toctoc.timbre.ring.RingActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/**
 * Servicio en primer plano que mantiene una conexión de larga duración con el
 * stream de ntfy. Cuando llega un mensaje, dispara el timbre a pantalla completa.
 */
class RingListenerService : Service() {

    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    @Volatile private var connection: HttpURLConnection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (running.compareAndSet(false, true)) {
            worker = Thread { listenLoop() }.also { it.isDaemon = true; it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        try { connection?.disconnect() } catch (_: Exception) {}
        worker?.interrupt()
        super.onDestroy()
    }

    /**
     * Cuando el usuario cierra la app (swipe en recientes), el sistema puede
     * detener el servicio. Reprogramamos su reinicio para seguir escuchando.
     * (Mejor esfuerzo: en Android 12+ el reinicio en segundo plano puede estar
     * limitado por el sistema; la solución definitiva y confiable es FCM.)
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val settings = SettingsRepository(applicationContext).snapshot()
            if (settings.listening && settings.topic.isNotBlank()) {
                val restart = Intent(applicationContext, RingListenerService::class.java)
                val pi = PendingIntent.getService(
                    this, 1, restart,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
                val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.set(AlarmManager.RTC, System.currentTimeMillis() + 1500, pi)
            }
        } catch (_: Exception) {}
        super.onTaskRemoved(rootIntent)
    }

    private fun startForegroundCompat() {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, TocTocApp.CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.listening_notification_title))
            .setContentText(getString(R.string.listening_notification_text))
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setOngoing(true)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                    startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                else ->
                    startForeground(NOTIF_ID, notif)
            }
        } catch (_: Exception) {
            // p. ej. ForegroundServiceStartNotAllowedException al reiniciar en
            // segundo plano en Android 12+. Evitamos que tire la app.
        }
    }

    private fun listenLoop() {
        val repo = SettingsRepository(applicationContext)
        var backoffMs = 2_000L
        while (running.get()) {
            val settings = try { repo.snapshot() } catch (_: Exception) { null }
            val topic = settings?.topic
            if (settings == null || topic.isNullOrBlank()) {
                sleepQuiet(3_000); continue
            }
            try {
                val url = URL(Links.jsonStreamUrl(settings.ntfyServer, topic))
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    // ntfy manda keepalive cada ~45s; si en 70s no llega nada,
                    // asumimos conexión muerta y reconectamos.
                    readTimeout = 70_000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/x-ndjson")
                    if (this is HttpsURLConnection) { /* usa el trust store del sistema */ }
                    connect()
                }
                connection = conn
                if (conn.responseCode !in 200..299) {
                    conn.disconnect(); sleepQuiet(backoffMs); backoffMs = nextBackoff(backoffMs); continue
                }
                backoffMs = 2_000L // conexión OK, reinicia backoff
                BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                    while (running.get()) {
                        val line = reader.readLine() ?: break
                        handleLine(line)
                    }
                }
            } catch (_: Exception) {
                // se cayó la conexión: reintenta con backoff
            } finally {
                try { connection?.disconnect() } catch (_: Exception) {}
                connection = null
            }
            if (running.get()) { sleepQuiet(backoffMs); backoffMs = nextBackoff(backoffMs) }
        }
    }

    private fun handleLine(line: String?) {
        val text = line?.trim().orEmpty()
        if (text.isEmpty()) return
        try {
            val obj = JSONObject(text)
            when (obj.optString("event")) {
                "message" -> triggerRing(obj.optString("message", "").ifBlank { "Alguien está en la puerta" })
                // "open", "keepalive", "poll_request" -> ignorar
            }
        } catch (_: Exception) { /* línea no-JSON, ignorar */ }
    }

    private fun triggerRing(message: String) {
        RingActivity.start(applicationContext, message)
    }

    private fun sleepQuiet(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }

    private fun nextBackoff(current: Long): Long = (current * 2).coerceAtMost(60_000L)

    companion object {
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, RingListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RingListenerService::class.java))
        }
    }
}
