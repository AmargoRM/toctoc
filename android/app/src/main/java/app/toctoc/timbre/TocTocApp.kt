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

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Un canal por tono, CADA UNO CON SU SONIDO. Así el timbre suena aunque
        // no se abra la pantalla completa (p. ej. sin permiso de full-screen,
        // cuando el push llega con la app cerrada).
        Ringtones.all.forEach { tone ->
            val ch = NotificationChannel(
                ringChannelId(tone.id),
                "Timbre — ${tone.label}",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerta cuando alguien toca el timbre"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(Uri.parse("android.resource://$packageName/${tone.res}"), attrs)
            }
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_SERVICE = "toctoc_service"
        // Un canal por tono (v3), cada uno con su sonido incorporado.
        fun ringChannelId(toneId: String): String = "toctoc_ring_${toneId}_v3"
    }
}
