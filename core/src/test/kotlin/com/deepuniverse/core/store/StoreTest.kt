package com.deepuniverse.core.store

import com.deepuniverse.core.game.GameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

/**
 * The store is the one part of the game that can take real money, so it gets the strictest tests:
 * a bug here is not a glitch, it is a charge.
 */
class StoreTest {

    private val now = 1_700_000_000L

    @Test
    fun `every tier and offer is well formed`() {
        assertTrue(StoreCatalog.tiers.isNotEmpty())
        val ids = StoreCatalog.tiers.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Duplicate tier ids: $ids")

        for (tier in StoreCatalog.tiers) {
            assertTrue(tier.title.isNotBlank(), "${tier.id} has no title")
            assertTrue(tier.blurb.isNotBlank(), "${tier.id} has no blurb")
            assertTrue(tier.starlight > 0, "${tier.id} grants nothing")
            assertTrue(tier.bonusStarlight >= 0, "${tier.id} has a negative bonus")
            assertTrue(
                NoOpPurchaseGateway.priceUnitsOf(tier) > 0,
                "${tier.id} has an unreadable price label",
            )
        }

        val offerIds = StoreCatalog.offers.map { it.id }
        assertEquals(offerIds.size, offerIds.toSet().size, "Duplicate offer ids: $offerIds")
        for (offer in StoreCatalog.offers) {
            assertTrue(offer.cost > 0, "${offer.id} is free, which is probably a mistake")
        }
    }

    @Test
    fun `bigger tiers are better value, so the pricing is never a trap`() {
        val sorted = StoreCatalog.tiers.sortedBy { NoOpPurchaseGateway.priceUnitsOf(it) }
        var previousRate = 0f
        for (tier in sorted) {
            val rate = tier.totalStarlight.toFloat() / NoOpPurchaseGateway.priceUnitsOf(tier)
            assertTrue(
                rate >= previousRate,
                "${tier.id} gives $rate per unit, worse than the cheaper tier's $previousRate",
            )
            previousRate = rate
        }
    }

    @Test
    fun `only one tier is highlighted, so the nudge stays honest`() {
        assertTrue(StoreCatalog.tiers.count { it.highlighted } <= 1)
    }

    @Test
    fun `the smallest tier can afford something, so the cheapest option is not useless`() {
        val smallest = StoreCatalog.tiers.minBy { NoOpPurchaseGateway.priceUnitsOf(it) }
        val cheapestOffer = StoreCatalog.offers.minBy { it.cost }
        assertTrue(
            smallest.totalStarlight >= cheapestOffer.cost,
            "The cheapest purchase cannot buy the cheapest thing",
        )
    }

    // ---------------------------------------------------------------- wallet

    @Test
    fun `spending Starlight requires having it`() {
        val wallet = Wallet(starlight = 50)
        assertTrue(wallet.canAfford(50))
        assertTrue(!wallet.canAfford(51))
        assertEquals(0, wallet.spend(50).starlight)
        assertEquals(50, wallet.spend(51).starlight, "An unaffordable spend must be a no-op")
    }

    @Test
    fun `a purchase grants the tier's total including its bonus`() {
        val tier = StoreCatalog.tier("support_medium") ?: fail("Missing tier")
        val wallet = Wallet().recordPurchase(tier, priceUnits = 4, monthKey = "2026-08")
        assertEquals(tier.starlight + tier.bonusStarlight, wallet.starlight)
        assertEquals(1, wallet.purchaseCount)
        assertEquals(4, wallet.spentThisMonth)
    }

    @Test
    fun `monthly spend resets when the month rolls over`() {
        val tier = StoreCatalog.tiers.first()
        val august = Wallet().recordPurchase(tier, priceUnits = 5, monthKey = "2026-08")
        val alsoAugust = august.recordPurchase(tier, priceUnits = 5, monthKey = "2026-08")
        assertEquals(10, alsoAugust.spentThisMonth)

        val september = alsoAugust.recordPurchase(tier, priceUnits = 5, monthKey = "2026-09")
        assertEquals(5, september.spentThisMonth, "A new month starts from zero")
        assertEquals(3, september.purchaseCount, "Lifetime count keeps counting")
    }

    @Test
    fun `a self-imposed limit blocks a purchase before it happens`() {
        val wallet = Wallet(selfImposedMonthlyLimit = 10)
            .recordPurchase(StoreCatalog.tiers.first(), priceUnits = 8, monthKey = "2026-08")

        assertTrue(!wallet.wouldExceedSelfLimit(2, "2026-08"), "Exactly at the limit is allowed")
        assertTrue(wallet.wouldExceedSelfLimit(3, "2026-08"), "Over the limit must be blocked")
        assertTrue(
            !wallet.wouldExceedSelfLimit(10, "2026-09"),
            "The limit is monthly, so a new month is clear",
        )
    }

    @Test
    fun `no limit set means nothing is blocked`() {
        assertTrue(!Wallet().wouldExceedSelfLimit(9999, "2026-08"))
    }

    // ---------------------------------------------------------------- boosts

    @Test
    fun `a boost expires exactly when it says it does`() {
        val boost = Boost(BoostKind.AFFECTION_DOUBLE, expiresAtEpochSeconds = now + 60)
        assertTrue(boost.isActive(now))
        assertTrue(boost.isActive(now + 59))
        assertTrue(!boost.isActive(now + 60), "Should be over at the stated second")
        assertEquals(0L, boost.secondsRemaining(now + 999))
    }

    // ---------------------------------------------------------------- gateway

    @Test
    fun `the stub gateway completes a purchase and reports the price`() = runTest {
        val gateway = NoOpPurchaseGateway()
        val tier = StoreCatalog.tiers.first()
        val result = gateway.purchase(tier)
        val success = result as? PurchaseResult.Success ?: fail("Expected success, got $result")
        assertEquals(tier, success.tier)
        assertTrue(success.priceUnits > 0)
        assertEquals(listOf(tier.id), gateway.attempted)
    }

    @Test
    fun `a cancelled purchase grants nothing`() = runTest {
        val gateway = NoOpPurchaseGateway(autoApprove = false)
        val result = gateway.purchase(StoreCatalog.tiers.first())
        assertEquals(PurchaseResult.Cancelled, result)
    }

    // ---------------------------------------------------------------- the promise

    @Test
    fun `nothing purchasable unlocks a story scene or a character`() {
        // The guarantee the whole design rests on: money buys time and cosmetics, never content.
        val everythingBuyable = StoreCatalog.offers.map { it.id }.toSet()
        val contentWords = listOf("scene", "unlock", "character", "route", "story", "chapter")
        for (offer in StoreCatalog.offers) {
            val text = "${offer.id} ${offer.title} ${offer.blurb}".lowercase()
            for (word in contentWords) {
                assertTrue(
                    !text.contains(word),
                    "Offer '${offer.id}' looks like it sells content ('$word'), which the " +
                        "design forbids",
                )
            }
        }
        assertTrue(everythingBuyable.isNotEmpty())
    }

    @Test
    fun `a player who never pays still reaches every face`() {
        // Grinding with no wallet at all must be able to unlock the whole collection.
        val richestFace = com.deepuniverse.core.character.Expression.entries.maxBy { it.unlockRank }
        val pointsNeeded = com.deepuniverse.core.game.Bond.totalPointsFor(richestFace.unlockRank)
        val state = GameState().withAffection("lyra", pointsNeeded)
        assertEquals(0, state.wallet.starlight, "This player has never spent anything")
        assertTrue(
            com.deepuniverse.core.character.Expression.unlockedAt(state.rankFor("lyra"))
                .contains(richestFace),
            "The last face must be reachable without paying",
        )
    }
}
