package com.deepuniverse.core.world

import kotlinx.serialization.Serializable

/**
 * A grid of tiles, parsed from ASCII rows.
 *
 * Parsing validates as it goes — every row the same length, every symbol known — so a malformed map
 * fails loudly at construction rather than producing a location with an invisible hole in it.
 */
class TileMap(rows: List<String>) {

    val height: Int = rows.size
    val width: Int = rows.firstOrNull()?.length ?: 0
    private val tiles: List<TileType>

    init {
        require(height > 0 && width > 0) { "A map cannot be empty" }
        rows.forEachIndexed { y, row ->
            require(row.length == width) {
                "Map row $y is ${row.length} wide but row 0 is $width — every row must match"
            }
            row.forEachIndexed { x, symbol ->
                require(TileType.exists(symbol)) { "Unknown map symbol '$symbol' at ($x, $y)" }
            }
        }
        tiles = rows.flatMap { row -> row.map { TileType.of(it) } }
    }

    fun contains(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    operator fun get(x: Int, y: Int): TileType {
        require(contains(x, y)) { "($x, $y) is outside this ${width}x$height map" }
        return tiles[y * width + x]
    }

    /** Off-map counts as not walkable, so the edge of a map is a wall by default. */
    fun isWalkable(x: Int, y: Int): Boolean = contains(x, y) && this[x, y].walkable
}

/**
 * A tile that moves the player to another area — a path off the edge of a clearing, or a cabin door.
 *
 * @param facingOnArrival which way the player should be looking after the transition, so walking
 *   south into an area leaves you facing south rather than spun around.
 */
data class Warp(
    val x: Int,
    val y: Int,
    val toAreaId: String,
    val toX: Int,
    val toY: Int,
    val facingOnArrival: Direction,
)

/**
 * A love interest standing somewhere in the world.
 *
 * @param activity what they are doing here, shown when you approach. Each character is placed where
 *   their role puts them — the one who cooks is at the fire, the one who runs briefings is at the
 *   lodge table — so the world tells you who people are before they say a word.
 * @param idleLine what they say when there is no new scene available, so walking up to someone is
 *   never a dead end.
 */
data class NpcSpawn(
    val loveInterestId: String,
    val x: Int,
    val y: Int,
    val facing: Direction,
    val activity: String,
    val idleLine: String,
)

/** One named place: a map, its exits, and whoever is standing in it. */
data class Area(
    val id: String,
    val name: String,
    val subtitle: String,
    val map: TileMap,
    val warps: List<Warp> = emptyList(),
    val npcs: List<NpcSpawn> = emptyList(),
    /** Indoor areas are lit and framed differently from outdoor ones. */
    val indoors: Boolean = false,
) {
    fun warpAt(x: Int, y: Int): Warp? = warps.firstOrNull { it.x == x && it.y == y }

    fun npcAt(x: Int, y: Int): NpcSpawn? = npcs.firstOrNull { it.x == x && it.y == y }
}

/** Where the player is standing, and which way they are looking. */
@Serializable
data class WorldPosition(
    val areaId: String,
    val x: Int,
    val y: Int,
    val facing: Direction = Direction.DOWN,
)
