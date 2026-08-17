package com.deepuniverse.core.game

import com.deepuniverse.core.character.PresentationStyle
import com.deepuniverse.core.character.Pronouns
import com.deepuniverse.core.color.argb
import kotlinx.serialization.Serializable

/**
 * A romanceable character aboard Aurora-9.
 *
 * The cast is mixed by design — the player romances whoever they like, and no love interest is
 * gated behind the player's own pronouns or presentation.
 */
@Serializable
data class LoveInterest(
    val id: String,
    val name: String,
    val role: String,
    val pronouns: Pronouns,
    val presentation: PresentationStyle,
    val tagline: String,
    val bio: String,
    /** Accent colour for this character's cards, dialogue box and route UI. */
    val themeColor: Int,
    /** False for the people of Uto, who are drawn with their own ears and colouring. */
    val human: Boolean = true,
)

/** Bond tiers, unlocked by accumulating affection. Scene gating reads these. */
enum class AffectionLevel(val label: String, val minPoints: Int) {
    STRANGER("Stranger", 0),
    ACQUAINTED("Acquainted", 10),
    CLOSE("Close", 25),
    TRUSTED("Trusted", 45),
    BELOVED("Beloved", 70),
    ;

    companion object {
        fun forPoints(points: Int): AffectionLevel =
            entries.last { points >= it.minPoints }

        /** Points still needed to reach the next tier, or null at the top. */
        fun pointsToNext(points: Int): Int? =
            entries.firstOrNull { it.minPoints > points }?.let { it.minPoints - points }
    }
}

object Cast {

    val lyra = LoveInterest(
        id = "lyra",
        name = "Lyra Vance",
        role = "Interceptor Pilot",
        pronouns = Pronouns.SHE,
        presentation = PresentationStyle.FEMININE,
        tagline = "Flies like she has nothing to lose. She does, actually.",
        bio = "Aurora-9's best interceptor pilot and its worst patient. Lyra grew up running cargo " +
            "through the Veil and never entirely stopped running. She flirts the way she flies: fast, " +
            "precise, and slightly too close to something that could kill her.",
        themeColor = argb(0xE8, 0x5D, 0x75),
    )

    val nadia = LoveInterest(
        id = "nadia",
        name = "Dr. Nadia Okonkwo",
        role = "Xenobiologist",
        pronouns = Pronouns.SHE,
        presentation = PresentationStyle.FEMININE,
        tagline = "Reads people the way she reads samples: slowly, and completely.",
        bio = "The station's xenobiology lead, and the only person aboard who talks to the Drift " +
            "specimens like colleagues. Nadia is unhurried, exact, and disarmingly direct once she " +
            "decides you are worth the time.",
        themeColor = argb(0x4F, 0xB3, 0x9A),
    )

    val rook = LoveInterest(
        id = "rook",
        name = "Rook",
        role = "Salvage Runner",
        pronouns = Pronouns.SHE,
        presentation = PresentationStyle.ANDROGYNOUS,
        tagline = "No surname on file. No apologies either.",
        bio = "Independent salvage, docked at Aurora-9 more often than her contracts explain. Rook " +
            "keeps a wall between herself and everyone aboard, and has recently started making an " +
            "inconvenient exception.",
        themeColor = argb(0x8B, 0x7A, 0xC4),
    )

    val kaito = LoveInterest(
        id = "kaito",
        name = "Kaito Mori",
        role = "Navigator",
        pronouns = Pronouns.HE,
        presentation = PresentationStyle.MASCULINE,
        tagline = "Charts the Drift by feel. Panics at small talk.",
        bio = "Aurora-9's navigator, and the reason the station's jump record is spotless. Kaito is " +
            "quiet, careful and endlessly patient with everything except himself.",
        themeColor = argb(0x4A, 0x8F, 0xD4),
    )

    val sev = LoveInterest(
        id = "sev",
        name = "Sev Aldair",
        role = "Station Commander",
        pronouns = Pronouns.HE,
        presentation = PresentationStyle.MASCULINE,
        tagline = "Duty first. Everything else in a locked drawer.",
        bio = "Commander of Aurora-9 and the only officer who never filed a complaint about your " +
            "arrival — because he never filed anything about you at all. Sev is exacting, formal, " +
            "and quietly carrying more than the station's manifest.",
        themeColor = argb(0xC9, 0x8B, 0x3E),
    )

    val idris = LoveInterest(
        id = "idris",
        name = "Idris Calloway",
        role = "Chief Engineer",
        pronouns = Pronouns.HE,
        presentation = PresentationStyle.ANDROGYNOUS,
        tagline = "Fixes everything. Occasionally breaks it first, for science.",
        bio = "Chief engineer, menace to the requisition system, and the warmest person on the " +
            "station by a wide margin. Idris will talk your ear off while rewiring a reactor and " +
            "somehow get both right.",
        themeColor = argb(0xE0, 0x9A, 0x4F),
    )

    // ---- Uto -------------------------------------------------------------
    // Met only after finding the ship, so they are absent from a new player's journal.

    val vess = LoveInterest(
        id = "vess",
        name = "Vess Ilorun",
        role = "Tide-Reader of Uto",
        pronouns = Pronouns.SHE,
        presentation = PresentationStyle.FEMININE,
        tagline = "Has read the sky here for two hundred years. You are new weather.",
        bio = "Verdant-skinned, long-eared, and unhurried in the way of someone who measures time " +
            "in tides rather than days. Vess reads the Drift from the other side and has been " +
            "waiting a long while for someone to arrive who could hear it too.",
        themeColor = argb(0x6F, 0xC5, 0x9A),
        human = false,
    )

    val orrin = LoveInterest(
        id = "orrin",
        name = "Orrin Sath",
        role = "Ship-Breaker",
        pronouns = Pronouns.HE,
        presentation = PresentationStyle.MASCULINE,
        tagline = "Takes apart everything that lands. Yours included, if you leave it out.",
        bio = "Amethyst-skinned and finned at the ear, Orrin salvages the wrecks that fall out of " +
            "the Drift onto Uto's flats. He recognised your ship on sight, which is the first " +
            "thing that should worry you and the second thing that should interest you.",
        themeColor = argb(0x9A, 0x7B, 0xD4),
        human = false,
    )

    val tuli = LoveInterest(
        id = "tuli",
        name = "Tuli",
        role = "Keeper of the Long Kitchen",
        pronouns = Pronouns.THEY,
        presentation = PresentationStyle.ANDROGYNOUS,
        tagline = "Feeds anything that arrives. Asks questions afterwards, if at all.",
        bio = "Tidal-blue and tapered-eared, Tuli runs the kitchen that every traveller on Uto ends " +
            "up in eventually. They have fed four species this month and are quietly keeping count " +
            "of which ones came back.",
        themeColor = argb(0x62, 0xA8, 0xC4),
        human = false,
    )

    val all: List<LoveInterest> = listOf(lyra, nadia, rook, kaito, sev, idris, vess, orrin, tuli)

    /** The people of Uto, who only exist in the player's world once they have flown. */
    val utoCast: List<LoveInterest> = all.filter { !it.human }

    fun byId(id: String): LoveInterest =
        all.firstOrNull { it.id == id } ?: error("Unknown love interest: $id")
}
