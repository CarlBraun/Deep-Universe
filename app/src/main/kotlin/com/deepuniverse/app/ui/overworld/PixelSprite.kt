package com.deepuniverse.app.ui.overworld

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.deepuniverse.core.character.AppearanceParam
import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.character.EarType
import com.deepuniverse.core.character.HairStyle
import com.deepuniverse.core.world.Direction

/**
 * Draws the tiny overworld version of a character.
 *
 * ### Why it is built from the same appearance data
 * The walking sprite is not a separate asset — it reads the very same [CharacterAppearance] the
 * portrait does, so the skin tone, hair colour, hair length and outfit you chose (or that the photo
 * generator picked for you) are the ones walking around the camp. Change a slider in the creator and
 * the little pixel person changes too, with no extra art and nothing to keep in sync.
 *
 * Everything is drawn on a 12x16 pixel grid and scaled up, so it stays crisp and deliberately
 * chunky at any tile size rather than turning into a smudge on a high-density screen.
 */
object PixelSprite {

    private const val GRID_W = 12
    private const val GRID_H = 16

    /**
     * @param frame walk-cycle frame; 0 and 2 are the mid-stride poses, 1 and 3 the passing poses.
     * @param height how tall the sprite should be drawn, in pixels. The sprite is drawn standing on
     *   [origin], which is the bottom-centre of its tile, so taller characters sink into the ground
     *   correctly rather than floating.
     */
    fun DrawScope.drawCharacterSprite(
        appearance: CharacterAppearance,
        facing: Direction,
        frame: Int,
        origin: Offset,
        height: Float,
    ) {
        val unit = height / GRID_H
        val left = origin.x - (GRID_W * unit) / 2f
        val top = origin.y - height

        fun px(x: Int, y: Int, w: Int, h: Int, color: Color) {
            drawRect(
                color = color,
                topLeft = Offset(left + x * unit, top + y * unit),
                size = Size(w * unit, h * unit),
            )
        }

        val skin = Color(appearance.skinColor)
        val skinShade = skin.scaled(0.82f)
        val hair = Color(appearance.hairColor)
        val hairShade = hair.scaled(0.72f)
        val eye = Color(appearance.eyeColor)
        // The outfit picks up the hair colour so the character reads as one design, darkened enough
        // to stay clearly separate from it.
        val outfit = hair.scaled(0.45f).mixedWith(Color(0xFF2A2440), 0.55f)
        val outfitShade = outfit.scaled(0.75f)
        val boots = Color(0xFF2B2233)

        val longHair = appearance.hairStyle in LONG_STYLES
        val bigHair = appearance.hairStyle == HairStyle.CURLY_CLOUD

        // ---- shadow on the ground, so the sprite sits in the world -----------
        drawOval(
            color = Color.Black.copy(alpha = 0.25f),
            topLeft = Offset(left + 2 * unit, top + (GRID_H - 1.2f) * unit),
            size = Size(8 * unit, 1.6f * unit),
        )

        // ---- hair behind the head and shoulders ------------------------------
        // Stacked and tapered rather than one block: a single rectangle of hair reads as a square
        // stuck to the character, which is exactly how it looked.
        if (longHair) {
            val fall = if (bigHair) 9 else 11
            px(2, 2, 8, 2, hairShade)
            px(1, 4, 10, 3, hairShade)
            px(2, 7, 8, fall - 5, hairShade)
            px(3, fall + 2, 6, 1, hairShade)
        }

        // ---- legs -------------------------------------------------------------
        val stride = when (frame % 4) {
            1 -> 1
            3 -> -1
            else -> 0
        }
        px(4, 12, 2, 4 - maxOf(0, stride), boots)
        px(6, 12, 2, 4 - maxOf(0, -stride), boots)

        // ---- body -------------------------------------------------------------
        px(3, 8, 6, 5, outfit)
        px(3, 8, 6, 1, outfitShade)
        // Arms, swinging opposite to the legs.
        px(2, 8 + maxOf(0, -stride), 1, 3, skin)
        px(9, 8 + maxOf(0, stride), 1, 3, skin)

        // ---- ears, before the head so they sit behind the face ---------------
        when (appearance.earType) {
            EarType.ROUNDED -> Unit
            EarType.TAPERED -> {
                px(2, 4, 1, 2, skin)
                px(9, 4, 1, 2, skin)
            }
            EarType.LONG -> {
                px(2, 3, 1, 3, skin)
                px(1, 2, 1, 2, skin)
                px(9, 3, 1, 3, skin)
                px(10, 2, 1, 2, skin)
            }
            EarType.FINNED -> {
                px(2, 4, 2, 1, skin)
                px(1, 5, 2, 1, skinShade)
                px(8, 4, 2, 1, skin)
                px(9, 5, 2, 1, skinShade)
            }
        }

        // ---- head -------------------------------------------------------------
        px(3, 3, 6, 5, skin)
        px(3, 7, 6, 1, skinShade)

        // ---- hair on top, shaped by the chosen style --------------------------
        when (appearance.hairStyle) {
            HairStyle.BUZZ -> px(3, 2, 6, 1, hair)

            HairStyle.SLICKED_BACK, HairStyle.UNDERCUT -> {
                px(3, 2, 6, 2, hair)
                px(3, 2, 6, 1, hairShade)
            }

            HairStyle.CURLY_CLOUD -> {
                px(2, 1, 8, 3, hair)
                px(1, 2, 1, 2, hair)
                px(10, 2, 1, 2, hair)
            }

            HairStyle.TWIN_TAILS -> {
                px(3, 2, 6, 2, hair)
                px(1, 4, 2, 5, hair)
                px(9, 4, 2, 5, hair)
            }

            else -> {
                px(3, 2, 6, 3, hair)
                // A fringe that leaves the face clear.
                px(3, 4, 2, 1, hair)
                px(7, 4, 2, 1, hair)
            }
        }

        // ---- face, only when we can see it ------------------------------------
        when (facing) {
            Direction.DOWN -> {
                px(4, 5, 1, 1, eye)
                px(7, 5, 1, 1, eye)
                px(5, 6, 2, 1, skinShade)
            }

            Direction.LEFT -> {
                px(4, 5, 1, 1, eye)
                px(3, 5, 1, 1, skinShade)
            }

            Direction.RIGHT -> {
                px(7, 5, 1, 1, eye)
                px(8, 5, 1, 1, skinShade)
            }

            // Facing away: the back of the head is all hair.
            Direction.UP -> px(3, 3, 6, 3, hair)
        }
    }

    private val LONG_STYLES = setOf(
        HairStyle.LONG_STRAIGHT,
        HairStyle.LONG_WAVY,
        HairStyle.SHOULDER_LAYERED,
        HairStyle.CURLY_CLOUD,
        HairStyle.TWIN_TAILS,
    )

    /** How tall this character should stand, from the Height slider. */
    fun spriteHeight(appearance: CharacterAppearance, tileSize: Float): Float {
        val tallness = 1.35f + appearance[AppearanceParam.HEIGHT] * 0.35f
        return tileSize * tallness
    }

    private fun Color.scaled(factor: Float) = Color(
        red = (red * factor).coerceIn(0f, 1f),
        green = (green * factor).coerceIn(0f, 1f),
        blue = (blue * factor).coerceIn(0f, 1f),
        alpha = alpha,
    )

    private fun Color.mixedWith(other: Color, amount: Float): Color {
        val t = amount.coerceIn(0f, 1f)
        return Color(
            red = red + (other.red - red) * t,
            green = green + (other.green - green) * t,
            blue = blue + (other.blue - blue) * t,
            alpha = alpha,
        )
    }
}
