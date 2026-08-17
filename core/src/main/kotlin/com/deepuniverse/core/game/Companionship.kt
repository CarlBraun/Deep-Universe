package com.deepuniverse.core.game

import com.deepuniverse.core.character.Expression
import com.deepuniverse.core.store.Boost
import com.deepuniverse.core.store.BoostKind

/** What came of spending a moment with someone. */
sealed interface CompanionResult {

    data class Shared(
        val state: GameState,
        val pointsGained: Int,
        val boosted: Boolean,
        val line: String,
        /** Set when this pushed them over into a new rank. */
        val newRank: Int?,
        /** Set when the new rank unlocked a face. */
        val unlockedExpression: Expression?,
    ) : CompanionResult

    /** Out of moments. [secondsUntilNext] tells the player exactly how long to wait. */
    data class OutOfMoments(val secondsUntilNext: Long?) : CompanionResult
}

/**
 * The repeatable loop: spend a moment with someone, gain bond points, occasionally earn a new face.
 *
 * This is what makes the game endless once the written scenes run out. It is deliberately modest —
 * a few points and a line of dialogue — because its job is to be a pleasant thing to do daily, not
 * to replace the story. Scenes remain the reason to play; this is the reason to come back.
 */
object Companionship {

    /** Base points for one moment together. */
    const val BASE_POINTS = 3

    fun spendMomentWith(
        state: GameState,
        loveInterestId: String,
        nowEpochSeconds: Long,
    ): CompanionResult {
        val stamina = Stamina.regenerated(state.stamina, nowEpochSeconds)
        if (!stamina.canSpend()) {
            return CompanionResult.OutOfMoments(stamina.secondsUntilNext(nowEpochSeconds))
        }

        val boosted = state.activeBoost(BoostKind.AFFECTION_DOUBLE, nowEpochSeconds) != null
        val points = if (boosted) BASE_POINTS * BoostKind.AFFECTION_DOUBLE.multiplier else BASE_POINTS

        val before = state.affectionFor(loveInterestId)
        val rankBefore = Bond.rankFor(before)

        var next = state
            .copy(stamina = stamina.spend(nowEpochSeconds = nowEpochSeconds))
            .withAffection(loveInterestId, points)

        val after = next.affectionFor(loveInterestId)
        val rankAfter = Bond.rankFor(after)

        var unlocked: Expression? = null
        if (rankAfter > rankBefore) {
            // Unlock every face this rank jump crossed, and report the newest.
            val newlyAvailable = Expression.unlockedAt(rankAfter)
                .filter { it.unlockRank > rankBefore }
            if (newlyAvailable.isNotEmpty()) {
                next = next.withUnlockedExpressions(loveInterestId, newlyAvailable)
                unlocked = newlyAvailable.maxByOrNull { it.unlockRank }
            }
        }

        return CompanionResult.Shared(
            state = next,
            pointsGained = points,
            boosted = boosted,
            line = lineFor(loveInterestId, rankAfter),
            newRank = if (rankAfter > rankBefore) rankAfter else null,
            unlockedExpression = unlocked,
        )
    }

    /**
     * A passing line, picked by who they are and how close you are.
     *
     * Warmth scales with the bond and nothing else — never with what the player has spent.
     */
    private fun lineFor(loveInterestId: String, rank: Int): String {
        val lines = LINES[loveInterestId] ?: DEFAULT_LINES
        val band = when {
            rank <= 1 -> 0
            rank <= 3 -> 1
            rank <= 5 -> 2
            else -> 3
        }
        return lines[band.coerceIn(lines.indices)]
    }

    private val DEFAULT_LINES = listOf(
        "They nod at you across the camp.",
        "They make room without being asked.",
        "They save you the good seat.",
        "They look up before you speak, every time.",
    )

    private val LINES: Map<String, List<String>> = mapOf(
        "lyra" to listOf(
            "\"You're still here. Huh.\"",
            "\"Sit. I'm not done telling you about the landing.\"",
            "\"I fly better on days I've seen you. Don't put that in a report.\"",
            "\"Come here. No reason. That's allowed now, isn't it?\"",
        ),
        "nadia" to listOf(
            "\"Mind the third tray. It bites.\"",
            "\"Stay. You're quiet in a way I can work through.\"",
            "\"I wrote down something you said. It was useful. That's a compliment.\"",
            "\"I have stopped pretending this is about the samples.\"",
        ),
        "rook" to listOf(
            "\"You again.\" She doesn't sound like she minds.",
            "\"I left the hatch open. Wasn't for anyone in particular.\"",
            "\"Nine years alone, and now I notice when you're late.\"",
            "\"Don't go quiet on me. I've got used to the noise.\"",
        ),
        "kaito" to listOf(
            "\"Oh — hello. Sorry. Hello.\"",
            "\"I saved you the chart with the good handwriting.\"",
            "\"I plotted a route to nowhere today. Just to see if you'd come.\"",
            "\"I stopped checking the exits. I think that means something.\"",
        ),
        "sev" to listOf(
            "\"Pilot.\" It is, from him, a greeting.",
            "\"Sit. The paperwork can watch.\"",
            "\"I have started listening for your step in the corridor.\"",
            "\"Stay. That is not an order. I want to be clear that it is not an order.\"",
        ),
        "idris" to listOf(
            "\"Taste this! No — okay, don't taste that one.\"",
            "\"You're my favourite person to be wrong in front of.\"",
            "\"I fixed the kettle. I fixed it for you. That's the whole story.\"",
            "\"Stay till it burns. It always burns. That's the tradition now.\"",
        ),
    )
}

/** Convenience for reading an active boost off a [GameState]. */
fun GameState.activeBoost(kind: BoostKind, nowEpochSeconds: Long): Boost? =
    boosts.firstOrNull { it.kind == kind && it.isActive(nowEpochSeconds) }
