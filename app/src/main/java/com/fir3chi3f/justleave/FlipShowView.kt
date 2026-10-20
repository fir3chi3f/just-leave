package com.fir3chi3f.justleave

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the screens and turns touches into [ShowController] events. Redraws every vsync while
 * playing; otherwise redraws only on touch.
 *
 * Once open, the screen splits in two: the intro flash then the spinning line on top (or the
 * checkmark while held), and a control
 * panel below with the big asterisk button, three small buttons arcing around its left side,
 * and an on/off toggle in the upper left. Only the asterisk button drives the show; the small
 * buttons and toggle respond to touch but don't do anything yet.
 *
 * Sounds: the loading clip plays while the line sweeps the oval, and the complete clip plays
 * when the asterisk button is pressed.
 */
class FlipShowView(
    context: Context,
    private val controller: ShowController,
    private val onClose: () -> Unit,
) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val appIcon = checkNotNull(context.getDrawable(R.mipmap.ic_launcher))
    private val pixel = Paint().apply { style = Paint.Style.FILL }
    private val path = Path()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // Control panel layout, rebuilt in onSizeChanged.
    private val mainButton = RectF()
    private val smallButtons = List(SMALL_BUTTON_COUNT) { RectF() }
    private val toggleTrack = RectF()
    private val toggleHit = RectF()
    private val whiteSquare = RectF()

    // Control panel touch state, keyed by pointer id.
    private var mainPointer = NO_POINTER
    private val smallPointers = IntArray(SMALL_BUTTON_COUNT) { NO_POINTER }
    /** Every launch starts with the toggle off. */
    private var toggleOn = false

    // SoundPool rather than MediaPlayer: it starts short clips with little delay, keeping the
    // loading clip in step with the sweep.
    private val sounds = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val loadingSound = sounds.load(context, R.raw.loading, 1)
    private val completeSound = sounds.load(context, R.raw.complete, 1)
    private var loadingStream = 0
    private var loadingStarted = false

    init {
        isClickable = true
        isHapticFeedbackEnabled = true
        contentDescription = context.getString(R.string.app_name)
        keepScreenOn = true
    }

    /** Call when the controller changes state from outside a touch (e.g. the flip opened). */
    fun refresh() = postInvalidateOnAnimation()

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val w = width.toFloat()
        val h = height.toFloat()
        val base = min(w, h)

        val top = h / 2f
        val topBase = min(w, top)
        when (controller.state) {
            ShowController.State.ARMED -> drawArmed(canvas, w, h, base)
            ShowController.State.PLAYING -> {
                canvas.drawColor(Color.BLACK)
                val elapsed = controller.elapsedMs(now)
                if (elapsed < INTRO_MS) {
                    drawIntro(canvas, elapsed.toFloat() / INTRO_MS, w, top)
                } else {
                    if (!loadingStarted) {
                        loadingStarted = true
                        loadingStream = sounds.play(loadingSound, 1f, 1f, 1, 0, 1f)
                    }
                    drawSweep(canvas, elapsed - INTRO_MS, w, top, topBase)
                }
                drawPanel(canvas, w, h)
            }
            ShowController.State.HELD -> {
                inTopHalf(canvas, w, top) { drawCheckmark(canvas, w, top, topBase) }
                drawPanel(canvas, w, h)
            }
            ShowController.State.CLOSED -> canvas.drawColor(Color.BLACK)
        }

        if (controller.state == ShowController.State.PLAYING) postInvalidateOnAnimation()
    }

    /** Cover-screen idle: the app icon, centred on black. */
    private fun drawArmed(canvas: Canvas, w: Float, h: Float, base: Float) {
        canvas.drawColor(Color.BLACK)
        val half = (base * 0.25f).toInt()
        val cx = (w / 2f).toInt()
        val cy = (h / 2f).toInt()
        appIcon.setBounds(cx - half, cy - half, cx + half, cy + half)
        appIcon.draw(canvas)
    }

    /**
     * Opening flash in the top half: a white bar over a wider loading bar, outlined in white on
     * every side but the right, that fills white from the left as [progress] goes from 0 to 1.
     */
    private fun drawIntro(canvas: Canvas, progress: Float, w: Float, top: Float) {
        val cx = w / 2f
        val barW = w / 3f
        val barH = top * 0.1f
        val boxW = w / 2f
        val boxH = top * 0.2f
        val gap = top * 0.05f
        val barTop = (top - barH - gap - boxH) / 2f
        val boxTop = barTop + barH + gap

        fill.color = Color.WHITE
        canvas.drawRect(cx - barW / 2f, barTop, cx + barW / 2f, barTop + barH, fill)

        val left = cx - boxW / 2f
        val right = cx + boxW / 2f
        val bottom = boxTop + boxH
        fill.color = Color.BLACK
        canvas.drawRect(left, boxTop, right, bottom, fill)
        fill.color = Color.WHITE
        canvas.drawRect(left, boxTop, left + boxW * progress, bottom, fill)
        stroke.color = Color.WHITE
        stroke.strokeWidth = w * 0.015f
        stroke.strokeCap = Paint.Cap.SQUARE
        path.reset()
        path.moveTo(right, boxTop)
        path.lineTo(left, boxTop)
        path.lineTo(left, bottom)
        path.lineTo(right, bottom)
        canvas.drawPath(path, stroke)
        stroke.strokeCap = Paint.Cap.ROUND
    }

    /**
     * After the intro: on the left, a white line spins clockwise in place around its midpoint,
     * starting vertical, and fills in a light red oval behind it like a pie. The oval is a
     * little over half the screen wide and its edge sits 4/5 of the way out along each half of
     * the line. Once the oval is complete the line stops, back at vertical. On the right, a
     * light red panel with a black pixel asterisk at the bottom, spinning clockwise.
     */
    private fun drawSweep(canvas: Canvas, ms: Long, w: Float, top: Float, base: Float) {
        val panelLeft = w * 0.66f
        fill.color = LIGHT_RED
        canvas.drawRect(panelLeft, 0f, w, top, fill)
        val panelW = w - panelLeft
        val starW = panelW * 0.5f
        val starX = panelLeft + panelW / 2f
        val starY = top - top * 0.06f - starW / 2f
        canvas.save()
        canvas.rotate((ms % STAR_SPIN_MS) * 360f / STAR_SPIN_MS, starX, starY)
        pixel.color = Color.BLACK
        drawPixelArt(canvas, ASTERISK, starX, starY, starW)
        canvas.restore()

        val cx = w * 0.35f
        val cy = top / 2f
        val ry = base * 0.32f
        val rx = w * 0.275f
        val sweep = min(ms * 360f / SWEEP_MS, 360f)

        canvas.drawArc(cx - rx, cy - ry, cx + rx, cy + ry, -90f, sweep, true, fill)

        // Canvas angles grow clockwise; -90° is 12 o'clock. The line is squished with the oval
        // so its ends always stick out a fifth past the edge.
        val angle = Math.toRadians((sweep - 90f).toDouble())
        val dx = (rx / 0.8f * cos(angle)).toFloat()
        val dy = (ry / 0.8f * sin(angle)).toFloat()
        stroke.color = Color.WHITE
        stroke.strokeWidth = base * 0.015f
        canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, stroke)
    }

    private inline fun inTopHalf(canvas: Canvas, w: Float, top: Float, draw: () -> Unit) {
        canvas.save()
        canvas.clipRect(0f, 0f, w, top)
        draw()
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sounds.release()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutPanel(w.toFloat(), h.toFloat())
    }

    /**
     * Lower half: the main button sits right of centre for the right thumb, the small buttons
     * arc around its left side, and the toggle sits in the upper left.
     */
    private fun layoutPanel(w: Float, h: Float) {
        val top = h / 2f
        val panelH = h - top
        val base = min(w, panelH)

        val side = base * 0.36f
        val cx = w * 0.64f
        val cy = top + panelH * 0.6f
        mainButton.set(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)

        val small = side * 0.42f
        val arc = side / 2f + small * 0.5f + base * 0.07f
        for ((i, rect) in smallButtons.withIndex()) {
            // Fan around the left side of the main button: 225°, 180°, 135°.
            val angle = Math.toRadians(225.0 - i * 45.0)
            val x = cx + (arc * cos(angle)).toFloat()
            val y = cy - (arc * sin(angle)).toFloat()
            rect.set(x - small / 2f, y - small / 2f, x + small / 2f, y + small / 2f)
        }

        val toggleY = top + panelH * 0.15f
        val toggleH = base * 0.16f
        val toggleLeft = w * 0.08f
        toggleTrack.set(toggleLeft, toggleY - toggleH / 2f, toggleLeft + toggleH * 2.2f, toggleY + toggleH / 2f)
        val grab = base * 0.04f
        toggleHit.set(toggleTrack.left - grab, toggleTrack.top - grab, toggleTrack.right + grab, toggleTrack.bottom + grab)

        // A bit smaller than the small buttons, left-aligned under the toggle.
        val square = small * 0.75f
        val squareTop = toggleTrack.bottom + base * 0.05f
        whiteSquare.set(toggleLeft, squareTop, toggleLeft + square, squareTop + square)
    }

    private fun drawPanel(canvas: Canvas, w: Float, h: Float) {
        fill.color = PANEL
        canvas.drawRect(0f, h / 2f, w, h, fill)

        val squareR = whiteSquare.width() * CORNER
        fill.color = Color.WHITE
        canvas.drawRoundRect(whiteSquare, squareR, squareR, fill)

        // Toggle: pill track with an outlined pill knob, left when off and right when on.
        val trackR = toggleTrack.height() / 2f
        fill.color = if (toggleOn) HIGHLIGHTER else CONTROL
        canvas.drawRoundRect(toggleTrack, trackR, trackR, fill)
        val inset = toggleTrack.height() * 0.16f
        val knobW = toggleTrack.width() * 0.5f
        val knobLeft = if (toggleOn) toggleTrack.right - inset - knobW else toggleTrack.left + inset
        val knobR = trackR - inset
        stroke.color = if (toggleOn) Color.BLACK else Color.WHITE
        stroke.strokeWidth = toggleTrack.height() * 0.07f
        canvas.drawRoundRect(
            knobLeft, toggleTrack.top + inset, knobLeft + knobW, toggleTrack.bottom - inset,
            knobR, knobR, stroke,
        )

        // One border width for every button, so the small ones match the main one.
        val border = mainButton.width() * 0.05f
        for ((i, rect) in smallButtons.withIndex()) {
            val color = if (smallPointers[i] != NO_POINTER) CONTROL_PRESSED else CONTROL
            drawButton(canvas, rect, color, border)
        }

        val held = mainPointer != NO_POINTER
        drawButton(canvas, mainButton, if (held) HIGHLIGHTER else MAGENTA, border)
        pixel.color = Color.BLACK
        drawPixelArt(canvas, ASTERISK, mainButton.centerX(), mainButton.centerY(), mainButton.width() * 0.55f)
    }

    /** A rounded square filled with [color] and a white [border] inset so it stays inside. */
    private fun drawButton(canvas: Canvas, rect: RectF, color: Int, border: Float) {
        val r = rect.width() * CORNER
        fill.color = color
        canvas.drawRoundRect(rect, r, r, fill)
        stroke.color = Color.WHITE
        stroke.strokeWidth = border
        canvas.drawRoundRect(
            rect.left + border / 2f, rect.top + border / 2f,
            rect.right - border / 2f, rect.bottom - border / 2f,
            r - border / 2f, r - border / 2f, stroke,
        )
    }

    /**
     * Held: a thin black pixel-art checkmark on highlighter yellow-green, framed by plus signs
     * in the corners.
     */
    private fun drawCheckmark(canvas: Canvas, w: Float, h: Float, base: Float) {
        canvas.drawColor(HIGHLIGHTER)
        pixel.color = Color.BLACK
        drawPixelArt(canvas, CHECKMARK, w / 2f, h / 2f, base * 0.6f)

        // Thin plus signs inset from each corner.
        val arm = base * 0.03f
        val inset = base * 0.06f + arm
        stroke.color = Color.BLACK
        stroke.strokeWidth = base * 0.006f
        stroke.strokeCap = Paint.Cap.BUTT
        for (x in floatArrayOf(inset, w - inset)) {
            for (y in floatArrayOf(inset, h - inset)) {
                canvas.drawLine(x - arm, y, x + arm, y, stroke)
                canvas.drawLine(x, y - arm, x, y + arm, stroke)
            }
        }
        stroke.strokeCap = Paint.Cap.ROUND
    }

    /** Draws [art] ('#' = a [pixel]) centred on ([cx], [cy]), about [width] wide. */
    private fun drawPixelArt(canvas: Canvas, art: List<String>, cx: Float, cy: Float, width: Float) {
        val cols = art[0].length
        val rows = art.size
        // Whole-pixel cells keep the edges crisp.
        val cell = (width / cols).toInt().coerceAtLeast(1).toFloat()
        val left = (cx - cols * cell / 2f).toInt().toFloat()
        val top = (cy - rows * cell / 2f).toInt().toFloat()
        for ((row, line) in art.withIndex()) {
            for ((col, c) in line.withIndex()) {
                if (c != '#') continue
                val x = left + col * cell
                val y = top + row * cell
                canvas.drawRect(x, y, x + cell, y + cell, pixel)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val now = SystemClock.uptimeMillis()
        if (controller.state == ShowController.State.ARMED) {
            // Cover screen: a tap anywhere previews the show.
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> controller.onPress(now)
                MotionEvent.ACTION_UP -> {
                    controller.onRelease(now)
                    performClick()
                }
            }
            invalidate()
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                pointerDown(event.getPointerId(i), event.getX(i), event.getY(i), now)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                pointerUp(event.getPointerId(event.actionIndex), now)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                val main = mainPointer
                smallPointers.fill(NO_POINTER)
                if (main != NO_POINTER) pointerUp(main, now)
            }
        }
        invalidate()
        return true
    }

    private fun pointerDown(id: Int, x: Float, y: Float, now: Long) {
        when {
            mainPointer == NO_POINTER && mainButton.contains(x, y) -> {
                mainPointer = id
                val wasPlaying = controller.state == ShowController.State.PLAYING
                controller.onPress(now)
                if (wasPlaying) {
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    if (loadingStream != 0) sounds.stop(loadingStream)
                    sounds.play(completeSound, 1f, 1f, 1, 0, 1f)
                }
            }
            toggleHit.contains(x, y) -> {
                toggleOn = !toggleOn
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            else -> {
                val hit = smallButtons.indexOfFirst { it.contains(x, y) }
                if (hit >= 0 && smallPointers[hit] == NO_POINTER) {
                    smallPointers[hit] = id
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
        }
    }

    private fun pointerUp(id: Int, now: Long) {
        if (id == mainPointer) {
            mainPointer = NO_POINTER
            controller.onRelease(now)
            if (controller.state == ShowController.State.CLOSED) onClose()
        }
        for (i in smallPointers.indices) if (smallPointers[i] == id) smallPointers[i] = NO_POINTER
    }

    override fun performClick(): Boolean = super.performClick()

    private companion object {
        /** Highlighter yellow-green. */
        val HIGHLIGHTER = Color.rgb(0xCC, 0xFF, 0x00)

        val PANEL = Color.rgb(0x14, 0x14, 0x14)
        val CONTROL = Color.rgb(0x3A, 0x3A, 0x3A)
        val MAGENTA = Color.rgb(0xFF, 0x5C, 0xFF)
        val LIGHT_RED = Color.rgb(0xFF, 0x5C, 0x5C)
        val CONTROL_PRESSED = Color.rgb(0x6A, 0x6A, 0x6A)

        /** Corner radius as a fraction of a button's side. */
        const val CORNER = 0.22f

        /** How long the opening flash shows in the top half. */
        const val INTRO_MS = 750L

        /** Time for the line to make one full turn: the length of res/raw/loading.wav. */
        const val SWEEP_MS = 1_743L

        /** Time for the asterisk in the red panel to spin once. */
        const val STAR_SPIN_MS = 2_000L
        const val SMALL_BUTTON_COUNT = 3
        const val NO_POINTER = -1

        /** Pixel-art checkmark; '#' is a black pixel. */
        val CHECKMARK = listOf(
            "...........#",
            "..........#.",
            ".........#..",
            "........#...",
            "#......#....",
            ".#....#.....",
            "..#..#......",
            "...##.......",
        )

        /** Pixel-art eight-armed asterisk; '#' is a black pixel. */
        val ASTERISK = listOf(
            "....#....",
            ".#..#..#.",
            "..#.#.#..",
            "...###...",
            "#########",
            "...###...",
            "..#.#.#..",
            ".#..#..#.",
            "....#....",
        )
    }
}
