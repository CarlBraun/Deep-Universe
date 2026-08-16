package com.deepuniverse.core.photo

import com.deepuniverse.core.character.AppearanceParam
import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.character.HairStyle
import com.deepuniverse.core.character.Palettes
import com.deepuniverse.core.color.argb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class PhotoToAppearanceTest {

    private val noColors = ColorSamples(skin = null, hair = null, iris = null)

    private fun analyze(
        landmarks: FaceLandmarks? = SyntheticFace.landmarks(),
        colors: ColorSamples = noColors,
        base: CharacterAppearance = CharacterAppearance(),
    ): AnalysisResult = PhotoToAppearance.analyze(landmarks, colors, base)

    private fun success(
        landmarks: FaceLandmarks = SyntheticFace.landmarks(),
        colors: ColorSamples = noColors,
        base: CharacterAppearance = CharacterAppearance(),
    ): AnalysisResult.Success =
        analyze(landmarks, colors, base) as? AnalysisResult.Success
            ?: fail("Expected a successful analysis")

    // ------------------------------------------------------------ calibration

    @Test
    fun `an average face lands near the middle of every inferred slider`() {
        val appearance = success().appearance
        for (param in AppearanceParam.photoInferable) {
            val value = appearance[param]
            assertTrue(
                value in 0.25f..0.75f,
                "$param should sit mid-range for an average face but was $value — the " +
                    "calibration range for it is off centre",
            )
        }
    }

    @Test
    fun `parameters a photo cannot show are left untouched`() {
        val base = CharacterAppearance()
            .with(AppearanceParam.HEIGHT, 0.9f)
            .with(AppearanceParam.FRECKLES, 0.7f)
            .with(AppearanceParam.BUILD, 0.15f)
            .copy(hairStyle = HairStyle.TWIN_TAILS)

        val result = success(base = base).appearance

        assertEquals(0.9f, result[AppearanceParam.HEIGHT], 1e-6f)
        assertEquals(0.7f, result[AppearanceParam.FRECKLES], 1e-6f)
        assertEquals(0.15f, result[AppearanceParam.BUILD], 1e-6f)
        assertEquals(HairStyle.TWIN_TAILS, result.hairStyle, "Hair style is not inferable from a photo")
    }

    @Test
    fun `re-running the analysis on the same photo gives the same character`() {
        val first = success().appearance
        val second = success().appearance
        assertEquals(first, second)
    }

    @Test
    fun `the same face measured at a different distance gives the same character`() {
        val near = success(SyntheticFace.landmarks(scale = 1.5f)).appearance
        val far = success(SyntheticFace.landmarks(scale = 0.8f)).appearance
        for (param in AppearanceParam.photoInferable) {
            assertEquals(near[param], far[param], 0.01f, "$param drifted with camera distance")
        }
    }

    // ------------------------------------------------------------ responsiveness

    @Test
    fun `bigger eyes in the photo raise the eye size slider`() {
        val base = success().appearance[AppearanceParam.EYE_SIZE]
        val bigEyed = success(
            SyntheticFace.landmarks(
                edits = mapOf(
                    FacePoint.LEFT_EYE_OUTER to Vec2(-52f, -1f),
                    FacePoint.RIGHT_EYE_OUTER to Vec2(52f, -1f),
                ),
            ),
        ).appearance[AppearanceParam.EYE_SIZE]
        assertTrue(bigEyed > base, "Wider eye openings should raise EYE_SIZE ($bigEyed vs $base)")
    }

    @Test
    fun `a longer face raises the face length slider`() {
        val base = success().appearance[AppearanceParam.FACE_LENGTH]
        val longFace = success(
            SyntheticFace.landmarks(
                edits = mapOf(
                    FacePoint.FOREHEAD_TOP to Vec2(0f, -115f),
                    FacePoint.CHIN_BOTTOM to Vec2(0f, 130f),
                ),
            ),
        ).appearance[AppearanceParam.FACE_LENGTH]
        assertTrue(longFace > base, "A taller face should raise FACE_LENGTH")
    }

    @Test
    fun `fuller lips raise the lip fullness slider`() {
        val base = success().appearance[AppearanceParam.LIP_FULLNESS]
        val full = success(
            SyntheticFace.landmarks(
                edits = mapOf(
                    FacePoint.LIP_UPPER_TOP to Vec2(0f, 40f),
                    FacePoint.LIP_LOWER_BOTTOM to Vec2(0f, 72f),
                ),
            ),
        ).appearance[AppearanceParam.LIP_FULLNESS]
        assertTrue(full > base)
    }

    @Test
    fun `a wide angular jaw reads as wide and sharp`() {
        val base = success().appearance
        val square = success(
            SyntheticFace.landmarks(
                edits = mapOf(
                    FacePoint.JAW_LEFT to Vec2(-62f, 62f),
                    FacePoint.JAW_RIGHT to Vec2(62f, 62f),
                ),
            ),
        ).appearance
        assertTrue(square[AppearanceParam.JAW_WIDTH] > base[AppearanceParam.JAW_WIDTH])
        assertTrue(
            square[AppearanceParam.CHEEKBONES] < base[AppearanceParam.CHEEKBONES],
            "A jaw as wide as the cheeks should read as less cheekbone-prominent",
        )
    }

    @Test
    fun `extreme features clamp instead of running off the slider`() {
        val absurd = success(
            SyntheticFace.landmarks(
                edits = mapOf(
                    FacePoint.NOSE_LEFT to Vec2(-90f, 44f),
                    FacePoint.NOSE_RIGHT to Vec2(90f, 44f),
                ),
            ),
        ).appearance
        assertEquals(1f, absurd[AppearanceParam.NOSE_WIDTH], 1e-6f)
        assertTrue(absurd.resolvedParams().values.all { it in 0f..1f })
    }

    // ------------------------------------------------------------ colour

    @Test
    fun `sampled colours snap to the art-directed palette`() {
        val result = success(
            colors = ColorSamples(
                // Deliberately off-palette samples, as a real photo would give.
                skin = argb(0xD9, 0xA8, 0x84),
                hair = argb(0x2A, 0x20, 0x1C),
                iris = argb(0x40, 0x72, 0xAF),
            ),
        ).appearance

        assertTrue(Palettes.skinTones.any { it.argb == result.skinColor }, "Skin must be a palette swatch")
        assertTrue(Palettes.hairColors.any { it.argb == result.hairColor }, "Hair must be a palette swatch")
        assertTrue(Palettes.eyeColors.any { it.argb == result.eyeColor }, "Eyes must be a palette swatch")
        assertEquals(Palettes.eye("blue").argb, result.eyeColor, "A blue iris should pick the blue swatch")
    }

    @Test
    fun `a photo never produces stylised hair colours`() {
        // Green-tinted lighting must not be able to hand the player mint hair.
        val result = success(
            colors = ColorSamples(skin = null, hair = argb(0x5A, 0xC0, 0x9A), iris = null),
        ).appearance
        assertTrue(
            Palettes.naturalHairColors.any { it.argb == result.hairColor },
            "Inferred hair colour must come from the natural range only",
        )
    }

    @Test
    fun `missing colour samples leave the existing colours alone`() {
        val base = CharacterAppearance(
            skinColor = Palettes.skin("umber").argb,
            hairColor = Palettes.hair("nebula").argb,
            eyeColor = Palettes.eye("violet").argb,
        )
        val result = success(base = base).appearance
        assertEquals(base.skinColor, result.skinColor)
        assertEquals(base.hairColor, result.hairColor)
        assertEquals(base.eyeColor, result.eyeColor)
    }

    // ------------------------------------------------------------ confidence and failure

    @Test
    fun `a clean straight-on photo is high confidence`() {
        val result = success(colors = ColorSamples(argb(0xD9, 0xA8, 0x84), argb(0x33, 0x28, 0x22), argb(0x50, 0x33, 0x22)))
        assertTrue(result.confidence > 0.85f, "Expected high confidence, got ${result.confidence}")
    }

    @Test
    fun `a turned head lowers confidence and warns the player`() {
        val turned = success(
            SyntheticFace.landmarks(edits = mapOf(FacePoint.NOSE_TIP to Vec2(26f, 42f))),
        )
        assertTrue(turned.confidence < 0.85f, "A turned head should reduce confidence")
        assertTrue(
            turned.notes.any { "turned" in it.message },
            "The player should be told why the match may be off",
        )
    }

    @Test
    fun `the player is always reminded the sliders stay editable`() {
        assertTrue(success().notes.any { "slider" in it.message })
    }

    @Test
    fun `no face means a helpful failure rather than a crash`() {
        val result = analyze(landmarks = null)
        assertEquals(AnalysisResult.Failed(AnalysisFailure.NO_FACE), result)
    }

    @Test
    fun `a partially detected face is refused`() {
        val partial = SyntheticFace.landmarks().let { it.copy(points = it.points - FacePoint.NOSE_TIP) }
        assertEquals(AnalysisResult.Failed(AnalysisFailure.INCOMPLETE_FACE), analyze(partial))
    }

    @Test
    fun `a face too small in frame is refused before it produces noise`() {
        val tiny = SyntheticFace.landmarks(scale = 0.2f)
        assertEquals(AnalysisResult.Failed(AnalysisFailure.FACE_TOO_SMALL), analyze(tiny))
    }

    // ------------------------------------------------------------ blending

    @Test
    fun `photo strength blends between the current look and the photo`() {
        val manual = CharacterAppearance().with(AppearanceParam.EYE_SIZE, 0f)
        val fromPhoto = success(
            SyntheticFace.landmarks(
                edits = mapOf(
                    FacePoint.LEFT_EYE_OUTER to Vec2(-55f, -1f),
                    FacePoint.RIGHT_EYE_OUTER to Vec2(55f, -1f),
                ),
            ),
            base = manual,
        ).appearance

        val half = manual.blendTowards(fromPhoto, 0.5f)
        assertEquals(manual, manual.blendTowards(fromPhoto, 0f), "Zero strength must change nothing")
        assertEquals(
            fromPhoto[AppearanceParam.EYE_SIZE],
            manual.blendTowards(fromPhoto, 1f)[AppearanceParam.EYE_SIZE],
            1e-6f,
        )
        assertTrue(half[AppearanceParam.EYE_SIZE] > manual[AppearanceParam.EYE_SIZE])
        assertTrue(half[AppearanceParam.EYE_SIZE] < fromPhoto[AppearanceParam.EYE_SIZE])
    }

    @Test
    fun `manual edits survive a later photo analysis of an inferable parameter`() {
        // Editing then re-analysing legitimately overwrites inferable sliders — that is the point
        // of re-running it — but the edit must not be lost silently anywhere else.
        val edited = CharacterAppearance(name = "Vesper").with(AppearanceParam.EYE_MAKEUP, 0.95f)
        val result = success(base = edited).appearance
        assertEquals("Vesper", result.name)
        assertEquals(0.95f, result[AppearanceParam.EYE_MAKEUP], 1e-6f)
        assertNotEquals(edited[AppearanceParam.EYE_SIZE], result[AppearanceParam.EYE_SIZE])
    }
}
