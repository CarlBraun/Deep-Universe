package com.deepuniverse.core.puzzle

import kotlinx.serialization.Serializable

/** Which minigame a character wants to play with you. */
@Serializable
enum class PuzzleKind(
    val label: String,
    val blurb: String,
    /** Bond points a win is worth, before any boost. */
    val reward: Int,
) {
    MINESWEEPER(
        label = "Sweep the grid",
        blurb = "Old survey software, still the best way to find what is buried.",
        reward = 12,
    ),
    SQUIRREL_HUNT(
        label = "Find the squirrel",
        blurb = "It is in the woods somewhere. It knows you are looking.",
        reward = 9,
    ),
    COOKING(
        label = "Keep the pot right",
        blurb = "Hold the heat inside the line. Let it wander and dinner is ruined.",
        reward = 10,
    ),
}

/** Where a puzzle has got to. */
enum class PuzzleOutcome { IN_PROGRESS, WON, LOST }

/**
 * The minigames.
 *
 * ### Why they exist
 * Bonding needed something to *do*. Spending a moment is a tap; a puzzle is a few minutes of
 * attention with someone, and paying out roughly three to four times a moment's points makes it the
 * efficient way to grow close without making the plain interaction pointless.
 *
 * ### Why the rules live here
 * Every one of these is a pure state machine: input in, new state out, no clock of its own and no
 * drawing. That means the fiddly parts — flood fill, first-click safety, the heat gauge's drain —
 * are unit-tested rather than eyeballed on a phone, which is the only practical way to know a
 * minigame is fair before shipping it.
 */
object Puzzles {

    /** Difficulty scales gently with how well you already know someone. */
    fun mineCountFor(rank: Int): Int = (6 + rank / 3).coerceAtMost(14)
}
