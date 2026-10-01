package app.toctoc.timbre.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.toctoc.timbre.BuildConfig
import app.toctoc.timbre.data.Doorbell
import app.toctoc.timbre.data.Links
import app.toctoc.timbre.data.Ntfy
import app.toctoc.timbre.data.Relay
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
            doorbells = emptyList(),
            ntfyServer = "https://ntfy.sh",
            listening = false,
            forceSoundInSilent = false
        )
    )

    val updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val toast = MutableStateFlow<String?>(null)
    /** El id del timbre recién creado para que la UI lo expanda automáticamente. */
    val focusDoorbellId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            repo.ensureAtLeastOne()
            // Si las alertas ya estaban activas, resuscribir a FCM. Al re-instalar
            // o actualizar la app el token cambia y hay que re-suscribirse.
            val s = repo.flow.first()
            if (s.listening) syncSubscriptions(desired = s.activeTopics)
        }
    }

    // ---- FCM topic sync ----
    private fun subscribe(topic: String) {
        try { FirebaseMessaging.getInstance().subscribeToTopic(topic) } catch (_: Exception) {}
    }
    private fun unsubscribe(topic: String) {
        try { FirebaseMessaging.getInstance().unsubscribeFromTopic(topic) } catch (_: Exception) {}
    }
    private fun syncSubscriptions(desired: List<String>) {
        // No tracking local: FCM es idempotente. Suscribimos lo deseado (todos los
        // enabled) y nos desuscribimos solo lo que explícitamente deja de estar
        // vía deleteDoorbell/regenerateTopic/setEnabled(false).
        desired.forEach { subscribe(it) }
    }

    // ---- Links ----
    fun tagUrl(d: Doorbell): String =
        Links.tagUrl(settings.value.ntfyServer, d.topic, d.name)
    fun recibirUrl(d: Doorbell): String =
        Links.recibirUrl(settings.value.ntfyServer, d.topic, d.name)
    fun crearUrl(): String = Links.crearPageUrl()

    // ---- Toggles globales ----
    fun toggleListening(on: Boolean) = viewModelScope.launch {
        repo.setListening(on)
        val s = settings.value
        if (on) {
            s.activeTopics.forEach { subscribe(it) }
        } else {
            s.doorbells.forEach { unsubscribe(it.topic) }
        }
        if (!BuildConfig.PLAY_BUILD) {
            val ctx = getApplication<Application>()
            if (on && s.doorbells.any { it.enabled }) RingListenerService.start(ctx)
            else RingListenerService.stop(ctx)
        }
    }

    fun toggleForceSoundInSilent(on: Boolean) = viewModelScope.launch {
        repo.setForceSoundInSilent(on)
    }

    fun setServer(server: String) = viewModelScope.launch { repo.setServer(server) }

    // ---- Operaciones por timbre ----
    fun addDoorbell(name: String) = viewModelScope.launch {
        val d = repo.addDoorbell(name)
        if (settings.value.listening && d.enabled) subscribe(d.topic)
        focusDoorbellId.value = d.id
        toast.value = "Timbre «${d.name}» creado."
    }

    fun deleteDoorbell(id: String) = viewModelScope.launch {
        val d = settings.value.byId(id) ?: return@launch
        unsubscribe(d.topic)
        repo.deleteDoorbell(id)
        toast.value = "Timbre «${d.name}» eliminado."
    }

    fun setDoorbellName(id: String, name: String) =
        viewModelScope.launch { repo.setDoorbellName(id, name) }

    fun setDoorbellRingtone(id: String, toneId: String) =
        viewModelScope.launch { repo.setDoorbellRingtone(id, toneId) }

    fun setDoorbellEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        val d = settings.value.byId(id) ?: return@launch
        repo.setDoorbellEnabled(id, enabled)
        if (settings.value.listening) {
            if (enabled) subscribe(d.topic) else unsubscribe(d.topic)
        }
    }

    fun regenerateTopic(id: String) = viewModelScope.launch {
        val old = settings.value.byId(id) ?: return@launch
        unsubscribe(old.topic)
        val new = repo.regenerateTopic(id) ?: return@launch
        if (settings.value.listening && old.enabled) subscribe(new)
        toast.value = "Nuevo código. Volvé a grabar la etiqueta NFC."
    }

    fun clearFocus() { focusDoorbellId.value = null }

    fun testRing(id: String) = viewModelScope.launch {
        val d = settings.value.byId(id) ?: return@launch
        val s = settings.value
        val relayR = Relay.ring(d.topic, d.name)
        val ntfyR = Ntfy.publish(s.ntfyServer, d.topic, "Prueba del timbre «${d.name}» 🔔", d.name)
        val relayOk = relayR.isSuccess
        val ntfyOk = ntfyR.isSuccess
        toast.value = when {
            relayOk -> "Enviado por FCM. Si no suena, revisá permisos y batería."
            ntfyOk -> "Solo llegó por ntfy. Relay caído: ${relayR.exceptionOrNull()?.message}"
            else -> "Falló todo. Relay: ${relayR.exceptionOrNull()?.message}. ntfy: ${ntfyR.exceptionOrNull()?.message}"
        }
    }

    fun ringtoneLabel(id: String): String = Ringtones.labelFor(id)

    fun clearToast() { toast.value = null }

    // ---- Updater (solo sideload) ----
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
}
