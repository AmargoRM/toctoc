package app.toctoc.timbre.data

import android.net.Uri
import app.toctoc.timbre.BuildConfig

/** Construye las URLs que enlazan etiqueta NFC, página de timbre y ntfy. */
object Links {

    /** Endpoint de publicación de ntfy (donde el visitante "toca el timbre"). */
    fun publishUrl(server: String, topic: String): String =
        "${server.trimEnd('/')}/$topic"

    /** Stream JSON de ntfy que escucha el dueño. */
    fun jsonStreamUrl(server: String, topic: String): String =
        "${server.trimEnd('/')}/$topic/json"

    /**
     * URL que se graba en la etiqueta NFC. Apunta a una página web pública
     * (GitHub Pages) que, al abrirse, publica el timbre en ntfy. Así funciona
     * para CUALQUIER visitante, tenga o no la app instalada.
     */
    fun tagUrl(server: String, topic: String, doorbellName: String): String {
        return Uri.parse(BuildConfig.RING_PAGE_BASE).buildUpon()
            .appendQueryParameter("t", topic)
            .appendQueryParameter("s", server.trimEnd('/'))
            .appendQueryParameter("n", doorbellName)
            .build()
            .toString()
    }

    /**
     * URL de la página para recibir el timbre en OTRO teléfono (iPhone) usando
     * la app oficial de ntfy. Se comparte con quien quiera recibir los avisos.
     */
    fun recibirUrl(server: String, topic: String, doorbellName: String): String {
        return Uri.parse(BuildConfig.RECIBIR_PAGE_BASE).buildUpon()
            .appendQueryParameter("t", topic)
            .appendQueryParameter("s", server.trimEnd('/'))
            .appendQueryParameter("n", doorbellName)
            .build()
            .toString()
    }
}
