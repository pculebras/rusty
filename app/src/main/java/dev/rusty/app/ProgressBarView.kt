package dev.rusty.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

/**
 * The now-playing progress fill: a pill whose width is [fraction] of the view.
 *
 * The value is expressed at **draw** time rather than through layout, which matters twice
 * over on this screen:
 *
 *  - Setting [fraction] calls `invalidate()` and never `requestLayout()`. The original
 *    implementation resized a child's `layoutParams` once per second, and `TextView`
 *    restarts its marquee on every layout pass, so the title could not scroll.
 *  - The pill is drawn at its true size instead of being scaled, so both end caps keep
 *    their full radius at every value. A `scaleX` transform squashes them — at 20% the
 *    left cap flattens visibly.
 */
class ProgressBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent_fallback)
    }

    /** Portion of the track played, 0f..1f. */
    var fraction: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped != field) {
                field = clamped
                invalidate()
            }
        }

    /** Accent colour of the fill, driven from the album-art palette. */
    var accent: Int
        get() = paint.color
        set(value) {
            if (value != paint.color) {
                paint.color = value
                invalidate()
            }
        }

    /** Previewed fraction while a finger is down. Set to drive a scrub readout. */
    var onScrub: ((Float) -> Unit)? = null

    /**
     * Final fraction on release. Setting this enables touch scrubbing; while it is null the
     * view consumes no touches, so the bar stays inert where seeking is not wanted.
     */
    var onSeek: ((Float) -> Unit)? = null

    /** True between touch-down and release, so callers can stop driving [fraction]. */
    var isScrubbing = false
        private set

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val commit = onSeek ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isScrubbing = true
                // Claim the gesture so an ancestor cannot steal it mid-drag.
                parent?.requestDisallowInterceptTouchEvent(true)
                preview(event.x)
            }
            MotionEvent.ACTION_MOVE -> if (isScrubbing) preview(event.x) else return false
            MotionEvent.ACTION_UP -> {
                if (!isScrubbing) return false
                preview(event.x)
                isScrubbing = false
                commit(fraction)
            }
            MotionEvent.ACTION_CANCEL -> isScrubbing = false
            else -> return false
        }
        return true
    }

    private fun preview(x: Float) {
        if (width <= 0) return
        fraction = x / width
        onScrub?.invoke(fraction)
    }

    override fun onDraw(canvas: Canvas) {
        if (fraction <= 0f) return
        val h = height.toFloat()
        // Same fully-rounded profile as the track it sits in. Never narrower than one
        // full circle, so the start of a track reads as a dot rather than a sliver.
        val w = (width * fraction).coerceAtLeast(h)
        canvas.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, paint)
    }
}
