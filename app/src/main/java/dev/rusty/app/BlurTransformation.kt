package dev.rusty.app

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

/**
 * Coil transformation that reduces an image and blurs it, for use as a background wash.
 *
 * Coil applies transformations on its own dispatcher, so the blur never touches the main thread,
 * and the result is what lands in Coil's memory/disk cache — so re-showing the same cover costs
 * nothing. [cacheKey] therefore has to encode the parameters, or a cached wash from different
 * settings would be reused.
 */
class BlurTransformation(
    private val size: Int = 160,
    private val radius: Int = 16,
) : Transformation {

    override val cacheKey: String = "${javaClass.name}:$size:$radius"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        // Square, matching ArtworkProcessor: centerCrop then has pixels for any aspect ratio.
        val small = Bitmap.createScaledBitmap(input, this.size, this.size, true)
        return ArtworkBlur.blur(small, radius)
    }
}
