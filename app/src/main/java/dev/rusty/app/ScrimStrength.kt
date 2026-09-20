package dev.rusty.app

import android.graphics.Bitmap
import kotlin.math.pow

/**
 * How opaque a legibility scrim has to be for white text to stay readable over a given
 * background image.
 *
 * A fixed scrim cannot do this: the value that rescues a white, sunlit cover crushes a dark one
 * that never needed dimming. Measuring the background instead means a dark cover keeps its
 * colour and a bright one is dimmed by exactly as much as it takes.
 *
 * Thresholds come from WCAG contrast. White has relative luminance 1.0, and the contrast ratio
 * against a background of luminance `L` is `(1.0 + 0.05) / (L + 0.05)`, so:
 *
 *  - 4.5:1 (normal text) needs `L <= 0.183`
 *  - 3:1   (large text)  needs `L <= 0.300`
 *
 * [TARGET_LUMINANCE] takes the stricter of the two, since these faces mix large clocks with
 * small status lines. Compositing black at alpha `a` scales luminance by `(1 - a)`, so the
 * required alpha follows directly.
 */
object ScrimStrength {

    /** Background luminance we aim to sit at or below: 4.5:1 against white text. */
    private const val TARGET_LUMINANCE = 0.183f

    /** Never fully clear — a little scrim keeps edges from competing with text. */
    private const val MIN_ALPHA = 0.20f

    /** Never fully opaque — past this the artwork stops reading as artwork. */
    private const val MAX_ALPHA = 0.80f

    /** Alpha in 0..1 for a black scrim laid over [background]. */
    fun forBackground(background: Bitmap): Float = forLuminance(meanLuminance(background))

    /** Alpha in 0..1 for a black scrim over a background of relative luminance [luminance]. */
    fun forLuminance(luminance: Float): Float {
        if (luminance <= 0f) return MIN_ALPHA
        val needed = 1f - TARGET_LUMINANCE / luminance
        return needed.coerceIn(MIN_ALPHA, MAX_ALPHA)
    }

    /**
     * Mean WCAG relative luminance of [bitmap], sampled on a grid.
     *
     * The mean is the right statistic for an already-blurred wash, which has no local detail
     * left for text to collide with — its brightness is essentially uniform.
     */
    fun meanLuminance(bitmap: Bitmap, samples: Int = 32): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return 0f
        val stepX = (w / samples).coerceAtLeast(1)
        val stepY = (h / samples).coerceAtLeast(1)
        var total = 0.0
        var count = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val p = bitmap.getPixel(x, y)
                total += relativeLuminance(
                    (p ushr 16) and 0xFF,
                    (p ushr 8) and 0xFF,
                    p and 0xFF,
                )
                count++
                x += stepX
            }
            y += stepY
        }
        return if (count == 0) 0f else (total / count).toFloat()
    }

    /** WCAG relative luminance from 8-bit sRGB components. */
    private fun relativeLuminance(r: Int, g: Int, b: Int): Double =
        0.2126 * linearise(r) + 0.7152 * linearise(g) + 0.0722 * linearise(b)

    private fun linearise(component: Int): Double {
        val c = component / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}
