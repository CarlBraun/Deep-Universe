package com.deepuniverse.core.game

import kotlinx.serialization.Serializable
import kotlin.math.min

/**
 * "Moments" — the resource spent on repeatable interactions.
 *
 * This is the pacing mechanism for an endless game, and the thing a purchase can shortcut. Two
 * decisions worth stating plainly:
 *
 * - **Regeneration is computed from a timestamp, not ticked.** The game does not need to be running
 *   for moments to come back, and there is no background timer to get wrong. Reopening the app a day
 *   later simply recomputes.
 * - **Story scenes never cost a moment.** Only the repeatable "spend time together" interaction
 *   does. A player who has run out can still see every piece of written content, so the resource
 *   paces the grind without ever gating the actual game behind a wallet.
 */
@Serializable
data class Stamina(
    val spent: Int = 0,
    val max: Int = 20,
    /** When the currently-regenerating moment started coming back. */
    val lastSpentAtEpochSeconds: Long = 0L,
) {
    companion object {
        /** One moment back every five minutes; a full bar in a little under two hours. */
        const val REGEN_SECONDS = 300L

        /**
         * The state as of [nowEpochSeconds], with elapsed regeneration applied.
         *
         * Always call this before reading [available] — the stored value is only correct at the
         * instant it was written.
         */
        fun regenerated(stamina: Stamina, nowEpochSeconds: Long): Stamina {
            if (stamina.spent <= 0) return stamina.copy(spent = 0)
            val elapsed = nowEpochSeconds - stamina.lastSpentAtEpochSeconds
            if (elapsed <= 0) return stamina
            val recovered = (elapsed / REGEN_SECONDS).toInt()
            if (recovered <= 0) return stamina
            val stillSpent = (stamina.spent - recovered).coerceAtLeast(0)
            return stamina.copy(
                spent = stillSpent,
                // Keep the remainder, so partial progress towards the next moment is not lost by
                // opening the app.
                lastSpentAtEpochSeconds = if (stillSpent == 0) {
                    nowEpochSeconds
                } else {
                    stamina.lastSpentAtEpochSeconds + recovered * REGEN_SECONDS
                },
            )
        }
    }

    val available: Int get() = (max - spent).coerceIn(0, max)

    val isFull: Boolean get() = spent <= 0

    fun canSpend(amount: Int = 1): Boolean = available >= amount

    fun spend(amount: Int = 1, nowEpochSeconds: Long): Stamina {
        if (!canSpend(amount)) return this
        val wasFull = isFull
        return copy(
            spent = min(max, spent + amount),
            // The regeneration clock only starts when the bar leaves full.
            lastSpentAtEpochSeconds = if (wasFull) nowEpochSeconds else lastSpentAtEpochSeconds,
        )
    }

    fun refilled(nowEpochSeconds: Long): Stamina =
        copy(spent = 0, lastSpentAtEpochSeconds = nowEpochSeconds)

    /** Seconds until one more moment is available, or null when the bar is full. */
    fun secondsUntilNext(nowEpochSeconds: Long): Long? {
        if (isFull) return null
        val elapsed = (nowEpochSeconds - lastSpentAtEpochSeconds).coerceAtLeast(0)
        return (REGEN_SECONDS - elapsed % REGEN_SECONDS).coerceIn(0, REGEN_SECONDS)
    }

    /** Seconds until the bar is completely full, or null when it already is. */
    fun secondsUntilFull(nowEpochSeconds: Long): Long? {
        if (isFull) return null
        val next = secondsUntilNext(nowEpochSeconds) ?: return null
        return next + (spent - 1).coerceAtLeast(0) * REGEN_SECONDS
    }
}
