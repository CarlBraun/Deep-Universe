package com.deepuniverse.core.character

import com.deepuniverse.core.color.blendArgb
import kotlinx.serialization.Serializable

/** Pronoun set for the player character. Purely cosmetic — every story branch supports all three. */
@Serializable
enum class Pronouns(val subject: String, val objectForm: String, val possessive: String, val label: String) {
    SHE("she", "her", "her", "she/her"),
    HE("he", "him", "his", "he/him"),
    THEY("they", "them", "their", "they/them"),
    ;

    /** "they are" vs "she is" — keeps generated story lines grammatical. */
    val toBe: String get() = if (this == THEY) "are" else "is"
}

/**
 * How the character is styled. This is deliberately separate from [Pronouns]: the player picks
 * pronouns and silhouette independently, so any combination is valid.
 */
@Serializable
enum class PresentationStyle(val label: String) {
    FEMININE("Feminine"),
    MASCULINE("Masculine"),
    ANDROGYNOUS("Androgynous"),
}

@Serializable
enum class HairStyle(val label: String, val presentationHint: PresentationStyle) {
    LONG_STRAIGHT("Long straight", PresentationStyle.FEMININE),
    LONG_WAVY("Long wavy", PresentationStyle.FEMININE),
    TWIN_TAILS("Twin tails", PresentationStyle.FEMININE),
    BOB("Bob", PresentationStyle.ANDROGYNOUS),
    SHOULDER_LAYERED("Shoulder layered", PresentationStyle.ANDROGYNOUS),
    CURLY_CLOUD("Curly cloud", PresentationStyle.ANDROGYNOUS),
    UNDERCUT("Undercut", PresentationStyle.MASCULINE),
    SHORT_MESSY("Short messy", PresentationStyle.MASCULINE),
    SLICKED_BACK("Slicked back", PresentationStyle.MASCULINE),
    BUZZ("Buzz cut", PresentationStyle.MASCULINE),
}

/**
 * The complete, serialisable description of a player character's look.
 *
 * Colours are packed ARGB [Int]s so that this module stays free of any Android dependency and can
 * be unit-tested on the JVM.
 */
@Serializable
data class CharacterAppearance(
    val name: String = "Traveller",
    val pronouns: Pronouns = Pronouns.THEY,
    val presentation: PresentationStyle = PresentationStyle.ANDROGYNOUS,
    val hairStyle: HairStyle = HairStyle.SHOULDER_LAYERED,
    val skinColor: Int = Palettes.skinTones[3].argb,
    val hairColor: Int = Palettes.hairColors[1].argb,
    val eyeColor: Int = Palettes.eyeColors[0].argb,
    val params: Map<AppearanceParam, Float> = emptyMap(),
) {
    /** Value of [param], falling back to its neutral default, always clamped to `0f..1f`. */
    operator fun get(param: AppearanceParam): Float =
        (params[param] ?: param.default).coerceIn(0f, 1f)

    fun with(param: AppearanceParam, value: Float): CharacterAppearance =
        copy(params = params + (param to value.coerceIn(0f, 1f)))

    fun withAll(values: Map<AppearanceParam, Float>): CharacterAppearance =
        copy(params = params + values.mapValues { it.value.coerceIn(0f, 1f) })

    /** Resets one parameter back to its neutral default. */
    fun reset(param: AppearanceParam): CharacterAppearance = copy(params = params - param)

    /** Every parameter with an explicit value, defaults filled in. */
    fun resolvedParams(): Map<AppearanceParam, Float> =
        AppearanceParam.entries.associateWith { this[it] }

    /**
     * Blends towards [other] by [amount] (`0f` = unchanged, `1f` = fully [other]).
     *
     * Used when the player applies a photo-derived face on top of an existing character, and by the
     * "how strongly should the photo apply" strength slider in the creator.
     */
    fun blendTowards(other: CharacterAppearance, amount: Float): CharacterAppearance {
        val t = amount.coerceIn(0f, 1f)
        if (t == 0f) return this
        val blended = AppearanceParam.entries.associateWith { p -> this[p] + (other[p] - this[p]) * t }
        return copy(
            params = blended,
            skinColor = blendArgb(skinColor, other.skinColor, t),
            hairColor = blendArgb(hairColor, other.hairColor, t),
            eyeColor = blendArgb(eyeColor, other.eyeColor, t),
            hairStyle = if (t >= 0.5f) other.hairStyle else hairStyle,
        )
    }
}
