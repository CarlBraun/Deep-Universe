package com.deepuniverse.core.puzzle

import kotlin.random.Random

/**
 * How well the pot went.
 *
 * Read from how much of the clock was left, not from a separate score: a cook who held the heat on
 * the line finishes early, and a cook who kept sliding off it scrapes in at the buzzer. The player
 * therefore never has to be told what "good" means — they felt it while they were doing it.
 */
enum class DishQuality(val label: String) {
    PASSABLE("Edible"),
    GOOD("Properly good"),
    EXCELLENT("Best thing all week"),
}

/** What goes on top. Purely decorative, but it is the difference between food and a coloured oval. */
enum class Garnish { HERBS, SEEDS, RINGS, EMBERS, SPORES }

/**
 * The thing you actually cooked.
 *
 * Colours live here as ARGB ints rather than in the drawing code because *what the dish is* is
 * content, not presentation — a Uto kitchen turns out something violet and glassy, a camp fire turns
 * out something brown and hot, and that difference should survive whoever redraws the plate later.
 */
data class Dish(
    val name: String,
    /** What the cook says when they look at it. */
    val note: String,
    val bowlColor: Int,
    val foodColor: Int,
    val garnishColor: Int,
    val garnish: Garnish,
    val quality: DishQuality,
    /** False for something scorched — a ruined pot has smoke, not steam. */
    val steaming: Boolean = true,
)

/**
 * The menus.
 *
 * Every cook has their own, so the reward for the minigame is a thing *they* made rather than a
 * generic bowl with their name attached to it.
 */
object Dishes {

    private data class Recipe(
        val name: String,
        val bowlColor: Int,
        val foodColor: Int,
        val garnishColor: Int,
        val garnish: Garnish,
    )

    private val campKitchen = listOf(
        Recipe("Ash-baked flatbread", 0xFF6B4A32.toInt(), 0xFFD8A55C.toInt(), 0xFF7FB36A.toInt(), Garnish.HERBS),
        Recipe("Smoked river trout", 0xFF4A4038.toInt(), 0xFFE0956F.toInt(), 0xFF9ED17F.toInt(), Garnish.HERBS),
        Recipe("Nine-hour bean stew", 0xFF3B3F4A.toInt(), 0xFF9C5A3C.toInt(), 0xFFE8C46A.toInt(), Garnish.SEEDS),
        Recipe("Wild onion soup", 0xFF5A4636.toInt(), 0xFFE3C271.toInt(), 0xFF7FB36A.toInt(), Garnish.HERBS),
        Recipe("Blackened corn", 0xFF4A4038.toInt(), 0xFFF0C24E.toInt(), 0xFFE07A4E.toInt(), Garnish.EMBERS),
    )

    private val longKitchen = listOf(
        Recipe("Tide-fruit in glass broth", 0xFF2E4A55.toInt(), 0xFF6FD3C4.toInt(), 0xFFB79BFF.toInt(), Garnish.RINGS),
        Recipe("Braided moss-root", 0xFF32424A.toInt(), 0xFF7FCB6A.toInt(), 0xFFE8C46A.toInt(), Garnish.SPORES),
        Recipe("Ember-cured skyfish", 0xFF3A3050.toInt(), 0xFFD98BC7.toInt(), 0xFFFFB36A.toInt(), Garnish.EMBERS),
        Recipe("Nine-salt porridge", 0xFF2E4A55.toInt(), 0xFFB2A7E8.toInt(), 0xFFEFE7FF.toInt(), Garnish.SEEDS),
        Recipe("Low-light greens", 0xFF32424A.toInt(), 0xFF58C29B.toInt(), 0xFFB79BFF.toInt(), Garnish.RINGS),
    )

    private val anyKitchen = listOf(
        Recipe("A pot of something warm", 0xFF4A4038.toInt(), 0xFFD8A55C.toInt(), 0xFF7FB36A.toInt(), Garnish.HERBS),
        Recipe("Whatever was in the cupboard", 0xFF3B3F4A.toInt(), 0xFFE3C271.toInt(), 0xFFE8C46A.toInt(), Garnish.SEEDS),
    )

    private fun menuFor(loveInterestId: String): List<Recipe> = when (loveInterestId) {
        "idris" -> campKitchen
        "tuli" -> longKitchen
        else -> anyKitchen
    }

    private val remarks: Map<String, Map<DishQuality, String>> = mapOf(
        "idris" to mapOf(
            DishQuality.PASSABLE to "It'll feed people. That's most of the job.",
            DishQuality.GOOD to "There. That's the one. Don't touch it.",
            DishQuality.EXCELLENT to "Four years I've cooked on that fire. That's the best it's given me.",
        ),
        "tuli" to mapOf(
            DishQuality.PASSABLE to "It holds together. We'll say that was intentional.",
            DishQuality.GOOD to "Look at the colour on it. That's exactly right.",
            DishQuality.EXCELLENT to "At home we'd carry that to the long table with both hands.",
        ),
    )

    private val genericRemarks = mapOf(
        DishQuality.PASSABLE to "It's food. We're not going to be precious about it.",
        DishQuality.GOOD to "That went better than either of us expected.",
        DishQuality.EXCELLENT to "We should do that again. Soon.",
    )

    /**
     * The dish this cook turned out, for a given pot.
     *
     * Deterministic in [seed], so leaving the screen and coming back shows the same plate rather
     * than quietly rerolling into a different dinner.
     */
    fun cookedBy(loveInterestId: String, seed: Int, quality: DishQuality): Dish {
        val menu = menuFor(loveInterestId)
        val recipe = menu[Random(seed).nextInt(menu.size)]
        return Dish(
            name = recipe.name,
            note = (remarks[loveInterestId] ?: genericRemarks)[quality] ?: genericRemarks.getValue(quality),
            bowlColor = recipe.bowlColor,
            foodColor = recipe.foodColor,
            garnishColor = recipe.garnishColor,
            garnish = recipe.garnish,
            quality = quality,
        )
    }

    /**
     * What is in the pot after a loss.
     *
     * A failed cook still produces something, and showing it is kinder than showing nothing — the
     * player gets a joke rather than a blank space where the reward would have been.
     */
    fun ruinedBy(loveInterestId: String, seed: Int): Dish {
        val base = cookedBy(loveInterestId, seed, DishQuality.PASSABLE)
        return base.copy(
            name = "Charred ${base.name.lowercase()}",
            note = when (loveInterestId) {
                "idris" -> "We're calling that smoked. On purpose."
                "tuli" -> "On Uto this is a texture. I promise you it's a texture."
                else -> "We can start again in a bit."
            },
            foodColor = 0xFF3A2C24.toInt(),
            garnishColor = 0xFF7A6A5E.toInt(),
            steaming = false,
        )
    }
}
