package com.deepuniverse.core.character

/**
 * Groups of appearance sliders, used to lay the character editor out in tabs.
 */
enum class ParamGroup(val label: String) {
    FACE("Face"),
    EYES("Eyes"),
    BROWS("Brows"),
    NOSE("Nose"),
    MOUTH("Mouth"),
    BODY("Body"),
    DETAILS("Details"),
}

/**
 * Every continuous appearance parameter of a Deep Universe character.
 *
 * Both halves of the character creator write into this one set: the photo analyzer
 * ([com.deepuniverse.core.photo.PhotoToAppearance]) estimates a value for the parameters it can
 * infer from a face, and the manual editor exposes *all* of them as sliders. Because the set is an
 * enum, the editor UI can be generated from it — a new parameter here becomes a new slider with no
 * UI change.
 *
 * Values are always normalised to `0f..1f`. [lowLabel] and [highLabel] describe the ends of the
 * range for the UI; [default] is the neutral, population-average setting.
 */
enum class AppearanceParam(
    val group: ParamGroup,
    val label: String,
    val lowLabel: String,
    val highLabel: String,
    val default: Float = 0.5f,
    /** True when [com.deepuniverse.core.photo.PhotoToAppearance] can estimate this from a photo. */
    val inferredFromPhoto: Boolean = true,
) {
    // ---- Face ----
    FACE_LENGTH(ParamGroup.FACE, "Face length", "Short", "Long"),
    FACE_WIDTH(ParamGroup.FACE, "Face width", "Narrow", "Wide"),
    CHEEKBONES(ParamGroup.FACE, "Cheekbones", "Soft", "High"),
    JAW_WIDTH(ParamGroup.FACE, "Jaw width", "Narrow", "Wide"),
    JAW_SHARPNESS(ParamGroup.FACE, "Jaw definition", "Rounded", "Angular"),
    CHIN_LENGTH(ParamGroup.FACE, "Chin length", "Short", "Long"),

    // ---- Eyes ----
    EYE_SIZE(ParamGroup.EYES, "Eye size", "Small", "Large"),
    EYE_SPACING(ParamGroup.EYES, "Eye spacing", "Close", "Wide"),
    EYE_TILT(ParamGroup.EYES, "Eye tilt", "Downturned", "Upturned"),
    EYE_OPENNESS(ParamGroup.EYES, "Eye openness", "Hooded", "Wide open"),
    EYE_HEIGHT(ParamGroup.EYES, "Eye position", "Low", "High"),
    LASH_LENGTH(ParamGroup.EYES, "Lashes", "Subtle", "Dramatic", default = 0.4f, inferredFromPhoto = false),

    // ---- Brows ----
    BROW_THICKNESS(ParamGroup.BROWS, "Brow thickness", "Thin", "Thick"),
    BROW_HEIGHT(ParamGroup.BROWS, "Brow height", "Low", "High"),
    BROW_ANGLE(ParamGroup.BROWS, "Brow angle", "Flat", "Arched"),

    // ---- Nose ----
    NOSE_WIDTH(ParamGroup.NOSE, "Nose width", "Narrow", "Wide"),
    NOSE_LENGTH(ParamGroup.NOSE, "Nose length", "Short", "Long"),
    NOSE_BRIDGE(ParamGroup.NOSE, "Bridge", "Flat", "Defined"),

    // ---- Mouth ----
    MOUTH_WIDTH(ParamGroup.MOUTH, "Mouth width", "Narrow", "Wide"),
    LIP_FULLNESS(ParamGroup.MOUTH, "Lip fullness", "Thin", "Full"),
    LIP_CURVE(ParamGroup.MOUTH, "Lip curve", "Neutral", "Upturned"),
    MOUTH_HEIGHT(ParamGroup.MOUTH, "Mouth position", "High", "Low"),

    // ---- Body ----
    HEIGHT(ParamGroup.BODY, "Height", "Petite", "Tall", inferredFromPhoto = false),
    BUILD(ParamGroup.BODY, "Build", "Slight", "Broad", inferredFromPhoto = false),
    SHOULDER_WIDTH(ParamGroup.BODY, "Shoulders", "Narrow", "Broad", inferredFromPhoto = false),

    // ---- Details ----
    FRECKLES(ParamGroup.DETAILS, "Freckles", "None", "Heavy", default = 0f, inferredFromPhoto = false),
    BLUSH(ParamGroup.DETAILS, "Blush", "None", "Rosy", default = 0.25f, inferredFromPhoto = false),
    EYE_MAKEUP(ParamGroup.DETAILS, "Eye makeup", "Bare", "Bold", default = 0.2f, inferredFromPhoto = false),
    SKIN_GLOW(ParamGroup.DETAILS, "Skin glow", "Matte", "Dewy", default = 0.5f, inferredFromPhoto = false),
    ;

    companion object {
        /** Parameters the photo analyzer is able to estimate, in declaration order. */
        val photoInferable: List<AppearanceParam> get() = entries.filter { it.inferredFromPhoto }

        fun inGroup(group: ParamGroup): List<AppearanceParam> = entries.filter { it.group == group }
    }
}
