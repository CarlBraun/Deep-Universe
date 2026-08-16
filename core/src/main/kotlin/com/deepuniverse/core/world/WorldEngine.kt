package com.deepuniverse.core.world

/** What happened when the player tried to move. */
sealed interface MoveResult {

    /** Turned on the spot without moving — the player was facing another way. */
    data class Turned(val position: WorldPosition) : MoveResult

    data class Walked(val position: WorldPosition) : MoveResult

    /** Something solid is there. [blockedBy] names it, so the UI can say "a boulder". */
    data class Blocked(val position: WorldPosition, val blockedBy: String) : MoveResult

    /** Stepped onto an exit and arrived somewhere new. */
    data class Travelled(val position: WorldPosition, val toArea: Area) : MoveResult
}

/**
 * Grid movement and interaction — the Pokémon-style overworld rules.
 *
 * Pure and immutable: every call takes a position and returns a new one, so the whole overworld can
 * be walked through in a unit test without a screen. The UI's only job is to draw the position it
 * is given and to animate between the old and new one.
 */
object WorldEngine {

    /**
     * Attempts to move one tile in [direction].
     *
     * Turning costs a step when you are facing another way, which is what makes it possible to talk
     * to someone beside you without walking into them — the same reason the classic games do it.
     */
    fun move(position: WorldPosition, direction: Direction): MoveResult {
        val area = WorldAtlas.area(position.areaId)

        if (position.facing != direction) {
            return MoveResult.Turned(position.copy(facing = direction))
        }

        val nextX = position.x + direction.dx
        val nextY = position.y + direction.dy

        if (!area.map.contains(nextX, nextY)) {
            return MoveResult.Blocked(position, "the edge of the world")
        }

        area.npcAt(nextX, nextY)?.let {
            return MoveResult.Blocked(position, "someone standing there")
        }

        val tile = area.map[nextX, nextY]
        if (!tile.walkable) {
            return MoveResult.Blocked(position, tile.label)
        }

        // Warps are checked after walkability so a door must also be a tile you can stand on.
        area.warpAt(nextX, nextY)?.let { warp ->
            val destination = WorldAtlas.area(warp.toAreaId)
            return MoveResult.Travelled(
                position = WorldPosition(warp.toAreaId, warp.toX, warp.toY, warp.facingOnArrival),
                toArea = destination,
            )
        }

        return MoveResult.Walked(position.copy(x = nextX, y = nextY))
    }

    /** The tile the player is looking at. */
    fun facingTile(position: WorldPosition): Tile =
        Tile(position.x + position.facing.dx, position.y + position.facing.dy)

    /**
     * Whoever the player is facing, or null.
     *
     * Interaction reads the tile in front rather than the tile underneath, so you talk to people by
     * walking up to them and facing them — never by standing on them, which is impossible anyway.
     */
    fun facingNpc(position: WorldPosition): NpcSpawn? {
        val area = WorldAtlas.area(position.areaId)
        val tile = facingTile(position)
        return area.npcAt(tile.x, tile.y)
    }

    /** True when there is anything worth pressing the interact button for. */
    fun canInteract(position: WorldPosition): Boolean = facingNpc(position) != null

    /**
     * Every tile reachable on foot from [from], following warps between areas.
     *
     * Used by the tests to prove no character or exit is walled off — the failure mode of a
     * hand-drawn map is a location nobody can actually get to.
     */
    fun reachableTiles(from: WorldPosition): Set<Triple<String, Int, Int>> {
        val seen = mutableSetOf(Triple(from.areaId, from.x, from.y))
        val queue = ArrayDeque(listOf(Triple(from.areaId, from.x, from.y)))

        while (queue.isNotEmpty()) {
            val (areaId, x, y) = queue.removeFirst()
            val area = WorldAtlas.area(areaId)

            for (direction in Direction.entries) {
                val nextX = x + direction.dx
                val nextY = y + direction.dy
                if (!area.map.isWalkable(nextX, nextY)) continue
                if (area.npcAt(nextX, nextY) != null) continue

                val step = area.warpAt(nextX, nextY)?.let { warp ->
                    Triple(warp.toAreaId, warp.toX, warp.toY)
                } ?: Triple(areaId, nextX, nextY)

                if (seen.add(step)) queue.add(step)
            }
        }
        return seen
    }

    /** Areas the player can walk to from [from]. */
    fun reachableAreas(from: WorldPosition): Set<String> =
        reachableTiles(from).map { it.first }.toSet()

    /**
     * True when the player can stand somewhere that faces [npc] in [areaId] — i.e. the character can
     * actually be talked to, not just seen across water.
     */
    fun isNpcApproachable(areaId: String, npc: NpcSpawn, from: WorldPosition): Boolean {
        val reachable = reachableTiles(from)
        return Direction.entries.any { direction ->
            val standX = npc.x + direction.dx
            val standY = npc.y + direction.dy
            Triple(areaId, standX, standY) in reachable
        }
    }
}
