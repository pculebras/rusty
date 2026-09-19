package dev.rusty.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
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

    override fun onDraw(canvas: Canvas) {
        if (fraction <= 0f) return
        val h = height.toFloat()
        // Same fully-rounded profile as the track it sits in. Never narrower than one
        // full circle, so the start of a track reads as a dot rather than a sliver.
        val w = (width * fraction).coerceAtLeast(h)
        canvas.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, paint)
    }
}
