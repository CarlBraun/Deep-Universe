package com.deepuniverse.app.ui.avatar

import com.deepuniverse.core.character.AppearanceParam
import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.character.HairStyle
import com.deepuniverse.core.character.Palettes
import com.deepuniverse.core.character.PresentationStyle
import com.deepuniverse.core.character.Pronouns
import com.deepuniverse.core.game.Cast

/**
 * Appearances for the love interests, so the cast is drawn by the same renderer as the player.
 *
 * Reusing one renderer for everybody means the cast is never more detailed than the player
 * character can be — whatever range the sliders offer is visibly what the cast is built from. When
 * real character art lands, these are what it replaces.
 */
object CastLooks {

    private val looks: Map<String, CharacterAppearance> = mapOf(
        Cast.lyra.id to CharacterAppearance(
            name = Cast.lyra.name,
            pronouns = Pronouns.SHE,
            presentation = PresentationStyle.FEMININE,
            hairStyle = HairStyle.LONG_WAVY,
            skinColor = Palettes.skin("sand").argb,
            hairColor = Palettes.hair("auburn").argb,
            eyeColor = Palettes.eye("amber").argb,
            params = mapOf(
                AppearanceParam.EYE_TILT to 0.72f,
                AppearanceParam.EYE_SIZE to 0.62f,
                AppearanceParam.JAW_SHARPNESS to 0.62f,
                AppearanceParam.CHEEKBONES to 0.72f,
                AppearanceParam.LIP_CURVE to 0.78f,
                AppearanceParam.BROW_ANGLE to 0.58f,
                AppearanceParam.FRECKLES to 0.3f,
                AppearanceParam.EYE_MAKEUP to 0.35f,
            ),
        ),
        Cast.nadia.id to CharacterAppearance(
            name = Cast.nadia.name,
            pronouns = Pronouns.SHE,
            presentation = PresentationStyle.FEMININE,
            hairStyle = HairStyle.CURLY_CLOUD,
            skinColor = Palettes.skin("umber").argb,
            hairColor = Palettes.hair("jet").argb,
            eyeColor = Palettes.eye("darkbrown").argb,
            params = mapOf(
                AppearanceParam.EYE_OPENNESS to 0.38f,
                AppearanceParam.EYE_SIZE to 0.55f,
                AppearanceParam.LIP_FULLNESS to 0.78f,
                AppearanceParam.CHEEKBONES to 0.68f,
                AppearanceParam.BROW_THICKNESS to 0.6f,
                AppearanceParam.SKIN_GLOW to 0.7f,
            ),
        ),
        Cast.rook.id to CharacterAppearance(
            name = Cast.rook.name,
            pronouns = Pronouns.SHE,
            presentation = PresentationStyle.ANDROGYNOUS,
            hairStyle = HairStyle.UNDERCUT,
            skinColor = Palettes.skin("ivory").argb,
            hairColor = Palettes.hair("ash").argb,
            eyeColor = Palettes.eye("grey").argb,
            params = mapOf(
                AppearanceParam.EYE_OPENNESS to 0.30f,
                AppearanceParam.EYE_TILT to 0.28f,
                AppearanceParam.JAW_SHARPNESS to 0.72f,
                AppearanceParam.MOUTH_WIDTH to 0.42f,
                AppearanceParam.LIP_CURVE to 0.2f,
                AppearanceParam.BROW_ANGLE to 0.2f,
                AppearanceParam.SHOULDER_WIDTH to 0.6f,
            ),
        ),
        Cast.kaito.id to CharacterAppearance(
            name = Cast.kaito.name,
            pronouns = Pronouns.HE,
            presentation = PresentationStyle.MASCULINE,
            hairStyle = HairStyle.SHORT_MESSY,
            skinColor = Palettes.skin("honey").argb,
            hairColor = Palettes.hair("jet").argb,
            eyeColor = Palettes.eye("darkbrown").argb,
            params = mapOf(
                AppearanceParam.EYE_SIZE to 0.6f,
                AppearanceParam.EYE_TILT to 0.3f,
                AppearanceParam.FACE_LENGTH to 0.42f,
                AppearanceParam.JAW_SHARPNESS to 0.4f,
                AppearanceParam.BROW_HEIGHT to 0.62f,
                AppearanceParam.LIP_FULLNESS to 0.5f,
                AppearanceParam.SHOULDER_WIDTH to 0.5f,
            ),
        ),
        Cast.sev.id to CharacterAppearance(
            name = Cast.sev.name,
            pronouns = Pronouns.HE,
            presentation = PresentationStyle.MASCULINE,
            hairStyle = HairStyle.SLICKED_BACK,
            skinColor = Palettes.skin("porcelain").argb,
            hairColor = Palettes.hair("espresso").argb,
            eyeColor = Palettes.eye("grey").argb,
            params = mapOf(
                AppearanceParam.JAW_WIDTH to 0.72f,
                AppearanceParam.JAW_SHARPNESS to 0.78f,
                AppearanceParam.EYE_OPENNESS to 0.32f,
                AppearanceParam.BROW_THICKNESS to 0.62f,
                AppearanceParam.BROW_ANGLE to 0.12f,
                AppearanceParam.MOUTH_WIDTH to 0.45f,
                AppearanceParam.LIP_CURVE to 0.12f,
                AppearanceParam.SHOULDER_WIDTH to 0.72f,
                AppearanceParam.BUILD to 0.66f,
            ),
        ),
        Cast.idris.id to CharacterAppearance(
            name = Cast.idris.name,
            pronouns = Pronouns.HE,
            presentation = PresentationStyle.ANDROGYNOUS,
            hairStyle = HairStyle.CURLY_CLOUD,
            skinColor = Palettes.skin("bronze").argb,
            hairColor = Palettes.hair("caramel").argb,
            eyeColor = Palettes.eye("hazel").argb,
            params = mapOf(
                AppearanceParam.EYE_SIZE to 0.68f,
                AppearanceParam.LIP_CURVE to 0.85f,
                AppearanceParam.MOUTH_WIDTH to 0.7f,
                AppearanceParam.CHEEKBONES to 0.55f,
                AppearanceParam.FRECKLES to 0.2f,
                AppearanceParam.SKIN_GLOW to 0.65f,
            ),
        ),
    )

    fun of(loveInterestId: String): CharacterAppearance =
        looks[loveInterestId] ?: CharacterAppearance(name = loveInterestId)
}
