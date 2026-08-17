package com.deepuniverse.core.store

/** How a rewarded ad ended. */
sealed interface AdResult {
    /** Watched to the end — the reward is earned. */
    data object Watched : AdResult

    /** Closed early. No reward, and no complaint. */
    data object Skipped : AdResult

    /** Nothing to show: no fill, no network, or ads disabled. */
    data class Unavailable(val reason: String) : AdResult
}

/**
 * The seam between the game and an ad network.
 *
 * Same arrangement as [PurchaseGateway]: a real implementation needs an AdMob account and a signed
 * build, so the boundary sits here and the whole retry flow is finished and testable now.
 *
 * **Ads are only ever offered, never forced.** [PuzzleRetry] always leaves a free way forward, so an
 * implementation returning [AdResult.Unavailable] for every call — which is exactly what happens
 * with no network — must never leave a player stuck.
 */
interface AdGateway {
    /** Whether an ad is ready. The UI hides the button rather than showing one that fails. */
    suspend fun isRewardedAdReady(): Boolean

    suspend fun showRewardedAd(): AdResult
}

/** Stands in until a real network is wired up. Reports nothing available, and never blocks. */
class NoOpAdGateway(private val pretendReady: Boolean = true) : AdGateway {

    var timesShown: Int = 0
        private set

    override suspend fun isRewardedAdReady(): Boolean = pretendReady

    override suspend fun showRewardedAd(): AdResult {
        if (!pretendReady) return AdResult.Unavailable("No ad network in this build")
        timesShown++
        return AdResult.Watched
    }
}

/**
 * What a player is offered after failing a puzzle.
 *
 * ### The rule
 * There is **always a free way on**. Paying or watching an ad buys an *immediate* retry; waiting
 * costs nothing but time. A minigame you can be permanently walled behind is the version of this
 * pattern that generates refund requests and store complaints, and it is not needed — "again, now"
 * is a strong enough offer on its own.
 */
data class PuzzleRetry(
    val starCost: Int = STAR_COST,
    val adAvailable: Boolean = false,
) {
    companion object {
        /** Deliberately small: a nuisance to lose, never a wall. */
        const val STAR_COST = 15

        /** Free retries come back with the moments bar, so a patient player pays nothing, ever. */
        const val FREE_RETRY_EXPLANATION =
            "Or come back when you have another moment spare — retrying later is always free."
    }
}
