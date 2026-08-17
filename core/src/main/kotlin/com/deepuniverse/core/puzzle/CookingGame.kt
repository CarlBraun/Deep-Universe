package com.deepuniverse.core.puzzle

import kotlin.math.abs
import kotlin.random.Random

/**
 * Keep the heat inside the line — the Stardew-style hold-and-balance minigame.
 *
 * You press and hold to raise the heat and release to let it fall; a target band drifts up and down
 * the gauge and you try to keep the heat inside it. Progress fills while you are in the band and
 * drains while you are out, and the dish is done when the bar fills.
 *
 * ### Why it is stepped rather than timed
 * The whole thing advances through [step], which takes the elapsed milliseconds and whether the
 * finger is down. It owns no clock and no frame loop, so the physics — the gravity, the drain rate,
 * how forgiving the band is — are unit-tested at exact timings instead of being tuned by feel on a
 * device and hoped about. The UI's only job is to call [step] once a frame and draw the numbers.
 *
 * All positions are `0f..1f` up the gauge.
 */
data class CookingGame(
    val heat: Float = 0.25f,
    /** Upward velocity of the heat, per second. */
    val heatVelocity: Float = 0f,
    val bandCentre: Float = 0.5f,
    val bandHalfHeight: Float,
    /** Where the band is drifting to; a new one is picked when it arrives. */
    val bandTarget: Float = 0.5f,
    val progress: Float = 0f,
    val elapsedMillis: Long = 0,
    val timeLimitMillis: Long,
    val outcome: PuzzleOutcome = PuzzleOutcome.IN_PROGRESS,
    private val seed: Int = 0,
    private val bandMoves: Int = 0,
) {
    val inBand: Boolean get() = abs(heat - bandCentre) <= bandHalfHeight

    /** How much time is left, for the countdown. */
    val millisRemaining: Long get() = (timeLimitMillis - elapsedMillis).coerceAtLeast(0)

    /** Which plate the cook chose, so the same pot always produces the same dinner. */
    val dishSeed: Int get() = seed

    /**
     * How good the dish is, from how much of the clock was still on it.
     *
     * There is no separate scoring pass: holding the heat on the line fills the bar quickly, so
     * finishing early *is* cooking it well. That keeps the reward honest — you cannot be told the
     * dinner is excellent after a cook that felt like a scramble.
     */
    val quality: DishQuality
        get() {
            val used = if (timeLimitMillis <= 0L) 1f else elapsedMillis.toFloat() / timeLimitMillis
            return when {
                used <= 0.45f -> DishQuality.EXCELLENT
                used <= 0.72f -> DishQuality.GOOD
                else -> DishQuality.PASSABLE
            }
        }

    /**
     * Advances the pot by [deltaMillis].
     *
     * @param holding true while the player's finger is on the pot.
     */
    fun step(deltaMillis: Long, holding: Boolean): CookingGame {
        if (outcome != PuzzleOutcome.IN_PROGRESS) return this
        val dt = (deltaMillis.coerceIn(0, 100)) / 1000f

        // Heat behaves like something buoyant: holding pushes it up, gravity pulls it down, and
        // drag stops it oscillating wildly. Those three constants are the entire feel of the game.
        val push = if (holding) LIFT else 0f
        var velocity = (heatVelocity + (push - GRAVITY) * dt) * (1f - DRAG * dt)
        var position = heat + velocity * dt

        // The ends of the gauge are soft walls, so slamming into them does not fling the heat back.
        if (position <= 0f) {
            position = 0f
            velocity = velocity.coerceAtLeast(0f)
        } else if (position >= 1f) {
            position = 1f
            velocity = velocity.coerceAtMost(0f)
        }

        val nextProgress = if (abs(position - bandCentre) <= bandHalfHeight) {
            progress + FILL_PER_SECOND * dt
        } else {
            // Draining is slower than filling, so one slip does not undo a good run.
            progress - DRAIN_PER_SECOND * dt
        }.coerceIn(0f, 1f)

        val nextElapsed = elapsedMillis + deltaMillis
        val (centre, target, moves) = driftBand(dt)

        return copy(
            heat = position,
            heatVelocity = velocity,
            bandCentre = centre,
            bandTarget = target,
            bandMoves = moves,
            progress = nextProgress,
            elapsedMillis = nextElapsed,
            outcome = when {
                nextProgress >= 1f -> PuzzleOutcome.WON
                nextElapsed >= timeLimitMillis -> PuzzleOutcome.LOST
                else -> PuzzleOutcome.IN_PROGRESS
            },
        )
    }

    /** Moves the band towards its target, choosing a new one when it arrives. */
    private fun driftBand(dt: Float): Triple<Float, Float, Int> {
        val direction = bandTarget - bandCentre
        val stepSize = BAND_SPEED * dt
        if (abs(direction) <= stepSize) {
            // Arrived. Pick the next spot, seeded so a given cook is always the same challenge.
            val random = Random(seed + bandMoves)
            val next = (0.15f + random.nextFloat() * 0.7f)
            return Triple(bandTarget, next, bandMoves + 1)
        }
        val moved = bandCentre + stepSize * if (direction > 0) 1f else -1f
        return Triple(moved.coerceIn(0f, 1f), bandTarget, bandMoves)
    }

    companion object {
        /** Upward acceleration while held, per second. */
        private const val LIFT = 2.4f
        private const val GRAVITY = 1.2f
        private const val DRAG = 2.2f
        private const val FILL_PER_SECOND = 0.30f
        private const val DRAIN_PER_SECOND = 0.18f
        private const val BAND_SPEED = 0.16f

        /**
         * A pot sized to the cook's skill: the better you know each other, the tighter the band and
         * the less time you get.
         */
        fun new(rank: Int, seed: Int): CookingGame {
            val tightness = (rank * 0.006f).coerceAtMost(0.06f)
            return CookingGame(
                bandHalfHeight = (0.16f - tightness).coerceAtLeast(0.08f),
                timeLimitMillis = (30_000L - rank * 400L).coerceAtLeast(18_000L),
                seed = seed,
                bandTarget = 0.7f,
            )
        }
    }
}
