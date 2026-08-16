package com.deepuniverse.core.character

/**
 * Starting looks offered before (and alongside) the photo path.
 *
 * The photo generator is one of three ways into a character — preset, photo, or fully manual — and
 * all three land in the same editor. A player who doesn't want to photograph themselves is never
 * pushed towards it.
 */
data class Preset(val id: String, val label: String, val appearance: CharacterAppearance)

object Presets {

    private fun look(
        label: String,
        id: String,
        presentation: PresentationStyle,
        pronouns: Pronouns,
        hairStyle: HairStyle,
        skin: String,
        hair: String,
        eye: String,
        params: Map<AppearanceParam, Float>,
    ) = Preset(
        id = id,
        label = label,
        appearance = CharacterAppearance(
            name = "Traveller",
            pronouns = pronouns,
            presentation = presentation,
            hairStyle = hairStyle,
            skinColor = Palettes.skin(skin).argb,
            hairColor = Palettes.hair(hair).argb,
            eyeColor = Palettes.eye(eye).argb,
            params = params,
        ),
    )

    val all: List<Preset> = listOf(
        look(
            label = "Starlit",
            id = "starlit",
            presentation = PresentationStyle.FEMININE,
            pronouns = Pronouns.SHE,
            hairStyle = HairStyle.LONG_WAVY,
            skin = "ivory",
            hair = "starlight",
            eye = "violet",
            params = mapOf(
                AppearanceParam.EYE_SIZE to 0.72f,
                AppearanceParam.EYE_TILT to 0.6f,
                AppearanceParam.FACE_LENGTH to 0.45f,
                AppearanceParam.JAW_SHARPNESS to 0.35f,
                AppearanceParam.LIP_FULLNESS to 0.65f,
                AppearanceParam.LASH_LENGTH to 0.7f,
                AppearanceParam.EYE_MAKEUP to 0.5f,
            ),
        ),
        look(
            label = "Ironside",
            id = "ironside",
            presentation = PresentationStyle.MASCULINE,
            pronouns = Pronouns.HE,
            hairStyle = HairStyle.UNDERCUT,
            skin = "amber",
            hair = "jet",
            eye = "grey",
            params = mapOf(
                AppearanceParam.JAW_WIDTH to 0.75f,
                AppearanceParam.JAW_SHARPNESS to 0.8f,
                AppearanceParam.BROW_THICKNESS to 0.72f,
                AppearanceParam.EYE_OPENNESS to 0.4f,
                AppearanceParam.NOSE_BRIDGE to 0.7f,
                AppearanceParam.SHOULDER_WIDTH to 0.75f,
                AppearanceParam.BUILD to 0.7f,
            ),
        ),
        look(
            label = "Driftborn",
            id = "driftborn",
            presentation = PresentationStyle.ANDROGYNOUS,
            pronouns = Pronouns.THEY,
            hairStyle = HairStyle.SHOULDER_LAYERED,
            skin = "honey",
            hair = "nebula",
            eye = "ice",
            params = mapOf(
                AppearanceParam.CHEEKBONES to 0.7f,
                AppearanceParam.EYE_SPACING to 0.58f,
                AppearanceParam.LIP_CURVE to 0.6f,
                AppearanceParam.BROW_ANGLE to 0.4f,
                AppearanceParam.HEIGHT to 0.6f,
            ),
        ),
        look(
            label = "Sunward",
            id = "sunward",
            presentation = PresentationStyle.FEMININE,
            pronouns = Pronouns.SHE,
            hairStyle = HairStyle.CURLY_CLOUD,
            skin = "chestnut",
            hair = "espresso",
            eye = "amber",
            params = mapOf(
                AppearanceParam.FACE_WIDTH to 0.55f,
                AppearanceParam.LIP_FULLNESS to 0.78f,
                AppearanceParam.NOSE_WIDTH to 0.6f,
                AppearanceParam.EYE_SIZE to 0.66f,
                AppearanceParam.FRECKLES to 0.35f,
                AppearanceParam.BLUSH to 0.4f,
            ),
        ),
        look(
            label = "Quiet Orbit",
            id = "quiet_orbit",
            presentation = PresentationStyle.ANDROGYNOUS,
            pronouns = Pronouns.THEY,
            hairStyle = HairStyle.BOB,
            skin = "sand",
            hair = "caramel",
            eye = "green",
            params = mapOf(
                AppearanceParam.EYE_OPENNESS to 0.42f,
                AppearanceParam.BROW_HEIGHT to 0.6f,
                AppearanceParam.MOUTH_WIDTH to 0.42f,
                AppearanceParam.CHIN_LENGTH to 0.45f,
            ),
        ),
        look(
            label = "Longshot",
            id = "longshot",
            presentation = PresentationStyle.MASCULINE,
            pronouns = Pronouns.HE,
            hairStyle = HairStyle.SHORT_MESSY,
            skin = "porcelain",
            hair = "ember",
            eye = "gold",
            params = mapOf(
                AppearanceParam.FACE_LENGTH to 0.62f,
                AppearanceParam.EYE_TILT to 0.68f,
                AppearanceParam.BROW_ANGLE to 0.6f,
                AppearanceParam.LIP_CURVE to 0.7f,
                AppearanceParam.FRECKLES to 0.5f,
            ),
        ),
    )

    fun byId(id: String): Preset = all.first { it.id == id }
}
