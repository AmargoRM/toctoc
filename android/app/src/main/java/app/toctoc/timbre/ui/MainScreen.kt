package app.toctoc.timbre.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.toctoc.timbre.BuildConfig
import app.toctoc.timbre.MainActivity
import app.toctoc.timbre.data.Doorbell
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
    val focusId by vm.focusDoorbellId.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var onboardingDone by remember { mutableStateOf(false) }
    if (!onboardingDone) {
        PermissionOnboarding(onFinished = { onboardingDone = true })
    }

    // Qué cards están expandidas
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(focusId) {
        focusId?.let {
            expanded[it] = true
            vm.clearFocus()
        }
    }
    // Si solo hay uno, lo mostramos expandido por defecto
    LaunchedEffect(settings.doorbells.size) {
        if (settings.doorbells.size == 1) {
            expanded[settings.doorbells.first().id] = true
        }
    }

    // Preview player para escuchar un tono (reutilizado entre cards)
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

    var showAdd by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<Doorbell?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Upe timbre", fontWeight = FontWeight.Bold) },
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
            // ---- Estado global ----
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    val active = settings.doorbells.count { it.enabled }
                    val total = settings.doorbells.size
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (settings.listening) Icons.Filled.NotificationsActive
                            else Icons.Filled.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (settings.listening) "Alertas activas" else "Alertas pausadas",
                                fontSize = 18.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (settings.listening)
                                    "Escuchando $active de $total timbre${if (total == 1) "" else "s"}"
                                else "Activá para recibir avisos cuando alguien llegue",
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

            // ---- Forzar sonido en silencio ----
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Sonar aunque esté en silencio", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Usa el canal de alarma: salta el modo silencio como un despertador.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.forceSoundInSilent,
                        onCheckedChange = { vm.toggleForceSoundInSilent(it) }
                    )
                }
            }

            // ---- Lista de timbres ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Nfc, null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("Mis timbres", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "${settings.doorbells.size}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Programá un NFC por lugar (casa, trabajo, auto…). Cada uno con " +
                    "su nombre y su tono. Al tocarlo, te avisa que alguien llegó ahí.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (settings.doorbells.isEmpty()) {
                Text(
                    "No hay ningún timbre todavía.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            settings.doorbells.forEach { d ->
                DoorbellCard(
                    doorbell = d,
                    expanded = expanded[d.id] == true,
                    onToggleExpanded = { expanded[d.id] = !(expanded[d.id] ?: false) },
                    onEnable = { vm.setDoorbellEnabled(d.id, it) },
                    onRename = { vm.setDoorbellName(d.id, it) },
                    onSelectRingtone = { vm.setDoorbellRingtone(d.id, it) },
                    onPreview = { preview(it) },
                    onWriteNfc = { onStartWrite(vm.tagUrl(d)) },
                    onShare = { shareText(context, vm.tagUrl(d)) },
                    onTest = { vm.testRing(d.id) },
                    onRegenerate = { vm.regenerateTopic(d.id) },
                    onDelete = { deleteCandidate = d }
                )
            }

            Button(
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.AddCircle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Agregar un timbre")
            }

            // ---- Recibir en otro teléfono (iPhone / sin la app) ----
            SectionCard(title = "Recibir en iPhone u otro teléfono", icon = Icons.Filled.PhoneIphone) {
                Text(
                    "¿Querés que los avisos también lleguen a un iPhone? Compartí este " +
                        "enlace: te muestra cómo recibirlo con la app gratuita ntfy y " +
                        "cómo configurar el sonido como timbre.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (settings.doorbells.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    settings.doorbells.forEach { d ->
                        OutlinedButton(
                            onClick = { shareText(context, vm.recibirUrl(d)) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.Share, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Compartir receptor de «${d.name}»", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // ---- Que otros creen el suyo ----
            SectionCard(title = "Invitar a crear un timbre (iPhone)", icon = Icons.Filled.AddCircle) {
                Text(
                    "¿Querés que otra persona arme su propio timbre, incluso desde iPhone, " +
                        "sin instalar nada? Compartí esta página: genera uno o varios timbres " +
                        "en el navegador y hasta graba la etiqueta NFC.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { shareText(context, vm.crearUrl()) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Compartir")
                    }
                    OutlinedButton(onClick = { openUrl(context, vm.crearUrl()) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Abrir")
                    }
                }
            }

            // ---- Fiabilidad ----
            SectionCard(title = "Que no se pierda ningún aviso", icon = Icons.Filled.BatteryAlert) {
                Text(
                    "Para que el timbre suene siempre, desactivá la optimización de batería " +
                        "para Upe timbre.",
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
                        "para que el aviso despierte la pantalla.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { activity.openFullScreenIntentSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Permiso pantalla completa") }
            }

            // ---- Actualizaciones (solo sideload) ----
            if (!BuildConfig.PLAY_BUILD)
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
                            Text("Nueva versión: ${st.info.versionName}", fontWeight = FontWeight.Bold)
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
                        is UpdateState.ReadyToInstall -> Text("Abriendo el instalador…")
                        UpdateState.Idle -> {}
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.checkUpdate() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Buscar actualización") }
                }

            Spacer(Modifier.height(8.dp))
        }
    }

    // ---- Diálogo de alta ----
    if (showAdd) {
        AddDoorbellDialog(
            onConfirm = { name ->
                vm.addDoorbell(name)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    // ---- Diálogo de borrado ----
    deleteCandidate?.let { d ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            icon = { Icon(Icons.Filled.DeleteForever, null, Modifier.size(32.dp)) },
            title = { Text("Eliminar «${d.name}»") },
            text = {
                Text(
                    "La etiqueta NFC grabada con este timbre va a dejar de funcionar. " +
                        "¿Querés continuar?"
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteDoorbell(d.id); deleteCandidate = null }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancelar") } }
        )
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
private fun DoorbellCard(
    doorbell: Doorbell,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEnable: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onSelectRingtone: (String) -> Unit,
    onPreview: (Int) -> Unit,
    onWriteNfc: () -> Unit,
    onShare: () -> Unit,
    onTest: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (doorbell.enabled) Icons.Filled.NotificationsActive
                    else Icons.Filled.NotificationsPaused,
                    contentDescription = null,
                    tint = if (doorbell.enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        doorbell.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = { onEnable(!doorbell.enabled) },
                            label = {
                                Text(
                                    if (doorbell.enabled) "Activo" else "Pausado",
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier.height(26.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "· ${Ringtones.labelFor(doorbell.ringtone)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Ocultar" else "Mostrar"
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    var name by remember(doorbell.name) { mutableStateOf(doorbell.name) }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { if (name != doorbell.name) onRename(name) }) {
                            Text("Guardar nombre")
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("Tono", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Ringtones.all.forEach { tone ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = doorbell.ringtone == tone.id,
                                onClick = { onSelectRingtone(tone.id) }
                            )
                            Text(tone.label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onPreview(tone.res) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Escuchar")
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onWriteNfc, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Grabar NFC")
                        }
                        OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Compartir")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = onTest, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Probar sonido")
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onRegenerate, modifier = Modifier.weight(1f)) {
                            Text("Regenerar código", fontSize = 12.sp)
                        }
                        TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                            Text(
                                "Eliminar",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddDoorbellDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.AddCircle, null, Modifier.size(32.dp)) },
        title = { Text("Nuevo timbre") },
        text = {
            Column {
                Text(
                    "Ponele un nombre al lugar que querés avisar " +
                        "(ej: Casa, Trabajo, Auto, Depto 4B).",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.trim()) }) { Text("Crear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
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

@Suppress("unused")
private fun copyToClipboard(context: Context, label: String, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, value))
}
