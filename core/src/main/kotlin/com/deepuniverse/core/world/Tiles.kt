package com.deepuniverse.core.world

import kotlinx.serialization.Serializable

/**
 * Every kind of ground or object a map square can hold.
 *
 * Maps are authored as ASCII art (see [WorldAtlas]), and [symbol] is the character that stands for
 * this tile. Writing maps as text rather than as data structures means a location can be read,
 * reviewed and edited as a picture — which matters far more than compactness when someone is
 * laying out a camp.
 */
enum class TileType(val symbol: Char, val walkable: Boolean, val label: String) {
    GRASS('.', true, "grass"),
    TALL_GRASS(',', true, "long grass"),
    FLOWERS('*', true, "wildflowers"),
    PATH('-', true, "trodden path"),
    SAND('s', true, "sand"),
    FLOOR('_', true, "floorboards"),
    PIER('p', true, "pier"),
    DOOR('D', true, "door"),

    TREE('T', false, "pine"),
    ROCK('o', false, "boulder"),
    WATER('~', false, "water"),
    WALL('#', false, "wall"),
    CAMPFIRE('f', false, "campfire"),
    TABLE('=', false, "table"),
    CHAIR('h', false, "chair"),
    BED('b', false, "bunk"),
    CRATE('x', false, "supply crate"),
    ;

    companion object {
        private val bySymbol = entries.associateBy { it.symbol }

        fun of(symbol: Char): TileType =
            bySymbol[symbol] ?: error("No tile for symbol '$symbol'")

        fun exists(symbol: Char): Boolean = symbol in bySymbol
    }
}

/** The four directions the player and NPCs can face. */
@Serializable
enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0),
    ;

    val opposite: Direction
        get() = when (this) {
            UP -> DOWN
            DOWN -> UP
            LEFT -> RIGHT
            RIGHT -> LEFT
        }
}

/** A square on a specific map. */
data class Tile(val x: Int, val y: Int)
