package app.toctoc.timbre.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.toctoc.timbre.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "toctoc")

/** Un timbre: NFC → push al dueño. Varios conviven en el mismo teléfono. */
data class Doorbell(
    val id: String,
    val topic: String,
    val name: String,
    val ringtone: String,
    val enabled: Boolean,
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("topic", topic)
        .put("name", name)
        .put("ringtone", ringtone)
        .put("enabled", enabled)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject): Doorbell = Doorbell(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            topic = o.optString("topic"),
            name = o.optString("name").ifBlank { "Mi timbre" },
            ringtone = o.optString("ringtone").ifBlank { Ringtones.DEFAULT_ID }
                .let { if (Ringtones.exists(it)) it else Ringtones.DEFAULT_ID },
            enabled = o.optBoolean("enabled", true),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    }
}

data class TocTocSettings(
    val doorbells: List<Doorbell>,
    val ntfyServer: String,
    val listening: Boolean,
    val forceSoundInSilent: Boolean
) {
    fun byId(id: String): Doorbell? = doorbells.firstOrNull { it.id == id }
    fun byTopic(topic: String): Doorbell? = doorbells.firstOrNull { it.topic == topic }
    val activeTopics: List<String> get() = doorbells.filter { it.enabled }.map { it.topic }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        // v2: lista de timbres (JSON). Reemplaza TOPIC/NAME/RINGTONE legacy.
        val DOORBELLS = stringPreferencesKey("doorbells_v2")
        val SERVER = stringPreferencesKey("ntfy_server")
        val LISTENING = booleanPreferencesKey("listening")
        val FORCE_SILENT = booleanPreferencesKey("force_sound_in_silent")

        // Legacy (v1): un único timbre. Se migra la primera vez que leemos v2.
        val LEGACY_TOPIC = stringPreferencesKey("topic")
        val LEGACY_NAME = stringPreferencesKey("doorbell_name")
        val LEGACY_RINGTONE = stringPreferencesKey("ringtone")
    }

    val flow: Flow<TocTocSettings> = context.dataStore.data.map { p ->
        TocTocSettings(
            doorbells = decodeDoorbells(p[Keys.DOORBELLS]) ?: migrateFromLegacyReadOnly(p),
            ntfyServer = (p[Keys.SERVER] ?: BuildConfig.DEFAULT_NTFY_SERVER).trimEnd('/'),
            listening = p[Keys.LISTENING] ?: false,
            forceSoundInSilent = p[Keys.FORCE_SILENT] ?: false
        )
    }

    /** Lectura sincrónica para servicios/receivers sin scope de corrutina. */
    fun snapshot(): TocTocSettings = runBlocking { flow.first() }

    /**
     * Asegura que exista al menos un timbre. Si no hay, crea uno con nombre
     * por defecto y le genera un topic nuevo. Devuelve el id del timbre.
     * También persiste la migración desde v1 si corresponde.
     */
    suspend fun ensureAtLeastOne(): String {
        val current = context.dataStore.data.first()
        decodeDoorbells(current[Keys.DOORBELLS])?.firstOrNull()?.let { return it.id }

        // Migramos desde v1 si había datos legacy, si no creamos uno nuevo.
        val legacyTopic = current[Keys.LEGACY_TOPIC]
        val d = if (!legacyTopic.isNullOrBlank()) {
            Doorbell(
                id = UUID.randomUUID().toString(),
                topic = legacyTopic,
                name = current[Keys.LEGACY_NAME] ?: "Mi timbre",
                ringtone = current[Keys.LEGACY_RINGTONE]?.takeIf { Ringtones.exists(it) }
                    ?: Ringtones.DEFAULT_ID,
                enabled = true,
                createdAt = System.currentTimeMillis()
            )
        } else {
            Doorbell(
                id = UUID.randomUUID().toString(),
                topic = generateTopic(),
                name = "Mi timbre",
                ringtone = Ringtones.DEFAULT_ID,
                enabled = true,
                createdAt = System.currentTimeMillis()
            )
        }
        context.dataStore.edit { p ->
            p[Keys.DOORBELLS] = encodeDoorbells(listOf(d))
            // Limpiamos las claves legacy ya que ahora están en el JSON.
            p.remove(Keys.LEGACY_TOPIC); p.remove(Keys.LEGACY_NAME); p.remove(Keys.LEGACY_RINGTONE)
        }
        return d.id
    }

    suspend fun addDoorbell(name: String): Doorbell {
        val current = context.dataStore.data.first()
        val list = decodeDoorbells(current[Keys.DOORBELLS]) ?: emptyList()
        val d = Doorbell(
            id = UUID.randomUUID().toString(),
            topic = generateTopic(),
            name = name.ifBlank { "Timbre ${list.size + 1}" },
            ringtone = Ringtones.DEFAULT_ID,
            enabled = true,
            createdAt = System.currentTimeMillis()
        )
        context.dataStore.edit { it[Keys.DOORBELLS] = encodeDoorbells(list + d) }
        return d
    }

    suspend fun deleteDoorbell(id: String) {
        update { list -> list.filterNot { it.id == id } }
    }

    suspend fun setDoorbellName(id: String, name: String) {
        update { list ->
            list.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it }
        }
    }

    suspend fun setDoorbellRingtone(id: String, toneId: String) {
        val safe = if (Ringtones.exists(toneId)) toneId else Ringtones.DEFAULT_ID
        update { list -> list.map { if (it.id == id) it.copy(ringtone = safe) else it } }
    }

    suspend fun setDoorbellEnabled(id: String, enabled: Boolean) {
        update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }
    }

    suspend fun regenerateTopic(id: String): String? {
        var newTopic: String? = null
        update { list ->
            list.map {
                if (it.id == id) { newTopic = generateTopic(); it.copy(topic = newTopic!!) } else it
            }
        }
        return newTopic
    }

    suspend fun setServer(server: String) =
        context.dataStore.edit { it[Keys.SERVER] = server.trim().trimEnd('/') }

    suspend fun setListening(on: Boolean) =
        context.dataStore.edit { it[Keys.LISTENING] = on }

    suspend fun setForceSoundInSilent(on: Boolean) =
        context.dataStore.edit { it[Keys.FORCE_SILENT] = on }

    private suspend fun update(fn: (List<Doorbell>) -> List<Doorbell>) {
        context.dataStore.edit { p ->
            val cur = decodeDoorbells(p[Keys.DOORBELLS]) ?: emptyList()
            p[Keys.DOORBELLS] = encodeDoorbells(fn(cur))
        }
    }

    // ---- JSON ----
    private fun encodeDoorbells(list: List<Doorbell>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    private fun decodeDoorbells(json: String?): List<Doorbell>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                runCatching { Doorbell.fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        } catch (_: Exception) { null }
    }

    /**
     * Vista READ-ONLY desde legacy mientras no haya guardado migrado. No
     * escribe en DataStore (porque flow.map es puro); la persistencia la hace
     * ensureAtLeastOne() cuando arranca el VM.
     */
    private fun migrateFromLegacyReadOnly(p: Preferences): List<Doorbell> {
        val topic = p[Keys.LEGACY_TOPIC].orEmpty()
        if (topic.isBlank()) return emptyList()
        return listOf(
            Doorbell(
                id = "legacy",
                topic = topic,
                name = p[Keys.LEGACY_NAME] ?: "Mi timbre",
                ringtone = p[Keys.LEGACY_RINGTONE]?.takeIf { Ringtones.exists(it) }
                    ?: Ringtones.DEFAULT_ID,
                enabled = true,
                createdAt = 0L
            )
        )
    }

    companion object {
        private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"

        fun generateTopic(): String {
            val rnd = SecureRandom()
            val sb = StringBuilder("timbre-")
            repeat(12) { sb.append(ALPHABET[rnd.nextInt(ALPHABET.length)]) }
            return sb.toString()
        }
    }
}
