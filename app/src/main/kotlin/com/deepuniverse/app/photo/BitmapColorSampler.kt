package com.deepuniverse.app.photo

import android.graphics.Bitmap
import com.deepuniverse.core.color.blue
import com.deepuniverse.core.color.green
import com.deepuniverse.core.color.luminance
import com.deepuniverse.core.color.perceptualDistance
import com.deepuniverse.core.color.red
import com.deepuniverse.core.photo.ColorSamples
import com.deepuniverse.core.photo.FaceLandmarks
import com.deepuniverse.core.photo.FacePoint
import com.deepuniverse.core.photo.Vec2
import com.deepuniverse.core.color.argb as packArgb

/**
 * Reads skin, hair and iris colour out of the photograph.
 *
 * Every sample is a **median** over a patch rather than a single pixel. Photographs are noisy, and
 * one pixel of a stray eyelash, a specular highlight or a JPEG artefact would otherwise decide a
 * player's skin tone. Medians also survive small landmark errors: a patch that slips a few pixels
 * still sits mostly on the intended feature.
 *
 * The colours produced here are raw and carry the photo's white balance; the core pipeline snaps
 * them to the game's palette, which is what keeps warm indoor lighting from producing an orange
 * character.
 */
object BitmapColorSampler {

    fun sample(bitmap: Bitmap, landmarks: FaceLandmarks): ColorSamples {
        val faceHeight = kotlin.math.abs(
            landmarks[FacePoint.CHIN_BOTTOM].y - landmarks[FacePoint.FOREHEAD_TOP].y,
        ).coerceAtLeast(1f)
        val unit = faceHeight * 0.04f // Patch radius: small enough to stay on one feature.

        val skin = sampleSkin(bitmap, landmarks, unit)
        return ColorSamples(
            skin = skin,
            hair = sampleHair(bitmap, landmarks, faceHeight, unit, skin),
            iris = sampleIris(bitmap, landmarks, faceHeight),
        )
    }

    /**
     * Skin is sampled from several places at once — both cheeks, the forehead and the chin — and
     * the median taken across all of them. A single cheek patch would be thrown off by blush,
     * stubble, a scar or the shadow of a fringe; agreeing across four regions will not be.
     */
    private fun sampleSkin(bitmap: Bitmap, landmarks: FaceLandmarks, unit: Float): Int? {
        val noseTip = landmarks[FacePoint.NOSE_TIP]
        val regions = listOf(
            // Mid-cheek: between the cheekbone edge and the nose, level with the nose tip.
            Vec2((landmarks[FacePoint.CHEEK_LEFT].x * 0.55f + noseTip.x * 0.45f), noseTip.y),
            Vec2((landmarks[FacePoint.CHEEK_RIGHT].x * 0.55f + noseTip.x * 0.45f), noseTip.y),
            // Forehead: above the brows, below the hairline.
            Vec2(
                landmarks[FacePoint.NOSE_BRIDGE_TOP].x,
                landmarks[FacePoint.LEFT_BROW_TOP].y -
                    (landmarks[FacePoint.LEFT_BROW_TOP].y - landmarks[FacePoint.FOREHEAD_TOP].y) * 0.35f,
            ),
            // Chin: below the lower lip.
            Vec2(
                landmarks[FacePoint.LIP_LOWER_BOTTOM].x,
                (landmarks[FacePoint.LIP_LOWER_BOTTOM].y + landmarks[FacePoint.CHIN_BOTTOM].y) / 2f,
            ),
        )
        val samples = regions.mapNotNull { patchMedian(bitmap, it, unit) }
        return medianOf(samples)
    }

    /**
     * Hair is sampled from a band above the hairline. It is the least reliable of the three: the
     * band may land on a wall, a hat or a bald scalp, so the result is discarded unless the band
     * looks like hair — meaningfully different from the face and reasonably consistent across the
     * band. Returning null is a perfectly good outcome; the creator then leaves hair colour alone
     * and says so.
     */
    private fun sampleHair(
        bitmap: Bitmap,
        landmarks: FaceLandmarks,
        faceHeight: Float,
        unit: Float,
        skin: Int?,
    ): Int? {
        val top = landmarks[FacePoint.FOREHEAD_TOP]
        val halfWidth = kotlin.math.abs(
            landmarks[FacePoint.CHEEK_RIGHT].x - landmarks[FacePoint.CHEEK_LEFT].x,
        ) / 2f

        val band = buildList {
            for (dx in listOf(-0.45f, -0.2f, 0f, 0.2f, 0.45f)) {
                for (dy in listOf(0.06f, 0.11f, 0.16f)) {
                    val p = Vec2(top.x + halfWidth * dx, top.y - faceHeight * dy)
                    patchMedian(bitmap, p, unit)?.let { add(it) }
                }
            }
        }
        if (band.size < 6) return null

        val median = medianOf(band) ?: return null

        // Too close to the skin tone: most likely forehead or a bald head, not hair.
        if (skin != null && perceptualDistance(median, skin) < 0.01f) return null

        // A band that disagrees with itself is a mix of hair and background. Only trust it if most
        // patches cluster around the median.
        val agreeing = band.count { perceptualDistance(it, median) < 0.06f }
        if (agreeing < band.size / 2) return null

        return median
    }

    /**
     * The iris is sampled from a narrow patch inside the eye opening, discarding the brightest
     * pixels (the catchlight from whatever the person was looking at) and the darkest (the pupil
     * and lash line). What is left is the iris itself.
     */
    private fun sampleIris(bitmap: Bitmap, landmarks: FaceLandmarks, faceHeight: Float): Int? {
        val eyes = listOf(
            eyeCentre(landmarks, left = true),
            eyeCentre(landmarks, left = false),
        )
        val radius = faceHeight * 0.018f
        val candidates = eyes.mapNotNull { irisAt(bitmap, it, radius) }
        return medianOf(candidates)
    }

    private fun eyeCentre(landmarks: FaceLandmarks, left: Boolean): Vec2 {
        val inner = landmarks[if (left) FacePoint.LEFT_EYE_INNER else FacePoint.RIGHT_EYE_INNER]
        val outer = landmarks[if (left) FacePoint.LEFT_EYE_OUTER else FacePoint.RIGHT_EYE_OUTER]
        val top = landmarks[if (left) FacePoint.LEFT_EYE_TOP else FacePoint.RIGHT_EYE_TOP]
        val bottom = landmarks[if (left) FacePoint.LEFT_EYE_BOTTOM else FacePoint.RIGHT_EYE_BOTTOM]
        return Vec2((inner.x + outer.x) / 2f, (top.y + bottom.y) / 2f)
    }

    private fun irisAt(bitmap: Bitmap, centre: Vec2, radius: Float): Int? {
        val pixels = readPatch(bitmap, centre, radius)
        if (pixels.size < 9) return null
        val sorted = pixels.sortedBy { luminance(it) }
        // Drop the darkest 30% (pupil, lashes) and the brightest 25% (sclera, catchlight).
        val from = (sorted.size * 0.30f).toInt()
        val to = (sorted.size * 0.75f).toInt().coerceAtLeast(from + 1)
        val core = sorted.subList(from.coerceAtMost(sorted.lastIndex), to.coerceAtMost(sorted.size))
        return medianOf(core)
    }

    // ------------------------------------------------------------------ pixel helpers

    private fun patchMedian(bitmap: Bitmap, centre: Vec2, radius: Float): Int? =
        medianOf(readPatch(bitmap, centre, radius))

    /** Reads a square patch, clipped to the bitmap, subsampled to at most ~121 pixels. */
    private fun readPatch(bitmap: Bitmap, centre: Vec2, radius: Float): List<Int> {
        val r = radius.toInt().coerceAtLeast(1)
        val left = (centre.x.toInt() - r).coerceIn(0, bitmap.width - 1)
        val right = (centre.x.toInt() + r).coerceIn(0, bitmap.width - 1)
        val top = (centre.y.toInt() - r).coerceIn(0, bitmap.height - 1)
        val bottom = (centre.y.toInt() + r).coerceIn(0, bitmap.height - 1)
        if (right <= left || bottom <= top) return emptyList()

        val step = (((right - left) / 11) + 1)
        val out = ArrayList<Int>()
        var y = top
        while (y <= bottom) {
            var x = left
            while (x <= right) {
                out.add(bitmap.getPixel(x, y))
                x += step
            }
            y += step
        }
        return out
    }

    /** Per-channel median. Robust to outliers in a way that a mean is not. */
    private fun medianOf(colors: List<Int>): Int? {
        if (colors.isEmpty()) return null
        fun median(selector: (Int) -> Int): Int =
            colors.map(selector).sorted()[colors.size / 2]
        return packArgb(
            r = median { it.red },
            g = median { it.green },
            b = median { it.blue },
        )
    }
}
