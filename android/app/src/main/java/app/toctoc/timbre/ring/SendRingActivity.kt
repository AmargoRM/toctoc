package app.toctoc.timbre.ring

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import app.toctoc.timbre.BuildConfig
import app.toctoc.timbre.data.Ntfy
import app.toctoc.timbre.data.Relay
import app.toctoc.timbre.ui.theme.TocTocTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/**
 * Pantalla nativa que se abre cuando el VISITANTE toca la etiqueta y tiene la
 * app instalada (vía deep link toctoc://ring?t=..&s=..&n=..). Publica el timbre
 * en ntfy sin tocar la configuración del dueño: es totalmente sin estado.
 */
class SendRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent?.data
        val topic = data?.getQueryParameter("t").orEmpty()
        val server = data?.getQueryParameter("s")?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_NTFY_SERVER
        val name = data?.getQueryParameter("n")?.takeIf { it.isNotBlank() } ?: "la casa"

        setContent {
            TocTocTheme {
                SendRingScreen(
                    topic = topic,
                    doorbellName = name,
                    onRing = { onDone ->
                        lifecycleScope.launch {
                            // Disparamos AMBAS vías en paralelo (igual que la web):
                            //  - Relay/FCM: llega con la app cerrada / teléfono dormido.
                            //  - ntfy: back-up para versiones sideload que aún escuchan
                            //    por servicio en primer plano.
                            val msg = "Alguien está tocando el timbre de $name"
                            val results = listOf(
                                async { Relay.ring(topic, name) },
                                async { Ntfy.publish(server, topic, msg, name) }
                            ).awaitAll()
                            val relayOk = results[0].isSuccess
                            val ntfyOk = results[1].isSuccess
                            // Enviamos el detalle al UI para poder diagnosticar
                            // por qué el timbre no suena en algún teléfono viejo.
                            val detail = buildString {
                                append(if (relayOk) "FCM ✓" else "FCM ✗: ${results[0].exceptionOrNull()?.message ?: "?"}")
                                append(" | ")
                                append(if (ntfyOk) "ntfy ✓" else "ntfy ✗: ${results[1].exceptionOrNull()?.message ?: "?"}")
                            }
                            onDone(relayOk || ntfyOk, detail)
                        }
                    }
                )
            }
        }
    }
}

private enum class RingUi { Idle, Sending, Ok, Error, Invalid }

@Composable
private fun SendRingScreen(
    topic: String,
    doorbellName: String,
    onRing: ((Boolean, String) -> Unit) -> Unit
) {
    var state by remember {
        mutableStateOf(if (topic.isBlank()) RingUi.Invalid else RingUi.Idle)
    }
    var lastDetail by remember { mutableStateOf("") }

    fun ring() {
        if (topic.isBlank()) { state = RingUi.Invalid; return }
        state = RingUi.Sending
        onRing { ok, detail ->
            lastDetail = detail
            state = if (ok) RingUi.Ok else RingUi.Error
        }
    }

    // Toca automáticamente al abrir desde la etiqueta
    LaunchedEffect(Unit) { if (state == RingUi.Idle) ring() }

    val infinite = rememberInfiniteTransition(label = "bell")
    val swing by infinite.animateFloat(
        initialValue = -14f, targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(350), RepeatMode.Reverse),
        label = "swing"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF3F51B5)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(Color(0x1AFFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🔔",
                    fontSize = 92.sp,
                    modifier = if (state == RingUi.Sending) Modifier.rotate(swing) else Modifier
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Tocar el timbre",
                color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (state) {
                    RingUi.Invalid -> "Enlace inválido: falta el identificador del timbre."
                    RingUi.Ok -> "✅ ¡Listo! Ya avisamos que estás en la puerta."
                    RingUi.Error -> "❌ No se pudo avisar. Revisá tu conexión."
                    RingUi.Sending -> "Tocando…"
                    RingUi.Idle -> "Avisar a $doorbellName que estás en la puerta."
                },
                color = Color(0xE6FFFFFF), fontSize = 16.sp, textAlign = TextAlign.Center
            )
            // Diagnóstico: cuál canal entregó / falló. Ayuda a explicar por qué
            // un teléfono viejo abre esta pantalla pero el dueño no recibe.
            if ((state == RingUi.Ok || state == RingUi.Error) && lastDetail.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    lastDetail,
                    color = Color(0x99FFFFFF),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(36.dp))
            if (state == RingUi.Sending) {
                CircularProgressIndicator(color = Color.White)
            } else if (state != RingUi.Invalid) {
                Button(
                    onClick = { ring() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White, contentColor = Color(0xFF283593)
                    ),
                    shape = RoundedCornerShape(30.dp),
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Text(
                        if (state == RingUi.Ok) "Tocar de nuevo" else "🔔 Tocar timbre",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Enviado con Upe timbre · timbre NFC",
                color = Color(0x99FFFFFF), fontSize = 12.sp
            )
        }
    }
}
