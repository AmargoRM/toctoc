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
import app.toctoc.timbre.data.Ringtones
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
 * Servicio en primer plano que mantiene una conexión de larga duración con ntfy.
 * Suscribe a TODOS los topics habilitados en una sola conexión (ntfy acepta
 * topics coma-separados en la URL) y, al llegar un mensaje, dispara el aviso
 * a pantalla completa usando el tono del timbre que corresponda.
 *
 * Solo relevante en el flavor sideload; en Play la entrega es 100% FCM.
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val s = SettingsRepository(applicationContext).snapshot()
            if (s.listening && s.doorbells.any { it.enabled }) {
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
                else -> startForeground(NOTIF_ID, notif)
            }
        } catch (_: Exception) { /* ForegroundServiceStartNotAllowed en Android 12+ */ }
    }

    private fun listenLoop() {
        val repo = SettingsRepository(applicationContext)
        var backoffMs = 2_000L
        var currentTopics: List<String> = emptyList()
        while (running.get()) {
            val s = try { repo.snapshot() } catch (_: Exception) { null }
            val topics = s?.doorbells?.filter { it.enabled }?.map { it.topic }?.filter { it.isNotBlank() }
                ?: emptyList()
            if (s == null || topics.isEmpty()) {
                sleepQuiet(3_000); continue
            }
            // Si la lista cambió, cerrá la conexión para re-abrir con las nuevas.
            if (topics != currentTopics) {
                try { connection?.disconnect() } catch (_: Exception) {}
                currentTopics = topics
            }
            try {
                val joined = topics.joinToString(",")
                val url = URL("${s.ntfyServer.trimEnd('/')}/$joined/json")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 70_000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/x-ndjson")
                    if (this is HttpsURLConnection) { /* trust store del sistema */ }
                    connect()
                }
                connection = conn
                if (conn.responseCode !in 200..299) {
                    conn.disconnect(); sleepQuiet(backoffMs); backoffMs = nextBackoff(backoffMs); continue
                }
                backoffMs = 2_000L
                BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                    while (running.get()) {
                        val line = reader.readLine() ?: break
                        handleLine(line, s)
                        // Si cambió la lista mientras leíamos, salimos para reconectar
                        val newTopics = try { repo.snapshot().doorbells.filter { it.enabled }.map { it.topic } } catch (_: Exception) { topics }
                        if (newTopics != currentTopics) break
                    }
                }
            } catch (_: Exception) {
                // Conexión caída: reintenta con backoff
            } finally {
                try { connection?.disconnect() } catch (_: Exception) {}
                connection = null
            }
            if (running.get()) { sleepQuiet(backoffMs); backoffMs = nextBackoff(backoffMs) }
        }
    }

    private fun handleLine(line: String?, settings: app.toctoc.timbre.data.TocTocSettings) {
        val text = line?.trim().orEmpty()
        if (text.isEmpty()) return
        try {
            val obj = JSONObject(text)
            if (obj.optString("event") != "message") return
            val topic = obj.optString("topic")
            val doorbell = topic.takeIf { it.isNotBlank() }?.let { settings.byTopic(it) }
            val msg = obj.optString("message", "").ifBlank {
                doorbell?.let { "Alguien llegó a ${it.name}" } ?: "Alguien llegó"
            }
            RingActivity.start(
                applicationContext,
                msg,
                doorbell?.ringtone ?: Ringtones.DEFAULT_ID,
                settings.forceSoundInSilent
            )
        } catch (_: Exception) { /* línea no-JSON */ }
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
