package com.faradaymicrosystems.nobodygenerator.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KokoroTest {
    // speaker_names from kokoro-multi-lang-v1_0/model.onnx metadata (first 28 = English).
    private val modelSpeakers = (
        "af_alloy,af_aoede,af_bella,af_heart,af_jessica,af_kore,af_nicole,af_nova,af_river,af_sarah,af_sky," +
            "am_adam,am_echo,am_eric,am_fenrir,am_liam,am_michael,am_onyx,am_puck,am_santa," +
            "bf_alice,bf_emma,bf_isabella,bf_lily,bm_daniel,bm_fable,bm_george,bm_lewis"
        ).split(',')

    @Test fun speakerIdsMatchTheModel() {
        for (v in KokoroVoices.all) {
            assertEquals(v.id, modelSpeakers.indexOf(v.id), v.sid)
            assertEquals(v.id, v.id.startsWith("b"), v.british)
        }
        assertEquals(KokoroVoices.all.size, KokoroVoices.all.map { it.sid }.toSet().size)
    }

    @Test fun defaultIsBritishEmma() {
        assertEquals("bf_emma", KokoroVoices.default.id)
        assertTrue(KokoroVoices.all.take(8).all { it.british })
    }

    @Test fun findsVoicesByOptionName() {
        assertEquals(21, KokoroVoices.find("kokoro:bf_emma")?.sid)
        assertNull(KokoroVoices.find("en-au-x-aub-local"))
        assertNull(KokoroVoices.find("kokoro:nope"))
        assertTrue(KokoroVoices.isKokoro("kokoro:nope"))
        assertFalse(KokoroVoices.isKokoro(null))
    }

    @Test fun keepsOnlyEnglishFiles() {
        val p = "kokoro-multi-lang-v1_0/"
        assertEquals("model.onnx", KokoroModel.keepPath(p + "model.onnx"))
        assertEquals("voices.bin", KokoroModel.keepPath(p + "voices.bin"))
        assertEquals("tokens.txt", KokoroModel.keepPath(p + "tokens.txt"))
        assertEquals("lexicon-gb-en.txt", KokoroModel.keepPath(p + "lexicon-gb-en.txt"))
        assertEquals("lexicon-us-en.txt", KokoroModel.keepPath(p + "lexicon-us-en.txt"))
        assertEquals("espeak-ng-data/en_dict", KokoroModel.keepPath(p + "espeak-ng-data/en_dict"))
        assertEquals("espeak-ng-data/voices/", KokoroModel.keepPath(p + "espeak-ng-data/voices/"))
        assertNull(KokoroModel.keepPath(p + "lexicon-zh.txt"))
        assertNull(KokoroModel.keepPath(p + "dict/jieba.dict.utf8"))
        assertNull(KokoroModel.keepPath(p + "date-zh.fst"))
        assertNull(KokoroModel.keepPath(p))
        assertNull(KokoroModel.keepPath(p + "espeak-ng-data/../../evil"))
    }

    @Test fun naturalVoicesAreOfferedAndPreferred() {
        val natural = VoiceCatalog.naturalOptions()
        assertEquals(KokoroVoices.all.size, natural.size)
        assertTrue(natural.all { it.natural && it.offline })
        val phoneAu = VoiceOption("en-au-x-aub-local", "Australian · Voice B · offline", "AU", true, 400)
        assertEquals("kokoro:bf_emma", VoiceCatalog.defaultVoice(natural + phoneAu)?.name)
        assertEquals(phoneAu.name, VoiceCatalog.defaultVoice(listOf(phoneAu))?.name)
        assertFalse(VoiceCatalog.hasAustralian(natural))
    }
}
