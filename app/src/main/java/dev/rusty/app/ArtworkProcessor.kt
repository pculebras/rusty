package dev.rusty.app

import android.graphics.Bitmap
import androidx.palette.graphics.Palette

/** Result of processing a cover bitmap. */
data class Artwork(val accent: Int, val wash: Bitmap)

/**
 * Turns a decoded cover into (accent color, blurred wash).
 *
 * The wash used to be a 48px copy stretched centerCrop across the screen, which is a ~20x
 * magnification on a 1024px-wide panel: bilinear filtering across that ratio shows its own
 * interpolation as visible blocky structure, so the background read as pixelated rather than
 * blurred. Now the cover is reduced to [WASH_SIZE] and genuinely blurred, so the upscale is
 * both far smaller and applied to an image with no detail left to alias.
 *
 * Takes an **already-generated** [Palette] (produced by Palette's async API on a worker
 * thread) rather than generating one here, and the blur makes this no longer cheap enough for
 * the UI thread — call it from a background dispatcher.
 */
object ArtworkProcessor {
    /**
     * Wash resolution before blurring. Large enough that the upscale to a full screen is mild,
     * small enough that the blur stays cheap: cost is O(pixels), so this is the only real dial.
     */
    private const val WASH_SIZE = 160

    /** ~10% of [WASH_SIZE], i.e. a soft wash rather than a recognisable cover. */
    private const val BLUR_RADIUS = 16

    fun fromPalette(palette: Palette?, cover: Bitmap, fallbackAccent: Int): Artwork {
        val candidates = listOf(
            palette?.vibrantSwatch?.rgb,
            palette?.lightVibrantSwatch?.rgb,
            palette?.lightMutedSwatch?.rgb,
            palette?.darkVibrantSwatch?.rgb,
            palette?.dominantSwatch?.rgb
        )
        val accent = AccentPicker.pick(candidates, fallbackAccent)
        // Square, so the centerCrop across any aspect ratio always has pixels to show.
        val small = Bitmap.createScaledBitmap(cover, WASH_SIZE, WASH_SIZE, true)
        val wash = ArtworkBlur.blur(small, BLUR_RADIUS)
        return Artwork(accent, wash)
    }
}
