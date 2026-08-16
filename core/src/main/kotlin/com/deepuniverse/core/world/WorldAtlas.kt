package com.deepuniverse.core.world

/**
 * Every location in the game, and who you find where.
 *
 * ### Reading the maps
 * Each map is ASCII art; see [TileType] for the legend. `T` is a pine, `.` grass, `-` a path, `s`
 * sand, `~` water, `#` a wall, `_` floorboards, `D` a door. Laying the camp out as a picture means
 * you can see at a glance that the path actually connects, which no list of coordinates would give
 * you. `WorldAtlasTest` then checks what the picture cannot: that every exit lands somewhere
 * walkable, and that every character is reachable on foot from the start.
 *
 * ### Where people stand
 * Nobody is placed at random. Each character is where their job puts them — the one who cooks is at
 * the fire pit, the one who runs briefings is at the lodge table, the navigator is at the end of the
 * pier with the sky. You learn who someone is by finding them before they ever speak.
 */
object WorldAtlas {

    const val YOUR_CABIN = "your_cabin"
    const val CABIN_ROW = "cabin_row"
    const val CAMP_CLEARING = "camp_clearing"
    const val GREAT_LODGE = "great_lodge"
    const val FIELD_LAB = "field_lab"
    const val PINE_PATH = "pine_path"
    const val THE_BEACH = "the_beach"

    /** A new player wakes up in their own bunk — the world opens from a door they choose to leave. */
    val startPosition = WorldPosition(YOUR_CABIN, x = 4, y = 4, facing = Direction.DOWN)

    private val yourCabin = Area(
        id = YOUR_CABIN,
        name = "Your Cabin",
        subtitle = "Bunk, desk, and a window facing the trees",
        indoors = true,
        map = TileMap(
            listOf(
                "##########",
                "#b_______#",
                "#________#",
                "#___==___#",
                "#________#",
                "#________#",
                "#________#",
                "####--####",
            ),
        ),
        warps = listOf(
            Warp(4, 7, CABIN_ROW, 5, 10, Direction.DOWN),
            Warp(5, 7, CABIN_ROW, 5, 10, Direction.DOWN),
        ),
    )

    private val fieldLab = Area(
        id = FIELD_LAB,
        name = "The Field Lab",
        subtitle = "Someone has labelled every jar, twice",
        indoors = true,
        map = TileMap(
            listOf(
                "##########",
                "#________#",
                "#_======_#",
                "#________#",
                "#_x____x_#",
                "#________#",
                "#________#",
                "####--####",
            ),
        ),
        warps = listOf(
            Warp(4, 7, CABIN_ROW, 3, 5, Direction.DOWN),
            Warp(5, 7, CABIN_ROW, 3, 5, Direction.DOWN),
        ),
        npcs = listOf(
            NpcSpawn(
                loveInterestId = "nadia",
                x = 4,
                y = 3,
                facing = Direction.UP,
                activity = "Cataloguing samples by lamplight",
                idleLine = "Sit if you like. I think better when someone else is breathing nearby.",
            ),
        ),
    )

    private val cabinRow = Area(
        id = CABIN_ROW,
        name = "Cabin Row",
        subtitle = "Four huts and a washing line",
        map = TileMap(
            listOf(
                "TTTTTTTTTTTTTTTT",
                "T..............T",
                "T.####...####..T",
                "T.#__#...#__#..T",
                "T.#D##...####..T",
                "T..............T",
                "-..............T",
                "T...####.......T",
                "T...#__#.......T",
                "T...#D##.......T",
                "T..............T",
                "TTTTTTTTTTTTTTTT",
            ),
        ),
        warps = listOf(
            Warp(3, 4, FIELD_LAB, 4, 6, Direction.UP),
            Warp(5, 9, YOUR_CABIN, 4, 6, Direction.UP),
            Warp(0, 6, CAMP_CLEARING, 14, 6, Direction.LEFT),
        ),
    )

    private val campClearing = Area(
        id = CAMP_CLEARING,
        name = "Camp Clearing",
        subtitle = "The fire is always going, and so is Idris",
        map = TileMap(
            listOf(
                "TTTTTTT--TTTTTTT",
                "T..,,......,,..T",
                "T.*..........*.T",
                "T.....---......T",
                "T....--f--.....T",
                "T.....---......T",
                "T...x.....x....-",
                "T..............T",
                "T..............T",
                "T..*........*..T",
                "T......--......T",
                "TTTTTTT--TTTTTTT",
            ),
        ),
        warps = listOf(
            Warp(7, 0, GREAT_LODGE, 7, 10, Direction.UP),
            Warp(8, 0, GREAT_LODGE, 8, 10, Direction.UP),
            Warp(7, 11, PINE_PATH, 7, 1, Direction.DOWN),
            Warp(8, 11, PINE_PATH, 8, 1, Direction.DOWN),
            Warp(15, 6, CABIN_ROW, 1, 6, Direction.RIGHT),
        ),
        npcs = listOf(
            NpcSpawn(
                loveInterestId = "idris",
                x = 8,
                y = 4,
                facing = Direction.LEFT,
                activity = "Cooking for everyone, badly, cheerfully",
                idleLine = "Taste this. No, don't look at it. Taste it.",
            ),
        ),
    )

    private val greatLodge = Area(
        id = GREAT_LODGE,
        name = "The Great Lodge",
        subtitle = "Where the whole camp meets, under one long roof",
        indoors = true,
        map = TileMap(
            listOf(
                "################",
                "#______________#",
                "#_==========___#",
                "#_hhhhhhhhhh___#",
                "#______________#",
                "#___x______x___#",
                "#______________#",
                "#_b__________b_#",
                "#______________#",
                "#______________#",
                "#______________#",
                "#######--#######",
            ),
        ),
        warps = listOf(
            Warp(7, 11, CAMP_CLEARING, 7, 1, Direction.DOWN),
            Warp(8, 11, CAMP_CLEARING, 8, 1, Direction.DOWN),
        ),
        npcs = listOf(
            NpcSpawn(
                loveInterestId = "sev",
                x = 6,
                y = 4,
                facing = Direction.UP,
                activity = "Standing over a map nobody asked him to redraw",
                idleLine = "The camp runs because someone stays awake. That is all it is.",
            ),
        ),
    )

    private val pinePath = Area(
        id = PINE_PATH,
        name = "The Pine Path",
        subtitle = "The long way down to the water",
        map = TileMap(
            listOf(
                "TTTTTTT--TTTTTTT",
                "TT....,,--,,..TT",
                "T..,,,.--..,,..T",
                "T....---.......T",
                "T...--.........T",
                "T..--..o.......T",
                "T..--..........T",
                "T..--...,,,....T",
                "T..---.........T",
                "T....--........T",
                "T.....--.......T",
                "TTTTTT--TTTTTTTT",
            ),
        ),
        warps = listOf(
            Warp(7, 0, CAMP_CLEARING, 7, 10, Direction.UP),
            Warp(8, 0, CAMP_CLEARING, 8, 10, Direction.UP),
            Warp(6, 11, THE_BEACH, 6, 1, Direction.DOWN),
            Warp(7, 11, THE_BEACH, 7, 1, Direction.DOWN),
        ),
        npcs = listOf(
            NpcSpawn(
                loveInterestId = "rook",
                x = 8,
                y = 5,
                facing = Direction.LEFT,
                activity = "Off the path, where she can see you coming",
                idleLine = "You walk loud. I heard you from the ridge.",
            ),
        ),
    )

    private val theBeach = Area(
        id = THE_BEACH,
        name = "The Beach",
        subtitle = "Grey water, and a pier someone rebuilt badly",
        map = TileMap(
            listOf(
                "TTTTTT--TTTTTTTT",
                "T..ssssssssss..T",
                "T.ssssssssssss.T",
                "TssssssssssssssT",
                "TssssssssssssssT",
                "TssssssssssssssT",
                "T~~~~~~pp~~~~~~T",
                "T~~~~~~pp~~~~~~T",
                "T~~~~~~pp~~~~~~T",
                "T~~~~~~~~~~~~~~T",
                "T~~~~~~~~~~~~~~T",
                "TTTTTTTTTTTTTTTT",
            ),
        ),
        warps = listOf(
            Warp(6, 0, PINE_PATH, 6, 10, Direction.UP),
            Warp(7, 0, PINE_PATH, 7, 10, Direction.UP),
        ),
        npcs = listOf(
            NpcSpawn(
                loveInterestId = "lyra",
                x = 4,
                y = 4,
                facing = Direction.DOWN,
                activity = "Daring the water to be colder than she is",
                idleLine = "Swim with me. You've got that face people make right before they say no.",
            ),
            NpcSpawn(
                loveInterestId = "kaito",
                x = 8,
                y = 8,
                facing = Direction.DOWN,
                activity = "At the end of the pier, charting something",
                idleLine = "The tide's four minutes early. I don't know what that means yet.",
            ),
        ),
    )

    val areas: List<Area> = listOf(
        yourCabin,
        cabinRow,
        fieldLab,
        campClearing,
        greatLodge,
        pinePath,
        theBeach,
    )

    private val byId = areas.associateBy { it.id }

    fun area(id: String): Area = byId[id] ?: error("Unknown area: $id")

    /** Where in the world a given character is standing, for the journal's "last seen" line. */
    fun locationOf(loveInterestId: String): Area? =
        areas.firstOrNull { area -> area.npcs.any { it.loveInterestId == loveInterestId } }
}
