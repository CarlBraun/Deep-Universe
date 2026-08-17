package com.deepuniverse.app.ui.puzzle

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.deepuniverse.core.puzzle.Dish
import com.deepuniverse.core.puzzle.DishQuality
import com.deepuniverse.core.puzzle.Garnish
import kotlin.math.sin

/**
 * The finished pot.
 *
 * The cooking minigame used to end on a progress bar reaching the right-hand side, which is a
 * statement that you won rather than a thing you made. This draws the actual dish — bowl, food,
 * garnish, steam — so the reward for holding the heat on the line is seeing dinner.
 *
 * Everything is parametric, like the character portraits: the colours and the garnish come from the
 * [Dish] and the shapes are drawn from them, so a new recipe is five numbers in `core` rather than
 * an art asset.
 */
@Composable
fun DishPlate(dish: Dish, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dish")
    // One slow phase drives every wisp; each gets a different offset into it, so the steam reads as
    // several independent curls without needing several animations.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)),
        label = "steam",
    )

    Canvas(modifier) { drawDish(dish, phase) }
}

private fun DrawScope.drawDish(dish: Dish, phase: Float) {
    val bowlColor = Color(dish.bowlColor)
    val foodColor = Color(dish.foodColor)
    val garnishColor = Color(dish.garnishColor)

    val centreX = size.width / 2f
    // Sized off both axes so the bowl never overflows a short, wide box or a tall, narrow one.
    val bowlWidth = minOf(size.width * 0.74f, size.height * 1.30f)
    val bowlDepth = bowlWidth * 0.36f
    val rimY = size.height * 0.66f

    // The table it is standing on.
    drawOval(
        color = Color.Black.copy(alpha = 0.28f),
        topLeft = Offset(centreX - bowlWidth * 0.44f, rimY + bowlDepth * 0.78f),
        size = Size(bowlWidth * 0.88f, bowlDepth * 0.26f),
    )

    // The bowl: the lower half of an ellipse, so it sits rather than floats.
    drawArc(
        color = bowlColor,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(centreX - bowlWidth / 2f, rimY - bowlDepth),
        size = Size(bowlWidth, bowlDepth * 2f),
    )
    // A curved specular down the near-left side, which is what makes glazed clay read as glazed.
    drawArc(
        color = bowlColor.lighten(0.30f),
        startAngle = 128f,
        sweepAngle = 42f,
        useCenter = false,
        topLeft = Offset(centreX - bowlWidth * 0.42f, rimY - bowlDepth * 0.82f),
        size = Size(bowlWidth * 0.84f, bowlDepth * 1.64f),
        style = Stroke(width = bowlWidth * 0.030f),
    )

    // What is in it. Mounded very slightly above the rim so it looks like a full bowl.
    val foodWidth = bowlWidth * 0.86f
    val foodHeight = bowlDepth * 0.58f
    val foodCentreY = rimY - bowlDepth * 0.07f
    drawOval(
        color = foodColor,
        topLeft = Offset(centreX - foodWidth / 2f, foodCentreY - foodHeight / 2f),
        size = Size(foodWidth, foodHeight),
    )
    drawOval(
        color = foodColor.lighten(0.26f),
        topLeft = Offset(centreX - foodWidth * 0.30f, foodCentreY - foodHeight * 0.36f),
        size = Size(foodWidth * 0.42f, foodHeight * 0.34f),
    )

    drawGarnish(dish.garnish, garnishColor, centreX, foodCentreY, foodWidth, foodHeight)

    // The near rim, drawn last so the food tucks in behind it.
    drawOval(
        color = bowlColor.lighten(0.16f),
        topLeft = Offset(centreX - bowlWidth / 2f, rimY - bowlDepth * 0.34f),
        size = Size(bowlWidth, bowlDepth * 0.68f),
        style = Stroke(width = bowlWidth * 0.042f),
    )

    if (dish.steaming) {
        drawSteam(centreX, foodCentreY - foodHeight, bowlWidth, phase, Color.White.copy(alpha = 0.30f))
    } else {
        // Burnt: the same wisps, darker, wider and slower — smoke rather than steam.
        drawSteam(centreX, foodCentreY - foodHeight, bowlWidth * 1.15f, phase * 0.6f, Color(0xFF6E665F).copy(alpha = 0.42f))
    }

    if (dish.quality == DishQuality.EXCELLENT) {
        drawSparkles(centreX, rimY - bowlDepth, bowlWidth, garnishColor)
    }
}

/** Scattered but fixed, so the same dish is plated the same way every time it is looked at. */
private val scatter = listOf(
    -0.34f to -0.18f,
    0.28f to -0.30f,
    -0.10f to 0.22f,
    0.36f to 0.14f,
    -0.28f to 0.30f,
    0.06f to -0.34f,
    0.20f to 0.34f,
)

private fun DrawScope.drawGarnish(
    garnish: Garnish,
    color: Color,
    centreX: Float,
    centreY: Float,
    width: Float,
    height: Float,
) {
    fun spot(index: Int): Offset {
        val (dx, dy) = scatter[index % scatter.size]
        return Offset(centreX + dx * width * 0.82f, centreY + dy * height * 0.86f)
    }

    when (garnish) {
        Garnish.HERBS -> repeat(5) { i ->
            val at = spot(i)
            // Angled leaves rather than lozenges — an unrotated oval reads as a sweet, not a herb.
            rotate(degrees = -34f + i * 27f, pivot = at) {
                drawOval(
                    color = color,
                    topLeft = Offset(at.x - width * 0.065f, at.y - height * 0.055f),
                    size = Size(width * 0.13f, height * 0.11f),
                )
            }
        }

        Garnish.SEEDS -> repeat(7) { i ->
            drawCircle(color = color, radius = width * 0.021f, center = spot(i))
        }

        Garnish.RINGS -> repeat(3) { i ->
            drawCircle(
                color = color,
                radius = width * 0.085f,
                center = spot(i),
                style = Stroke(width = width * 0.016f),
            )
        }

        Garnish.EMBERS -> repeat(5) { i ->
            val at = spot(i)
            drawCircle(color = color.copy(alpha = 0.35f), radius = width * 0.055f, center = at)
            drawCircle(color = color, radius = width * 0.020f, center = at)
        }

        Garnish.SPORES -> repeat(4) { i ->
            val at = spot(i)
            drawCircle(
                color = color.copy(alpha = 0.45f),
                radius = width * 0.062f,
                center = at,
                style = Stroke(width = width * 0.010f),
            )
            drawCircle(color = color, radius = width * 0.026f, center = at)
        }
    }
}

/** Three curling wisps, each riding the same phase from a different point in the cycle. */
private fun DrawScope.drawSteam(
    centreX: Float,
    topOfFood: Float,
    width: Float,
    phase: Float,
    color: Color,
) {
    val height = width * 0.62f
    for (wisp in 0 until 3) {
        val offsetX = centreX + (wisp - 1) * width * 0.20f
        val local = (phase + wisp / 3f) % 1f
        val path = Path()
        val baseY = topOfFood - height * local * 0.35f
        path.moveTo(offsetX, baseY)
        // Four segments of a sine, so the wisp bends one way then the other as it rises.
        for (segment in 1..4) {
            val t = segment / 4f
            val y = baseY - height * t
            val sway = sin((local * 2f + t * 2.2f) * Math.PI.toFloat()) * width * 0.055f
            path.lineTo(offsetX + sway, y)
        }
        drawPath(
            path = path,
            // Wisps fade out as they rise, so they dissipate instead of stopping dead.
            color = color.copy(alpha = color.alpha * (1f - local * 0.75f)),
            style = Stroke(width = width * 0.022f),
        )
    }
}

/** The mark of a cook that went really well. Four-point stars, drawn as crossed strokes. */
private fun DrawScope.drawSparkles(centreX: Float, aboveY: Float, width: Float, color: Color) {
    val places = listOf(-0.46f to -0.10f, 0.44f to -0.26f, -0.30f to -0.44f, 0.22f to -0.52f)
    for ((index, place) in places.withIndex()) {
        val (dx, dy) = place
        val at = Offset(centreX + dx * width, aboveY + dy * width)
        val arm = width * (0.030f + (index % 2) * 0.014f)
        drawLine(color, Offset(at.x - arm, at.y), Offset(at.x + arm, at.y), strokeWidth = width * 0.008f)
        drawLine(color, Offset(at.x, at.y - arm), Offset(at.x, at.y + arm), strokeWidth = width * 0.008f)
    }
}

/** Nudges a colour towards white, for rims and highlights. */
private fun Color.lighten(amount: Float): Color = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)
