package com.deepuniverse.core.puzzle

import com.deepuniverse.core.game.Bond
import com.deepuniverse.core.game.Cast
import com.deepuniverse.core.game.GameState
import com.deepuniverse.core.game.Stamina
import com.deepuniverse.core.game.activeBoost
import com.deepuniverse.core.store.BoostKind

/**
 * Which minigame each character wants to play, and what it costs to say yes.
 *
 * Assignments are thematic rather than arbitrary — the cooks cook, the surveyors sweep grids, the
 * ones who live in the woods look for the squirrel. Playing a character's game should feel like
 * doing something *with them*, not selecting from a menu that happens to be attached to them.
 */
object PuzzleInvite {

    private val assignments: Map<String, PuzzleKind> = mapOf(
        // The kitchens.
        "idris" to PuzzleKind.COOKING,
        "tuli" to PuzzleKind.COOKING,
        // The ones who read grids for a living.
        "nadia" to PuzzleKind.MINESWEEPER,
        "sev" to PuzzleKind.MINESWEEPER,
        "vess" to PuzzleKind.MINESWEEPER,
        "orrin" to PuzzleKind.MINESWEEPER,
        // The ones who find things.
        "rook" to PuzzleKind.SQUIRREL_HUNT,
        "kaito" to PuzzleKind.SQUIRREL_HUNT,
        "lyra" to PuzzleKind.SQUIRREL_HUNT,
    )

    fun kindFor(loveInterestId: String): PuzzleKind =
        assignments[loveInterestId] ?: PuzzleKind.SQUIRREL_HUNT

    /** Every character must have a game, or their route quietly loses half its content. */
    fun allAssigned(): Boolean = Cast.all.all { it.id in assignments }

    /**
     * Points a win is worth, after any active boost.
     *
     * Puzzles pay several times a plain moment because they cost several minutes of attention. If
     * they paid the same, nobody would ever play one twice.
     */
    fun rewardFor(kind: PuzzleKind, state: GameState, nowEpochSeconds: Long): Int {
        val boosted = state.activeBoost(BoostKind.AFFECTION_DOUBLE, nowEpochSeconds) != null
        return if (boosted) kind.reward * BoostKind.AFFECTION_DOUBLE.multiplier else kind.reward
    }

    /** Whether the player can afford to start a game right now. */
    fun canStart(state: GameState, nowEpochSeconds: Long): Boolean =
        Stamina.regenerated(state.stamina, nowEpochSeconds).canSpend()

    /**
     * Takes the moment that starting a game costs.
     *
     * Charged up front rather than on winning, so a loss is not free — that is what makes the retry
     * offer mean anything — but the charge is a moment, never Stars.
     */
    fun start(state: GameState, nowEpochSeconds: Long): GameState {
        val stamina = Stamina.regenerated(state.stamina, nowEpochSeconds)
        return state.copy(stamina = stamina.spend(nowEpochSeconds = nowEpochSeconds))
    }

    /** Applies a win: bond points, and any faces the new rank unlocks. */
    fun win(
        state: GameState,
        loveInterestId: String,
        kind: PuzzleKind,
        nowEpochSeconds: Long,
    ): PuzzleWin {
        val points = rewardFor(kind, state, nowEpochSeconds)
        val rankBefore = state.rankFor(loveInterestId)
        val next = state.withAffection(loveInterestId, points)
        val rankAfter = next.rankFor(loveInterestId)

        val unlocked = com.deepuniverse.core.character.Expression.unlockedAt(rankAfter)
            .filter { it.unlockRank > rankBefore }

        return PuzzleWin(
            state = if (unlocked.isEmpty()) next else next.withUnlockedExpressions(loveInterestId, unlocked),
            points = points,
            newRank = if (rankAfter > rankBefore) rankAfter else null,
            unlockedExpression = unlocked.maxByOrNull { it.unlockRank },
            rankTitle = Bond.titleFor(rankAfter),
        )
    }
}

data class PuzzleWin(
    val state: GameState,
    val points: Int,
    val newRank: Int?,
    val unlockedExpression: com.deepuniverse.core.character.Expression?,
    val rankTitle: String,
)
