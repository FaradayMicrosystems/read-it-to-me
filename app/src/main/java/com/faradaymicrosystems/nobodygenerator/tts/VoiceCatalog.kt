package com.faradaymicrosystems.nobodygenerator.tts

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice

data class VoiceOption(
    val name: String,
    val label: String,
    val country: String,
    val offline: Boolean,
    val quality: Int,
    /** Offline neural (Kokoro) voice rather than one of the phone's own. */
    val natural: Boolean = false,
)

/** Lists the phone's English voices, Australian first. */
object VoiceCatalog {
    private val REGION_ORDER = listOf("AU", "NZ", "GB", "IE", "US", "CA", "IN", "ZA")
    private val GOOGLE_CODE = Regex("""-x-([a-z]{3})-(local|network)$""")
    private val REGION_NAMES = mapOf(
        "AU" to "Australian", "NZ" to "New Zealand", "GB" to "British", "IE" to "Irish",
        "US" to "American", "CA" to "Canadian", "IN" to "Indian", "ZA" to "South African",
        "NG" to "Nigerian",
    )

    fun options(voices: Collection<Voice>): List<VoiceOption> = voices
        .filter { it.locale.language == "en" }
        .filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in (it.features ?: emptySet()) }
        .map { v ->
            val offline = !v.isNetworkConnectionRequired
            VoiceOption(v.name, label(v.name, v.locale.country, offline), v.locale.country, offline, v.quality)
        }
        .distinctBy { it.name }
        .sortedWith(
            compareBy<VoiceOption> { REGION_ORDER.indexOf(it.country).let { i -> if (i < 0) 99 else i } }
                .thenBy { !it.offline }
                .thenByDescending { it.quality }
                .thenBy { it.label },
        )

    /** The natural (Kokoro) voices, for when they're installed. */
    fun naturalOptions(): List<VoiceOption> = KokoroVoices.all.map { v ->
        VoiceOption(v.optionName, v.label, if (v.british) "GB" else "US", offline = true, quality = 500, natural = true)
    }

    fun defaultVoice(options: List<VoiceOption>): VoiceOption? =
        options.firstOrNull { it.name == KokoroVoices.default.optionName }
            ?: options.firstOrNull { it.country == "AU" && it.offline }
            ?: options.firstOrNull { it.country == "AU" }
            ?: options.firstOrNull()

    fun hasAustralian(options: List<VoiceOption>) = options.any { it.country == "AU" && !it.natural }

    /** "en-au-x-aub-local" -> "Australian · Voice B · offline". */
    fun label(name: String, country: String, offline: Boolean): String {
        val region = REGION_NAMES[country.uppercase()] ?: country.ifBlank { "English" }
        val code = GOOGLE_CODE.find(name)?.groupValues?.get(1)
        val voice = if (code != null) "Voice ${code.last().uppercaseChar()}" else name
        return "$region · $voice · ${if (offline) "offline" else "online"}"
    }
}
