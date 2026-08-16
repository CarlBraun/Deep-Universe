package com.deepuniverse.core.game

import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.world.WorldAtlas
import com.deepuniverse.core.world.WorldPosition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything a save file holds.
 *
 * The player's character is stored inline rather than by reference: a save should survive the
 * player re-running the photo generator or editing sliders, and both simply produce a new
 * [CharacterAppearance] here.
 */
@Serializable
data class GameState(
    val player: CharacterAppearance = CharacterAppearance(),
    val characterCreated: Boolean = false,
    /** Where the player is standing in the overworld, so a save drops them back on the spot. */
    val world: WorldPosition = WorldAtlas.startPosition,
    val affection: Map<String, Int> = emptyMap(),
    val flags: Set<String> = emptySet(),
    val completedScenes: Set<String> = emptySet(),
) {
    fun affectionFor(loveInterestId: String): Int = affection[loveInterestId] ?: 0

    fun levelFor(loveInterestId: String): AffectionLevel =
        AffectionLevel.forPoints(affectionFor(loveInterestId))

    fun withAffection(loveInterestId: String, delta: Int): GameState {
        val next = (affectionFor(loveInterestId) + delta).coerceAtLeast(0)
        return copy(affection = affection + (loveInterestId to next))
    }

    fun withFlag(flag: String): GameState = copy(flags = flags + flag)

    fun withCompletedScene(sceneId: String): GameState =
        copy(completedScenes = completedScenes + sceneId)

    /** True once the player has reached the top bond tier with anyone. */
    val hasBeloved: Boolean
        get() = affection.values.any { AffectionLevel.forPoints(it) == AffectionLevel.BELOVED }

    companion object {
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(state: GameState): String = json.encodeToString(serializer(), state)

        /**
         * Reads a save back. Returns null on corrupt or unreadable data so callers can start a
         * fresh game instead of crashing on launch — a save file is not worth a crash loop.
         */
        fun decode(text: String): GameState? = runCatching {
            json.decodeFromString(serializer(), text)
        }.getOrNull()
    }
}
