package dev.rusty.app

import android.graphics.Bitmap

/**
 * A separable box blur, run repeatedly to approximate a Gaussian.
 *
 * `RenderEffect.createBlurEffect` would be the obvious tool, but it is API 31 and this app
 * supports API 26 — the LineageOS devices it targets are Android 11 (API 30), so the platform
 * blur is unavailable on exactly the hardware that needs it. RenderScript is deprecated. Hence
 * doing it here.
 *
 * Three box passes converge closely enough on a Gaussian for a background wash, and each pass is
 * a sliding-window sum, so cost is O(pixels) per pass regardless of [radius] — a large, soft
 * radius is no more expensive than a tight one.
 *
 * Runs on whatever thread calls it and allocates two int arrays; keep it off the main thread.
 */
object ArtworkBlur {

    /** Blurred copy of [src]. [radius] is in pixels of [src], so scale it with the source size. */
    fun blur(src: Bitmap, radius: Int, passes: Int = 3): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0 || radius < 1) return src

        // A radius wider than the image makes the window degenerate; clamp so priming stays sane.
        val r = radius.coerceAtMost(minOf(w, h) / 2).coerceAtLeast(1)
        var front = IntArray(w * h)
        var back = IntArray(w * h)
        src.getPixels(front, 0, w, 0, 0, w, h)

        repeat(passes) {
            horizontal(front, back, w, h, r)
            vertical(back, front, w, h, r)
        }
        return Bitmap.createBitmap(front, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun horizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var a = 0; var red = 0; var green = 0; var blue = 0
            // Prime the window, clamping at the edges so borders do not darken.
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, w - 1)]
                a += (p ushr 24) and 0xFF
                red += (p ushr 16) and 0xFF
                green += (p ushr 8) and 0xFF
                blue += p and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = ((a / div) shl 24) or ((red / div) shl 16) or
                    ((green / div) shl 8) or (blue / div)
                val out = src[row + (x - r).coerceIn(0, w - 1)]
                val into = src[row + (x + r + 1).coerceIn(0, w - 1)]
                a += ((into ushr 24) and 0xFF) - ((out ushr 24) and 0xFF)
                red += ((into ushr 16) and 0xFF) - ((out ushr 16) and 0xFF)
                green += ((into ushr 8) and 0xFF) - ((out ushr 8) and 0xFF)
                blue += (into and 0xFF) - (out and 0xFF)
            }
        }
    }

    private fun vertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var a = 0; var red = 0; var green = 0; var blue = 0
            for (i in -r..r) {
                val p = src[i.coerceIn(0, h - 1) * w + x]
                a += (p ushr 24) and 0xFF
                red += (p ushr 16) and 0xFF
                green += (p ushr 8) and 0xFF
                blue += p and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = ((a / div) shl 24) or ((red / div) shl 16) or
                    ((green / div) shl 8) or (blue / div)
                val out = src[(y - r).coerceIn(0, h - 1) * w + x]
                val into = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                a += ((into ushr 24) and 0xFF) - ((out ushr 24) and 0xFF)
                red += ((into ushr 16) and 0xFF) - ((out ushr 16) and 0xFF)
                green += ((into ushr 8) and 0xFF) - ((out ushr 8) and 0xFF)
                blue += (into and 0xFF) - (out and 0xFF)
            }
        }
    }
}
