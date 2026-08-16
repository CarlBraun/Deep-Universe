package com.deepuniverse.core.game

import com.deepuniverse.core.character.CharacterAppearance

/** Who is speaking a line. */
sealed interface Speaker {
    /** The love interest whose scene this is. */
    data object Partner : Speaker

    /** The player character, using their chosen name. */
    data object Player : Speaker

    /** Un-attributed prose. */
    data object Narrator : Speaker

    /** Anyone else — station announcements, background crew. */
    data class Other(val name: String) : Speaker
}

/** One option at a choice point. */
data class Choice(
    val text: String,
    /** Affection granted with the scene's love interest. May be negative. */
    val affection: Int = 0,
    /** Optional immediate reply, shown before the scene continues. */
    val reply: String? = null,
    val setsFlag: String? = null,
)

/** A single step of a scene. */
sealed interface Beat {
    data class Say(val speaker: Speaker, val text: String) : Beat
    data class Narrate(val text: String) : Beat
    data class Ask(val prompt: String, val options: List<Choice>) : Beat
}

/**
 * A self-contained story scene on one character's route.
 *
 * @param requiredLevel bond tier the player must have reached with [loveInterestId] before this
 *   scene appears.
 * @param requiresFlags flags that must all be set — used for scenes that only make sense after a
 *   specific earlier choice.
 */
data class Scene(
    val id: String,
    val loveInterestId: String,
    val title: String,
    val summary: String,
    val requiredLevel: AffectionLevel = AffectionLevel.STRANGER,
    val requiresFlags: Set<String> = emptySet(),
    val beats: List<Beat>,
) {
    init {
        require(beats.isNotEmpty()) { "Scene $id has no beats" }
    }
}

/**
 * Substitutes player details into scene text.
 *
 * Story content is written once and reads correctly for any player, so every line goes through
 * here. Supported tokens: `{name}`, `{they}`, `{them}`, `{their}`, `{are}`, and the capitalised
 * `{They}`, `{Them}`, `{Their}`.
 */
object TextTemplate {

    fun render(text: String, player: CharacterAppearance): String {
        if ('{' !in text) return text
        val p = player.pronouns
        return text
            .replace("{name}", player.name)
            .replace("{they}", p.subject)
            .replace("{them}", p.objectForm)
            .replace("{their}", p.possessive)
            .replace("{are}", p.toBe)
            .replace("{They}", p.subject.replaceFirstChar { it.uppercase() })
            .replace("{Them}", p.objectForm.replaceFirstChar { it.uppercase() })
            .replace("{Their}", p.possessive.replaceFirstChar { it.uppercase() })
    }
}
