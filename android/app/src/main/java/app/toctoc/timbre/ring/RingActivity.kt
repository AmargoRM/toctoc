package app.toctoc.timbre.ring

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.NotificationCompat
import app.toctoc.timbre.R
import app.toctoc.timbre.TocTocApp
import app.toctoc.timbre.data.Ringtones
import app.toctoc.timbre.data.SettingsRepository
import kotlinx.coroutines.delay
import androidx.compose.runtime.*

/**
 * Pantalla a pantalla completa que se muestra cuando llega un aviso.
 * Se lanza mediante una notificación con full-screen intent para poder
 * aparecer incluso con el teléfono bloqueado o la app en segundo plano.
 */
class RingActivity : ComponentActivity() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var ringtoneRes: Int = R.raw.doorbell
    private var forceSoundInSilent: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockscreen()

        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Alguien llegó"
        val toneId = intent.getStringExtra(EXTRA_TONE) ?: Ringtones.DEFAULT_ID
        ringtoneRes = Ringtones.resFor(toneId)
        forceSoundInSilent = intent.getBooleanExtra(EXTRA_FORCE_SILENT, false)
        startAlarm()

        setContent {
            var seconds by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                while (seconds < AUTO_DISMISS_SECONDS) {
                    delay(1000); seconds++
                }
                finishRing()
            }
            RingScreen(message = message, onDismiss = { finishRing() })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Ya está sonando; reiniciar el sonido por si acaso
        startAlarm()
    }

    private fun showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun startAlarm() {
        stopAlarm()
        // Si "forzar sonido en silencio" está activo, usamos el stream de ALARMA
        // (bypasea modo silencio/no molestar como un despertador). Si no, usamos
        // ringtone (respeta la configuración del teléfono).
        val usage = if (forceSoundInSilent) AudioAttributes.USAGE_ALARM
                    else AudioAttributes.USAGE_NOTIFICATION_RINGTONE
        try {
            val soundUri = Uri.parse("android.resource://$packageName/$ringtoneRes")
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@RingActivity, soundUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {}

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 600, 300, 600, 300)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlarm() {
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        try { vibrator?.cancel() } catch (_: Exception) {}
    }

    private fun finishRing() {
        stopAlarm()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(RING_NOTIF_ID)
        finish()
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_TONE = "tone"
        private const val EXTRA_FORCE_SILENT = "force_silent"
        private const val RING_NOTIF_ID = 2002
        private const val AUTO_DISMISS_SECONDS = 60

        // Anti-doble-aviso: si llegan dos avisos casi juntos (ntfy + FCM),
        // ignoramos el segundo.
        @Volatile private var lastRingAt = 0L
        private const val DEBOUNCE_MS = 4000L

        /**
         * Punto de entrada desde el servicio de FCM / ntfy. Publica una
         * notificación con full-screen intent para despertar la pantalla aun
         * estando bloqueado.
         *
         * @param toneId tono específico del timbre que disparó el aviso.
         * @param forceSoundInSilent si true, usa canal de ALARMA (bypass silencio).
         */
        fun start(
            context: Context,
            message: String,
            toneId: String? = null,
            forceSoundInSilent: Boolean? = null
        ) {
            val now = System.currentTimeMillis()
            if (now - lastRingAt < DEBOUNCE_MS) return
            lastRingAt = now

            val settings = try { SettingsRepository(context.applicationContext).snapshot() }
                           catch (_: Exception) { null }
            val tone = toneId?.takeIf { Ringtones.exists(it) }
                ?: Ringtones.DEFAULT_ID
            val forceSilent = forceSoundInSilent ?: (settings?.forceSoundInSilent == true)
            val channelId = TocTocApp.channelFor(tone, forceSilent)

            val full = Intent(context, RingActivity::class.java).apply {
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_TONE, tone)
                putExtra(EXTRA_FORCE_SILENT, forceSilent)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val fsPending = PendingIntent.getActivity(
                context, 1, full,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle("🔔 ¡Aviso de timbre!")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setOngoing(true)
                .setFullScreenIntent(fsPending, true)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(RING_NOTIF_ID, notif)

            try { context.startActivity(full) } catch (_: Exception) {}
        }
    }
}
