package com.deepuniverse.core.game

import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.character.Expression
import com.deepuniverse.core.store.Boost
import com.deepuniverse.core.store.Wallet
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
    /** Faces earned, per character. The main collection. */
    val unlockedExpressions: Map<String, Set<Expression>> = emptyMap(),
    val stamina: Stamina = Stamina(),
    val wallet: Wallet = Wallet(),
    val boosts: List<Boost> = emptyList(),
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

    fun rankFor(loveInterestId: String): Int = Bond.rankFor(affectionFor(loveInterestId))

    /**
     * Faces earned with this character.
     *
     * Derived from the bond rank *as well as* read from storage. Affection is granted from several
     * places — story choices, moments together, and possibly gifts later — and only one of them
     * used to record unlocks, so a player who levelled up purely through story choices earned no
     * faces at all. Deriving from rank makes that impossible by construction; the stored set then
     * only has to carry anything granted outside the rank curve.
     */
    fun expressionsFor(loveInterestId: String): Set<Expression> =
        (unlockedExpressions[loveInterestId] ?: emptySet()) +
            Expression.unlockedAt(Bond.rankFor(affectionFor(loveInterestId))) +
            Expression.NEUTRAL

    fun withUnlockedExpressions(loveInterestId: String, faces: List<Expression>): GameState {
        if (faces.isEmpty()) return this
        val existing = unlockedExpressions[loveInterestId] ?: emptySet()
        return copy(unlockedExpressions = unlockedExpressions + (loveInterestId to existing + faces))
    }

    /** Total faces collected across the whole cast, for the gallery's counter. */
    val collectedExpressionCount: Int
        get() = unlockedExpressions.values.sumOf { it.size }

    fun withBoost(boost: Boost, nowEpochSeconds: Long): GameState = copy(
        // Drop anything expired while we are here, so the list cannot grow without bound.
        boosts = boosts.filter { it.isActive(nowEpochSeconds) && it.kind != boost.kind } + boost,
    )

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
