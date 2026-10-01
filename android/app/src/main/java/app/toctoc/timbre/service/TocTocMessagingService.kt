package app.toctoc.timbre.service

import app.toctoc.timbre.data.Ringtones
import app.toctoc.timbre.data.SettingsRepository
import app.toctoc.timbre.ring.RingActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Recibe los pushes de FCM. Como el relay envía mensajes de tipo DATA con
 * prioridad alta, onMessageReceived se ejecuta también con la app cerrada o el
 * teléfono dormido, y dispara el aviso a pantalla completa.
 *
 * Enrutamiento por timbre: cada doorbell está suscripta a su propio topic FCM,
 * así podemos distinguir cuál sonó y usar su tono.
 */
class TocTocMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data

        // Resolver el topic (y por ende el timbre): intentamos payload → from.
        val topicFromData = data["topic"]?.trim()?.takeIf { it.isNotBlank() }
        val topicFromFrom = message.from
            ?.removePrefix("/topics/")
            ?.takeIf { it.isNotBlank() && !it.startsWith("/") }
        val topic = topicFromData ?: topicFromFrom

        val settings = try { SettingsRepository(applicationContext).snapshot() } catch (_: Exception) { null }
        val doorbell = topic?.let { t -> settings?.byTopic(t) }

        val name = doorbell?.name
            ?: data["name"]
            ?: data["title"]
            ?: message.notification?.title
            ?: "alguien"
        val text = data["message"]
            ?: message.notification?.body
            ?: "Alguien llegó a $name"

        val toneId = doorbell?.ringtone ?: Ringtones.DEFAULT_ID
        val forceAlarm = settings?.forceSoundInSilent == true

        RingActivity.start(applicationContext, text, toneId, forceAlarm)
    }

    override fun onNewToken(token: String) {
        // Usamos suscripción por "topic": al renovarse el token, FCM re-suscribe
        // automáticamente a los topics ya suscritos. Nada que hacer acá.
    }
}
