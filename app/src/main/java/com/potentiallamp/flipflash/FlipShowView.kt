package com.potentiallamp.flipflash

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the show and turns touches into [ShowController] events. Redraws every vsync while
 * playing; draws a single static frame while armed-idle or held.
 */
class FlipShowView(
    context: Context,
    private val controller: ShowController,
    private val onClose: () -> Unit,
) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val path = Path()

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
        text.textSize = base * 0.07f

        when (controller.state) {
            ShowController.State.ARMED -> drawArmed(canvas, now, w, h, base)
            ShowController.State.PLAYING, ShowController.State.HELD -> {
                drawFrame(canvas, ShowScript.frameAt(controller.elapsedMs(now)), w, h, base)
                if (controller.state == ShowController.State.HELD) {
                    text.alpha = 200
                    canvas.drawText(context.getString(R.string.held_hint), w / 2f, h - base * 0.08f, text)
                }
            }
            ShowController.State.CLOSED -> canvas.drawColor(Color.BLACK)
        }

        if (controller.state == ShowController.State.PLAYING ||
            controller.state == ShowController.State.ARMED
        ) {
            postInvalidateOnAnimation()
        }
    }

    /** Cover-screen idle: a slowly breathing CMY disc and a hint. */
    private fun drawArmed(canvas: Canvas, now: Long, w: Float, h: Float, base: Float) {
        canvas.drawColor(Color.BLACK)
        val breath = 0.85f + 0.15f * sin(now / 600.0).toFloat()
        fill.color = ShowScript.cmyColor(now / 6_000f)
        fill.alpha = 230
        canvas.drawCircle(w / 2f, h / 2f, base * 0.18f * breath, fill)
        fill.alpha = 255
        text.alpha = 220
        canvas.drawText(context.getString(R.string.armed_hint), w / 2f, h / 2f + base * 0.34f, text)
    }

    private fun drawFrame(canvas: Canvas, frame: Frame, w: Float, h: Float, base: Float) {
        canvas.drawColor(frame.backgroundColor)
        val cx = w / 2f
        val cy = h / 2f

        // Ring of small icons orbiting the centre.
        val orbitRadius = base * 0.36f
        val ringCount = 6
        fill.color = frame.orbitColor
        for (i in 0 until ringCount) {
            val angle = Math.toRadians(frame.orbitAngle + i * 360.0 / ringCount)
            val x = cx + (orbitRadius * cos(angle)).toFloat()
            val y = cy + (orbitRadius * sin(angle)).toFloat()
            drawIcon(canvas, frame.icon, x, y, base * 0.05f, -frame.rotation)
        }

        // The star of the show.
        fill.color = frame.iconColor
        drawIcon(canvas, frame.icon, cx, cy, base * 0.2f * frame.iconScale, frame.rotation)
    }

    private fun drawIcon(canvas: Canvas, icon: Icon, cx: Float, cy: Float, r: Float, rotation: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotation)
        path.reset()
        when (icon) {
            Icon.CIRCLE -> path.addCircle(0f, 0f, r, Path.Direction.CW)
            Icon.TRIANGLE -> polygon(3, r, r)
            Icon.DIAMOND -> polygon(4, r, r)
            Icon.STAR -> polygon(5, r, r * 0.45f)
            Icon.HEART -> heart(r)
        }
        canvas.drawPath(path, fill)
        canvas.restore()
    }

    /**
     * Regular polygon with [points] outer vertices; an [inner] radius smaller than [outer]
     * adds a vertex between each pair, making a star.
     */
    private fun polygon(points: Int, outer: Float, inner: Float) {
        val star = inner < outer
        val steps = if (star) points * 2 else points
        for (i in 0 until steps) {
            val radius = if (star && i % 2 == 1) inner else outer
            val angle = -PI / 2 + i * 2 * PI / steps
            val x = (radius * cos(angle)).toFloat()
            val y = (radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private fun heart(r: Float) {
        path.moveTo(0f, r * 0.9f)
        path.cubicTo(-r * 1.4f, -r * 0.1f, -r * 0.6f, -r * 1.2f, 0f, -r * 0.45f)
        path.cubicTo(r * 0.6f, -r * 1.2f, r * 1.4f, -r * 0.1f, 0f, r * 0.9f)
        path.close()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val now = SystemClock.uptimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val wasPlaying = controller.state == ShowController.State.PLAYING
                controller.onPress(now)
                if (wasPlaying) performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                controller.onRelease(now)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                if (controller.state == ShowController.State.CLOSED) onClose()
            }
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
