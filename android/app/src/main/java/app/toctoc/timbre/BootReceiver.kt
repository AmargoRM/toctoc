package app.toctoc.timbre

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.toctoc.timbre.data.SettingsRepository
import app.toctoc.timbre.service.RingListenerService

/** Reinicia el servicio de escucha tras reiniciar el equipo o actualizar la app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        // En el build de Play no existe el servicio de escucha (entrega por FCM).
        if (BuildConfig.PLAY_BUILD) return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val settings = try { SettingsRepository(context).snapshot() } catch (_: Exception) { null }
            if (settings?.listening == true && settings.topic.isNotBlank()) {
                try { RingListenerService.start(context) } catch (_: Exception) {}
            }
        }
    }
}
