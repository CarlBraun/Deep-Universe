package com.deepuniverse.core.puzzle

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/** How close the last search was. */
enum class Warmth(val label: String) {
    FOUND("There you are."),
    BURNING("Something moved. Right there."),
    WARM("Leaves rustling nearby."),
    COOL("Quiet over here."),
    COLD("Nothing but pine needles."),
}

/**
 * Find the squirrel: search bushes one at a time, guided by how close you got.
 *
 * The hint is the whole game, so it is deliberately generous — the distance bands mean a player who
 * pays attention converges in a handful of guesses rather than sweeping the grid. A search puzzle
 * that comes down to exhaustion is not fun, and here losing costs an ad or Stars, which makes an
 * unfair one worse than merely dull.
 */
data class SquirrelHunt(
    val width: Int,
    val height: Int,
    val squirrelX: Int,
    val squirrelY: Int,
    val searched: Set<Pair<Int, Int>> = emptySet(),
    val attemptsAllowed: Int,
    val lastWarmth: Warmth? = null,
    val outcome: PuzzleOutcome = PuzzleOutcome.IN_PROGRESS,
) {
    val attemptsUsed: Int get() = searched.size

    val attemptsLeft: Int get() = (attemptsAllowed - attemptsUsed).coerceAtLeast(0)

    fun contains(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    /** Chebyshev distance: diagonals count as one step, which matches how the grid reads. */
    private fun distanceFrom(x: Int, y: Int): Int =
        max(abs(x - squirrelX), abs(y - squirrelY))

    fun warmthAt(x: Int, y: Int): Warmth = when (distanceFrom(x, y)) {
        0 -> Warmth.FOUND
        1 -> Warmth.BURNING
        2 -> Warmth.WARM
        in 3..4 -> Warmth.COOL
        else -> Warmth.COLD
    }

    fun search(x: Int, y: Int): SquirrelHunt {
        if (outcome != PuzzleOutcome.IN_PROGRESS || !contains(x, y)) return this
        // Re-tapping a bush you already checked is a misclick, not a wasted turn.
        if ((x to y) in searched) return this

        val warmth = warmthAt(x, y)
        val nextSearched = searched + (x to y)
        val found = warmth == Warmth.FOUND
        val exhausted = !found && nextSearched.size >= attemptsAllowed

        return copy(
            searched = nextSearched,
            lastWarmth = warmth,
            outcome = when {
                found -> PuzzleOutcome.WON
                exhausted -> PuzzleOutcome.LOST
                else -> PuzzleOutcome.IN_PROGRESS
            },
        )
    }

    companion object {
        fun new(width: Int, height: Int, attempts: Int, seed: Int): SquirrelHunt {
            val random = Random(seed)
            return SquirrelHunt(
                width = width,
                height = height,
                squirrelX = random.nextInt(width),
                squirrelY = random.nextInt(height),
                attemptsAllowed = attempts,
            )
        }
    }
}
