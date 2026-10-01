package com.potentiallamp.flipflash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowScriptTest {

    private val cyan = 0xFF00FFFF.toInt()
    private val magenta = 0xFFFF00FF.toInt()
    private val yellow = 0xFFFFFF00.toInt()

    @Test
    fun cmyStopsLandExactly() {
        assertEquals(cyan, ShowScript.cmyColor(0f))
        assertEquals(magenta, ShowScript.cmyColor(1f / 3f))
        assertEquals(yellow, ShowScript.cmyColor(2f / 3f))
        assertEquals(cyan, ShowScript.cmyColor(1f))
    }

    @Test
    fun cmyWrapsNegativeAndLargePhases() {
        assertEquals(ShowScript.cmyColor(0.25f), ShowScript.cmyColor(1.25f))
        assertEquals(ShowScript.cmyColor(0.75f), ShowScript.cmyColor(-0.25f))
    }

    @Test
    fun colorsAreAlwaysOpaqueAndOnTheCmyGamut() {
        // Every color is a blend of two of C/M/Y, so at least one channel is always full.
        for (i in 0..300) {
            val c = ShowScript.cmyColor(i / 300f)
            assertEquals(0xFF, (c ushr 24) and 0xFF)
            val channels = listOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
            assertTrue("color ${Integer.toHexString(c)}", channels.max() == 0xFF)
        }
    }

    @Test
    fun iconAndBackgroundNeverMatch() {
        for (t in 0L..ShowScript.COLOR_LOOP_MS step 37) {
            val f = ShowScript.frameAt(t)
            assertNotEquals(f.backgroundColor, f.iconColor)
            assertNotEquals(f.iconColor, f.orbitColor)
        }
    }

    @Test
    fun iconAdvancesEveryBeatAndLoops() {
        val beat = ShowScript.BEAT_MS
        assertEquals(Icon.CIRCLE, ShowScript.frameAt(0).icon)
        assertEquals(Icon.CIRCLE, ShowScript.frameAt(beat - 1).icon)
        assertEquals(Icon.TRIANGLE, ShowScript.frameAt(beat).icon)
        assertEquals(Icon.CIRCLE, ShowScript.frameAt(beat * Icon.entries.size).icon)
    }

    @Test
    fun pulsePopsOnTheBeatThenSettles() {
        assertEquals(1.25f, ShowScript.pulse(0f), 1e-6f)
        assertEquals(1f, ShowScript.pulse(1f), 1e-6f)
        assertTrue(ShowScript.pulse(0.2f) > ShowScript.pulse(0.6f))
    }

    @Test
    fun anglesStayInRange() {
        for (t in 0L..20_000L step 113) {
            val f = ShowScript.frameAt(t)
            assertTrue(f.rotation in 0f..360f)
            assertTrue(f.orbitAngle in 0f..360f)
        }
    }

    @Test
    fun sameTimeGivesSameFrame() {
        assertEquals(ShowScript.frameAt(1_234), ShowScript.frameAt(1_234))
    }
}
