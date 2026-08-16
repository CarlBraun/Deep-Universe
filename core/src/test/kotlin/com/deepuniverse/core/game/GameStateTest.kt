package com.deepuniverse.core.game

import com.deepuniverse.core.character.AppearanceParam
import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.character.HairStyle
import com.deepuniverse.core.character.Palettes
import com.deepuniverse.core.character.Presets
import com.deepuniverse.core.character.PresentationStyle
import com.deepuniverse.core.character.Pronouns
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameStateTest {

    @Test
    fun `a save round-trips through text without losing the character`() {
        val state = GameState(
            player = CharacterAppearance(
                name = "Vesper",
                pronouns = Pronouns.THEY,
                presentation = PresentationStyle.ANDROGYNOUS,
                hairStyle = HairStyle.UNDERCUT,
                skinColor = Palettes.skin("bronze").argb,
                hairColor = Palettes.hair("nebula").argb,
                eyeColor = Palettes.eye("gold").argb,
                params = mapOf(
                    AppearanceParam.EYE_SIZE to 0.72f,
                    AppearanceParam.JAW_SHARPNESS to 0.31f,
                ),
            ),
            characterCreated = true,
            affection = mapOf("lyra" to 32, "kaito" to 8),
            flags = setOf("told_lyra_truth"),
            completedScenes = setOf("lyra_01_hangar"),
        )

        val restored = GameState.decode(GameState.encode(state))
        assertEquals(state, restored)
    }

    @Test
    fun `a corrupt save is refused instead of crashing the game`() {
        assertNull(GameState.decode("{not json at all"))
        assertNull(GameState.decode(""))
    }

    @Test
    fun `a save from an older build still loads`() {
        // Fields added later must not break existing players' saves.
        val old = """{"player":{"name":"Rae"},"affection":{"sev":12},"unknownFutureField":true}"""
        val restored = GameState.decode(old)
        assertEquals("Rae", restored?.player?.name)
        assertEquals(12, restored?.affectionFor("sev"))
    }

    @Test
    fun `bond tiers follow accumulated affection`() {
        assertEquals(AffectionLevel.STRANGER, AffectionLevel.forPoints(0))
        assertEquals(AffectionLevel.STRANGER, AffectionLevel.forPoints(9))
        assertEquals(AffectionLevel.ACQUAINTED, AffectionLevel.forPoints(10))
        assertEquals(AffectionLevel.CLOSE, AffectionLevel.forPoints(25))
        assertEquals(AffectionLevel.TRUSTED, AffectionLevel.forPoints(45))
        assertEquals(AffectionLevel.BELOVED, AffectionLevel.forPoints(70))
        assertEquals(AffectionLevel.BELOVED, AffectionLevel.forPoints(9999))
    }

    @Test
    fun `progress to the next tier is reported for the UI meter`() {
        assertEquals(10, AffectionLevel.pointsToNext(0))
        assertEquals(1, AffectionLevel.pointsToNext(24))
        assertNull(AffectionLevel.pointsToNext(70), "There is nothing beyond the top tier")
    }

    @Test
    fun `every preset is a valid, complete character`() {
        assertTrue(Presets.all.isNotEmpty())
        for (preset in Presets.all) {
            val a = preset.appearance
            assertTrue(Palettes.skinTones.any { it.argb == a.skinColor }, "${preset.id} skin off-palette")
            assertTrue(Palettes.hairColors.any { it.argb == a.hairColor }, "${preset.id} hair off-palette")
            assertTrue(Palettes.eyeColors.any { it.argb == a.eyeColor }, "${preset.id} eyes off-palette")
            assertTrue(
                a.resolvedParams().values.all { it in 0f..1f },
                "${preset.id} has an out-of-range parameter",
            )
            assertEquals(preset.appearance, GameState.decode(GameState.encode(GameState(player = a)))?.player)
        }
    }

    @Test
    fun `presets cover a range of presentations so nobody starts boxed in`() {
        val presentations = Presets.all.map { it.appearance.presentation }.toSet()
        assertEquals(PresentationStyle.entries.toSet(), presentations)
        val pronouns = Presets.all.map { it.appearance.pronouns }.toSet()
        assertEquals(Pronouns.entries.toSet(), pronouns)
    }

    @Test
    fun `out of range slider values are clamped on the way in`() {
        val a = CharacterAppearance()
            .with(AppearanceParam.EYE_SIZE, 4f)
            .with(AppearanceParam.JAW_WIDTH, -2f)
        assertEquals(1f, a[AppearanceParam.EYE_SIZE])
        assertEquals(0f, a[AppearanceParam.JAW_WIDTH])
    }

    @Test
    fun `resetting a parameter restores its neutral default`() {
        val a = CharacterAppearance().with(AppearanceParam.EYE_SIZE, 0.9f)
        assertEquals(0.9f, a[AppearanceParam.EYE_SIZE])
        assertEquals(AppearanceParam.EYE_SIZE.default, a.reset(AppearanceParam.EYE_SIZE)[AppearanceParam.EYE_SIZE])
    }
}
