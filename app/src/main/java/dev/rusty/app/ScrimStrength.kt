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
 * Thresholds come from WCAG contrast. For text of relative luminance `Lt` over a background
 * `Lb` (with `Lt > Lb`) the ratio is `(Lt + 0.05) / (Lb + 0.05)`, so a target ratio `R` needs
 * `Lb <= (Lt + 0.05) / R - 0.05`.
 *
 * The text here is NOT white, which matters far more than it looks. The dimmest line —
 * `muted_dim`, the screensaver's status — sits at about 0.31 relative luminance, so 4.5:1
 * demands a background at or below ~0.030, where assuming white text would have allowed 0.183.
 * Solving against white therefore under-scrims every mid-bright cover: white covers still got
 * enough because they pinned the clamp, but a light blue did not.
 *
 * Compositing black at alpha `a` scales background luminance by `(1 - a)`, so the required alpha
 * follows directly.
 */
object ScrimStrength {

    /**
     * Contrast ratio targeted against the dimmest text drawn over the scrim.
     *
     * 3:1 rather than 4.5:1 on purpose. Solving for 4.5:1 against `muted_dim` demands a
     * near-opaque scrim on any bright cover — which defeats the point of showing the artwork at
     * all. The text carries a shadow (see screensaver_canvas.xml) for local contrast, so the
     * scrim only has to get the background into range rather than do the whole job alone.
     */
    private const val TARGET_RATIO = 3.0f

    /**
     * Relative luminance of the dimmest text the scrim has to carry — `muted_dim` (#9B9690),
     * the screensaver's status line. Solving for the *dimmest* text means the brighter lines
     * (`ink` clock, `muted` date) clear the bar comfortably.
     */
    private const val TEXT_LUMINANCE = 0.308f

    /** Never fully clear — a little scrim keeps edges from competing with text. */
    private const val MIN_ALPHA = 0.20f

    /**
     * Never fully opaque, or the artwork stops reading as artwork — which is the whole point
     * of this face.
     */
    private const val MAX_ALPHA = 0.78f

    /** Highest background luminance that still clears [TARGET_RATIO] against [TEXT_LUMINANCE]. */
    private val targetLuminance: Float
        get() = ((TEXT_LUMINANCE + 0.05f) / TARGET_RATIO - 0.05f).coerceAtLeast(0.005f)

    /** Alpha in 0..1 for a black scrim laid over [background]. */
    fun forBackground(background: Bitmap): Float = forLuminance(meanLuminance(background))

    /** Alpha in 0..1 for a black scrim over a background of relative luminance [luminance]. */
    fun forLuminance(luminance: Float): Float {
        if (luminance <= 0f) return MIN_ALPHA
        val needed = 1f - targetLuminance / luminance
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
