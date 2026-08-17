package com.deepuniverse.core.puzzle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PuzzleTest {

    // ---------------------------------------------------------------- minesweeper

    @Test
    fun `the first tap is never a mine`() {
        // The rule the whole puzzle's fairness rests on, and doubly so here where losing asks the
        // player for an ad or Stars. Checked across many seeds and many opening taps.
        for (seed in 0 until 60) {
            val board = Minesweeper.new(width = 8, height = 8, mines = 12, seed = seed)
            for (start in listOf(0 to 0, 3 to 4, 7 to 7, 5 to 1)) {
                val after = board.reveal(start.first, start.second)
                assertNotEquals(
                    PuzzleOutcome.LOST,
                    after.outcome,
                    "Seed $seed lost on the opening tap at $start",
                )
            }
        }
    }

    @Test
    fun `the squares around the first tap are also clear`() {
        // Otherwise the opening tap is a single number hemmed in by mines, which is a dead end.
        val board = Minesweeper.new(8, 8, 12, seed = 7).reveal(4, 4)
        for (dy in -1..1) for (dx in -1..1) {
            assertTrue(!board[4 + dx, 4 + dy].mine, "A mine was placed beside the opening tap")
        }
    }

    @Test
    fun `an empty region opens all at once`() {
        val board = Minesweeper.new(9, 9, 6, seed = 3).reveal(4, 4)
        assertTrue(
            board.cells.count { it.revealed } > 1,
            "Revealing a blank square should cascade rather than open one cell",
        )
    }

    @Test
    fun `revealing a mine ends it and shows where they all were`() {
        var board = Minesweeper.new(6, 6, 5, seed = 11).reveal(0, 0)
        val mine = board.cells.indexOfFirst { it.mine }
        board = board.reveal(mine % 6, mine / 6)
        assertEquals(PuzzleOutcome.LOST, board.outcome)
        assertTrue(board.cells.filter { it.mine }.all { it.revealed })
    }

    @Test
    fun `clearing every safe square wins`() {
        var board = Minesweeper.new(5, 5, 3, seed = 21).reveal(0, 0)
        for (y in 0 until 5) for (x in 0 until 5) {
            if (!board[x, y].mine) board = board.reveal(x, y)
        }
        assertEquals(PuzzleOutcome.WON, board.outcome, "Flags should not be required to win")
    }

    @Test
    fun `flags block reveals and are counted`() {
        val opened = Minesweeper.new(6, 6, 4, seed = 5).reveal(0, 0)
        val covered = opened.cells.indexOfFirst { !it.revealed }
        val fx = covered % 6
        val fy = covered / 6

        val board = opened.toggleFlag(fx, fy)
        assertTrue(board[fx, fy].flagged)
        assertEquals(3, board.minesRemaining)
        assertTrue(!board.reveal(fx, fy)[fx, fy].revealed, "A flagged square must not open")
        assertTrue(!board.toggleFlag(fx, fy)[fx, fy].flagged, "Flagging twice removes it")
    }

    @Test
    fun `taps outside the board and after the end do nothing`() {
        val board = Minesweeper.new(4, 4, 2, seed = 1)
        assertEquals(board, board.reveal(-1, 0))
        assertEquals(board, board.reveal(0, 99))

        var lost = board.reveal(0, 0)
        val mine = lost.cells.indexOfFirst { it.mine }
        lost = lost.reveal(mine % 4, mine / 4)
        assertEquals(lost, lost.reveal(1, 1), "A finished board must ignore further taps")
    }

    @Test
    fun `the same seed always deals the same board`() {
        val a = Minesweeper.new(8, 8, 10, seed = 99).reveal(2, 2)
        val b = Minesweeper.new(8, 8, 10, seed = 99).reveal(2, 2)
        assertEquals(a.cells.map { it.mine }, b.cells.map { it.mine })
    }

    @Test
    fun `difficulty grows with the bond but stops somewhere sane`() {
        assertTrue(Puzzles.mineCountFor(0) < Puzzles.mineCountFor(30))
        assertTrue(Puzzles.mineCountFor(500) <= 14, "A grid should never become unplayable")
    }

    // ---------------------------------------------------------------- squirrel

    @Test
    fun `searching the squirrel's bush wins`() {
        val hunt = SquirrelHunt.new(6, 6, attempts = 8, seed = 4)
        val found = hunt.search(hunt.squirrelX, hunt.squirrelY)
        assertEquals(PuzzleOutcome.WON, found.outcome)
        assertEquals(Warmth.FOUND, found.lastWarmth)
    }

    @Test
    fun `warmth tells you honestly how close you got`() {
        val hunt = SquirrelHunt(width = 9, height = 9, squirrelX = 4, squirrelY = 4, attemptsAllowed = 9)
        assertEquals(Warmth.FOUND, hunt.warmthAt(4, 4))
        assertEquals(Warmth.BURNING, hunt.warmthAt(5, 4))
        assertEquals(Warmth.BURNING, hunt.warmthAt(3, 3))
        assertEquals(Warmth.WARM, hunt.warmthAt(6, 4))
        assertEquals(Warmth.COOL, hunt.warmthAt(7, 4))
        assertEquals(Warmth.COOL, hunt.warmthAt(0, 0), "The far corner of a 9x9 grid is still only 4 away")

        // Cold needs a longer board than a centred squirrel on a 9x9 can offer.
        val corner = hunt.copy(squirrelX = 0, squirrelY = 0)
        assertEquals(Warmth.COLD, corner.warmthAt(6, 6))
    }

    @Test
    fun `running out of guesses loses`() {
        var hunt = SquirrelHunt(width = 8, height = 8, squirrelX = 7, squirrelY = 7, attemptsAllowed = 3)
        hunt = hunt.search(0, 0).search(1, 0).search(2, 0)
        assertEquals(PuzzleOutcome.LOST, hunt.outcome)
        assertEquals(0, hunt.attemptsLeft)
    }

    @Test
    fun `re-tapping a bush you already checked is not punished`() {
        // Misclicks happen, and burning a turn on one would feel cheated rather than difficult.
        var hunt = SquirrelHunt(width = 8, height = 8, squirrelX = 7, squirrelY = 7, attemptsAllowed = 4)
        hunt = hunt.search(0, 0)
        val afterFirst = hunt
        hunt = hunt.search(0, 0)
        assertEquals(afterFirst, hunt)
        assertEquals(1, hunt.attemptsUsed)
    }

    @Test
    fun `the squirrel is always somewhere on the board`() {
        for (seed in 0 until 50) {
            val hunt = SquirrelHunt.new(7, 7, attempts = 8, seed = seed)
            assertTrue(hunt.contains(hunt.squirrelX, hunt.squirrelY), "Squirrel placed off-grid")
        }
    }

    // ---------------------------------------------------------------- cooking

    @Test
    fun `holding raises the heat and releasing lets it fall`() {
        val start = CookingGame.new(rank = 0, seed = 1)
        var held = start
        repeat(10) { held = held.step(50, holding = true) }
        assertTrue(held.heat > start.heat, "Holding should lift the heat")

        var released = held
        repeat(20) { released = released.step(50, holding = false) }
        assertTrue(released.heat < held.heat, "Releasing should let it drop")
    }

    @Test
    fun `the heat stays on the gauge however hard you push`() {
        var game = CookingGame.new(rank = 0, seed = 2)
        repeat(200) { game = game.step(50, holding = true) }
        assertTrue(game.heat <= 1f, "Heat ran off the top: ${game.heat}")
        var falling = game
        repeat(400) { falling = falling.step(50, holding = false) }
        assertTrue(falling.heat >= 0f, "Heat ran off the bottom: ${falling.heat}")
    }

    @Test
    fun `staying in the band fills the bar and wins`() {
        // Drive the heat towards the band each frame, as a competent player would.
        var game = CookingGame.new(rank = 0, seed = 3)
        var guard = 0
        while (game.outcome == PuzzleOutcome.IN_PROGRESS && guard++ < 2000) {
            game = game.step(16, holding = game.heat < game.bandCentre)
        }
        assertEquals(PuzzleOutcome.WON, game.outcome, "A player tracking the band should finish")
        assertTrue(game.progress >= 1f)
    }

    @Test
    fun `ignoring the pot loses when the time runs out`() {
        var game = CookingGame.new(rank = 0, seed = 4)
        var guard = 0
        while (game.outcome == PuzzleOutcome.IN_PROGRESS && guard++ < 5000) {
            game = game.step(50, holding = false)
        }
        assertEquals(PuzzleOutcome.LOST, game.outcome)
        assertEquals(0L, game.millisRemaining)
    }

    @Test
    fun `progress drains slower than it fills, so one slip is survivable`() {
        var inBand = CookingGame.new(rank = 0, seed = 5)
        inBand = inBand.copy(heat = inBand.bandCentre)
        val filled = inBand.step(1000, holding = false)
        val gained = filled.progress

        var out = filled.copy(heat = 0f, bandCentre = 0.9f)
        val drained = gained - out.step(1000, holding = false).progress
        assertTrue(drained < gained, "Draining must be gentler than filling")
    }

    @Test
    fun `a finished pot ignores further input`() {
        var game = CookingGame.new(rank = 0, seed = 6)
        var guard = 0
        while (game.outcome == PuzzleOutcome.IN_PROGRESS && guard++ < 5000) {
            game = game.step(50, holding = false)
        }
        assertEquals(game, game.step(50, holding = true))
    }

    @Test
    fun `a higher bond means a tighter band and less time, but never impossible`() {
        val easy = CookingGame.new(rank = 0, seed = 1)
        val hard = CookingGame.new(rank = 40, seed = 1)
        assertTrue(hard.bandHalfHeight < easy.bandHalfHeight)
        assertTrue(hard.timeLimitMillis < easy.timeLimitMillis)
        assertTrue(hard.bandHalfHeight >= 0.08f, "The band must stay hittable")
        assertTrue(hard.timeLimitMillis >= 18_000L, "There must always be time to cook")
    }

    @Test
    fun `every puzzle pays out more than a plain moment together`() {
        for (kind in PuzzleKind.entries) {
            assertTrue(
                kind.reward > com.deepuniverse.core.game.Companionship.BASE_POINTS,
                "${kind.label} is worth less than simply spending a moment, so nobody would play it",
            )
            assertTrue(kind.label.isNotBlank() && kind.blurb.isNotBlank())
        }
    }
}
