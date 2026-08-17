package com.deepuniverse.core.store

/** How a purchase attempt ended. */
sealed interface PurchaseResult {
    data class Success(val tier: SupportTier, val priceUnits: Int) : PurchaseResult
    data object Cancelled : PurchaseResult
    data class Blocked(val reason: String) : PurchaseResult
    data class Failed(val reason: String) : PurchaseResult
}

/**
 * The seam between the game and whatever is actually taking the money.
 *
 * Real billing needs a Play Console account, a signed release build and products configured on the
 * store, none of which can exist while the game is still being built. Putting the boundary here
 * means the entire economy — prices, grants, spend guards, the UI — is finished and testable now,
 * and shipping for real is one implementation of this interface.
 *
 * Implementations must be safe to call twice: a player who taps through a slow payment sheet should
 * not be charged or granted twice.
 */
interface PurchaseGateway {

    /** Prices as the platform reports them, keyed by tier id. Empty when unavailable. */
    suspend fun priceLabels(): Map<String, String>

    suspend fun purchase(tier: SupportTier): PurchaseResult

    /**
     * Re-grants anything paid for but not delivered — an app killed mid-purchase, a payment that
     * settled later. Called on launch. Without this, a player who is charged during a crash loses
     * what they bought, which is the one bug in a store you can never apologise your way out of.
     */
    suspend fun restorePurchases(): List<PurchaseResult.Success>
}

/**
 * A gateway that takes no money at all.
 *
 * Used until real billing is wired up, and in tests. It grants the goods so the whole loop can be
 * played and reviewed, and it is deliberately obvious about being fake so a build with it in cannot
 * be mistaken for a shipping one.
 */
class NoOpPurchaseGateway(
    private val autoApprove: Boolean = true,
) : PurchaseGateway {

    val attempted = mutableListOf<String>()

    override suspend fun priceLabels(): Map<String, String> = emptyMap()

    override suspend fun purchase(tier: SupportTier): PurchaseResult {
        attempted.add(tier.id)
        return if (autoApprove) {
            PurchaseResult.Success(tier, priceUnits = priceUnitsOf(tier))
        } else {
            PurchaseResult.Cancelled
        }
    }

    override suspend fun restorePurchases(): List<PurchaseResult.Success> = emptyList()

    companion object {
        /**
         * The price in **minor units** (cents), parsed out of the fallback label.
         *
         * Minor units, not whole ones: rounding $1.99 down to $1 makes the cheapest tier look like
         * the best value per Starlight and the catalogue read as a trap. Real billing APIs report
         * prices in micros for exactly this reason, and this stand-in matches that shape so
         * swapping in a real gateway changes nothing downstream.
         */
        fun priceUnitsOf(tier: SupportTier): Int {
            val amount = tier.fallbackPriceLabel.filter { it.isDigit() || it == '.' }
                .toDoubleOrNull() ?: return 0
            return Math.round(amount * 100).toInt()
        }
    }
}
