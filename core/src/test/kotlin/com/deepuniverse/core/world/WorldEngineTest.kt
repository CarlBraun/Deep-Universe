package com.deepuniverse.core.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class WorldEngineTest {

    /** Standing in the middle of the lodge floor, well clear of walls and furniture. */
    private val inLodge = WorldPosition(WorldAtlas.GREAT_LODGE, x = 7, y = 8, facing = Direction.UP)

    @Test
    fun `pressing a new direction turns on the spot before walking`() {
        val result = WorldEngine.move(inLodge, Direction.LEFT)
        val turned = result as? MoveResult.Turned ?: fail("Expected a turn, got $result")
        assertEquals(Direction.LEFT, turned.position.facing)
        assertEquals(inLodge.x, turned.position.x, "Turning must not move the player")
        assertEquals(inLodge.y, turned.position.y)
    }

    @Test
    fun `pressing the direction you already face walks one tile`() {
        val result = WorldEngine.move(inLodge, Direction.UP)
        val walked = result as? MoveResult.Walked ?: fail("Expected a step, got $result")
        assertEquals(inLodge.y - 1, walked.position.y)
        assertEquals(inLodge.x, walked.position.x)
        assertEquals(Direction.UP, walked.position.facing)
    }

    @Test
    fun `walls stop the player and are named`() {
        val atWall = WorldPosition(WorldAtlas.GREAT_LODGE, x = 1, y = 8, facing = Direction.LEFT)
        val result = WorldEngine.move(atWall, Direction.LEFT)
        val blocked = result as? MoveResult.Blocked ?: fail("Expected to be blocked, got $result")
        assertEquals("wall", blocked.blockedBy)
        assertEquals(atWall.x, blocked.position.x, "A blocked move must not move the player")
    }

    @Test
    fun `water cannot be walked into`() {
        // On the beach, standing on the last row of sand facing the sea.
        val atShore = WorldPosition(WorldAtlas.THE_BEACH, x = 3, y = 5, facing = Direction.DOWN)
        val result = WorldEngine.move(atShore, Direction.DOWN)
        val blocked = result as? MoveResult.Blocked ?: fail("Expected water to block, got $result")
        assertEquals("water", blocked.blockedBy)
    }

    @Test
    fun `the pier lets you walk out over the water`() {
        val onSand = WorldPosition(WorldAtlas.THE_BEACH, x = 7, y = 5, facing = Direction.DOWN)
        val result = WorldEngine.move(onSand, Direction.DOWN)
        val walked = result as? MoveResult.Walked ?: fail("Expected to step onto the pier, got $result")
        assertEquals(6, walked.position.y)
        assertEquals(TileType.PIER, WorldAtlas.area(WorldAtlas.THE_BEACH).map[7, 6])
    }

    @Test
    fun `you cannot walk through a person`() {
        // Idris stands at (8, 4) in the camp; approach from the right.
        val beside = WorldPosition(WorldAtlas.CAMP_CLEARING, x = 9, y = 4, facing = Direction.LEFT)
        val result = WorldEngine.move(beside, Direction.LEFT)
        val blocked = result as? MoveResult.Blocked ?: fail("Expected Idris to block, got $result")
        assertEquals("someone standing there", blocked.blockedBy)
    }

    @Test
    fun `stepping on an exit moves you to the next area`() {
        // Walk south out of the camp onto the path.
        val atExit = WorldPosition(WorldAtlas.CAMP_CLEARING, x = 7, y = 10, facing = Direction.DOWN)
        val result = WorldEngine.move(atExit, Direction.DOWN)
        val travelled = result as? MoveResult.Travelled ?: fail("Expected to travel, got $result")
        assertEquals(WorldAtlas.PINE_PATH, travelled.position.areaId)
        assertEquals(WorldAtlas.PINE_PATH, travelled.toArea.id)
        assertEquals(Direction.DOWN, travelled.position.facing, "Should keep walking the same way")
    }

    @Test
    fun `leaving and coming back returns you to where you were`() {
        val atExit = WorldPosition(WorldAtlas.CAMP_CLEARING, x = 7, y = 10, facing = Direction.DOWN)
        val out = WorldEngine.move(atExit, Direction.DOWN) as MoveResult.Travelled

        // Turn around and walk back north through the same exit.
        val turned = WorldEngine.move(out.position, Direction.UP) as MoveResult.Turned
        val back = WorldEngine.move(turned.position, Direction.UP)
        val returned = back as? MoveResult.Travelled ?: fail("Expected to travel back, got $back")
        assertEquals(WorldAtlas.CAMP_CLEARING, returned.position.areaId)
    }

    @Test
    fun `the edge of a map is solid`() {
        val corner = WorldPosition(WorldAtlas.FIELD_LAB, x = 1, y = 1, facing = Direction.UP)
        val result = WorldEngine.move(corner, Direction.UP)
        assertTrue(result is MoveResult.Blocked, "Expected the top wall to block, got $result")
    }

    // ---------------------------------------------------------------- interaction

    @Test
    fun `facing a character lets you talk to them`() {
        val beside = WorldPosition(WorldAtlas.CAMP_CLEARING, x = 9, y = 4, facing = Direction.LEFT)
        val npc = WorldEngine.facingNpc(beside) ?: fail("Expected to be facing Idris")
        assertEquals("idris", npc.loveInterestId)
        assertTrue(WorldEngine.canInteract(beside))
    }

    @Test
    fun `facing away from a character offers nothing`() {
        val facingAway = WorldPosition(WorldAtlas.CAMP_CLEARING, x = 9, y = 4, facing = Direction.RIGHT)
        assertNull(WorldEngine.facingNpc(facingAway))
        assertTrue(!WorldEngine.canInteract(facingAway))
    }

    @Test
    fun `standing next to nobody offers nothing`() {
        assertNull(WorldEngine.facingNpc(inLodge))
    }

    @Test
    fun `each character can be talked to from the tile in front of them`() {
        for (area in WorldAtlas.areas) {
            for (npc in area.npcs) {
                // Stand where the character is looking, and look back at them.
                val standX = npc.x + npc.facing.dx
                val standY = npc.y + npc.facing.dy
                if (!area.map.isWalkable(standX, standY)) continue

                val player = WorldPosition(area.id, standX, standY, npc.facing.opposite)
                val found = WorldEngine.facingNpc(player)
                assertEquals(
                    npc.loveInterestId,
                    found?.loveInterestId,
                    "Standing in front of ${npc.loveInterestId} in ${area.name} did not find them",
                )
            }
        }
    }

    @Test
    fun `a full walk from the cabin to the beach works`() {
        // Walks the route a player actually takes on their first playthrough, one tile at a time,
        // proving the whole chain of maps and exits joins up.
        var position = WorldAtlas.startPosition

        // Exactly one tile of movement per call: turn first if needed, then take a single step.
        fun walk(direction: Direction) {
            if (position.facing != direction) {
                (WorldEngine.move(position, direction) as? MoveResult.Turned)?.let {
                    position = it.position
                }
            }
            when (val result = WorldEngine.move(position, direction)) {
                is MoveResult.Walked -> position = result.position
                is MoveResult.Travelled -> position = result.position
                is MoveResult.Turned -> position = result.position
                is MoveResult.Blocked -> Unit
            }
        }

        // Out of your bunk and through the cabin door onto Cabin Row.
        repeat(4) { walk(Direction.DOWN) }
        assertEquals(WorldAtlas.CABIN_ROW, position.areaId, "Should have stepped outside")

        // Sidestep clear of your own doorway, then up and west to the camp.
        repeat(4) { walk(Direction.LEFT) }
        repeat(4) { walk(Direction.UP) }
        repeat(2) { walk(Direction.LEFT) }
        assertEquals(WorldAtlas.CAMP_CLEARING, position.areaId, "Should have reached the camp")

        // Down to the south gate and out onto the pine path.
        repeat(4) { walk(Direction.DOWN) }
        repeat(6) { walk(Direction.LEFT) }
        repeat(2) { walk(Direction.DOWN) }
        assertEquals(WorldAtlas.PINE_PATH, position.areaId, "Should be on the path")

        // Sidestep the boulder that sits in the middle of the path, then follow it to the water.
        walk(Direction.LEFT)
        repeat(12) { walk(Direction.DOWN) }
        assertEquals(WorldAtlas.THE_BEACH, position.areaId, "Should have reached the beach")
    }
}
