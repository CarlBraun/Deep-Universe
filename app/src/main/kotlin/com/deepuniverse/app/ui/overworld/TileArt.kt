package com.deepuniverse.app.ui.overworld

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.deepuniverse.core.world.TileType
import kotlin.random.Random

/**
 * Draws one map square.
 *
 * Every tile gets a flat base colour plus a little deterministic detail — speckles in the grass,
 * grain in the floorboards, ripples on the water. The detail is seeded from the tile's own
 * coordinates, so it is identical every frame and every time you re-enter an area: the world looks
 * hand-placed rather than noisy, and nothing shimmers as you walk.
 */
object TileArt {

    fun DrawScope.drawTile(tile: TileType, x: Int, y: Int, left: Float, top: Float, size: Float) {
        val random = Random(x * 73856093 xor y * 19349663)

        fun fill(color: Color) {
            drawRect(color, topLeft = Offset(left, top), size = Size(size, size))
        }

        fun speck(color: Color, count: Int, scale: Float = 0.09f) {
            repeat(count) {
                val sx = left + random.nextFloat() * size * 0.85f
                val sy = top + random.nextFloat() * size * 0.85f
                drawRect(color, topLeft = Offset(sx, sy), size = Size(size * scale, size * scale))
            }
        }

        when (tile) {
            TileType.GRASS -> {
                fill(GRASS)
                speck(GRASS_DARK, 3)
            }

            TileType.TALL_GRASS -> {
                fill(GRASS)
                repeat(5) {
                    val sx = left + random.nextFloat() * size * 0.8f
                    val sy = top + random.nextFloat() * size * 0.6f
                    drawRect(
                        GRASS_TALL,
                        topLeft = Offset(sx, sy),
                        size = Size(size * 0.08f, size * 0.34f),
                    )
                }
            }

            TileType.FLOWERS -> {
                fill(GRASS)
                speck(GRASS_DARK, 2)
                repeat(3) {
                    val sx = left + random.nextFloat() * size * 0.75f
                    val sy = top + random.nextFloat() * size * 0.75f
                    val petal = if (random.nextBoolean()) FLOWER_A else FLOWER_B
                    drawRect(petal, topLeft = Offset(sx, sy), size = Size(size * 0.16f, size * 0.16f))
                }
            }

            TileType.PATH -> {
                fill(PATH)
                speck(PATH_DARK, 4)
            }

            TileType.SAND -> {
                fill(SAND)
                speck(SAND_DARK, 3, scale = 0.07f)
            }

            TileType.WATER -> {
                fill(WATER)
                // Two ripples, offset by row so the sea reads as moving in bands.
                repeat(2) { i ->
                    val ry = top + size * (0.25f + i * 0.38f)
                    val rx = left + size * (if ((x + y + i) % 2 == 0) 0.12f else 0.44f)
                    drawRect(
                        WATER_FOAM,
                        topLeft = Offset(rx, ry),
                        size = Size(size * 0.34f, size * 0.07f),
                    )
                }
            }

            TileType.FLOOR -> {
                fill(FLOOR)
                drawRect(
                    FLOOR_LINE,
                    topLeft = Offset(left, top + size * 0.82f),
                    size = Size(size, size * 0.05f),
                )
            }

            TileType.PIER -> {
                fill(WATER)
                drawRect(PIER, topLeft = Offset(left, top), size = Size(size, size * 0.9f))
                drawRect(
                    PIER_DARK,
                    topLeft = Offset(left, top + size * 0.42f),
                    size = Size(size, size * 0.08f),
                )
            }

            TileType.DOOR -> {
                fill(WALL)
                drawRect(
                    DOOR,
                    topLeft = Offset(left + size * 0.15f, top + size * 0.12f),
                    size = Size(size * 0.7f, size * 0.88f),
                )
                drawRect(
                    DOOR_KNOB,
                    topLeft = Offset(left + size * 0.68f, top + size * 0.52f),
                    size = Size(size * 0.1f, size * 0.1f),
                )
            }

            TileType.WALL -> {
                fill(WALL)
                // Log courses.
                repeat(3) { i ->
                    drawRect(
                        WALL_LINE,
                        topLeft = Offset(left, top + size * (0.28f * i + 0.16f)),
                        size = Size(size, size * 0.05f),
                    )
                }
            }

            TileType.TREE -> {
                fill(GRASS_DARK)
                drawRect(
                    TRUNK,
                    topLeft = Offset(left + size * 0.42f, top + size * 0.55f),
                    size = Size(size * 0.16f, size * 0.45f),
                )
                drawOval(
                    color = CANOPY,
                    topLeft = Offset(left + size * 0.03f, top - size * 0.12f),
                    size = Size(size * 0.94f, size * 0.85f),
                )
                drawOval(
                    color = CANOPY_LIGHT,
                    topLeft = Offset(left + size * 0.18f, top - size * 0.02f),
                    size = Size(size * 0.44f, size * 0.36f),
                )
            }

            TileType.ROCK -> {
                fill(GRASS)
                drawOval(
                    color = ROCK,
                    topLeft = Offset(left + size * 0.1f, top + size * 0.22f),
                    size = Size(size * 0.8f, size * 0.66f),
                )
                drawOval(
                    color = ROCK_LIGHT,
                    topLeft = Offset(left + size * 0.24f, top + size * 0.32f),
                    size = Size(size * 0.3f, size * 0.2f),
                )
            }

            TileType.CAMPFIRE -> {
                fill(PATH)
                drawOval(
                    color = ROCK,
                    topLeft = Offset(left + size * 0.06f, top + size * 0.34f),
                    size = Size(size * 0.88f, size * 0.6f),
                )
                drawOval(
                    color = FIRE_OUTER,
                    topLeft = Offset(left + size * 0.24f, top + size * 0.14f),
                    size = Size(size * 0.52f, size * 0.62f),
                )
                drawOval(
                    color = FIRE_INNER,
                    topLeft = Offset(left + size * 0.36f, top + size * 0.3f),
                    size = Size(size * 0.28f, size * 0.38f),
                )
            }

            TileType.TABLE -> {
                fill(FLOOR)
                drawRect(
                    TABLE,
                    topLeft = Offset(left, top + size * 0.14f),
                    size = Size(size, size * 0.72f),
                )
                drawRect(
                    TABLE_LIGHT,
                    topLeft = Offset(left, top + size * 0.14f),
                    size = Size(size, size * 0.12f),
                )
            }

            TileType.CHAIR -> {
                fill(FLOOR)
                drawRect(
                    TABLE_DARK,
                    topLeft = Offset(left + size * 0.2f, top + size * 0.26f),
                    size = Size(size * 0.6f, size * 0.5f),
                )
            }

            TileType.BED -> {
                fill(FLOOR)
                drawRect(
                    BED_FRAME,
                    topLeft = Offset(left + size * 0.08f, top + size * 0.1f),
                    size = Size(size * 0.84f, size * 0.82f),
                )
                drawRect(
                    BED_PILLOW,
                    topLeft = Offset(left + size * 0.16f, top + size * 0.16f),
                    size = Size(size * 0.68f, size * 0.26f),
                )
            }

            TileType.CRATE -> {
                fill(GRASS)
                drawRect(
                    CRATE,
                    topLeft = Offset(left + size * 0.08f, top + size * 0.14f),
                    size = Size(size * 0.84f, size * 0.78f),
                )
                drawRect(
                    CRATE_LINE,
                    topLeft = Offset(left + size * 0.08f, top + size * 0.48f),
                    size = Size(size * 0.84f, size * 0.07f),
                )
            }
        }
    }

    // A deliberately small palette, so seven hand-drawn maps still look like one place.
    private val GRASS = Color(0xFF4C7A3E)
    private val GRASS_DARK = Color(0xFF3D6533)
    private val GRASS_TALL = Color(0xFF35592C)
    private val FLOWER_A = Color(0xFFE8D26A)
    private val FLOWER_B = Color(0xFFE0899F)
    private val PATH = Color(0xFFA98B62)
    private val PATH_DARK = Color(0xFF947450)
    private val SAND = Color(0xFFDFCB94)
    private val SAND_DARK = Color(0xFFCBB47C)
    private val WATER = Color(0xFF2F5F8E)
    private val WATER_FOAM = Color(0xFF6C9CC4)
    private val FLOOR = Color(0xFF8A6742)
    private val FLOOR_LINE = Color(0xFF755636)
    private val PIER = Color(0xFF7A5C3A)
    private val PIER_DARK = Color(0xFF63482C)
    private val WALL = Color(0xFF6B4A2E)
    private val WALL_LINE = Color(0xFF57391F)
    private val DOOR = Color(0xFF3F2A18)
    private val DOOR_KNOB = Color(0xFFD9BE72)
    private val TRUNK = Color(0xFF54381F)
    private val CANOPY = Color(0xFF2F5A2C)
    private val CANOPY_LIGHT = Color(0xFF3E7238)
    private val ROCK = Color(0xFF7C7A80)
    private val ROCK_LIGHT = Color(0xFF9B99A0)
    private val FIRE_OUTER = Color(0xFFE0722F)
    private val FIRE_INNER = Color(0xFFF5C242)
    private val TABLE = Color(0xFF7A5433)
    private val TABLE_LIGHT = Color(0xFF8F6640)
    private val TABLE_DARK = Color(0xFF5F3F24)
    private val BED_FRAME = Color(0xFF4A5C86)
    private val BED_PILLOW = Color(0xFFDDE3EF)
    private val CRATE = Color(0xFF9A7444)
    private val CRATE_LINE = Color(0xFF7E5C33)
}
