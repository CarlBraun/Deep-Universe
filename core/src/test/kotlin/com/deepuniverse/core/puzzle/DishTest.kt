package com.deepuniverse.core.puzzle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The plate at the end of a cook.
 *
 * The thing worth protecting here is that the dish is *stable*: the reward for winning is seeing
 * what you made, and a plate that rerolls into a different dinner on recomposition is not a reward,
 * it is a slot machine.
 */
class DishTest {

    @Test
    fun `the same pot always produces the same dinner`() {
        for (seed in 0 until 40) {
            val first = Dishes.cookedBy("idris", seed, DishQuality.GOOD)
            val second = Dishes.cookedBy("idris", seed, DishQuality.GOOD)
            assertEquals(first, second, "Dish for seed $seed changed between reads")
        }
    }

    @Test
    fun `each cook turns out their own kitchen's food`() {
        val camp = (0 until 60).map { Dishes.cookedBy("idris", it, DishQuality.GOOD).name }.toSet()
        val uto = (0 until 60).map { Dishes.cookedBy("tuli", it, DishQuality.GOOD).name }.toSet()
        assertTrue(camp.size > 1, "The camp kitchen only ever makes one thing")
        assertTrue(uto.size > 1, "The Long Kitchen only ever makes one thing")
        assertTrue(
            camp.intersect(uto).isEmpty(),
            "A camp fire and an alien kitchen are turning out the same dish: $camp / $uto",
        )
    }

    @Test
    fun `the cook says something different depending on how it went`() {
        for (cook in listOf("idris", "tuli", "sev")) {
            val notes = DishQuality.entries
                .map { Dishes.cookedBy(cook, 7, it).note }
                .toSet()
            assertEquals(
                DishQuality.entries.size,
                notes.size,
                "$cook reacts identically to a scramble and to their best work",
            )
            assertTrue(notes.none { it.isBlank() })
        }
    }

    @Test
    fun `every dish is drawable`() {
        // The renderer takes these straight to a canvas, so an unset colour is a black hole on
        // screen rather than a compile error.
        for (cook in listOf("idris", "tuli", "nadia")) {
            for (seed in 0 until 20) {
                val dish = Dishes.cookedBy(cook, seed, DishQuality.EXCELLENT)
                assertTrue(dish.name.isNotBlank())
                for (colour in listOf(dish.bowlColor, dish.foodColor, dish.garnishColor)) {
                    assertEquals(
                        0xFF,
                        (colour ushr 24) and 0xFF,
                        "A dish colour is transparent, which draws as nothing",
                    )
                }
            }
        }
    }

    @Test
    fun `a ruined pot is visibly ruined and stops steaming`() {
        val good = Dishes.cookedBy("idris", 3, DishQuality.PASSABLE)
        val ruined = Dishes.ruinedBy("idris", 3)
        assertNotEquals(good.foodColor, ruined.foodColor)
        assertTrue(!ruined.steaming, "Burnt food should smoke, not steam")
        assertTrue(ruined.name.contains("Charred", ignoreCase = true))
    }

    // ---------------------------------------------------------------- quality

    @Test
    fun `holding the heat on the line cooks better than scrambling`() {
        // Two cooks of the same pot: one that finishes with most of the clock left, one that
        // scrapes in. The first must not be graded the same as the second.
        val quick = CookingGame.new(rank = 0, seed = 1).copy(elapsedMillis = 6_000)
        val slow = CookingGame.new(rank = 0, seed = 1).copy(elapsedMillis = 27_000)
        assertEquals(DishQuality.EXCELLENT, quick.quality)
        assertEquals(DishQuality.PASSABLE, slow.quality)
    }

    @Test
    fun `a cook that actually plays well earns a good plate`() {
        // Played for real through the state machine: hold when below the band, release when above.
        var game = CookingGame.new(rank = 2, seed = 11)
        var guard = 0
        while (game.outcome == PuzzleOutcome.IN_PROGRESS && guard++ < 5_000) {
            game = game.step(16, holding = game.heat < game.bandCentre)
        }
        assertEquals(PuzzleOutcome.WON, game.outcome, "A competent cook could not finish the pot")
        assertNotEquals(
            DishQuality.PASSABLE,
            game.quality,
            "Playing the game correctly still produced the worst grade",
        )
    }

    @Test
    fun `the dish seed survives the pot so the plate matches the cook`() {
        val game = CookingGame.new(rank = 4, seed = 99).step(16, holding = true)
        assertEquals(99, game.dishSeed)
    }
}
