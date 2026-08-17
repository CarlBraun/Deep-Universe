package com.deepuniverse.core.game

import com.deepuniverse.core.character.Expression
import com.deepuniverse.core.store.Boost
import com.deepuniverse.core.store.BoostKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ProgressionTest {

    private val now = 1_700_000_000L

    // ---------------------------------------------------------------- bond ranks

    @Test
    fun `rank rises without any ceiling`() {
        assertEquals(0, Bond.rankFor(0))
        assertEquals(1, Bond.rankFor(Bond.totalPointsFor(1)))
        assertEquals(10, Bond.rankFor(Bond.totalPointsFor(10)))
        assertEquals(50, Bond.rankFor(Bond.totalPointsFor(50)))
        // The whole point of the mode: there is no last rank.
        assertTrue(Bond.rankFor(1_000_000) > 100)
    }

    @Test
    fun `rank and total points agree in both directions`() {
        for (rank in 0..60) {
            val floor = Bond.totalPointsFor(rank)
            assertEquals(rank, Bond.rankFor(floor), "Exactly at the threshold for $rank")
            assertEquals(rank, Bond.rankFor(floor + 1), "Just past the threshold for $rank")
            if (rank > 0) {
                assertEquals(rank - 1, Bond.rankFor(floor - 1), "Just short of $rank")
            }
        }
    }

    @Test
    fun `each rank costs more than the one before it`() {
        for (rank in 0..40) {
            assertTrue(
                Bond.costOfRank(rank + 1) > Bond.costOfRank(rank),
                "Rank ${rank + 1} should cost more than $rank",
            )
        }
    }

    @Test
    fun `progress through a rank runs cleanly from zero to one`() {
        val floor = Bond.totalPointsFor(4)
        val cost = Bond.costOfRank(4)
        assertEquals(0f, Bond.progressWithinRank(floor), 1e-4f)
        assertEquals(0.5f, Bond.progressWithinRank(floor + cost / 2), 0.05f)
        assertEquals(0, Bond.pointsToNextRank(floor + cost).let { Bond.rankFor(floor + cost) - 5 })
        for (points in 0..600) {
            val p = Bond.progressWithinRank(points)
            assertTrue(p in 0f..1f, "Progress out of range at $points: $p")
        }
    }

    @Test
    fun `points to the next rank counts down to zero`() {
        val floor = Bond.totalPointsFor(3)
        val cost = Bond.costOfRank(3)
        assertEquals(cost, Bond.pointsToNextRank(floor))
        assertEquals(1, Bond.pointsToNextRank(floor + cost - 1))
    }

    @Test
    fun `rank titles follow the story tiers and then become devotion ranks`() {
        assertEquals("Stranger", Bond.titleFor(0))
        assertEquals("Beloved", Bond.titleFor(5))
        assertEquals("Devotion I", Bond.titleFor(6))
        assertEquals("Devotion IV", Bond.titleFor(9))
        assertEquals("Devotion X", Bond.titleFor(15))
        // Never blank, however far it goes.
        for (rank in 0..200) assertTrue(Bond.titleFor(rank).isNotBlank())
    }

    // ---------------------------------------------------------------- moments

    @Test
    fun `spending a moment reduces what is available`() {
        val stamina = Stamina(max = 20)
        assertEquals(20, stamina.available)
        val after = stamina.spend(nowEpochSeconds = now)
        assertEquals(19, after.available)
    }

    @Test
    fun `moments come back over time without the game running`() {
        var stamina = Stamina(max = 20)
        repeat(5) { stamina = stamina.spend(nowEpochSeconds = now) }
        assertEquals(15, stamina.available)

        val later = now + Stamina.REGEN_SECONDS * 3
        val regenerated = Stamina.regenerated(stamina, later)
        assertEquals(18, regenerated.available, "Three regen periods should return three moments")
    }

    @Test
    fun `regeneration never overfills the bar`() {
        var stamina = Stamina(max = 20)
        repeat(3) { stamina = stamina.spend(nowEpochSeconds = now) }
        val muchLater = now + Stamina.REGEN_SECONDS * 500
        val regenerated = Stamina.regenerated(stamina, muchLater)
        assertEquals(20, regenerated.available)
        assertTrue(regenerated.isFull)
    }

    @Test
    fun `partial progress towards the next moment survives reopening the app`() {
        var stamina = Stamina(max = 20)
        stamina = stamina.spend(nowEpochSeconds = now)
        stamina = stamina.spend(nowEpochSeconds = now)

        // Most of the way to one moment, then checked repeatedly, as an app being reopened would.
        val almost = now + Stamina.REGEN_SECONDS - 5
        val a = Stamina.regenerated(stamina, almost)
        assertEquals(18, a.available, "Not quite a full period yet")

        val justAfter = now + Stamina.REGEN_SECONDS + 1
        val b = Stamina.regenerated(a, justAfter)
        assertEquals(19, b.available, "Checking early must not reset the clock")
    }

    @Test
    fun `a full bar reports no waiting time`() {
        val stamina = Stamina(max = 20)
        assertNull(stamina.secondsUntilNext(now))
        assertNull(stamina.secondsUntilFull(now))
    }

    @Test
    fun `the wait until full accounts for every missing moment`() {
        var stamina = Stamina(max = 20)
        repeat(4) { stamina = stamina.spend(nowEpochSeconds = now) }
        val untilFull = stamina.secondsUntilFull(now) ?: fail("Expected a wait")
        assertEquals(Stamina.REGEN_SECONDS * 4, untilFull)
    }

    @Test
    fun `an empty bar refuses to spend`() {
        var stamina = Stamina(max = 3)
        repeat(3) { stamina = stamina.spend(nowEpochSeconds = now) }
        assertEquals(0, stamina.available)
        assertTrue(!stamina.canSpend())
        assertEquals(0, stamina.spend(nowEpochSeconds = now).available, "Spending must be a no-op")
    }

    // ---------------------------------------------------------------- the loop

    @Test
    fun `spending time together grants points and costs a moment`() {
        val state = GameState(characterCreated = true)
        val result = Companionship.spendMomentWith(state, "lyra", now)
        val shared = result as? CompanionResult.Shared ?: fail("Expected to share a moment")

        assertEquals(Companionship.BASE_POINTS, shared.pointsGained)
        assertEquals(Companionship.BASE_POINTS, shared.state.affectionFor("lyra"))
        assertEquals(19, shared.state.stamina.available)
        assertTrue(shared.line.isNotBlank())
        assertTrue(!shared.boosted)
    }

    @Test
    fun `running out of moments is reported with the wait, not a crash`() {
        var state = GameState(characterCreated = true, stamina = Stamina(max = 2))
        repeat(2) {
            state = (Companionship.spendMomentWith(state, "lyra", now) as CompanionResult.Shared).state
        }
        val result = Companionship.spendMomentWith(state, "lyra", now)
        val out = result as? CompanionResult.OutOfMoments ?: fail("Expected to be out of moments")
        assertNotNull(out.secondsUntilNext, "The player should be told how long to wait")
    }

    @Test
    fun `a boost doubles points while it lasts and not after`() {
        val boosted = GameState(characterCreated = true)
            .withBoost(Boost(BoostKind.AFFECTION_DOUBLE, now + 3600), now)

        val during = Companionship.spendMomentWith(boosted, "lyra", now) as CompanionResult.Shared
        assertEquals(Companionship.BASE_POINTS * 2, during.pointsGained)
        assertTrue(during.boosted)

        val after = Companionship.spendMomentWith(boosted, "lyra", now + 7200) as CompanionResult.Shared
        assertEquals(Companionship.BASE_POINTS, after.pointsGained, "An expired boost must not apply")
        assertTrue(!after.boosted)
    }

    @Test
    fun `a boost cannot be stacked with itself to multiply forever`() {
        val state = GameState(characterCreated = true)
            .withBoost(Boost(BoostKind.AFFECTION_DOUBLE, now + 3600), now)
            .withBoost(Boost(BoostKind.AFFECTION_DOUBLE, now + 7200), now)

        assertEquals(1, state.boosts.count { it.kind == BoostKind.AFFECTION_DOUBLE })
        val result = Companionship.spendMomentWith(state, "lyra", now) as CompanionResult.Shared
        assertEquals(Companionship.BASE_POINTS * 2, result.pointsGained, "Still exactly double")
    }

    @Test
    fun `expired boosts are cleaned up rather than accumulating`() {
        var state = GameState(characterCreated = true)
        repeat(10) { i ->
            state = state.withBoost(Boost(BoostKind.AFFECTION_DOUBLE, now + i), now + i)
        }
        assertTrue(state.boosts.size <= 1, "Boost list grew to ${state.boosts.size}")
    }

    // ---------------------------------------------------------------- collection

    @Test
    fun `new faces unlock as the bond deepens`() {
        var state = GameState(characterCreated = true, stamina = Stamina(max = 10_000))
        val unlocked = mutableListOf<Expression>()

        repeat(400) {
            val result = Companionship.spendMomentWith(state, "kaito", now)
            val shared = result as? CompanionResult.Shared ?: return@repeat
            state = shared.state
            shared.unlockedExpression?.let { unlocked.add(it) }
        }

        assertTrue(unlocked.isNotEmpty(), "Grinding should have earned faces")
        assertTrue(
            state.expressionsFor("kaito").contains(Expression.SOFT_SMILE),
            "The first face should come early",
        )
        // Only what the rank permits.
        val rank = state.rankFor("kaito")
        for (face in state.expressionsFor("kaito")) {
            assertTrue(
                face.unlockRank <= rank,
                "${face.label} unlocked at rank $rank but needs ${face.unlockRank}",
            )
        }
    }

    @Test
    fun `faces are collected per character, not shared across the cast`() {
        var state = GameState(characterCreated = true, stamina = Stamina(max = 1000))
        repeat(40) {
            (Companionship.spendMomentWith(state, "rook", now) as? CompanionResult.Shared)
                ?.let { state = it.state }
        }
        assertTrue(state.expressionsFor("rook").size > 1)
        assertEquals(
            setOf(Expression.NEUTRAL),
            state.expressionsFor("sev"),
            "Another character's collection must be untouched",
        )
    }

    @Test
    fun `a big jump in rank unlocks every face it passed`() {
        val state = GameState(characterCreated = true)
            .withAffection("idris", Bond.totalPointsFor(6) - 1)
            .copy(stamina = Stamina(max = 10))

        val result = Companionship.spendMomentWith(state, "idris", now) as CompanionResult.Shared
        val faces = result.state.expressionsFor("idris")
        // Everything from rank 1 up to the new rank should now be present.
        for (face in Expression.unlockedAt(result.state.rankFor("idris"))) {
            assertTrue(faces.contains(face), "${face.label} was skipped over")
        }
    }

    @Test
    fun `the neutral face is always available even with nothing unlocked`() {
        val state = GameState()
        assertEquals(setOf(Expression.NEUTRAL), state.expressionsFor("lyra"))
        assertEquals(0, Expression.NEUTRAL.unlockRank)
    }

    @Test
    fun `every expression has a distinct unlock rank so rewards feel paced`() {
        val ranks = Expression.entries.map { it.unlockRank }
        assertEquals(ranks.size, ranks.toSet().size, "Two faces unlock at once: $ranks")
        assertTrue(Expression.entries.all { it.label.isNotBlank() && it.description.isNotBlank() })
    }

    @Test
    fun `there is always a next face to chase until the list runs out`() {
        assertNotNull(Expression.nextAfter(0))
        assertEquals(Expression.SOFT_SMILE, Expression.nextAfter(0))
        val last = Expression.entries.maxOf { it.unlockRank }
        assertNull(Expression.nextAfter(last), "Nothing should remain past the final face")
    }

    @Test
    fun `warmth depends on the bond and never on what was spent`() {
        // The same rank must produce the same line whether or not the player has ever paid.
        val poor = GameState(characterCreated = true).withAffection("sev", 200)
        val rich = GameState(characterCreated = true)
            .withAffection("sev", 200)
            .copy(wallet = com.deepuniverse.core.store.Wallet(starlight = 99_999, purchaseCount = 40))

        val a = Companionship.spendMomentWith(poor, "sev", now) as CompanionResult.Shared
        val b = Companionship.spendMomentWith(rich, "sev", now) as CompanionResult.Shared
        assertEquals(a.line, b.line)
        assertEquals(a.pointsGained, b.pointsGained)
    }
}
