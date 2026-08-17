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
    fun move(
        position: WorldPosition,
        direction: Direction,
        flags: Set<String> = emptySet(),
    ): MoveResult {
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
            if (warp.requiresFlag != null && warp.requiresFlag !in flags) {
                // Step onto the tile but go nowhere, so a closed exit is somewhere you can stand
                // and look at rather than an invisible wall.
                return MoveResult.Blocked(
                    position = position.copy(x = nextX, y = nextY),
                    blockedBy = warp.lockedMessage ?: "something not ready yet",
                )
            }
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
     * The route from [from] to the tile at [targetX], [targetY], as a list of steps.
     *
     * Breadth-first, so the route is always one of the shortest. It deliberately stays *inside the
     * current area* and never routes through an exit: a tap meaning "walk over there" should not
     * send the player through a door into another location, which would be a surprising amount of
     * consequence for one tap.
     *
     * Tapping a person is understood as "go and talk to them": the route ends on a tile beside them
     * and the last step faces them, so the talk button lights up on arrival.
     *
     * @return the steps to take, empty if already there, or null when there is no way through.
     */
    fun path(
        from: WorldPosition,
        targetX: Int,
        targetY: Int,
        flags: Set<String> = emptySet(),
    ): List<Direction>? {
        val area = WorldAtlas.area(from.areaId)
        if (!area.map.contains(targetX, targetY)) return null

        val npcTarget = area.npcAt(targetX, targetY)
        // Standing on a person is impossible, so aim for the squares around them instead.
        val goals: Set<Pair<Int, Int>> = if (npcTarget != null) {
            Direction.entries
                .map { targetX + it.dx to targetY + it.dy }
                .filter { (x, y) -> area.map.isWalkable(x, y) && area.npcAt(x, y) == null }
                .toSet()
        } else {
            if (!area.map.isWalkable(targetX, targetY)) return null
            setOf(targetX to targetY)
        }
        if (goals.isEmpty()) return null

        val start = from.x to from.y
        if (start in goals) {
            // Already in place; just turn to face a person if that is what was tapped.
            return npcTarget?.let { facingStep(from.x, from.y, targetX, targetY) } ?: emptyList()
        }

        val cameFrom = mutableMapOf(start to (null as Pair<Pair<Int, Int>, Direction>?))
        val queue = ArrayDeque(listOf(start))
        var found: Pair<Int, Int>? = null

        while (queue.isNotEmpty() && found == null) {
            val current = queue.removeFirst()
            for (direction in Direction.entries) {
                val next = current.first + direction.dx to current.second + direction.dy
                if (next in cameFrom) continue
                if (!area.map.isWalkable(next.first, next.second)) continue
                if (area.npcAt(next.first, next.second) != null) continue
                // Never route through an exit; walking somewhere should not change location.
                if (area.warpAt(next.first, next.second) != null && next !in goals) continue

                cameFrom[next] = current to direction
                if (next in goals) {
                    found = next
                    break
                }
                queue.add(next)
            }
        }

        val destination = found ?: return null
        val steps = ArrayDeque<Direction>()
        var walk: Pair<Int, Int>? = destination
        while (walk != null) {
            val previous = cameFrom[walk] ?: break
            steps.addFirst(previous.second)
            walk = previous.first
        }

        // Finish by turning to face the person who was tapped.
        if (npcTarget != null) {
            steps.addAll(facingStep(destination.first, destination.second, targetX, targetY))
        }
        return steps.toList()
    }

    /** The single step that turns someone at [x],[y] to face [towardX],[towardY]. */
    private fun facingStep(x: Int, y: Int, towardX: Int, towardY: Int): List<Direction> =
        Direction.entries.firstOrNull { x + it.dx == towardX && y + it.dy == towardY }
            ?.let { listOf(it) }
            ?: emptyList()

    /**
     * Every tile reachable on foot from [from], following warps between areas.
     *
     * Used by the tests to prove no character or exit is walled off — the failure mode of a
     * hand-drawn map is a location nobody can actually get to.
     */
    fun reachableTiles(
        from: WorldPosition,
        flags: Set<String> = emptySet(),
    ): Set<Triple<String, Int, Int>> {
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

                val warp = area.warpAt(nextX, nextY)
                val step = if (warp != null && (warp.requiresFlag == null || warp.requiresFlag in flags)) {
                    Triple(warp.toAreaId, warp.toX, warp.toY)
                } else {
                    Triple(areaId, nextX, nextY)
                }

                if (seen.add(step)) queue.add(step)
            }
        }
        return seen
    }

    /** Areas the player can walk to from [from]. */
    fun reachableAreas(from: WorldPosition, flags: Set<String> = emptySet()): Set<String> =
        reachableTiles(from, flags).map { it.first }.toSet()

    /**
     * True when the player can stand somewhere that faces [npc] in [areaId] — i.e. the character can
     * actually be talked to, not just seen across water.
     */
    fun isNpcApproachable(
        areaId: String,
        npc: NpcSpawn,
        from: WorldPosition,
        flags: Set<String> = emptySet(),
    ): Boolean {
        val reachable = reachableTiles(from, flags)
        return Direction.entries.any { direction ->
            val standX = npc.x + direction.dx
            val standY = npc.y + direction.dy
            Triple(areaId, standX, standY) in reachable
        }
    }
}
