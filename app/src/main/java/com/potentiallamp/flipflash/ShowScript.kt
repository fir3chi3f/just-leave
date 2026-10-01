package com.potentiallamp.flipflash

import kotlin.math.floor

/** The shapes the show cycles through. Drawn by [FlipShowView]. */
enum class Icon { CIRCLE, TRIANGLE, DIAMOND, STAR, HEART }

/** Everything needed to draw one frame. Colors are opaque ARGB ints. */
data class Frame(
    val backgroundColor: Int,
    val iconColor: Int,
    val orbitColor: Int,
    val icon: Icon,
    /** Multiplier on the base icon size; pulses on every beat. */
    val iconScale: Float,
    /** Degrees. */
    val rotation: Float,
    /** Degrees; position of the orbiting ring of small icons. */
    val orbitAngle: Float,
)

/**
 * The animation script: maps show time to a [Frame]. Pure Kotlin so it's deterministic and
 * testable; tweak the constants here to change the feel of the show.
 */
object ShowScript {

    /** Stops on the CMY wheel, visited in order and looped. */
    private val CMY_STOPS = intArrayOf(
        0xFF00FFFF.toInt(), // cyan
        0xFFFF00FF.toInt(), // magenta
        0xFFFFFF00.toInt(), // yellow
    )

    /** Time for the background to travel the full C → M → Y → C loop. */
    const val COLOR_LOOP_MS = 3_000L

    /** A new icon appears every beat. */
    const val BEAT_MS = 500L

    /** Degrees per second the main icon spins. */
    private const val SPIN_DEG_PER_S = 90f

    /** Degrees per second the orbit ring travels (opposite direction to the spin). */
    private const val ORBIT_DEG_PER_S = -45f

    fun frameAt(elapsedMs: Long): Frame {
        val loopPhase = (elapsedMs % COLOR_LOOP_MS).toFloat() / COLOR_LOOP_MS
        val beat = elapsedMs / BEAT_MS
        val beatPhase = (elapsedMs % BEAT_MS).toFloat() / BEAT_MS
        val seconds = elapsedMs / 1000f

        return Frame(
            backgroundColor = cmyColor(loopPhase),
            // Offset by a third of the wheel so the icon is always on a different ink.
            iconColor = cmyColor(loopPhase + 1f / 3f),
            orbitColor = cmyColor(loopPhase + 2f / 3f),
            icon = Icon.entries[(beat % Icon.entries.size).toInt()],
            iconScale = pulse(beatPhase),
            rotation = (seconds * SPIN_DEG_PER_S) % 360f,
            orbitAngle = ((seconds * ORBIT_DEG_PER_S) % 360f + 360f) % 360f,
        )
    }

    /**
     * Color at [phase] around the C → M → Y → C loop (any float; wraps). Phase 0, 1/3 and 2/3
     * land exactly on cyan, magenta and yellow.
     */
    fun cmyColor(phase: Float): Int {
        val wrapped = phase - floor(phase)
        val scaled = wrapped * CMY_STOPS.size
        val index = scaled.toInt().coerceIn(0, CMY_STOPS.size - 1)
        val t = scaled - index
        return lerpColor(CMY_STOPS[index], CMY_STOPS[(index + 1) % CMY_STOPS.size], t)
    }

    /** Pops to 1.25× at the start of a beat and eases back to 1×. */
    internal fun pulse(beatPhase: Float): Float {
        val decay = 1f - beatPhase
        return 1f + 0.25f * decay * decay
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t + 0.5f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
