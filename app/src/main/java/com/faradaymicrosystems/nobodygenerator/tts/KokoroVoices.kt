package com.faradaymicrosystems.nobodygenerator.tts

/** One Kokoro v1.0 speaker. [sid] is the speaker index inside kokoro-multi-lang-v1_0. */
data class KokoroVoice(val id: String, val sid: Int, val displayName: String, val british: Boolean) {
    val optionName: String get() = PREFIX + id
    val label: String get() = "Natural · ${if (british) "British" else "American"} · $displayName"

    companion object {
        const val PREFIX = "kokoro:"
    }
}

/**
 * The English Kokoro voices we offer, best first within each accent. Kokoro has no
 * Australian voices; British is the closest, so it comes first and Emma is the default.
 */
object KokoroVoices {
    val all: List<KokoroVoice> = listOf(
        KokoroVoice("bf_emma", 21, "Emma", british = true),
        KokoroVoice("bm_george", 26, "George", british = true),
        KokoroVoice("bf_isabella", 22, "Isabella", british = true),
        KokoroVoice("bm_fable", 25, "Fable", british = true),
        KokoroVoice("bf_alice", 20, "Alice", british = true),
        KokoroVoice("bm_lewis", 27, "Lewis", british = true),
        KokoroVoice("bf_lily", 23, "Lily", british = true),
        KokoroVoice("bm_daniel", 24, "Daniel", british = true),
        KokoroVoice("af_heart", 3, "Heart", british = false),
        KokoroVoice("af_bella", 2, "Bella", british = false),
        KokoroVoice("af_nicole", 6, "Nicole", british = false),
        KokoroVoice("am_michael", 16, "Michael", british = false),
        KokoroVoice("am_fenrir", 14, "Fenrir", british = false),
        KokoroVoice("am_puck", 18, "Puck", british = false),
    )

    val default: KokoroVoice get() = all.first()

    fun isKokoro(optionName: String?) = optionName?.startsWith(KokoroVoice.PREFIX) == true

    fun find(optionName: String?): KokoroVoice? =
        if (isKokoro(optionName)) all.firstOrNull { it.optionName == optionName } else null
}
