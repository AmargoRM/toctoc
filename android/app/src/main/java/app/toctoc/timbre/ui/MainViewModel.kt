package app.toctoc.timbre.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.toctoc.timbre.BuildConfig
import app.toctoc.timbre.data.Links
import app.toctoc.timbre.data.Ntfy
import app.toctoc.timbre.data.Ringtones
import app.toctoc.timbre.data.SettingsRepository
import app.toctoc.timbre.data.TocTocSettings
import app.toctoc.timbre.service.RingListenerService
import app.toctoc.timbre.update.UpdateInfo
import app.toctoc.timbre.update.UpdateState
import app.toctoc.timbre.update.Updater
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    val settings: StateFlow<TocTocSettings> = repo.flow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TocTocSettings(
            topic = "",
            doorbellName = "Mi puerta",
            ntfyServer = "https://ntfy.sh",
            listening = false,
            ringtone = Ringtones.DEFAULT_ID
        )
    )

    val updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val toast = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            repo.ensureTopic()
            // Si el timbre ya estaba activo, aseguramos la suscripción FCM
            // (tras reinstalar/actualizar el token cambia y hay que re-suscribir).
            val s = repo.flow.first()
            if (s.listening && s.topic.isNotBlank()) subscribeFcm(s.topic, true)
        }
    }

    private fun subscribeFcm(topic: String, on: Boolean) {
        try {
            val fm = FirebaseMessaging.getInstance()
            if (on) fm.subscribeToTopic(topic) else fm.unsubscribeFromTopic(topic)
        } catch (_: Exception) {}
    }

    fun tagUrl(s: TocTocSettings): String =
        Links.tagUrl(s.ntfyServer, s.topic, s.doorbellName)

    fun recibirUrl(s: TocTocSettings): String =
        Links.recibirUrl(s.ntfyServer, s.topic, s.doorbellName)

    fun crearUrl(): String = Links.crearPageUrl()

    fun setRingtone(id: String) = viewModelScope.launch { repo.setRingtone(id) }

    fun setName(name: String) = viewModelScope.launch { repo.setName(name) }

    fun setServer(server: String) = viewModelScope.launch { repo.setServer(server) }

    fun regenerateTopic() = viewModelScope.launch {
        val old = settings.value.topic
        val new = repo.regenerateTopic()
        if (settings.value.listening) {
            if (old.isNotBlank()) subscribeFcm(old, false)
            subscribeFcm(new, true)
        }
        toast.value = "Se generó un topic nuevo. Volvé a grabar tu etiqueta NFC."
    }

    fun toggleListening(on: Boolean) = viewModelScope.launch {
        val topic = repo.ensureTopic()
        repo.setListening(on)
        subscribeFcm(topic, on)
        // En el build de Play la entrega es 100% FCM: no hay servicio de escucha.
        if (!BuildConfig.PLAY_BUILD) {
            val ctx = getApplication<Application>()
            if (on) RingListenerService.start(ctx) else RingListenerService.stop(ctx)
        }
    }

    fun testRing() = viewModelScope.launch {
        val s = settings.value
        val r = Ntfy.publish(s.ntfyServer, s.topic, "Prueba de timbre 🔔", s.doorbellName)
        toast.value = if (r.isSuccess) "Timbre de prueba enviado" else "No se pudo enviar: ${r.exceptionOrNull()?.message}"
    }

    fun checkUpdate() = viewModelScope.launch {
        updateState.value = UpdateState.Checking
        val r = Updater.check()
        updateState.value = r.fold(
            onSuccess = { info -> if (info.isNewer) UpdateState.Available(info) else UpdateState.UpToDate },
            onFailure = { UpdateState.Error(it.message ?: "Error al buscar actualización") }
        )
    }

    fun downloadUpdate(info: UpdateInfo) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        updateState.value = UpdateState.Downloading(0)
        val r = Updater.download(ctx, info) { pct ->
            updateState.value = UpdateState.Downloading(pct)
        }
        r.fold(
            onSuccess = { file ->
                updateState.value = UpdateState.ReadyToInstall
                Updater.install(ctx, file)
            },
            onFailure = { updateState.value = UpdateState.Error(it.message ?: "Error al descargar") }
        )
    }

    fun clearToast() { toast.value = null }
}
