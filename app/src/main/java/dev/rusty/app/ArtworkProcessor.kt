package dev.rusty.app

import androidx.palette.graphics.Palette

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
    /** Accent colour for the chrome, picked from the cover's palette. */
    fun accentFrom(palette: Palette?, fallbackAccent: Int): Int {
        val candidates = listOf(
            palette?.vibrantSwatch?.rgb,
            palette?.lightVibrantSwatch?.rgb,
            palette?.lightMutedSwatch?.rgb,
            palette?.darkVibrantSwatch?.rgb,
            palette?.dominantSwatch?.rgb
        )
        return AccentPicker.pick(candidates, fallbackAccent)
    }
}
