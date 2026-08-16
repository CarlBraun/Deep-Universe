package com.deepuniverse.core.photo

import kotlin.math.hypot

/** A point in image space. `y` grows downwards, as it does in every Android image API. */
data class Vec2(val x: Float, val y: Float) {
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun times(k: Float) = Vec2(x * k, y * k)
    fun distanceTo(other: Vec2): Float = hypot(x - other.x, y - other.y)

    companion object {
        fun midpoint(a: Vec2, b: Vec2) = Vec2((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    }
}

/**
 * The anatomical points the character generator needs from a face.
 *
 * This is deliberately a small, named set rather than a raw mesh: the Android side maps ML Kit's
 * 468-point face mesh onto these names in one lookup table, and every piece of reasoning about the
 * face then happens in this module, where it can be unit-tested on the JVM without a device or a
 * camera.
 *
 * LEFT and RIGHT are from the *viewer's* perspective — left means smaller `x` in the image.
 */
enum class FacePoint {
    FOREHEAD_TOP,
    CHIN_BOTTOM,
    CHEEK_LEFT,
    CHEEK_RIGHT,
    JAW_LEFT,
    JAW_RIGHT,

    LEFT_EYE_INNER,
    LEFT_EYE_OUTER,
    LEFT_EYE_TOP,
    LEFT_EYE_BOTTOM,
    RIGHT_EYE_INNER,
    RIGHT_EYE_OUTER,
    RIGHT_EYE_TOP,
    RIGHT_EYE_BOTTOM,

    LEFT_BROW_INNER,
    LEFT_BROW_OUTER,
    LEFT_BROW_TOP,
    LEFT_BROW_BOTTOM,
    RIGHT_BROW_INNER,
    RIGHT_BROW_OUTER,
    RIGHT_BROW_TOP,
    RIGHT_BROW_BOTTOM,

    NOSE_BRIDGE_TOP,
    NOSE_TIP,
    NOSE_LEFT,
    NOSE_RIGHT,

    MOUTH_LEFT,
    MOUTH_RIGHT,
    LIP_UPPER_TOP,
    LIP_LOWER_BOTTOM,
    ;

    companion object {
        val required: Set<FacePoint> = entries.toSet()
    }
}

/**
 * A detected face, as raw image-space points.
 *
 * @param imageWidth width of the source image in pixels, used to judge whether the face is large
 *   enough in frame to analyse reliably.
 */
data class FaceLandmarks(
    val points: Map<FacePoint, Vec2>,
    val imageWidth: Int,
    val imageHeight: Int,
) {
    val missingPoints: Set<FacePoint> get() = FacePoint.required - points.keys

    val isComplete: Boolean get() = missingPoints.isEmpty()

    operator fun get(point: FacePoint): Vec2 =
        points[point] ?: error("Landmark $point missing from detection")

    /** Rotates every point around [pivot] by [radians] (positive = clockwise in image space). */
    fun rotated(radians: Float, pivot: Vec2): FaceLandmarks {
        val cos = kotlin.math.cos(radians)
        val sin = kotlin.math.sin(radians)
        return copy(
            points = points.mapValues { (_, p) ->
                val dx = p.x - pivot.x
                val dy = p.y - pivot.y
                Vec2(pivot.x + dx * cos - dy * sin, pivot.y + dx * sin + dy * cos)
            },
        )
    }
}

/**
 * Colours sampled from the photograph itself.
 *
 * Sampling happens on the Android side (it needs a `Bitmap`), but which sample means what is
 * decided here. Any of them may be null when the region was not confidently visible — hair is
 * frequently missing on cropped or hat-wearing selfies.
 */
data class ColorSamples(
    val skin: Int?,
    val hair: Int?,
    val iris: Int?,
)
