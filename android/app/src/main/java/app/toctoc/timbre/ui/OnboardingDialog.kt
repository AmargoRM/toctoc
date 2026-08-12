package app.toctoc.timbre.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * Onboarding secuencial: al primer arranque pide TODOS los permisos que la app
 * necesita para funcionar bien (no los deja como opcionales).
 *  1. POST_NOTIFICATIONS (Android 13+)
 *  2. Notificaciones a pantalla completa (Android 14+)
 *  3. Excluir de la optimización de batería
 */
@Composable
fun PermissionOnboarding(onFinished: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(currentStep(context)) }

    if (step == null) {
        onFinished()
        return
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { step = currentStep(context) }

    val fullscreenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { step = currentStep(context) }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { step = currentStep(context) }

    when (step) {
        Step.Notifications -> PermStepDialog(
            icon = Icons.Filled.Notifications,
            title = "Permiso de notificaciones",
            body = "Upe timbre necesita mostrarte una notificación cuando alguien " +
                "toca tu timbre. Sin esto no vas a escuchar nada.",
            cta = "Permitir notificaciones",
            onCta = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    step = currentStep(context)
                }
            }
        )
        Step.FullScreen -> PermStepDialog(
            icon = Icons.Filled.Fullscreen,
            title = "Timbre en pantalla completa",
            body = "En Android 14+ tenemos que pedirte que actives «Notificaciones " +
                "a pantalla completa» para Upe timbre. Sin esto el timbre solo " +
                "vibra en vez de sonar y abrirse.",
            cta = "Abrir ajustes",
            onCta = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    fullscreenLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                            .setData(Uri.parse("package:${context.packageName}"))
                    )
                }
            }
        )
        Step.Battery -> PermStepDialog(
            icon = Icons.Filled.BatteryAlert,
            title = "Excluir de la optimización de batería",
            body = "Para que el timbre llegue al instante aunque el teléfono esté " +
                "en reposo, hay que desactivar la optimización de batería para " +
                "Upe timbre. Es lo mismo que hacen WhatsApp o Telegram para las " +
                "llamadas.",
            cta = "Abrir ajustes",
            onCta = {
                try {
                    batteryLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:${context.packageName}"))
                    )
                } catch (_: Exception) {
                    batteryLauncher.launch(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                }
            }
        )
        null -> onFinished()
    }
}

@Composable
private fun PermStepDialog(
    icon: ImageVector,
    title: String,
    body: String,
    cta: String,
    onCta: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body, fontSize = 14.sp) },
        confirmButton = {
            Button(onClick = onCta) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(cta)
            }
        }
    )
}

private enum class Step { Notifications, FullScreen, Battery }

private fun currentStep(context: Context): Step? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return Step.Notifications
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        if (!nm.canUseFullScreenIntent()) return Step.FullScreen
    }
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) return Step.Battery
    return null
}
