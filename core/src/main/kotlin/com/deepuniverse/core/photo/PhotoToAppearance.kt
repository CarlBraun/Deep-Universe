package com.deepuniverse.core.photo

import com.deepuniverse.core.character.AppearanceParam
import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.character.Palettes
import com.deepuniverse.core.color.luminance
import com.deepuniverse.core.color.saturation
import kotlin.math.abs

/** Why an analysis could not be run. */
enum class AnalysisFailure(val playerMessage: String) {
    NO_FACE("We couldn't find a face in that photo. Try a straight-on shot in good light."),
    INCOMPLETE_FACE("Part of the face was cut off. Fit your whole head in the frame and try again."),
    FACE_TOO_SMALL("Move a little closer — the face is too small in frame to read clearly."),
}

/** A hint shown under the result, telling the player how to get a better match. */
data class AnalysisNote(val message: String)

sealed interface AnalysisResult {

    data class Failed(val reason: AnalysisFailure) : AnalysisResult

    /**
     * @param appearance the base appearance with every photo-inferable parameter replaced by the
     *   value read off the face. Parameters the camera cannot see (build, freckles, hairstyle) are
     *   left exactly as they were, so re-running the analysis never silently undoes manual edits.
     * @param confidence `0f..1f`, how much the pipeline trusts this reading. The creator uses it to
     *   pre-set the "photo strength" slider rather than to reject the result.
     */
    data class Success(
        val appearance: CharacterAppearance,
        val confidence: Float,
        val notes: List<AnalysisNote>,
        val geometry: FaceGeometry,
    ) : AnalysisResult
}

/**
 * Turns a detected face into character parameters.
 *
 * ### Why it works this way
 * The pipeline is entirely on-device and deterministic: face landmarks in, slider values out. No
 * photo, no measurement and no derived character ever leaves the phone, and no API key or network
 * call is involved. That is a hard requirement for a consumer game handling face photos of what may
 * well be minors, and it is also the reason this is a geometric mapping rather than a generative
 * model — a diffusion model that hallucinated a face would be far harder to make safe, far slower,
 * and impossible to hand back to the player as editable sliders.
 *
 * The player always lands in the same editor afterwards, with every slider live. The analysis is a
 * *starting point*, never a replacement for customisation.
 *
 * ### Calibration
 * The `low`/`high` bounds below are art direction, not anthropometry. Each pair is the range over
 * which a real measurement should sweep the corresponding slider from one stylised extreme to the
 * other. They are centred on typical adult proportions so that an average face lands near the
 * middle of every slider, and deliberately narrower than the full human range so that the traits
 * that *do* stand out on a given face are visible on the character.
 */
object PhotoToAppearance {

    /** Below this fraction of the image width, landmark noise swamps the measurements. */
    private const val MIN_FACE_FRAME_RATIO = 0.12f

    fun analyze(
        landmarks: FaceLandmarks?,
        colors: ColorSamples,
        base: CharacterAppearance = CharacterAppearance(),
    ): AnalysisResult {
        if (landmarks == null) return AnalysisResult.Failed(AnalysisFailure.NO_FACE)
        if (!landmarks.isComplete) return AnalysisResult.Failed(AnalysisFailure.INCOMPLETE_FACE)

        val geometry = FaceGeometry.measure(landmarks)
        if (geometry.faceFrameRatio > 0f && geometry.faceFrameRatio < MIN_FACE_FRAME_RATIO) {
            return AnalysisResult.Failed(AnalysisFailure.FACE_TOO_SMALL)
        }

        val appearance = base
            .withAll(mapGeometry(geometry))
            .let { applyColors(it, colors) }

        return AnalysisResult.Success(
            appearance = appearance,
            confidence = confidenceOf(geometry, colors),
            notes = notesFor(geometry, colors),
            geometry = geometry,
        )
    }

    /** The geometric half of the mapping, exposed separately so tests can pin each slider down. */
    fun mapGeometry(g: FaceGeometry): Map<AppearanceParam, Float> = mapOf(
        AppearanceParam.FACE_LENGTH to span(g.faceAspect, 1.25f, 1.75f),
        AppearanceParam.FACE_WIDTH to span(g.faceWidth, 1.85f, 2.45f),
        // A face reads as high-cheekboned when the cheeks are wide *relative to the jaw*; there is
        // no reliable single landmark for cheekbone height in a 2D mesh, so the contrast stands in.
        AppearanceParam.CHEEKBONES to span(1f - g.jawWidth, 0.08f, 0.38f),
        AppearanceParam.JAW_WIDTH to span(g.jawWidth, 0.62f, 0.92f),
        AppearanceParam.JAW_SHARPNESS to span(g.jawAngleDeg, 30f, 65f),
        AppearanceParam.CHIN_LENGTH to span(g.chinLength, 0.14f, 0.26f),

        AppearanceParam.EYE_SIZE to span(g.eyeWidth, 0.42f, 0.60f),
        AppearanceParam.EYE_SPACING to span(g.eyeSpacing, 0.80f, 1.25f),
        AppearanceParam.EYE_TILT to span(g.eyeTiltDeg, -6f, 14f),
        AppearanceParam.EYE_OPENNESS to span(g.eyeAspect, 0.22f, 0.42f),
        AppearanceParam.EYE_HEIGHT to span(1f - g.eyeLevel, 0.50f, 0.62f),

        AppearanceParam.BROW_THICKNESS to span(g.browThickness, 0.08f, 0.22f),
        AppearanceParam.BROW_HEIGHT to span(g.browHeight, 0.10f, 0.30f),
        AppearanceParam.BROW_ANGLE to span(g.browAngleDeg, -2f, 18f),

        AppearanceParam.NOSE_WIDTH to span(g.noseWidth, 0.45f, 0.75f),
        AppearanceParam.NOSE_LENGTH to span(g.noseLength, 0.19f, 0.31f),
        // A 2D mesh cannot see depth, so bridge definition is inferred from the nose's slenderness:
        // long and narrow reads as a defined bridge, short and wide as a flat one.
        AppearanceParam.NOSE_BRIDGE to span(g.noseLength / g.noseWidth.coerceAtLeast(1e-3f), 0.30f, 0.55f),

        AppearanceParam.MOUTH_WIDTH to span(g.mouthWidth, 0.75f, 1.15f),
        AppearanceParam.LIP_FULLNESS to span(g.lipFullness, 0.22f, 0.50f),
        AppearanceParam.LIP_CURVE to span(g.lipCurve, -0.04f, 0.08f),
        AppearanceParam.MOUTH_HEIGHT to span(g.mouthLevel, 0.68f, 0.82f),
    )

    private fun applyColors(appearance: CharacterAppearance, colors: ColorSamples) =
        appearance.copy(
            skinColor = colors.skin
                ?.let { Palettes.nearest(Palettes.skinTones, it).argb }
                ?: appearance.skinColor,
            hairColor = colors.hair
                ?.let { Palettes.nearest(Palettes.naturalHairColors, it).argb }
                ?: appearance.hairColor,
            eyeColor = colors.iris
                ?.let { Palettes.nearest(Palettes.eyeColors, it).argb }
                ?: appearance.eyeColor,
        )

    /**
     * Maps a raw measurement onto `0f..1f`.
     *
     * Clamping rather than extrapolating is intentional: a landmark detector that misfires on one
     * point produces a wild ratio, and a clamp turns that into a slider pinned at an extreme
     * instead of a character with a nose three heads wide.
     */
    private fun span(value: Float, low: Float, high: Float): Float =
        ((value - low) / (high - low)).coerceIn(0f, 1f)

    private fun confidenceOf(g: FaceGeometry, colors: ColorSamples): Float {
        var confidence = 1f
        // A turned head foreshortens every horizontal measurement.
        confidence -= (g.yaw - 0.10f).coerceAtLeast(0f) * 1.2f
        // Roll is corrected out, but a steeply tilted head still had fewer pixels per feature.
        confidence -= ((abs(g.correctedRollDeg) - 15f).coerceAtLeast(0f) / 45f) * 0.3f
        // A small face in frame means noisy landmarks.
        if (g.faceFrameRatio > 0f) {
            confidence -= ((0.30f - g.faceFrameRatio).coerceAtLeast(0f) / 0.30f) * 0.35f
        }
        if (colors.skin == null) confidence -= 0.10f
        if (colors.hair == null) confidence -= 0.05f
        return confidence.coerceIn(0.05f, 1f)
    }

    private fun notesFor(g: FaceGeometry, colors: ColorSamples): List<AnalysisNote> = buildList {
        if (g.yaw > 0.22f) {
            add(AnalysisNote("Your head was turned a little. A straight-on photo matches your face more closely."))
        }
        if (abs(g.correctedRollDeg) > 20f) {
            add(AnalysisNote("We straightened a tilted photo. Holding the camera level gives a cleaner read."))
        }
        if (g.faceFrameRatio in 0.001f..0.20f) {
            add(AnalysisNote("Getting closer to the camera will sharpen the details we can pick up."))
        }
        if (colors.hair == null) {
            add(AnalysisNote("We couldn't see your hair, so the hair colour is unchanged — pick it in the Hair tab."))
        }
        colors.skin?.let { skin ->
            if (luminance(skin) > 0.92f || luminance(skin) < 0.06f) {
                add(AnalysisNote("The lighting was harsh, so the skin tone is a guess. Nudge it in the Skin tab."))
            } else if (saturation(skin) > 0.55f) {
                add(AnalysisNote("Strong coloured lighting can skew skin tone — check it looks right to you."))
            }
        }
        add(AnalysisNote("Every slider stays yours to change. Nothing here is locked in."))
    }
}
