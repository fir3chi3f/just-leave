package com.potentiallamp.flipflash

/**
 * Lifecycle of one show. Pure Kotlin (no Android imports) so it can be unit tested on the JVM.
 *
 *   ARMED   --flip open / tap--> PLAYING --press--> HELD --release--> CLOSED
 *
 * The show clock only advances while PLAYING, so a held frame stays frozen.
 */
class ShowController {

    enum class State { ARMED, PLAYING, HELD, CLOSED }

    var state: State = State.ARMED
        private set

    private var accumulatedMs = 0L
    private var playingSinceMs = 0L

    /** The hinge opened (or the app was launched already open). */
    fun onFlipOpened(nowMs: Long) {
        if (state == State.ARMED) startPlaying(nowMs)
    }

    /** A finger went down. */
    fun onPress(nowMs: Long) {
        if (state == State.PLAYING) {
            accumulatedMs = elapsedMs(nowMs)
            state = State.HELD
        }
    }

    /**
     * A finger came up (or the gesture was cancelled). While armed, a tap previews the show
     * without flipping; while held, releasing closes it.
     */
    fun onRelease(nowMs: Long) {
        when (state) {
            State.ARMED -> startPlaying(nowMs)
            State.HELD -> state = State.CLOSED
            State.PLAYING, State.CLOSED -> Unit
        }
    }

    /** Milliseconds of show time played so far, excluding time spent armed or held. */
    fun elapsedMs(nowMs: Long): Long =
        if (state == State.PLAYING) accumulatedMs + (nowMs - playingSinceMs) else accumulatedMs

    private fun startPlaying(nowMs: Long) {
        playingSinceMs = nowMs
        state = State.PLAYING
    }
}
