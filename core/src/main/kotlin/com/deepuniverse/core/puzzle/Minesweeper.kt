package com.deepuniverse.core.puzzle

import kotlin.random.Random

/** One square of the grid, as the player sees it. */
data class Cell(
    val mine: Boolean = false,
    val revealed: Boolean = false,
    val flagged: Boolean = false,
    /** Mines in the eight neighbours. Meaningless until revealed. */
    val neighbours: Int = 0,
)

/**
 * Minesweeper, as a value.
 *
 * Every operation returns a new board, so the whole game is a pure function of the taps that led to
 * it — trivially testable, trivially undoable, and safe to hold in Compose state.
 *
 * **First-tap safety** is the rule that matters most for fairness: mines are not placed until the
 * player's first reveal, and then never on or beside it. Losing on move one is not a difficulty, it
 * is a bug in the player's eyes, and it would be especially sour here where a loss asks them for an
 * ad or Stars.
 */
data class Minesweeper(
    val width: Int,
    val height: Int,
    val mines: Int,
    val cells: List<Cell>,
    val outcome: PuzzleOutcome = PuzzleOutcome.IN_PROGRESS,
    /** False until the first reveal places the mines. */
    val seeded: Boolean = false,
    private val seed: Int = 0,
) {
    init {
        require(width > 0 && height > 0) { "Board must have a size" }
        require(cells.size == width * height) { "Cell list does not match the board size" }
        require(mines in 0 until width * height) { "Impossible mine count: $mines" }
    }

    operator fun get(x: Int, y: Int): Cell = cells[index(x, y)]

    fun contains(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    private fun index(x: Int, y: Int) = y * width + x

    val flagsPlaced: Int get() = cells.count { it.flagged }

    /** Mines the player has yet to account for, for the counter. */
    val minesRemaining: Int get() = mines - flagsPlaced

    /** Reveals a square, placing mines first if this is the opening tap. */
    fun reveal(x: Int, y: Int): Minesweeper {
        if (outcome != PuzzleOutcome.IN_PROGRESS || !contains(x, y)) return this
        val board = if (seeded) this else placeMines(avoidX = x, avoidY = y)
        val cell = board[x, y]
        if (cell.revealed || cell.flagged) return board

        if (cell.mine) {
            // Show the whole board on a loss, so the player can see it was survivable.
            return board.copy(
                cells = board.cells.map { if (it.mine) it.copy(revealed = true) else it },
                outcome = PuzzleOutcome.LOST,
            )
        }

        val opened = board.floodFrom(x, y)
        return opened.copy(outcome = if (opened.isSwept()) PuzzleOutcome.WON else opened.outcome)
    }

    fun toggleFlag(x: Int, y: Int): Minesweeper {
        if (outcome != PuzzleOutcome.IN_PROGRESS || !contains(x, y)) return this
        val cell = this[x, y]
        if (cell.revealed) return this
        val next = cells.toMutableList()
        next[index(x, y)] = cell.copy(flagged = !cell.flagged)
        return copy(cells = next)
    }

    /** Won once every square that is not a mine has been revealed — flags are not required. */
    private fun isSwept(): Boolean = cells.none { !it.mine && !it.revealed }

    /**
     * Opens [x],[y] and, when it has no neighbouring mines, everything connected to it.
     *
     * Iterative rather than recursive on purpose: a large empty region would otherwise recurse
     * hundreds deep and can overflow the stack on a real device.
     */
    private fun floodFrom(x: Int, y: Int): Minesweeper {
        val next = cells.toMutableList()
        val queue = ArrayDeque(listOf(x to y))
        val seen = mutableSetOf(x to y)

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            val cell = next[index(cx, cy)]
            if (cell.revealed || cell.mine) continue
            next[index(cx, cy)] = cell.copy(revealed = true, flagged = false)
            if (cell.neighbours != 0) continue

            for (ny in cy - 1..cy + 1) {
                for (nx in cx - 1..cx + 1) {
                    if (!contains(nx, ny) || (nx == cx && ny == cy)) continue
                    if (seen.add(nx to ny)) queue.add(nx to ny)
                }
            }
        }
        return copy(cells = next)
    }

    /** Places mines away from the opening tap and its neighbours, then counts each square. */
    private fun placeMines(avoidX: Int, avoidY: Int): Minesweeper {
        val safe = buildSet {
            for (dy in -1..1) for (dx in -1..1) {
                val sx = avoidX + dx
                val sy = avoidY + dy
                if (contains(sx, sy)) add(sx to sy)
            }
        }

        val candidates = buildList {
            for (y in 0 until height) for (x in 0 until width) {
                if ((x to y) !in safe) add(x to y)
            }
        }
        // If the board is so small that the safe patch leaves too few squares, fall back to only
        // protecting the tapped square itself rather than placing fewer mines than promised.
        val pool = if (candidates.size >= mines) candidates else {
            buildList {
                for (y in 0 until height) for (x in 0 until width) {
                    if (x != avoidX || y != avoidY) add(x to y)
                }
            }
        }

        val chosen = pool.shuffled(Random(seed)).take(mines).toSet()
        val laid = List(width * height) { i ->
            val x = i % width
            val y = i / width
            Cell(mine = (x to y) in chosen)
        }

        val counted = laid.mapIndexed { i, cell ->
            val x = i % width
            val y = i / width
            var count = 0
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (contains(nx, ny) && laid[ny * width + nx].mine) count++
            }
            cell.copy(neighbours = count)
        }
        return copy(cells = counted, seeded = true)
    }

    companion object {
        fun new(width: Int, height: Int, mines: Int, seed: Int): Minesweeper = Minesweeper(
            width = width,
            height = height,
            mines = mines,
            cells = List(width * height) { Cell() },
            seed = seed,
        )
    }
}
