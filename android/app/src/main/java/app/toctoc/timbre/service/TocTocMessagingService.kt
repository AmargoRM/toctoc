package app.toctoc.timbre.service

import app.toctoc.timbre.ring.RingActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Recibe los pushes de FCM. Como el relay envía mensajes de tipo DATA con
 * prioridad alta, onMessageReceived se ejecuta también con la app cerrada o el
 * teléfono dormido, y dispara el timbre a pantalla completa.
 */
class TocTocMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val name = data["name"]
            ?: data["title"]
            ?: message.notification?.title
            ?: "Alguien está en la puerta"
        val text = data["message"]
            ?: message.notification?.body
            ?: "Están tocando el timbre de $name"
        RingActivity.start(applicationContext, text)
    }

    override fun onNewToken(token: String) {
        // Usamos suscripción por "topic" (no hace falta registrar el token en un
        // servidor). Al renovarse el token, FCM re-suscribe automáticamente a los
        // topics ya suscritos, así que no hay nada que hacer acá.
    }
}
