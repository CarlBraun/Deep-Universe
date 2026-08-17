package com.deepuniverse.core.game

/**
 * Bond ranks: the endless half of progression.
 *
 * [AffectionLevel] still gates story scenes and stops at Beloved, because a *story* has an end.
 * Ranks keep going forever on top of it, which is what gives a player something to do once they
 * have seen everything — and what the collectable faces hang off.
 *
 * ### The curve
 * Each rank costs a little more than the last (`rank * 12` points), so cumulative cost grows
 * quadratically. That is deliberate: early ranks arrive fast enough to teach the loop, and later
 * ones are slow enough that a boost is worth having without any single rank feeling impossible.
 * Nothing is ever locked behind payment — a boost only changes how long it takes.
 */
object Bond {

    /** Points to get from [rank] to `rank + 1`. */
    fun costOfRank(rank: Int): Int = (rank + 1) * 12

    /** Total points needed to have reached [rank]. */
    fun totalPointsFor(rank: Int): Int {
        require(rank >= 0) { "Rank cannot be negative" }
        // Sum of costOfRank(0..rank-1) = 12 * (1 + 2 + ... + rank)
        return 12 * rank * (rank + 1) / 2
    }

    /**
     * Highest rank the search will report.
     *
     * Far beyond anything reachable by playing, but bounded on purpose: without a cap, a large
     * enough points value overflows the threshold arithmetic, the comparison stays true, and the
     * loop never ends — which presents to a player as the app freezing.
     */
    const val MAX_RANK: Int = 10_000

    /** The rank [points] buys. Grows without practical limit. */
    fun rankFor(points: Int): Int {
        if (points <= 0) return 0
        var rank = 0
        while (rank < MAX_RANK && totalPointsFor(rank + 1) <= points) rank++
        return rank
    }

    /** Progress through the current rank, `0f..1f`, for the meter. */
    fun progressWithinRank(points: Int): Float {
        val rank = rankFor(points)
        val floor = totalPointsFor(rank)
        val cost = costOfRank(rank)
        return ((points - floor).toFloat() / cost).coerceIn(0f, 1f)
    }

    /** Points still needed for the next rank. */
    fun pointsToNextRank(points: Int): Int {
        val rank = rankFor(points)
        return totalPointsFor(rank + 1) - points
    }

    /**
     * What the player calls this rank.
     *
     * The first ranks borrow the story tiers so the two systems read as one thing; past the end of
     * the story it becomes a numbered devotion rank, which is honest about being a grind rather
     * than pretending there is more narrative than there is.
     */
    fun titleFor(rank: Int): String = when (rank) {
        0 -> "Stranger"
        1 -> "Acquainted"
        2 -> "Familiar"
        3 -> "Close"
        4 -> "Trusted"
        5 -> "Beloved"
        else -> "Devotion ${roman(rank - 5)}"
    }

    private fun roman(value: Int): String {
        if (value <= 0) return "I"
        val numerals = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
            100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
            10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
        )
        var remaining = value
        val out = StringBuilder()
        for ((number, symbol) in numerals) {
            while (remaining >= number) {
                out.append(symbol)
                remaining -= number
            }
        }
        return out.toString()
    }
}
