package app.toctoc.timbre.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Llama al relay (Cloudflare Worker) que envía el push por FCM al dueño.
 * Es la ÚNICA vía que funciona con la app cerrada / teléfono dormido.
 * La app del visitante (SendRingActivity) y la página web usan el mismo endpoint.
 */
object Relay {
    private const val BASE = "https://upe-timbre-relay.estebanrm8474.workers.dev"

    suspend fun ring(topic: String, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE/ring?t=${Uri.encode(topic)}&n=${Uri.encode(name)}"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            val code = conn.responseCode
            val ok = code in 200..299
            if (ok) {
                conn.disconnect()
                Result.success(Unit)
            } else {
                // El worker devuelve el detalle del fallo en el body (JSON con
                // el error de FCM). Lo leemos para poder diagnosticar.
                val detail = try {
                    (conn.errorStream ?: conn.inputStream)
                        ?.bufferedReader()?.use { it.readText() }?.take(300)
                } catch (_: Exception) { null }
                conn.disconnect()
                Result.failure(Exception("HTTP $code${detail?.let { ": $it" } ?: ""}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
