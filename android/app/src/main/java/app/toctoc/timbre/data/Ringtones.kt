package app.toctoc.timbre.data

import androidx.annotation.RawRes
import app.toctoc.timbre.R

/** Catálogo de tonos de alerta disponibles. */
object Ringtones {

    data class Tone(val id: String, val label: String, @RawRes val res: Int)

    val all: List<Tone> = listOf(
        Tone("dingdong", "Ding-dong (clásico)", R.raw.doorbell),
        Tone("goat", "Cabra gritona 🐐", R.raw.goat),
        Tone("chime", "Campanitas", R.raw.chime),
        Tone("whip", "Látigo 🥊", R.raw.whip)
    )

    const val DEFAULT_ID = "dingdong"

    @RawRes
    fun resFor(id: String): Int = (all.firstOrNull { it.id == id } ?: all.first()).res

    fun labelFor(id: String): String = (all.firstOrNull { it.id == id } ?: all.first()).label

    fun exists(id: String?): Boolean = id != null && all.any { it.id == id }
}
