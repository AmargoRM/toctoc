package app.toctoc.timbre

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.toctoc.timbre.nfc.NfcHelper
import app.toctoc.timbre.nfc.WriteResult
import app.toctoc.timbre.ui.MainScreen
import app.toctoc.timbre.ui.MainViewModel
import app.toctoc.timbre.ui.theme.TocTocTheme

class MainActivity : ComponentActivity() {

    /** URL pendiente de grabar en la próxima etiqueta acercada. */
    private var pendingWriteUrl: String? = null

    // Estado de escritura NFC observable por la UI
    var nfcWriting by mutableStateOf(false)
        private set
    var nfcWriteResult by mutableStateOf<String?>(null)

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Los permisos ahora los pide el onboarding secuencial de MainScreen
        // (PermissionOnboarding), que no los deja opcionales.

        setContent {
            TocTocTheme {
                val vm: MainViewModel = viewModel()
                MainScreen(
                    vm = vm,
                    activity = this,
                    nfcWriting = nfcWriting,
                    nfcWriteResult = nfcWriteResult,
                    onClearWriteResult = { nfcWriteResult = null },
                    onStartWrite = { url -> startNfcWrite(url) },
                    onCancelWrite = { cancelNfcWrite() }
                )
            }
        }
    }

    // ---------------- NFC ----------------

    fun startNfcWrite(url: String) {
        val adapter = NfcHelper.getAdapter(this)
        if (adapter == null) {
            nfcWriteResult = "Este teléfono no tiene NFC."
            return
        }
        if (!adapter.isEnabled) {
            nfcWriteResult = "El NFC está apagado. Activalo en Ajustes."
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            return
        }
        pendingWriteUrl = url
        nfcWriting = true
    }

    fun cancelNfcWrite() {
        pendingWriteUrl = null
        nfcWriting = false
    }

    override fun onResume() {
        super.onResume()
        NfcHelper.enableForegroundDispatch(this)
    }

    override fun onPause() {
        super.onPause()
        NfcHelper.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tag = NfcHelper.extractTag(intent) ?: return
        val url = pendingWriteUrl
        if (nfcWriting && url != null) {
            val result = NfcHelper.writeUrl(tag, url)
            nfcWriteResult = when (result) {
                is WriteResult.Success -> "✅ Etiqueta grabada. Ya podés pegarla en la puerta."
                is WriteResult.Error -> "❌ ${result.reason}"
            }
            pendingWriteUrl = null
            nfcWriting = false
        }
    }

    // ---------------- Permisos / sistema ----------------

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestIgnoreBatteryOptimizations() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {}
        }
    }

    fun openInstallUnknownAppsSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {}
        }
    }
}
