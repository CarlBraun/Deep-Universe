package com.deepuniverse.core.photo

import kotlin.math.cos
import kotlin.math.sin

/**
 * A parameterised, made-up face used to test the analysis pipeline.
 *
 * The canonical face below is built on typical adult proportions: inter-ocular distance 60 units,
 * face height 190, cheekbone span 129. Feeding it through [PhotoToAppearance] should land every
 * inferred slider near the middle of its range — that is the calibration contract the mapping is
 * meant to satisfy, and `PhotoToAppearanceTest` asserts it.
 *
 * Tests deform one feature at a time from this baseline, so any assertion failure points at a
 * single measurement rather than at "the face changed".
 */
object SyntheticFace {

    const val IMAGE_WIDTH = 400
    const val IMAGE_HEIGHT = 520

    /** Canonical points, eyes on `y = 0`, face centred on `x = 0`. */
    private val canonical: Map<FacePoint, Vec2> = mapOf(
        FacePoint.FOREHEAD_TOP to Vec2(0f, -85f),
        FacePoint.CHIN_BOTTOM to Vec2(0f, 105f),
        FacePoint.CHEEK_LEFT to Vec2(-64.5f, 5f),
        FacePoint.CHEEK_RIGHT to Vec2(64.5f, 5f),
        FacePoint.JAW_LEFT to Vec2(-50f, 55f),
        FacePoint.JAW_RIGHT to Vec2(50f, 55f),

        FacePoint.LEFT_EYE_INNER to Vec2(-15f, 0f),
        FacePoint.LEFT_EYE_OUTER to Vec2(-45f, -1f),
        FacePoint.LEFT_EYE_TOP to Vec2(-30f, -5f),
        FacePoint.LEFT_EYE_BOTTOM to Vec2(-30f, 5f),
        FacePoint.RIGHT_EYE_INNER to Vec2(15f, 0f),
        FacePoint.RIGHT_EYE_OUTER to Vec2(45f, -1f),
        FacePoint.RIGHT_EYE_TOP to Vec2(30f, -5f),
        FacePoint.RIGHT_EYE_BOTTOM to Vec2(30f, 5f),

        // The brow's lowest point is the underside at mid-brow, below both of its ends — so
        // BROW_BOTTOM is unambiguously the largest y in the region, as it is on a real face.
        FacePoint.LEFT_BROW_INNER to Vec2(-15f, -17f),
        FacePoint.LEFT_BROW_OUTER to Vec2(-45f, -21f),
        FacePoint.LEFT_BROW_TOP to Vec2(-30f, -25f),
        FacePoint.LEFT_BROW_BOTTOM to Vec2(-30f, -16f),
        FacePoint.RIGHT_BROW_INNER to Vec2(15f, -17f),
        FacePoint.RIGHT_BROW_OUTER to Vec2(45f, -21f),
        FacePoint.RIGHT_BROW_TOP to Vec2(30f, -25f),
        FacePoint.RIGHT_BROW_BOTTOM to Vec2(30f, -16f),

        FacePoint.NOSE_BRIDGE_TOP to Vec2(0f, -5f),
        FacePoint.NOSE_TIP to Vec2(0f, 42f),
        FacePoint.NOSE_LEFT to Vec2(-18f, 44f),
        FacePoint.NOSE_RIGHT to Vec2(18f, 44f),

        FacePoint.MOUTH_LEFT to Vec2(-28.5f, 55.6f),
        FacePoint.MOUTH_RIGHT to Vec2(28.5f, 55.6f),
        FacePoint.LIP_UPPER_TOP to Vec2(0f, 46.5f),
        FacePoint.LIP_LOWER_BOTTOM to Vec2(0f, 67f),
    )

    /**
     * Builds landmarks from the canonical face.
     *
     * @param scale multiplies every distance — simulates standing nearer or further from the lens.
     * @param rollDeg rotates the whole head clockwise — simulates a tilted photo.
     * @param offset shifts the face within the frame.
     * @param edits replaces individual points to deform one feature.
     */
    fun landmarks(
        scale: Float = 1f,
        rollDeg: Float = 0f,
        offset: Vec2 = Vec2(200f, 240f),
        edits: Map<FacePoint, Vec2> = emptyMap(),
    ): FaceLandmarks {
        val rad = Math.toRadians(rollDeg.toDouble())
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()
        val source = canonical + edits
        val points = source.mapValues { (_, p) ->
            val sx = p.x * scale
            val sy = p.y * scale
            Vec2(offset.x + sx * cos - sy * sin, offset.y + sx * sin + sy * cos)
        }
        return FaceLandmarks(points, IMAGE_WIDTH, IMAGE_HEIGHT)
    }

    /** The canonical point, before scaling or rotation — a base for writing deformations. */
    fun point(p: FacePoint): Vec2 = canonical.getValue(p)
}
