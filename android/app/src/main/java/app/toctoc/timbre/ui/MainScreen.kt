package app.toctoc.timbre.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.toctoc.timbre.BuildConfig
import app.toctoc.timbre.MainActivity
import app.toctoc.timbre.data.Ringtones
import app.toctoc.timbre.update.UpdateState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vm: MainViewModel,
    activity: MainActivity,
    nfcWriting: Boolean,
    nfcWriteResult: String?,
    onClearWriteResult: () -> Unit,
    onStartWrite: (String) -> Unit,
    onCancelWrite: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val updateState by vm.updateState.collectAsState()
    val toast by vm.toast.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Reproductor para escuchar una vista previa del tono
    val previewPlayer = remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    fun preview(res: Int) {
        previewPlayer.value?.let { try { it.stop(); it.release() } catch (_: Exception) {} }
        val mp = android.media.MediaPlayer.create(context, res)
        previewPlayer.value = mp
        mp?.setOnCompletionListener { it.release(); previewPlayer.value = null }
        mp?.start()
    }
    DisposableEffect(Unit) {
        onDispose { previewPlayer.value?.let { try { it.release() } catch (_: Exception) {} } }
    }

    LaunchedEffect(toast) {
        toast?.let { scope.launch { snackbar.showSnackbar(it) }; vm.clearToast() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("TocToc", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Estado del timbre ----
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (settings.listening) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (settings.listening) "Timbre activo" else "Timbre apagado",
                                fontSize = 18.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (settings.listening) "Recibirás alertas cuando toquen"
                                else "Activá para empezar a recibir timbres",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.listening,
                            onCheckedChange = { vm.toggleListening(it) }
                        )
                    }
                }
            }

            // ---- Nombre del timbre ----
            SectionCard(title = "Nombre del timbre", icon = Icons.Filled.Home) {
                var name by remember(settings.doorbellName) { mutableStateOf(settings.doorbellName) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ej: Casa, Depto 4B, Oficina") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.setName(name.ifBlank { "Mi puerta" }) },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Guardar") }
            }

            // ---- Etiqueta NFC ----
            SectionCard(title = "Tu etiqueta NFC", icon = Icons.Filled.Nfc) {
                Text(
                    "Grabá esta información en una etiqueta NFC y pegala en tu puerta. " +
                        "Cuando alguien la toque con su teléfono, sonará tu timbre.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                val url = vm.tagUrl(settings)
                SelectionRow(label = "Enlace de la etiqueta", value = url)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onStartWrite(url) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Grabar etiqueta")
                    }
                    OutlinedButton(
                        onClick = { shareText(context, url) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Compartir")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.testRing() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Probar timbre")
                }
            }

            // ---- Tono del timbre ----
            SectionCard(title = "Tono del timbre", icon = Icons.Filled.MusicNote) {
                Text(
                    "Elegí cómo suena tu timbre. Tocá ▶ para escucharlo.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Ringtones.all.forEach { tone ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = settings.ringtone == tone.id,
                            onClick = { vm.setRingtone(tone.id) }
                        )
                        Text(tone.label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { preview(tone.res) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Escuchar")
                        }
                    }
                }
            }

            // ---- Recibir en otro teléfono (iPhone / sin la app) ----
            SectionCard(title = "Recibir en otro teléfono", icon = Icons.Filled.PhoneIphone) {
                Text(
                    "¿Querés recibir el timbre en un iPhone u otro teléfono sin instalar " +
                        "TocToc? Compartí este enlace: explica cómo recibirlo con la app " +
                        "gratuita ntfy (incluye un QR).",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                val recibir = vm.recibirUrl(settings)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { shareText(context, recibir) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Compartir")
                    }
                    OutlinedButton(
                        onClick = { openUrl(context, recibir) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.QrCode2, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ver / QR")
                    }
                }
            }

            // ---- Que otros creen su propio timbre ----
            SectionCard(title = "Invitar a crear un timbre", icon = Icons.Filled.AddCircle) {
                Text(
                    "¿Querés que otra persona arme su PROPIO timbre (sin depender de vos, " +
                        "incluso desde iPhone)? Compartile esta página: genera su timbre, " +
                        "configura el recibir y hasta graba la etiqueta.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                val crear = vm.crearUrl()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { shareText(context, crear) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Compartir")
                    }
                    OutlinedButton(
                        onClick = { openUrl(context, crear) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Abrir")
                    }
                }
            }

            // ---- Actualizaciones ----
            SectionCard(title = "Actualizaciones", icon = Icons.Filled.SystemUpdate) {
                Text(
                    "Versión instalada: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                when (val st = updateState) {
                    is UpdateState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    is UpdateState.Downloading -> {
                        Text("Descargando… ${st.progress}%", fontSize = 13.sp)
                        LinearProgressIndicator(
                            progress = { st.progress / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                    is UpdateState.UpToDate ->
                        Text("Ya tenés la última versión ✅", color = MaterialTheme.colorScheme.primary)
                    is UpdateState.Error ->
                        Text("Error: ${st.message}", color = MaterialTheme.colorScheme.error)
                    is UpdateState.Available -> {
                        Text(
                            "Nueva versión disponible: ${st.info.versionName}",
                            fontWeight = FontWeight.Bold
                        )
                        if (st.info.notes.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(st.info.notes, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (!app.toctoc.timbre.update.Updater.canInstall(context)) {
                                    activity.openInstallUnknownAppsSettings()
                                }
                                vm.downloadUpdate(st.info)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Descargar e instalar") }
                    }
                    is UpdateState.ReadyToInstall ->
                        Text("Abriendo el instalador…")
                    UpdateState.Idle -> {}
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.checkUpdate() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Buscar actualización") }
            }

            // ---- Fiabilidad / batería ----
            SectionCard(title = "Que no se pierda ningún timbre", icon = Icons.Filled.BatteryAlert) {
                Text(
                    "Para que el timbre suene siempre, desactivá la optimización de batería para TocToc.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { activity.requestIgnoreBatteryOptimizations() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ajustes de batería") }
                Spacer(Modifier.height(8.dp))
                Text(
                    "En Android 14+ activá también «Notificaciones a pantalla completa» " +
                        "para que el timbre despierte la pantalla.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { activity.openFullScreenIntentSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Permiso pantalla completa") }
            }

            // ---- Avanzado ----
            var showAdvanced by remember { mutableStateOf(false) }
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Ocultar opciones avanzadas" else "Opciones avanzadas")
            }
            if (showAdvanced) {
                SectionCard(title = "Servidor y topic", icon = Icons.Filled.Settings) {
                    SelectionRow(label = "Topic", value = settings.topic)
                    Spacer(Modifier.height(12.dp))
                    var server by remember(settings.ntfyServer) { mutableStateOf(settings.ntfyServer) }
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = { Text("Servidor ntfy") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.setServer(server) }) { Text("Guardar servidor") }
                        OutlinedButton(onClick = { vm.regenerateTopic() }) { Text("Regenerar topic") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Podés autohospedar ntfy y poner tu propia URL. Si cambiás el " +
                            "servidor o el topic, volvé a grabar la etiqueta.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // ---- Diálogo de grabación NFC ----
    if (nfcWriting) {
        AlertDialog(
            onDismissRequest = { onCancelWrite() },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { onCancelWrite() }) { Text("Cancelar") } },
            icon = { Icon(Icons.Filled.Nfc, null, Modifier.size(40.dp)) },
            title = { Text("Acercá la etiqueta") },
            text = {
                Column {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(16.dp))
                    Text("Apoyá una etiqueta NFC en la parte trasera del teléfono para grabarla.")
                }
            }
        )
    }
    if (nfcWriteResult != null) {
        AlertDialog(
            onDismissRequest = onClearWriteResult,
            confirmButton = { TextButton(onClick = onClearWriteResult) { Text("Entendido") } },
            title = { Text("Grabación NFC") },
            text = { Text(nfcWriteResult) }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SelectionRow(label: String, value: String) {
    val context = LocalContext.current
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { copyToClipboard(context, label, value) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar", Modifier.size(18.dp))
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir enlace"))
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    } catch (_: Exception) {}
}
