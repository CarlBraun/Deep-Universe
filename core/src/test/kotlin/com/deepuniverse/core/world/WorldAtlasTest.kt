package com.deepuniverse.core.world

import com.deepuniverse.core.game.Cast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Checks the things you cannot see by looking at the ASCII maps.
 *
 * A hand-drawn world fails in ways that are invisible on the page: an exit that lands inside a tree,
 * a cabin with no door, a character standing where no one can reach them. Every one of those is a
 * player walking into a dead end, so each gets a test.
 */
class WorldAtlasTest {

    @Test
    fun `every area has a unique id and a non-empty map`() {
        val ids = WorldAtlas.areas.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Duplicate area ids: $ids")
        for (area in WorldAtlas.areas) {
            assertTrue(area.map.width > 0 && area.map.height > 0, "${area.id} has an empty map")
            assertTrue(area.name.isNotBlank(), "${area.id} has no name")
        }
    }

    @Test
    fun `every warp points at a real area and lands somewhere walkable`() {
        for (area in WorldAtlas.areas) {
            for (warp in area.warps) {
                assertTrue(
                    area.map.isWalkable(warp.x, warp.y),
                    "${area.id}: the warp tile at (${warp.x}, ${warp.y}) is not walkable, " +
                        "so the player could never step onto it",
                )

                val destination = WorldAtlas.areas.firstOrNull { it.id == warp.toAreaId }
                    ?: fail("${area.id}: warp points at unknown area '${warp.toAreaId}'")

                assertTrue(
                    destination.map.isWalkable(warp.toX, warp.toY),
                    "${area.id} → ${warp.toAreaId}: arrival tile (${warp.toX}, ${warp.toY}) is " +
                        "${destination.map[warp.toX, warp.toY].label}, which would strand the player",
                )
                assertTrue(
                    destination.npcAt(warp.toX, warp.toY) == null,
                    "${area.id} → ${warp.toAreaId}: the player would arrive on top of someone",
                )
            }
        }
    }

    @Test
    fun `arriving through a warp does not immediately warp you back`() {
        // A warp whose destination is itself a warp tile would bounce the player between two
        // areas forever.
        for (area in WorldAtlas.areas) {
            for (warp in area.warps) {
                val destination = WorldAtlas.area(warp.toAreaId)
                assertTrue(
                    destination.warpAt(warp.toX, warp.toY) == null,
                    "${area.id} → ${warp.toAreaId} arrives on another warp tile, causing a loop",
                )
            }
        }
    }

    @Test
    fun `every character stands somewhere walkable and is not on an exit`() {
        for (area in WorldAtlas.areas) {
            for (npc in area.npcs) {
                Cast.byId(npc.loveInterestId) // throws on a typo'd id
                assertTrue(
                    area.map.isWalkable(npc.x, npc.y),
                    "${area.id}: ${npc.loveInterestId} is standing in " +
                        "${area.map[npc.x, npc.y].label}",
                )
                assertTrue(
                    area.warpAt(npc.x, npc.y) == null,
                    "${area.id}: ${npc.loveInterestId} is blocking an exit",
                )
                assertTrue(npc.activity.isNotBlank(), "${npc.loveInterestId} has no activity")
                assertTrue(npc.idleLine.isNotBlank(), "${npc.loveInterestId} has no idle line")
            }
        }
    }

    @Test
    fun `no two characters share a tile`() {
        for (area in WorldAtlas.areas) {
            val tiles = area.npcs.map { it.x to it.y }
            assertEquals(tiles.size, tiles.toSet().size, "${area.id} has characters stacked up")
        }
    }

    @Test
    fun `the whole cast is placed somewhere in the world`() {
        val placed = WorldAtlas.areas.flatMap { area -> area.npcs.map { it.loveInterestId } }
        for (member in Cast.all) {
            assertTrue(
                member.id in placed,
                "${member.name} is romanceable but stands nowhere in the world",
            )
        }
        assertEquals(placed.size, placed.toSet().size, "Someone is in two places at once: $placed")
    }

    // ---------------------------------------------------------------- reachability

    @Test
    fun `every area can be walked to from where the player starts`() {
        val reachable = WorldEngine.reachableAreas(WorldAtlas.startPosition)
        for (area in WorldAtlas.areas) {
            assertTrue(
                area.id in reachable,
                "${area.name} cannot be reached on foot from the starting cabin",
            )
        }
    }

    @Test
    fun `every character can be walked up to and talked to`() {
        for (area in WorldAtlas.areas) {
            for (npc in area.npcs) {
                assertTrue(
                    WorldEngine.isNpcApproachable(area.id, npc, WorldAtlas.startPosition),
                    "${npc.loveInterestId} in ${area.name} cannot be reached — there is no tile " +
                        "next to them the player can stand on",
                )
            }
        }
    }

    @Test
    fun `the player starts somewhere they can stand`() {
        val start = WorldAtlas.startPosition
        val area = WorldAtlas.area(start.areaId)
        assertTrue(area.map.isWalkable(start.x, start.y), "The player starts inside scenery")
        assertTrue(area.warpAt(start.x, start.y) == null, "The player starts on a warp tile")
    }

    // ---------------------------------------------------------------- map parsing

    @Test
    fun `a map with ragged rows is rejected`() {
        val error = runCatching { TileMap(listOf("....", "...")) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException, "Expected a rejection, got $error")
    }

    @Test
    fun `a map with an unknown symbol is rejected`() {
        val error = runCatching { TileMap(listOf("..%.")) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException, "Expected a rejection, got $error")
    }

    @Test
    fun `tiles read back in the order they were written`() {
        val map = TileMap(listOf("T.s", "~#-"))
        assertEquals(TileType.TREE, map[0, 0])
        assertEquals(TileType.GRASS, map[1, 0])
        assertEquals(TileType.SAND, map[2, 0])
        assertEquals(TileType.WATER, map[0, 1])
        assertEquals(TileType.WALL, map[1, 1])
        assertEquals(TileType.PATH, map[2, 1])
    }

    @Test
    fun `off-map tiles are never walkable`() {
        val map = TileMap(listOf("..", ".."))
        assertTrue(map.isWalkable(0, 0))
        assertTrue(!map.isWalkable(-1, 0))
        assertTrue(!map.isWalkable(0, 2))
    }

    @Test
    fun `each character is found where their role puts them`() {
        // The placement is part of the storytelling, so it is worth pinning down.
        assertEquals(WorldAtlas.CAMP_CLEARING, WorldAtlas.locationOf("idris")?.id, "the cook")
        assertEquals(WorldAtlas.GREAT_LODGE, WorldAtlas.locationOf("sev")?.id, "the commander")
        assertEquals(WorldAtlas.FIELD_LAB, WorldAtlas.locationOf("nadia")?.id, "the scientist")
        assertEquals(WorldAtlas.PINE_PATH, WorldAtlas.locationOf("rook")?.id, "the loner")
        assertEquals(WorldAtlas.THE_BEACH, WorldAtlas.locationOf("lyra")?.id, "the swimmer")
        assertEquals(WorldAtlas.THE_BEACH, WorldAtlas.locationOf("kaito")?.id, "the navigator")
    }

    @Test
    fun `the meeting lodge is big enough to be worth meeting in`() {
        val lodge = WorldAtlas.area(WorldAtlas.GREAT_LODGE)
        assertTrue(lodge.indoors)
        assertNotNull(lodge.npcs.firstOrNull { it.loveInterestId == "sev" })
        val floor = (0 until lodge.map.height).sumOf { y ->
            (0 until lodge.map.width).count { x -> lodge.map.isWalkable(x, y) }
        }
        assertTrue(floor > 80, "The lodge only has $floor walkable tiles")
    }
}
