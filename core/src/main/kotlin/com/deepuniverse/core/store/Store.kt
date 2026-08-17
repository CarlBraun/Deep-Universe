package com.deepuniverse.core.store

import kotlinx.serialization.Serializable

/**
 * What money buys.
 *
 * ### The rule this catalogue is built on
 * **Nothing here buys affection, and nothing here is required.** Every scene, every character, every
 * expression and every rank is reachable by playing. Purchases buy *time* — a refilled bar, a
 * temporary multiplier — and cosmetics. A player who never spends a penny sees all of it, slower.
 *
 * That is not only the decent way to build a romance game aimed at a broad audience; it is also the
 * arrangement that survives store review and consumer-protection rules, which take a dim view of
 * emotional pressure attached to payment. A character in this game never withholds warmth pending a
 * transaction.
 */
@Serializable
data class SupportTier(
    val id: String,
    val title: String,
    val blurb: String,
    /** Display price. The real price always comes from the platform, never from this string. */
    val fallbackPriceLabel: String,
    val stars: Int,
    /** Extra thrown in on top, shown as a bonus. */
    val bonusStars: Int = 0,
    val highlighted: Boolean = false,
) {
    val totalStars: Int get() = stars + bonusStars
}

/** Something bought with Starlight rather than money. */
@Serializable
data class StoreOffer(
    val id: String,
    val title: String,
    val blurb: String,
    val cost: Int,
)

object StoreCatalog {

    /** The soft currency's name, used everywhere the player sees a number. */
    const val CURRENCY = "Stars"

    val tiers: List<SupportTier> = listOf(
        SupportTier(
            id = "support_small",
            title = "A coffee",
            blurb = "Keeps the lights on at Aurora-9.",
            fallbackPriceLabel = "$1.99",
            stars = 100,
        ),
        SupportTier(
            id = "support_medium",
            title = "A good evening",
            blurb = "The usual way people support the game.",
            fallbackPriceLabel = "$4.99",
            stars = 280,
            bonusStars = 30,
            highlighted = true,
        ),
        SupportTier(
            id = "support_large",
            title = "A whole weekend",
            blurb = "Enough Stars to stop thinking about Stars.",
            fallbackPriceLabel = "$9.99",
            stars = 600,
            bonusStars = 120,
        ),
        SupportTier(
            id = "support_patron",
            title = "Patron",
            blurb = "For people who want the thing to exist. Thank you, genuinely.",
            fallbackPriceLabel = "$19.99",
            stars = 1300,
            bonusStars = 400,
        ),
    )

    val offers: List<StoreOffer> = listOf(
        StoreOffer(
            id = "refill_moments",
            title = "Refill Moments",
            blurb = "Fill the bar now instead of waiting.",
            cost = 40,
        ),
        StoreOffer(
            id = "boost_affection",
            title = "Signal Boost · 1 hour",
            blurb = "Double bond points from time spent together, for an hour.",
            cost = 120,
        ),
        StoreOffer(
            id = "boost_affection_day",
            title = "Signal Boost · 24 hours",
            blurb = "The same, for a day. Better value if you are playing properly.",
            cost = 600,
        ),
    )

    fun tier(id: String): SupportTier? = tiers.firstOrNull { it.id == id }

    fun offer(id: String): StoreOffer? = offers.firstOrNull { it.id == id }
}

/** The player's Starlight and their support history. */
@Serializable
data class Wallet(
    val stars: Int = 0,
    /** Purchases made, ever. Drives the thank-you badge and the spend guard. */
    val purchaseCount: Int = 0,
    /** Real money spent this calendar month, in whole currency units, for the spend guard. */
    val spentThisMonth: Int = 0,
    val monthKey: String = "",
    /** A cap the player set on themselves. Null means none. */
    val selfImposedMonthlyLimit: Int? = null,
) {
    fun canAfford(cost: Int): Boolean = stars >= cost

    fun spend(cost: Int): Wallet =
        if (canAfford(cost)) copy(stars = stars - cost) else this

    fun grant(amount: Int): Wallet = copy(stars = stars + amount)

    /**
     * Records a completed purchase.
     *
     * [monthKey] is supplied by the caller (e.g. "2026-08") rather than read from a clock here, so
     * the rule stays testable and the core module stays free of time-zone handling.
     */
    fun recordPurchase(tier: SupportTier, priceUnits: Int, monthKey: String): Wallet {
        val sameMonth = this.monthKey == monthKey
        return copy(
            stars = stars + tier.totalStars,
            purchaseCount = purchaseCount + 1,
            spentThisMonth = if (sameMonth) spentThisMonth + priceUnits else priceUnits,
            monthKey = monthKey,
        )
    }

    /**
     * Whether a purchase of [priceUnits] would breach the player's own limit.
     *
     * A self-set cap is the one spending control that a game can offer without being patronising:
     * the player chooses it, and it is enforced before the payment sheet ever opens.
     */
    fun wouldExceedSelfLimit(priceUnits: Int, monthKey: String): Boolean {
        val limit = selfImposedMonthlyLimit ?: return false
        val alreadySpent = if (this.monthKey == monthKey) spentThisMonth else 0
        return alreadySpent + priceUnits > limit
    }
}

/** A temporary multiplier bought with Starlight. */
@Serializable
data class Boost(
    val kind: BoostKind,
    val expiresAtEpochSeconds: Long,
) {
    fun isActive(nowEpochSeconds: Long): Boolean = nowEpochSeconds < expiresAtEpochSeconds

    fun secondsRemaining(nowEpochSeconds: Long): Long =
        (expiresAtEpochSeconds - nowEpochSeconds).coerceAtLeast(0)
}

@Serializable
enum class BoostKind(val label: String, val multiplier: Int) {
    AFFECTION_DOUBLE("Signal Boost", 2),
}
