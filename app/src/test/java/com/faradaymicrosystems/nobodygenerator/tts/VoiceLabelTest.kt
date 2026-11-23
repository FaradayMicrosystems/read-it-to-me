package com.faradaymicrosystems.nobodygenerator.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceLabelTest {
    @Test fun friendlyLabels() {
        assertEquals("Australian · Voice B · offline", VoiceCatalog.label("en-au-x-aub-local", "AU", true))
        assertEquals("British · Voice D · online", VoiceCatalog.label("en-gb-x-gbd-network", "GB", false))
        assertEquals("American · en-US-language · offline", VoiceCatalog.label("en-US-language", "US", true))
    }
}
