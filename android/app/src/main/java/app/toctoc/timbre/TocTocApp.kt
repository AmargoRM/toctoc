package app.toctoc.timbre

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import app.toctoc.timbre.data.Ringtones

class TocTocApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        // Canal de bajo perfil para el servicio en primer plano
        val service = NotificationChannel(
            CHANNEL_SERVICE,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene el timbre activo en segundo plano"
            setShowBadge(false)
        }
        nm.createNotificationChannel(service)

        // Dos variantes por tono: notificación normal y alarma (bypass silencio).
        //  - Ring: suena en el stream de ringtone, respeta modo silencio.
        //  - Alarm: suena en el stream de alarma + bypass DND, salta el modo
        //    silencio y vibrador (equivalente a un despertador).
        // Los canales son INMUTABLES: cada perfil necesita su propio ID.
        val ringAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        Ringtones.all.forEach { tone ->
            val soundUri = Uri.parse("android.resource://$packageName/${tone.res}")

            val ring = NotificationChannel(
                ringChannelId(tone.id),
                "Timbre — ${tone.label}",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerta cuando llega un aviso de ${tone.label}"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(soundUri, ringAttrs)
            }
            nm.createNotificationChannel(ring)

            val alarm = NotificationChannel(
                alarmChannelId(tone.id),
                "Alarma — ${tone.label}",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    "Suena fuerte aunque el teléfono esté en silencio (${tone.label})"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(soundUri, alarmAttrs)
            }
            nm.createNotificationChannel(alarm)
        }
    }

    companion object {
        const val CHANNEL_SERVICE = "toctoc_service"

        /** Canal "normal" para un tono. Respeta el modo silencio del teléfono. */
        fun ringChannelId(toneId: String): String = "toctoc_ring_${toneId}_v3"

        /** Canal "alarma": suena aunque el teléfono esté en silencio. */
        fun alarmChannelId(toneId: String): String = "toctoc_alarm_${toneId}_v1"

        /** Elige canal según la preferencia de "forzar en silencio". */
        fun channelFor(toneId: String, forceSoundInSilent: Boolean): String =
            if (forceSoundInSilent) alarmChannelId(toneId) else ringChannelId(toneId)
    }
}
