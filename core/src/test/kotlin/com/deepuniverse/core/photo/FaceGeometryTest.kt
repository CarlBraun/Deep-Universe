package com.deepuniverse.core.photo

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The invariance properties the generator depends on: the same face, photographed differently,
 * must measure the same. Without these, a player would get a different character every time they
 * retook the photo.
 */
class FaceGeometryTest {

    private fun assertGeometryEquals(expected: FaceGeometry, actual: FaceGeometry, tolerance: Float) {
        fun check(name: String, a: Float, b: Float) =
            assertTrue(
                abs(a - b) <= tolerance,
                "$name differed: expected $a but was $b (tolerance $tolerance)",
            )
        check("faceAspect", expected.faceAspect, actual.faceAspect)
        check("faceWidth", expected.faceWidth, actual.faceWidth)
        check("jawWidth", expected.jawWidth, actual.jawWidth)
        check("jawAngleDeg", expected.jawAngleDeg, actual.jawAngleDeg)
        check("chinLength", expected.chinLength, actual.chinLength)
        check("eyeWidth", expected.eyeWidth, actual.eyeWidth)
        check("eyeAspect", expected.eyeAspect, actual.eyeAspect)
        check("eyeSpacing", expected.eyeSpacing, actual.eyeSpacing)
        check("eyeTiltDeg", expected.eyeTiltDeg, actual.eyeTiltDeg)
        check("eyeLevel", expected.eyeLevel, actual.eyeLevel)
        check("browThickness", expected.browThickness, actual.browThickness)
        check("browHeight", expected.browHeight, actual.browHeight)
        check("browAngleDeg", expected.browAngleDeg, actual.browAngleDeg)
        check("noseWidth", expected.noseWidth, actual.noseWidth)
        check("noseLength", expected.noseLength, actual.noseLength)
        check("mouthWidth", expected.mouthWidth, actual.mouthWidth)
        check("lipFullness", expected.lipFullness, actual.lipFullness)
        check("lipCurve", expected.lipCurve, actual.lipCurve)
        check("mouthLevel", expected.mouthLevel, actual.mouthLevel)
    }

    @Test
    fun `measurements are unchanged by how close the face is to the camera`() {
        val near = FaceGeometry.measure(SyntheticFace.landmarks(scale = 1.6f))
        val far = FaceGeometry.measure(SyntheticFace.landmarks(scale = 0.7f))
        assertGeometryEquals(near, far, tolerance = 0.001f)
    }

    @Test
    fun `measurements are unchanged by where the face sits in the frame`() {
        val centred = FaceGeometry.measure(SyntheticFace.landmarks(offset = Vec2(200f, 240f)))
        val corner = FaceGeometry.measure(SyntheticFace.landmarks(offset = Vec2(120f, 150f)))
        assertGeometryEquals(centred, corner, tolerance = 0.001f)
    }

    @Test
    fun `head tilt is corrected out before measuring`() {
        val level = FaceGeometry.measure(SyntheticFace.landmarks(rollDeg = 0f))
        val tilted = FaceGeometry.measure(SyntheticFace.landmarks(rollDeg = 25f))
        // Slightly looser: vertical spans are measured on the y axis, so a corrected rotation
        // leaves a little floating-point residue rather than being bit-identical.
        assertGeometryEquals(level, tilted, tolerance = 0.02f)
    }

    @Test
    fun `roll angle is reported so the UI can warn about tilted photos`() {
        val tilted = FaceGeometry.measure(SyntheticFace.landmarks(rollDeg = 25f))
        assertEquals(25f, tilted.correctedRollDeg, 0.5f)
    }

    @Test
    fun `a straight-on face reports no yaw`() {
        assertEquals(0f, FaceGeometry.measure(SyntheticFace.landmarks()).yaw, 0.02f)
    }

    @Test
    fun `a turned head is detected by the nose shifting off centre`() {
        val turned = SyntheticFace.landmarks(
            edits = mapOf(FacePoint.NOSE_TIP to Vec2(22f, 42f)),
        )
        assertTrue(
            FaceGeometry.measure(turned).yaw > 0.25f,
            "A nose well off centre should read as a turned head",
        )
    }

    @Test
    fun `wider set eyes measure as wider spacing`() {
        val base = FaceGeometry.measure(SyntheticFace.landmarks())
        val wide = FaceGeometry.measure(
            SyntheticFace.landmarks(
                edits = mapOf(
                    FacePoint.LEFT_EYE_INNER to Vec2(-24f, 0f),
                    FacePoint.RIGHT_EYE_INNER to Vec2(24f, 0f),
                ),
            ),
        )
        assertTrue(wide.eyeSpacing > base.eyeSpacing, "Moving inner corners apart must widen spacing")
    }

    @Test
    fun `narrowed eyes lower the eye aspect ratio`() {
        val base = FaceGeometry.measure(SyntheticFace.landmarks())
        val narrowed = FaceGeometry.measure(
            SyntheticFace.landmarks(
                edits = mapOf(
                    FacePoint.LEFT_EYE_TOP to Vec2(-30f, -2f),
                    FacePoint.LEFT_EYE_BOTTOM to Vec2(-30f, 2f),
                    FacePoint.RIGHT_EYE_TOP to Vec2(30f, -2f),
                    FacePoint.RIGHT_EYE_BOTTOM to Vec2(30f, 2f),
                ),
            ),
        )
        assertTrue(narrowed.eyeAspect < base.eyeAspect)
    }

    @Test
    fun `an incomplete detection is rejected rather than measured`() {
        val partial = SyntheticFace.landmarks().let {
            it.copy(points = it.points - FacePoint.CHIN_BOTTOM)
        }
        assertTrue(partial.missingPoints.contains(FacePoint.CHIN_BOTTOM))
        val error = runCatching { FaceGeometry.measure(partial) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException, "Expected a rejection, got $error")
    }
}
