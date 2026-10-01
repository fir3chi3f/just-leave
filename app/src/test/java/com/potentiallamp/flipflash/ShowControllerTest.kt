package com.potentiallamp.flipflash

import com.potentiallamp.flipflash.ShowController.State
import org.junit.Assert.assertEquals
import org.junit.Test

class ShowControllerTest {

    @Test
    fun startsArmedWithClockStopped() {
        val c = ShowController()
        assertEquals(State.ARMED, c.state)
        assertEquals(0L, c.elapsedMs(10_000))
    }

    @Test
    fun flipOpenStartsPlayingFromZero() {
        val c = ShowController()
        c.onFlipOpened(1_000)
        assertEquals(State.PLAYING, c.state)
        assertEquals(250L, c.elapsedMs(1_250))
    }

    @Test
    fun pressWhileArmedDoesNothingButTapPreviews() {
        val c = ShowController()
        c.onPress(100)
        assertEquals(State.ARMED, c.state)
        c.onRelease(200)
        assertEquals(State.PLAYING, c.state)
        assertEquals(50L, c.elapsedMs(250))
    }

    @Test
    fun holdFreezesTheClockAndReleaseCloses() {
        val c = ShowController()
        c.onFlipOpened(0)
        c.onPress(700)
        assertEquals(State.HELD, c.state)
        assertEquals(700L, c.elapsedMs(5_000))
        c.onRelease(5_000)
        assertEquals(State.CLOSED, c.state)
        assertEquals(700L, c.elapsedMs(9_000))
    }

    @Test
    fun repeatedFlipEventsDoNotRestartTheShow() {
        val c = ShowController()
        c.onFlipOpened(0)
        c.onFlipOpened(400)
        assertEquals(500L, c.elapsedMs(500))
    }

    @Test
    fun eventsAfterCloseAreIgnored() {
        val c = ShowController()
        c.onFlipOpened(0)
        c.onPress(10)
        c.onRelease(20)
        c.onFlipOpened(30)
        c.onPress(40)
        c.onRelease(50)
        assertEquals(State.CLOSED, c.state)
    }
}
