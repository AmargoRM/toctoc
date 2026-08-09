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
import java.security.SecureRandom

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "toctoc")

data class TocTocSettings(
    val topic: String,
    val doorbellName: String,
    val ntfyServer: String,
    val listening: Boolean,
    val ringtone: String
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val TOPIC = stringPreferencesKey("topic")
        val NAME = stringPreferencesKey("doorbell_name")
        val SERVER = stringPreferencesKey("ntfy_server")
        val LISTENING = booleanPreferencesKey("listening")
        val RINGTONE = stringPreferencesKey("ringtone")
    }

    val flow: Flow<TocTocSettings> = context.dataStore.data.map { p ->
        TocTocSettings(
            topic = p[Keys.TOPIC] ?: "",
            doorbellName = p[Keys.NAME] ?: "Mi puerta",
            ntfyServer = (p[Keys.SERVER] ?: BuildConfig.DEFAULT_NTFY_SERVER).trimEnd('/'),
            listening = p[Keys.LISTENING] ?: false,
            ringtone = p[Keys.RINGTONE] ?: Ringtones.DEFAULT_ID
        )
    }

    /** Lectura sincrónica para servicios/receivers que no tienen scope de corrutina. */
    fun snapshot(): TocTocSettings = runBlocking { flow.first() }

    suspend fun ensureTopic(): String {
        val current = context.dataStore.data.first()[Keys.TOPIC]
        if (!current.isNullOrBlank()) return current
        val topic = generateTopic()
        context.dataStore.edit { it[Keys.TOPIC] = topic }
        return topic
    }

    suspend fun setName(name: String) =
        context.dataStore.edit { it[Keys.NAME] = name }

    suspend fun setServer(server: String) =
        context.dataStore.edit { it[Keys.SERVER] = server.trim().trimEnd('/') }

    suspend fun setListening(on: Boolean) =
        context.dataStore.edit { it[Keys.LISTENING] = on }

    suspend fun setRingtone(id: String) =
        context.dataStore.edit { it[Keys.RINGTONE] = id }

    suspend fun regenerateTopic(): String {
        val topic = generateTopic()
        context.dataStore.edit { it[Keys.TOPIC] = topic }
        return topic
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
