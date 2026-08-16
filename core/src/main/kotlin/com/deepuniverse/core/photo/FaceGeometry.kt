package com.deepuniverse.core.photo

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Scale-, position- and rotation-independent measurements of a face.
 *
 * Every field is a *ratio*, never a pixel count. Two photos of the same person taken at different
 * distances, with the head tilted differently, must produce the same geometry — otherwise the
 * generated character would change every time the player retook the picture. That invariance is
 * what the unit tests in `FaceGeometryTest` pin down.
 *
 * Ratios are expressed against one of two references:
 *  - **IOD** (inter-ocular distance, pupil to pupil) for horizontal features. It is the single most
 *    stable measurement on a face — it barely changes with expression.
 *  - **face height** (forehead to chin) for vertical placement.
 */
data class FaceGeometry(
    /** Face height / cheekbone width. ~1.3 round, ~1.7 long. */
    val faceAspect: Float,
    /** Cheekbone width / IOD. */
    val faceWidth: Float,
    /** Jaw width / cheekbone width. 1.0 = jaw as wide as cheekbones. */
    val jawWidth: Float,
    /** Degrees between the jaw line and horizontal. Higher = more tapered, angular jaw. */
    val jawAngleDeg: Float,
    /** Distance from the lower lip to the chin, over face height. */
    val chinLength: Float,

    /** Mean eye width / IOD. */
    val eyeWidth: Float,
    /** Eye height / eye width — the classic "eye aspect ratio". Drops when eyes narrow. */
    val eyeAspect: Float,
    /** Gap between the inner eye corners, measured in eye-widths. 1.0 is the classic ideal. */
    val eyeSpacing: Float,
    /** Degrees the outer corner sits above the inner corner. Positive = upturned. */
    val eyeTiltDeg: Float,
    /** Eye centre height over face height, from the forehead down. */
    val eyeLevel: Float,

    /** Brow thickness / IOD. */
    val browThickness: Float,
    /** Gap between the brow underside and the eye top, over IOD. */
    val browHeight: Float,
    /** Degrees the outer brow end sits above the inner end. Positive = arched. */
    val browAngleDeg: Float,

    /** Nostril span / IOD. */
    val noseWidth: Float,
    /** Bridge-top to tip, over face height. */
    val noseLength: Float,

    /** Mouth corner span / IOD. */
    val mouthWidth: Float,
    /** Lip height / mouth width. */
    val lipFullness: Float,
    /** How far the mouth corners sit above the lip centre line, over mouth width. */
    val lipCurve: Float,
    /** Mouth centre height over face height. */
    val mouthLevel: Float,

    /** Estimated head turn, `0f` = straight on, `1f` = strongly turned. */
    val yaw: Float,
    /** Head tilt in degrees that was corrected out before measuring. */
    val correctedRollDeg: Float,
    /** Fraction of the image width the face spans. Small faces measure poorly. */
    val faceFrameRatio: Float,
) {
    companion object {

        /**
         * Measures [landmarks], first cancelling out any head tilt.
         *
         * @throws IllegalStateException if any required landmark is absent — call
         *   [FaceLandmarks.isComplete] first.
         */
        fun measure(landmarks: FaceLandmarks): FaceGeometry {
            require(landmarks.isComplete) {
                "Cannot measure an incomplete face, missing: ${landmarks.missingPoints}"
            }

            val rawLeftEye = eyeCentre(landmarks, left = true)
            val rawRightEye = eyeCentre(landmarks, left = false)

            // Cancel head tilt: rotate the whole face until the eye line is horizontal, so that a
            // photo taken with the head cocked measures identically to a level one.
            val rollRad = atan2(rawRightEye.y - rawLeftEye.y, rawRightEye.x - rawLeftEye.x)
            val pivot = Vec2.midpoint(rawLeftEye, rawRightEye)
            val f = landmarks.rotated(-rollRad, pivot)

            val leftEye = eyeCentre(f, left = true)
            val rightEye = eyeCentre(f, left = false)
            val iod = leftEye.distanceTo(rightEye).coerceAtLeast(1e-3f)

            val forehead = f[FacePoint.FOREHEAD_TOP]
            val chin = f[FacePoint.CHIN_BOTTOM]
            val faceHeight = abs(chin.y - forehead.y).coerceAtLeast(1e-3f)

            val cheekSpan = abs(f[FacePoint.CHEEK_RIGHT].x - f[FacePoint.CHEEK_LEFT].x)
                .coerceAtLeast(1e-3f)
            val jawSpan = abs(f[FacePoint.JAW_RIGHT].x - f[FacePoint.JAW_LEFT].x)

            // Jaw taper: the angle the jaw line makes running from the jaw corner down to the chin.
            // A soft, round jaw runs nearly horizontally out of the chin; a sharp one drops steeply.
            val jawL = f[FacePoint.JAW_LEFT]
            val jawAngleDeg = Math.toDegrees(
                atan2((chin.y - jawL.y).toDouble(), (chin.x - jawL.x).toDouble()),
            ).toFloat().let { abs(it) }

            val lipTop = f[FacePoint.LIP_UPPER_TOP]
            val lipBottom = f[FacePoint.LIP_LOWER_BOTTOM]
            val mouthL = f[FacePoint.MOUTH_LEFT]
            val mouthR = f[FacePoint.MOUTH_RIGHT]
            val mouthSpan = mouthL.distanceTo(mouthR).coerceAtLeast(1e-3f)
            val lipCentreY = (lipTop.y + lipBottom.y) / 2f
            val cornerY = (mouthL.y + mouthR.y) / 2f

            val eyeW = listOf(
                f[FacePoint.LEFT_EYE_INNER].distanceTo(f[FacePoint.LEFT_EYE_OUTER]),
                f[FacePoint.RIGHT_EYE_INNER].distanceTo(f[FacePoint.RIGHT_EYE_OUTER]),
            ).average().toFloat().coerceAtLeast(1e-3f)
            val eyeH = listOf(
                abs(f[FacePoint.LEFT_EYE_TOP].y - f[FacePoint.LEFT_EYE_BOTTOM].y),
                abs(f[FacePoint.RIGHT_EYE_TOP].y - f[FacePoint.RIGHT_EYE_BOTTOM].y),
            ).average().toFloat()
            val innerGap = f[FacePoint.LEFT_EYE_INNER].distanceTo(f[FacePoint.RIGHT_EYE_INNER])

            val eyeTiltDeg = listOf(
                tiltDeg(inner = f[FacePoint.LEFT_EYE_INNER], outer = f[FacePoint.LEFT_EYE_OUTER]),
                tiltDeg(inner = f[FacePoint.RIGHT_EYE_INNER], outer = f[FacePoint.RIGHT_EYE_OUTER]),
            ).average().toFloat()

            val browThickness = listOf(
                abs(f[FacePoint.LEFT_BROW_TOP].y - f[FacePoint.LEFT_BROW_BOTTOM].y),
                abs(f[FacePoint.RIGHT_BROW_TOP].y - f[FacePoint.RIGHT_BROW_BOTTOM].y),
            ).average().toFloat()
            val browGap = listOf(
                f[FacePoint.LEFT_EYE_TOP].y - f[FacePoint.LEFT_BROW_BOTTOM].y,
                f[FacePoint.RIGHT_EYE_TOP].y - f[FacePoint.RIGHT_BROW_BOTTOM].y,
            ).average().toFloat()
            val browAngleDeg = listOf(
                tiltDeg(inner = f[FacePoint.LEFT_BROW_INNER], outer = f[FacePoint.LEFT_BROW_OUTER]),
                tiltDeg(inner = f[FacePoint.RIGHT_BROW_INNER], outer = f[FacePoint.RIGHT_BROW_OUTER]),
            ).average().toFloat()

            val noseTip = f[FacePoint.NOSE_TIP]
            val noseSpan = abs(f[FacePoint.NOSE_RIGHT].x - f[FacePoint.NOSE_LEFT].x)

            val eyeMidY = (leftEye.y + rightEye.y) / 2f

            return FaceGeometry(
                faceAspect = faceHeight / cheekSpan,
                faceWidth = cheekSpan / iod,
                jawWidth = jawSpan / cheekSpan,
                jawAngleDeg = jawAngleDeg,
                chinLength = (chin.y - lipBottom.y) / faceHeight,

                eyeWidth = eyeW / iod,
                eyeAspect = eyeH / eyeW,
                eyeSpacing = innerGap / eyeW,
                eyeTiltDeg = eyeTiltDeg,
                eyeLevel = (eyeMidY - forehead.y) / faceHeight,

                browThickness = browThickness / iod,
                browHeight = browGap / iod,
                browAngleDeg = browAngleDeg,

                noseWidth = noseSpan / iod,
                noseLength = (noseTip.y - f[FacePoint.NOSE_BRIDGE_TOP].y) / faceHeight,

                mouthWidth = mouthSpan / iod,
                lipFullness = abs(lipBottom.y - lipTop.y) / mouthSpan,
                lipCurve = (lipCentreY - cornerY) / mouthSpan,
                mouthLevel = (lipCentreY - forehead.y) / faceHeight,

                yaw = estimateYaw(f, noseTip, cheekSpan),
                correctedRollDeg = Math.toDegrees(rollRad.toDouble()).toFloat(),
                faceFrameRatio = if (landmarks.imageWidth > 0) {
                    cheekSpan / landmarks.imageWidth
                } else {
                    0f
                },
            )
        }

        private fun eyeCentre(landmarks: FaceLandmarks, left: Boolean): Vec2 = Vec2.midpoint(
            landmarks[if (left) FacePoint.LEFT_EYE_INNER else FacePoint.RIGHT_EYE_INNER],
            landmarks[if (left) FacePoint.LEFT_EYE_OUTER else FacePoint.RIGHT_EYE_OUTER],
        )

        /**
         * Degrees the [outer] point sits above [inner]. Image `y` grows downwards, so the sign is
         * flipped to make "up" positive, and the result is mirrored for the left half of the face
         * so both eyes agree on what "upturned" means.
         */
        private fun tiltDeg(inner: Vec2, outer: Vec2): Float {
            val dx = outer.x - inner.x
            val dy = outer.y - inner.y
            val deg = Math.toDegrees(atan2(-dy.toDouble(), abs(dx).toDouble())).toFloat()
            return deg
        }

        /**
         * Estimates head turn from facial asymmetry: on a face turned away from the camera, the
         * nose tip shifts towards the near cheek. `0f` is straight on.
         */
        private fun estimateYaw(f: FaceLandmarks, noseTip: Vec2, cheekSpan: Float): Float {
            val leftCheek = f[FacePoint.CHEEK_LEFT].x
            val rightCheek = f[FacePoint.CHEEK_RIGHT].x
            val centre = (leftCheek + rightCheek) / 2f
            val offset = abs(noseTip.x - centre) / (cheekSpan / 2f)
            return min(1f, max(0f, offset))
        }
    }
}
