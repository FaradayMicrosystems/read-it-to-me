package com.faradaymicrosystems.nobodygenerator.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedStepTest {
    private val create = 0.5f..2.0f
    private val playback = 0.5f..3.0f

    @Test fun snapsToNearestFiveHundredth() {
        assertEquals(1.05f, snapSpeed(1.0500001f, create), 1e-4f)
        assertEquals(1.25f, snapSpeed(1.26f, create), 1e-4f)
        assertEquals(1.30f, snapSpeed(1.28f, create), 1e-4f)
    }

    @Test fun nudgesByExactlyOneStep() {
        var v = 1.0f
        repeat(5) { v = snapSpeed(v + SPEED_STEP, playback) }
        assertEquals(1.25f, v, 1e-4f)
        repeat(3) { v = snapSpeed(v - SPEED_STEP, playback) }
        assertEquals(1.10f, v, 1e-4f)
    }

    @Test fun staysInRange() {
        assertEquals(0.5f, snapSpeed(0.3f, create), 1e-4f)
        assertEquals(2.0f, snapSpeed(2.2f, create), 1e-4f)
        assertEquals(3.0f, snapSpeed(3.04f, playback), 1e-4f)
    }
}
