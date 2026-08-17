package com.deepuniverse.app.ui.avatar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
// animateFloat is an extension on InfiniteTransition and needs importing separately from
// animateFloatAsState, which is a different function entirely.
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import com.deepuniverse.core.character.AppearanceParam
import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.character.EarType
import com.deepuniverse.core.character.Expression
import com.deepuniverse.core.character.EarType
import com.deepuniverse.core.character.ExpressionShape
import com.deepuniverse.core.character.HairStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Draws a character portrait from their [CharacterAppearance].
 *
 * ### What this renderer is trying to be
 * Not a diagram of a face — a portrait somebody would want to look at. The things that actually
 * make a stylised character read as appealing, in rough order of impact:
 *
 * 1. **The eyes.** Most of the work is here: a graded iris, a dark limbal ring, a heavy upper lash
 *    line, a lid shadow and two catchlights at opposing corners. Flat eyes are the single biggest
 *    reason a drawn face looks dead.
 * 2. **Light with a direction.** One key light from the upper left, a warm bounce underneath, and a
 *    rim light along the opposite edge. The rim is what lifts the head off the background and does
 *    most of the "3D" work without any 3D.
 * 3. **Hair in masses, not outlines** — a back mass, a front fringe, and a single bright highlight
 *    band across the crown.
 * 4. **A background that belongs to the character**, tinted to their own colours, so the portrait
 *    reads as composed rather than cut out.
 *
 * Everything is still parametric, so all of it responds to the sliders and to whatever the photo
 * generator produced — and [expression] bends the same face into the collectable ones.
 */
@Composable
fun AvatarPortrait(
    appearance: CharacterAppearance,
    modifier: Modifier = Modifier,
    expression: Expression = Expression.NEUTRAL,
    showBackdrop: Boolean = true,
    animated: Boolean = true,
    /** Tints the backdrop. Defaults to the character's own hair colour. */
    accent: Color? = null,
) {
    val shape = ExpressionShape.of(expression)

    // Idle motion. A completely still portrait reads as a picture; a breathing, blinking one reads
    // as somebody waiting for you to say something.
    val transition = rememberInfiniteTransition(label = "idle")
    val breathe by if (animated) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(3400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breathe",
        )
    } else {
        animateFloatAsState(0.5f, label = "breathe")
    }

    val blinkPhase by if (animated) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(4600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "blink",
        )
    } else {
        animateFloatAsState(0f, label = "blink")
    }

    val shimmer by if (animated) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmer",
        )
    } else {
        animateFloatAsState(0f, label = "shimmer")
    }

    // A blink is short and sudden: shut for a sliver at the end of each cycle.
    val blink = when {
        shape.eyesClosed -> 1f
        blinkPhase > 0.965f -> ((blinkPhase - 0.965f) / 0.0175f).coerceIn(0f, 1f)
        blinkPhase > 0.9825f -> (1f - (blinkPhase - 0.9825f) / 0.0175f).coerceIn(0f, 1f)
        else -> 0f
    }

    Canvas(modifier = modifier) {
        drawPortrait(
            a = appearance,
            shape = shape,
            blink = blink,
            breathe = breathe,
            shimmer = shimmer,
            accent = accent ?: Color(appearance.hairColor),
            showBackdrop = showBackdrop,
        )
    }
}

// --------------------------------------------------------------------------- layout

private class Face(
    val cx: Float,
    val top: Float,
    val chinY: Float,
    val cheekHalf: Float,
    val jawHalf: Float,
    val chinHalf: Float,
    val cheekY: Float,
    val jawY: Float,
    val eyeY: Float,
    val eyeOffset: Float,
    val eyeW: Float,
    val eyeH: Float,
    val tilt: Float,
) {
    val height: Float get() = chinY - top
}

private fun DrawScope.drawPortrait(
    a: CharacterAppearance,
    shape: ExpressionShape,
    blink: Float,
    breathe: Float,
    shimmer: Float,
    accent: Color,
    showBackdrop: Boolean,
) {
    val w = size.width
    val h = size.height

    val skin = Color(a.skinColor)
    val hair = Color(a.hairColor)
    val eyeColor = Color(a.eyeColor)

    if (showBackdrop) drawBackdrop(accent, shimmer)

    // Breathing lifts the whole figure by a hair and widens the chest slightly.
    val lift = (breathe - 0.5f) * h * 0.006f

    val faceH = h * mix(0.40f, 0.48f, a[AppearanceParam.FACE_LENGTH])
    val faceW = w * mix(0.40f, 0.52f, a[AppearanceParam.FACE_WIDTH])
    val top = h * 0.155f + lift
    val chinY = top + faceH
    val cheekHalf = faceW / 2f
    val jawHalf = cheekHalf * mix(0.58f, 0.90f, a[AppearanceParam.JAW_WIDTH])
    val chinHalf = jawHalf * mix(0.66f, 0.30f, a[AppearanceParam.JAW_SHARPNESS])

    val face = Face(
        cx = w / 2f,
        top = top,
        chinY = chinY,
        cheekHalf = cheekHalf,
        jawHalf = jawHalf,
        chinHalf = chinHalf,
        cheekY = top + faceH * mix(0.46f, 0.36f, a[AppearanceParam.CHEEKBONES]),
        jawY = top + faceH * mix(0.66f, 0.75f, a[AppearanceParam.CHIN_LENGTH]),
        eyeY = top + faceH * mix(0.56f, 0.46f, a[AppearanceParam.EYE_HEIGHT]),
        eyeOffset = cheekHalf * mix(0.36f, 0.54f, a[AppearanceParam.EYE_SPACING]),
        // Deliberately generous: large eyes are most of what makes a stylised face appealing.
        eyeW = faceW * mix(0.185f, 0.275f, a[AppearanceParam.EYE_SIZE]),
        eyeH = 0f,
        tilt = mix(-8f, 14f, (a[AppearanceParam.EYE_TILT] + shape.eyeTilt).coerceIn(0f, 1f)),
    )
    val openness = (a[AppearanceParam.EYE_OPENNESS] + shape.eyeOpenness).coerceIn(0f, 1f)
    val eyeH = face.eyeW * mix(0.52f, 0.96f, openness) * (1f - blink * 0.94f)

    // The whole head tilts with the expression, which reads as personality more than any single
    // feature does.
    rotate(degrees = shape.headTilt, pivot = Offset(face.cx, chinY)) {
        val facePath = facePath(face)

        drawBackHair(a, hair, face, shimmer)
        drawBody(a, skin, hair, face, w, h, breathe)
        drawEars(a, skin, face)

        // ---- head, lit from the upper left ----------------------------------
        drawPath(facePath, skin)
        clipPath(facePath) {
            // Core shadow under the jaw and along the right side.
            drawRect(
                brush = Brush.verticalGradient(
                    0.45f to Color.Transparent,
                    1f to skin.shade(0.74f).copy(alpha = 0.75f),
                ),
                topLeft = Offset(face.cx - cheekHalf * 1.4f, face.top),
                size = Size(cheekHalf * 2.8f, face.height),
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    0.55f to Color.Transparent,
                    1f to skin.shade(0.80f).copy(alpha = 0.55f),
                ),
                topLeft = Offset(face.cx - cheekHalf, face.top),
                size = Size(cheekHalf * 2f, face.height),
            )
            // Key light on the forehead and left cheek.
            softGlow(
                centre = Offset(face.cx - cheekHalf * 0.35f, face.top + faceH * 0.26f),
                radius = cheekHalf * 1.15f,
                color = Color.White.copy(alpha = 0.13f + a[AppearanceParam.SKIN_GLOW] * 0.10f),
            )
            drawBlush(a, shape, face, faceH)
            drawFreckles(a, face, faceH, skin)
            drawNose(a, face, skin)
        }

        // Rim light: a bright sliver down the shadowed edge. This is what makes the head feel
        // like a solid object rather than a sticker.
        drawPath(
            facePath,
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, accent.lighten(1.9f).copy(alpha = 0.55f)),
                start = Offset(face.cx, face.top),
                end = Offset(face.cx + cheekHalf * 1.5f, chinY),
            ),
            style = Stroke(width = faceW * 0.018f),
        )

        drawMouth(a, shape, face, skin)

        for (side in listOf(-1f, 1f)) {
            drawEye(a, shape, face, eyeH, side, eyeColor, blink)
            drawBrow(a, shape, face, eyeH, side, hair)
        }

        drawFrontHair(a, hair, face, shimmer)
    }

    if (showBackdrop) drawVignette()
}

// --------------------------------------------------------------------------- background

private fun DrawScope.drawBackdrop(accent: Color, shimmer: Float) {
    drawRect(
        Brush.verticalGradient(
            listOf(
                accent.shade(0.35f).mix(Color(0xFF1A1430), 0.55f),
                Color(0xFF0A0814),
            ),
        ),
    )
    // A broad glow behind the head, in the character's own colour.
    softGlow(
        centre = Offset(size.width / 2f, size.height * 0.34f),
        radius = size.width * 0.72f,
        color = accent.copy(alpha = 0.30f),
    )

    // Drifting bokeh. Seeded, so it is stable, with only its brightness animated.
    val random = Random(11)
    repeat(26) { i ->
        val bx = random.nextFloat() * size.width
        val by = random.nextFloat() * size.height
        val r = size.width * (0.004f + random.nextFloat() * 0.020f)
        val phase = (shimmer + i * 0.13f) % 1f
        val pulse = 0.25f + 0.75f * (1f - kotlin.math.abs(phase - 0.5f) * 2f)
        drawCircle(
            color = Color.White.copy(alpha = 0.05f + pulse * 0.10f),
            radius = r,
            center = Offset(bx, by),
        )
    }
}

private fun DrawScope.drawVignette() {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
            center = Offset(size.width / 2f, size.height * 0.42f),
            radius = size.width * 0.95f,
        ),
    )
}

// --------------------------------------------------------------------------- head

private fun facePath(f: Face): Path {
    val templeHalf = f.cheekHalf * 0.95f
    val templeY = f.top + (f.cheekY - f.top) * 0.32f
    val jawTension = 0.42f

    return Path().apply {
        moveTo(f.cx, f.top)
        cubicTo(
            f.cx - f.cheekHalf * 0.70f, f.top,
            f.cx - templeHalf, templeY - (f.cheekY - f.top) * 0.30f,
            f.cx - templeHalf, templeY,
        )
        cubicTo(
            f.cx - f.cheekHalf, templeY + (f.cheekY - templeY) * 0.5f,
            f.cx - f.cheekHalf, f.cheekY - (f.cheekY - templeY) * 0.1f,
            f.cx - f.cheekHalf, f.cheekY,
        )
        cubicTo(
            f.cx - f.cheekHalf, f.cheekY + (f.jawY - f.cheekY) * 0.6f,
            f.cx - f.jawHalf * 1.02f, f.jawY - (f.jawY - f.cheekY) * 0.15f,
            f.cx - f.jawHalf, f.jawY,
        )
        cubicTo(
            f.cx - f.jawHalf, f.jawY + (f.chinY - f.jawY) * jawTension,
            f.cx - f.chinHalf, f.chinY - (f.chinY - f.jawY) * 0.10f,
            f.cx, f.chinY,
        )
        cubicTo(
            f.cx + f.chinHalf, f.chinY - (f.chinY - f.jawY) * 0.10f,
            f.cx + f.jawHalf, f.jawY + (f.chinY - f.jawY) * jawTension,
            f.cx + f.jawHalf, f.jawY,
        )
        cubicTo(
            f.cx + f.jawHalf * 1.02f, f.jawY - (f.jawY - f.cheekY) * 0.15f,
            f.cx + f.cheekHalf, f.cheekY + (f.jawY - f.cheekY) * 0.6f,
            f.cx + f.cheekHalf, f.cheekY,
        )
        cubicTo(
            f.cx + f.cheekHalf, f.cheekY - (f.cheekY - templeY) * 0.1f,
            f.cx + f.cheekHalf, templeY + (f.cheekY - templeY) * 0.5f,
            f.cx + templeHalf, templeY,
        )
        cubicTo(
            f.cx + templeHalf, templeY - (f.cheekY - f.top) * 0.30f,
            f.cx + f.cheekHalf * 0.70f, f.top,
            f.cx, f.top,
        )
        close()
    }
}

/**
 * Ears, which are most of what separates a person from Uto from a person from the camp.
 *
 * Drawn from the same skin colour as the face and swept back along the head, so a long ear reads as
 * part of the character rather than as something stuck on.
 */
private fun DrawScope.drawEars(a: CharacterAppearance, skin: Color, f: Face) {
    val earH = f.height * 0.15f
    val ear = skin.shade(0.93f)
    val inner = skin.shade(0.78f)

    for (side in listOf(-1f, 1f)) {
        val baseX = f.cx + side * f.cheekHalf
        when (a.earType) {
            EarType.ROUNDED -> {
                drawOval(
                    color = ear,
                    topLeft = Offset(baseX - earH * 0.26f, f.cheekY - earH * 0.30f),
                    size = Size(earH * 0.52f, earH),
                )
            }

            EarType.TAPERED, EarType.LONG -> {
                val reach = if (a.earType == EarType.LONG) 2.4f else 1.3f
                val tip = Offset(baseX + side * earH * reach, f.cheekY - earH * (0.55f + reach * 0.35f))
                val shell = Path().apply {
                    moveTo(baseX - side * earH * 0.10f, f.cheekY + earH * 0.42f)
                    quadraticTo(
                        baseX + side * earH * reach * 0.55f, f.cheekY + earH * 0.05f,
                        tip.x, tip.y,
                    )
                    quadraticTo(
                        baseX + side * earH * reach * 0.30f, f.cheekY - earH * 0.30f,
                        baseX - side * earH * 0.10f, f.cheekY - earH * 0.30f,
                    )
                    close()
                }
                drawPath(shell, ear)
                // A darker line down the inside, which is what makes a long ear read as an ear.
                drawPath(
                    Path().apply {
                        moveTo(baseX + side * earH * 0.10f, f.cheekY + earH * 0.10f)
                        quadraticTo(
                            baseX + side * earH * reach * 0.55f, f.cheekY - earH * 0.05f,
                            tip.x - side * earH * 0.25f, tip.y + earH * 0.22f,
                        )
                    },
                    color = inner,
                    style = Stroke(width = earH * 0.16f, cap = StrokeCap.Round),
                )
            }

            EarType.FINNED -> {
                // Three short fins fanning back from the temple.
                repeat(3) { i ->
                    val spread = earH * (0.9f + i * 0.28f)
                    val lift = earH * (0.5f - i * 0.34f)
                    val fin = Path().apply {
                        moveTo(baseX - side * earH * 0.10f, f.cheekY + earH * 0.30f - i * earH * 0.22f)
                        quadraticTo(
                            baseX + side * spread * 0.6f, f.cheekY - lift * 0.4f - i * earH * 0.20f,
                            baseX + side * spread, f.cheekY - lift - i * earH * 0.16f,
                        )
                        lineTo(baseX - side * earH * 0.10f, f.cheekY + earH * 0.05f - i * earH * 0.22f)
                        close()
                    }
                    drawPath(fin, if (i == 1) ear else ear.shade(0.88f))
                }
            }
        }
    }
}

private fun DrawScope.drawBody(
    a: CharacterAppearance,
    skin: Color,
    hair: Color,
    f: Face,
    w: Float,
    h: Float,
    breathe: Float,
) {
    val neckHalf = w * mix(0.052f, 0.082f, a[AppearanceParam.BUILD])
    val neckBottom = f.chinY + h * 0.070f

    drawRect(
        color = skin.shade(0.86f),
        topLeft = Offset(f.cx - neckHalf, f.chinY - h * 0.02f),
        size = Size(neckHalf * 2f, neckBottom - f.chinY + h * 0.03f),
    )
    // Shadow the neck casts under the jaw — cheap, and it seats the head on the body.
    drawOval(
        brush = Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent),
        ),
        topLeft = Offset(f.cx - neckHalf * 1.5f, f.chinY - h * 0.012f),
        size = Size(neckHalf * 3f, h * 0.055f),
    )

    val shoulderHalf = w * mix(0.32f, 0.50f, a[AppearanceParam.SHOULDER_WIDTH]) *
        (1f + (breathe - 0.5f) * 0.012f)
    val outfit = hair.shade(0.42f).mix(Color(0xFF1E1932), 0.60f)

    val body = Path().apply {
        moveTo(f.cx - neckHalf * 1.35f, neckBottom)
        cubicTo(
            f.cx - shoulderHalf * 0.72f, neckBottom + h * 0.010f,
            f.cx - shoulderHalf, neckBottom + h * 0.048f,
            f.cx - shoulderHalf, h,
        )
        lineTo(f.cx + shoulderHalf, h)
        cubicTo(
            f.cx + shoulderHalf, neckBottom + h * 0.048f,
            f.cx + shoulderHalf * 0.72f, neckBottom + h * 0.010f,
            f.cx + neckHalf * 1.35f, neckBottom,
        )
        close()
    }
    drawPath(body, outfit)
    clipPath(body) {
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0.10f),
                0.5f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.22f),
            ),
            topLeft = Offset(f.cx - shoulderHalf, neckBottom),
            size = Size(shoulderHalf * 2f, h - neckBottom),
        )
    }
    drawPath(body, outfit.lighten(1.45f).copy(alpha = 0.6f), style = Stroke(width = w * 0.005f))
}

// --------------------------------------------------------------------------- eyes

private fun DrawScope.drawEye(
    a: CharacterAppearance,
    shape: ExpressionShape,
    f: Face,
    eyeH: Float,
    side: Float,
    irisColor: Color,
    blink: Float,
) {
    val cx = f.cx + side * f.eyeOffset
    val cy = f.eyeY
    val halfW = f.eyeW / 2f
    val halfH = eyeH / 2f
    val lash = a[AppearanceParam.LASH_LENGTH]
    val makeup = a[AppearanceParam.EYE_MAKEUP]
    val lashWidth = f.eyeW * (0.045f + lash * 0.045f + makeup * 0.030f)

    rotate(degrees = f.tilt * side, pivot = Offset(cx, cy)) {

        // Shut: a single curved lash line, which reads far better than a squashed eye.
        if (blink > 0.85f) {
            val closed = Path().apply {
                moveTo(cx - halfW, cy)
                cubicTo(
                    cx - halfW * 0.4f, cy + f.eyeW * 0.16f,
                    cx + halfW * 0.4f, cy + f.eyeW * 0.16f,
                    cx + halfW, cy - f.eyeW * 0.02f,
                )
            }
            drawPath(
                closed,
                color = Color(0xFF1B1424),
                style = Stroke(width = lashWidth * 1.15f, cap = StrokeCap.Round),
            )
            return@rotate
        }

        val eye = Path().apply {
            moveTo(cx - halfW, cy)
            cubicTo(
                cx - halfW * 0.48f, cy - halfH * 1.32f,
                cx + halfW * 0.42f, cy - halfH * 1.22f,
                cx + halfW, cy - halfH * 0.08f,
            )
            cubicTo(
                cx + halfW * 0.45f, cy + halfH * 1.05f,
                cx - halfW * 0.45f, cy + halfH * 1.10f,
                cx - halfW, cy,
            )
            close()
        }

        // Sclera, slightly shaded rather than pure white.
        drawPath(eye, Color(0xFFF6F2FA))

        clipPath(eye) {
            val irisR = min(halfW * 0.82f, halfH * 1.45f)
            val centre = Offset(cx, cy - halfH * 0.04f)

            // Iris: dark at the rim, luminous at the bottom — the classic anime read.
            drawCircle(irisColor.shade(0.72f), radius = irisR, center = centre)
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(irisColor.shade(0.55f), irisColor.lighten(1.45f)),
                    startY = centre.y - irisR,
                    endY = centre.y + irisR,
                ),
                radius = irisR * 0.94f,
                center = centre,
            )
            // Limbal ring.
            drawCircle(
                color = irisColor.shade(0.35f),
                radius = irisR * 0.97f,
                center = centre,
                style = Stroke(width = irisR * 0.16f),
            )
            // A brighter pool low in the iris, where light bounces through.
            drawOval(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, irisColor.lighten(2.1f).copy(alpha = 0.85f)),
                ),
                topLeft = Offset(centre.x - irisR * 0.72f, centre.y - irisR * 0.1f),
                size = Size(irisR * 1.44f, irisR * 1.05f),
            )
            drawCircle(Color(0xFF120D1B), radius = irisR * 0.40f, center = centre)

            // Catchlights at opposing corners: the detail that makes eyes look wet.
            drawCircle(
                Color.White.copy(alpha = 0.95f),
                radius = irisR * 0.30f,
                center = Offset(centre.x - irisR * 0.34f, centre.y - irisR * 0.40f),
            )
            drawCircle(
                Color.White.copy(alpha = 0.55f),
                radius = irisR * 0.14f,
                center = Offset(centre.x + irisR * 0.36f, centre.y + irisR * 0.34f),
            )
            if (shape.sparkle > 0.01f) {
                drawCircle(
                    Color.White.copy(alpha = 0.75f * shape.sparkle),
                    radius = irisR * 0.11f,
                    center = Offset(centre.x + irisR * 0.10f, centre.y - irisR * 0.55f),
                )
            }

            // Shadow cast by the upper lid.
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent),
                ),
                topLeft = Offset(cx - halfW, cy - halfH * 1.35f),
                size = Size(f.eyeW, halfH * 1.15f),
            )
        }

        // Upper lash line, heavier towards the outer corner.
        val upperLid = Path().apply {
            moveTo(cx - halfW, cy)
            cubicTo(
                cx - halfW * 0.48f, cy - halfH * 1.32f,
                cx + halfW * 0.42f, cy - halfH * 1.22f,
                cx + halfW, cy - halfH * 0.08f,
            )
        }
        drawPath(
            upperLid,
            color = Color(0xFF1B1424),
            style = Stroke(width = lashWidth, cap = StrokeCap.Round),
        )
        // Lower lash line, much lighter — a full outline makes eyes look like buttons.
        val lowerLid = Path().apply {
            moveTo(cx - halfW * 0.75f, cy + halfH * 0.72f)
            quadraticTo(cx, cy + halfH * 1.05f, cx + halfW * 0.85f, cy + halfH * 0.35f)
        }
        drawPath(
            lowerLid,
            color = Color(0xFF3A2C42).copy(alpha = 0.75f),
            style = Stroke(width = lashWidth * 0.36f, cap = StrokeCap.Round),
        )

        // Crease above the lid.
        val crease = Path().apply {
            moveTo(cx - halfW * 0.8f, cy - halfH * 1.30f)
            quadraticTo(cx, cy - halfH * 1.85f, cx + halfW * 0.9f, cy - halfH * 0.9f)
        }
        drawPath(
            crease,
            color = Color(0xFF6B5568).copy(alpha = 0.35f),
            style = Stroke(width = lashWidth * 0.28f, cap = StrokeCap.Round),
        )

        // Outer lashes and liner wing.
        if (lash > 0.25f || makeup > 0.05f) {
            val wing = halfW * (0.20f + makeup * 0.38f)
            drawLine(
                color = Color(0xFF1B1424),
                start = Offset(cx + side * halfW * 0.92f, cy - halfH * 0.12f),
                end = Offset(cx + side * (halfW + wing), cy - halfH * (0.55f + makeup * 0.5f)),
                strokeWidth = lashWidth * 0.85f,
                cap = StrokeCap.Round,
            )
            repeat(3) { i ->
                val t = 0.42f + i * 0.20f
                val lx = cx + side * halfW * t
                val ly = cy - halfH * (1.28f - (t - 0.42f) * 0.9f)
                drawLine(
                    color = Color(0xFF1B1424),
                    start = Offset(lx, ly),
                    end = Offset(
                        lx + side * halfW * 0.20f * max(lash, 0.35f),
                        ly - halfH * 0.55f * max(lash, 0.35f),
                    ),
                    strokeWidth = lashWidth * 0.5f,
                    cap = StrokeCap.Round,
                )
            }
        }

        if (shape.tears > 0.01f) {
            drawCircle(
                color = Color(0xFFBFE2FF).copy(alpha = 0.85f * shape.tears),
                radius = halfH * 0.30f,
                center = Offset(cx - side * halfW * 0.55f, cy + halfH * 0.95f),
            )
        }
    }
}

private fun DrawScope.drawBrow(
    a: CharacterAppearance,
    shape: ExpressionShape,
    f: Face,
    eyeH: Float,
    side: Float,
    hair: Color,
) {
    val height = (a[AppearanceParam.BROW_HEIGHT] + shape.browHeight).coerceIn(0f, 1f)
    val angle = (a[AppearanceParam.BROW_ANGLE] + shape.browAngle).coerceIn(0f, 1f)
    val cx = f.cx + side * f.eyeOffset
    val gap = f.eyeW * mix(0.30f, 0.62f, height)
    val browY = f.eyeY - eyeH * 0.7f - gap
    val thickness = f.eyeW * mix(0.055f, 0.135f, a[AppearanceParam.BROW_THICKNESS])
    val arch = f.eyeW * mix(-0.03f, 0.22f, angle)
    val halfW = f.eyeW * 0.56f

    val innerX = cx - side * halfW * 0.9f
    val outerX = cx + side * halfW * 1.08f
    val peakX = cx + side * halfW * 0.28f

    val brow = Path().apply {
        moveTo(innerX, browY + thickness * 0.55f)
        quadraticTo(peakX, browY - arch - thickness * 0.15f, outerX, browY - arch * 0.42f)
        quadraticTo(peakX, browY - arch + thickness * 0.95f, innerX, browY + thickness * 0.55f)
        close()
    }
    drawPath(brow, hair.shade(0.78f))
    // A softer echo above, so brows read as hair rather than as stickers.
    drawPath(
        brow,
        color = hair.shade(0.60f).copy(alpha = 0.5f),
        style = Stroke(width = thickness * 0.18f),
    )
}

// --------------------------------------------------------------------------- nose and mouth

private fun DrawScope.drawNose(a: CharacterAppearance, f: Face, skin: Color) {
    val length = (f.chinY - f.eyeY) * mix(0.32f, 0.50f, a[AppearanceParam.NOSE_LENGTH])
    val tipY = f.eyeY + length
    val halfW = f.cheekHalf * mix(0.10f, 0.19f, a[AppearanceParam.NOSE_WIDTH])
    val bridge = a[AppearanceParam.NOSE_BRIDGE]

    // Stylised noses are mostly shadow: a soft wedge on the shaded side plus a small highlight.
    drawPath(
        Path().apply {
            moveTo(f.cx + halfW * 0.30f, f.eyeY + length * 0.10f)
            quadraticTo(
                f.cx + halfW * 0.85f, tipY - length * 0.25f,
                f.cx + halfW * 0.35f, tipY,
            )
        },
        color = skin.shade(0.76f).copy(alpha = 0.30f + bridge * 0.35f),
        style = Stroke(width = halfW * 0.55f, cap = StrokeCap.Round),
    )
    drawOval(
        color = skin.shade(0.82f).copy(alpha = 0.55f),
        topLeft = Offset(f.cx - halfW * 0.75f, tipY - halfW * 0.18f),
        size = Size(halfW * 1.5f, halfW * 0.55f),
    )
    drawOval(
        color = Color.White.copy(alpha = 0.16f),
        topLeft = Offset(f.cx - halfW * 0.42f, tipY - halfW * 0.42f),
        size = Size(halfW * 0.7f, halfW * 0.34f),
    )
}

private fun DrawScope.drawMouth(
    a: CharacterAppearance,
    shape: ExpressionShape,
    f: Face,
    skin: Color,
) {
    val mouthY = f.eyeY + (f.chinY - f.eyeY) * mix(0.62f, 0.78f, a[AppearanceParam.MOUTH_HEIGHT])
    val width = (a[AppearanceParam.MOUTH_WIDTH] + shape.mouthWidth).coerceIn(0f, 1f)
    val curveAmount = (a[AppearanceParam.LIP_CURVE] + shape.mouthCurve).coerceIn(0f, 1f)
    val halfW = f.cheekHalf * mix(0.20f, 0.34f, width)
    val fullness = a[AppearanceParam.LIP_FULLNESS]
    val upperH = halfW * mix(0.16f, 0.38f, fullness)
    val lowerH = halfW * mix(0.20f, 0.48f, fullness)
    val curve = mix(halfW * 0.16f, -halfW * 0.22f, curveAmount)
    val open = shape.mouthOpen

    val lip = skin.mix(Color(0xFFAF4257), 0.62f)

    val lips = Path().apply {
        moveTo(f.cx - halfW, mouthY + curve)
        cubicTo(
            f.cx - halfW * 0.52f, mouthY - upperH,
            f.cx - halfW * 0.18f, mouthY - upperH * 0.82f,
            f.cx, mouthY - upperH * 0.30f,
        )
        cubicTo(
            f.cx + halfW * 0.18f, mouthY - upperH * 0.82f,
            f.cx + halfW * 0.52f, mouthY - upperH,
            f.cx + halfW, mouthY + curve,
        )
        cubicTo(
            f.cx + halfW * 0.52f, mouthY + lowerH + open * halfW * 0.55f,
            f.cx - halfW * 0.52f, mouthY + lowerH + open * halfW * 0.55f,
            f.cx - halfW, mouthY + curve,
        )
        close()
    }
    drawPath(lips, lip)
    clipPath(lips) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(lip.shade(0.72f), lip.lighten(1.18f)),
            ),
            topLeft = Offset(f.cx - halfW, mouthY - upperH),
            size = Size(halfW * 2f, upperH + lowerH + open * halfW),
        )
    }

    // An open mouth needs a dark interior or it reads as a smear.
    if (open > 0.02f) {
        val inner = Path().apply {
            moveTo(f.cx - halfW * 0.78f, mouthY + curve * 0.4f)
            quadraticTo(f.cx, mouthY + lowerH * 0.5f + open * halfW * 0.75f, f.cx + halfW * 0.78f, mouthY + curve * 0.4f)
            quadraticTo(f.cx, mouthY + curve * 0.2f, f.cx - halfW * 0.78f, mouthY + curve * 0.4f)
            close()
        }
        drawPath(inner, Color(0xFF52182A).copy(alpha = min(1f, open * 2.2f)))
        // Teeth, on a wide open smile.
        if (open > 0.35f) {
            clipPath(inner) {
                drawRect(
                    color = Color(0xFFF6EFF3),
                    topLeft = Offset(f.cx - halfW * 0.78f, mouthY + curve * 0.2f),
                    size = Size(halfW * 1.56f, lowerH * 0.55f),
                )
            }
        }
    }

    drawPath(
        Path().apply {
            moveTo(f.cx - halfW, mouthY + curve)
            cubicTo(
                f.cx - halfW * 0.38f, mouthY + upperH * 0.20f,
                f.cx + halfW * 0.38f, mouthY + upperH * 0.20f,
                f.cx + halfW, mouthY + curve,
            )
        },
        color = lip.shade(0.55f),
        style = Stroke(width = halfW * 0.070f, cap = StrokeCap.Round),
    )
    drawPath(
        Path().apply {
            moveTo(f.cx - halfW * 0.32f, mouthY + lowerH * 0.62f)
            quadraticTo(f.cx, mouthY + lowerH * 0.86f, f.cx + halfW * 0.32f, mouthY + lowerH * 0.62f)
        },
        color = Color.White.copy(alpha = 0.30f),
        style = Stroke(width = halfW * 0.085f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawBlush(
    a: CharacterAppearance,
    shape: ExpressionShape,
    f: Face,
    faceH: Float,
) {
    val strength = (a[AppearanceParam.BLUSH] + shape.blush).coerceIn(0f, 1f)
    if (strength <= 0.02f) return
    for (side in listOf(-1f, 1f)) {
        val centre = Offset(f.cx + side * f.cheekHalf * 0.56f, f.eyeY + faceH * 0.16f)
        softGlow(centre, f.cheekHalf * 0.46f, Color(0xFFE8697F).copy(alpha = 0.50f * strength))
        // Hatching over the top of the blush, a stylisation that reads well at small sizes.
        if (strength > 0.4f) {
            repeat(3) { i ->
                drawLine(
                    color = Color(0xFFD9546C).copy(alpha = 0.30f * strength),
                    start = Offset(centre.x - f.cheekHalf * 0.22f, centre.y - f.cheekHalf * (0.06f - i * 0.07f)),
                    end = Offset(centre.x + f.cheekHalf * 0.24f, centre.y - f.cheekHalf * (0.14f - i * 0.07f)),
                    strokeWidth = f.cheekHalf * 0.035f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun DrawScope.drawFreckles(a: CharacterAppearance, f: Face, faceH: Float, skin: Color) {
    val density = a[AppearanceParam.FRECKLES]
    if (density <= 0.02f) return
    val random = Random(31)
    val color = skin.shade(0.70f).copy(alpha = 0.55f)
    repeat((density * 46).toInt()) {
        val x = f.cx + (random.nextFloat() - 0.5f) * f.cheekHalf * 1.9f
        val y = f.cheekY + (random.nextFloat() - 0.30f) * faceH * 0.24f
        drawCircle(
            color,
            radius = f.cheekHalf * (0.011f + random.nextFloat() * 0.013f),
            center = Offset(x, y),
        )
    }
}

// --------------------------------------------------------------------------- hair

private fun DrawScope.drawBackHair(a: CharacterAppearance, hair: Color, f: Face, shimmer: Float) {
    val length = when (a.hairStyle) {
        HairStyle.LONG_STRAIGHT, HairStyle.LONG_WAVY -> 1.20f
        HairStyle.TWIN_TAILS -> 1.00f
        HairStyle.SHOULDER_LAYERED, HairStyle.CURLY_CLOUD -> 0.64f
        HairStyle.BOB -> 0.44f
        else -> 0.16f
    }
    if (length <= 0.2f) return

    val span = f.height
    val bottom = f.chinY + span * length
    val width = f.cheekHalf * when (a.hairStyle) {
        HairStyle.CURLY_CLOUD -> 1.80f
        HairStyle.LONG_WAVY -> 1.48f
        else -> 1.30f
    }

    val back = Path().apply {
        moveTo(f.cx - width, f.chinY)
        cubicTo(
            f.cx - width * 1.06f, f.top + span * 0.24f,
            f.cx - width * 0.74f, f.top - span * 0.13f,
            f.cx, f.top - span * 0.15f,
        )
        cubicTo(
            f.cx + width * 0.74f, f.top - span * 0.13f,
            f.cx + width * 1.06f, f.top + span * 0.24f,
            f.cx + width, f.chinY,
        )
        lineTo(f.cx + width * 0.90f, bottom)
        cubicTo(
            f.cx + width * 0.38f, bottom + span * 0.07f,
            f.cx - width * 0.38f, bottom + span * 0.07f,
            f.cx - width * 0.90f, bottom,
        )
        close()
    }
    drawPath(back, hair.shade(0.66f))
    clipPath(back) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(hair.shade(0.82f), hair.shade(0.42f)),
            ),
            topLeft = Offset(f.cx - width, f.top - span * 0.2f),
            size = Size(width * 2f, bottom - f.top + span * 0.3f),
        )
        // Strand separations.
        val random = Random(5)
        repeat(7) {
            val sx = f.cx + (random.nextFloat() - 0.5f) * width * 1.8f
            drawLine(
                color = hair.shade(0.45f).copy(alpha = 0.5f),
                start = Offset(sx, f.top + span * 0.1f),
                end = Offset(sx + width * 0.10f, bottom),
                strokeWidth = width * 0.035f,
                cap = StrokeCap.Round,
            )
        }
    }

    if (a.hairStyle == HairStyle.TWIN_TAILS) {
        for (side in listOf(-1f, 1f)) {
            val tailX = f.cx + side * f.cheekHalf * 1.45f
            drawOval(
                brush = Brush.verticalGradient(listOf(hair.shade(0.85f), hair.shade(0.5f))),
                topLeft = Offset(tailX - f.cheekHalf * 0.44f, f.top + span * 0.26f),
                size = Size(f.cheekHalf * 0.88f, span * 1.05f),
            )
        }
    }
}

private fun DrawScope.drawFrontHair(a: CharacterAppearance, hair: Color, f: Face, shimmer: Float) {
    val span = f.height
    val crownTop = f.top - span * 0.11f
    val crownHalf = f.cheekHalf * 1.07f

    val cap = Path().apply {
        moveTo(f.cx - crownHalf, f.top + span * 0.24f)
        cubicTo(
            f.cx - crownHalf, crownTop + span * 0.02f,
            f.cx - crownHalf * 0.54f, crownTop,
            f.cx, crownTop,
        )
        cubicTo(
            f.cx + crownHalf * 0.54f, crownTop,
            f.cx + crownHalf, crownTop + span * 0.02f,
            f.cx + crownHalf, f.top + span * 0.24f,
        )
        close()
    }
    drawPath(cap, hair)
    clipPath(cap) {
        drawRect(
            brush = Brush.verticalGradient(listOf(hair.lighten(1.20f), hair.shade(0.72f))),
            topLeft = Offset(f.cx - crownHalf, crownTop),
            size = Size(crownHalf * 2f, span * 0.4f),
        )
    }

    val fringe = when (a.hairStyle) {
        HairStyle.BUZZ -> null

        HairStyle.UNDERCUT, HairStyle.SLICKED_BACK -> Path().apply {
            moveTo(f.cx - crownHalf, f.top + span * 0.20f)
            cubicTo(
                f.cx - crownHalf * 0.5f, f.top + span * 0.10f,
                f.cx + crownHalf * 0.4f, f.top + span * 0.05f,
                f.cx + crownHalf, f.top + span * 0.15f,
            )
            closeAlongCrown(f.cx, crownHalf, crownTop, span, f.top + span * 0.22f)
        }

        HairStyle.SHORT_MESSY, HairStyle.CURLY_CLOUD -> Path().apply {
            moveTo(f.cx - crownHalf, f.top + span * 0.08f)
            var x = f.cx - crownHalf
            val step = (crownHalf * 2f) / 5f
            repeat(5) { i ->
                val depth = if (i % 2 == 0) 0.30f else 0.17f
                quadraticTo(x + step * 0.5f, f.top + span * depth, x + step, f.top + span * 0.10f)
                x += step
            }
            closeAlongCrown(f.cx, crownHalf, crownTop, span, f.top + span * 0.22f)
        }

        else -> Path().apply {
            // A parted curtain fringe, the most flattering default.
            moveTo(f.cx - crownHalf, f.top + span * 0.04f)
            cubicTo(
                f.cx - crownHalf * 0.88f, f.top + span * 0.32f,
                f.cx - crownHalf * 0.30f, f.top + span * 0.28f,
                f.cx - crownHalf * 0.10f, f.top + span * 0.03f,
            )
            cubicTo(
                f.cx + crownHalf * 0.22f, f.top + span * 0.33f,
                f.cx + crownHalf * 0.82f, f.top + span * 0.37f,
                f.cx + crownHalf, f.top + span * 0.06f,
            )
            closeAlongCrown(f.cx, crownHalf, crownTop, span, f.top + span * 0.22f)
        }
    }

    fringe?.let { path ->
        drawPath(path, hair)
        clipPath(path) {
            drawRect(
                brush = Brush.verticalGradient(listOf(hair.lighten(1.15f), hair.shade(0.68f))),
                topLeft = Offset(f.cx - crownHalf, crownTop),
                size = Size(crownHalf * 2f, span * 0.45f),
            )
        }
    }

    // The shine across the crown — the single detail that most makes hair look like hair.
    // Drawn as a soft lens clipped to the head rather than a ring built from boolean path
    // operations, which are the other reliable way to end up with a stray rectangle on screen.
    val bandTop = crownTop + span * (0.06f + 0.01f * kotlin.math.sin(shimmer * 6.28f))
    clipPath(cap) {
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    hair.lighten(1.85f).copy(alpha = 0.60f),
                    Color.Transparent,
                ),
                startY = bandTop,
                endY = bandTop + span * 0.14f,
            ),
            topLeft = Offset(f.cx - crownHalf * 0.72f, bandTop),
            size = Size(crownHalf * 1.44f, span * 0.14f),
        )
    }
}

/**
 * Closes a fringe by retracing the skull's own outline back to the left side.
 *
 * Fringes used to close with straight lines across the top at full crown width. The cap underneath
 * curves inwards as it rises, so those corners stuck out past the head and read as a square block of
 * hair sitting on the character. Following the same curve the cap uses means the hair mass can never
 * be wider than the skull it is on.
 */
private fun Path.closeAlongCrown(
    cx: Float,
    crownHalf: Float,
    crownTop: Float,
    span: Float,
    sideY: Float,
) {
    lineTo(cx + crownHalf, sideY)
    cubicTo(
        cx + crownHalf, crownTop + span * 0.02f,
        cx + crownHalf * 0.54f, crownTop,
        cx, crownTop,
    )
    cubicTo(
        cx - crownHalf * 0.54f, crownTop,
        cx - crownHalf, crownTop + span * 0.02f,
        cx - crownHalf, sideY,
    )
    close()
}

// --------------------------------------------------------------------------- helpers

private fun DrawScope.softGlow(centre: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
}

private fun mix(from: Float, to: Float, t: Float): Float = from + (to - from) * t.coerceIn(0f, 1f)

private fun Color.shade(factor: Float) = Color(
    red = (red * factor).coerceIn(0f, 1f),
    green = (green * factor).coerceIn(0f, 1f),
    blue = (blue * factor).coerceIn(0f, 1f),
    alpha = alpha,
)

private fun Color.lighten(factor: Float) = Color(
    red = min(1f, red * factor),
    green = min(1f, green * factor),
    blue = min(1f, blue * factor),
    alpha = alpha,
)

private fun Color.mix(other: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * t,
        green = green + (other.green - green) * t,
        blue = blue + (other.blue - blue) * t,
        alpha = max(alpha, other.alpha),
    )
}
