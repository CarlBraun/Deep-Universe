package com.deepuniverse.app.ui.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import com.deepuniverse.core.character.AppearanceParam
import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.character.HairStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Draws a character from their [CharacterAppearance], live.
 *
 * ### Why the portrait is drawn rather than composed from art
 * A conventional character creator swaps pre-drawn assets, which means every slider needs an
 * artist. Drawing the face from the parameters directly means all ~30 sliders are continuous and
 * visible from the first build, with no art pipeline — which is exactly what the photo generator
 * needs, since it produces arbitrary values across every axis at once. When real art arrives this
 * renderer becomes the placeholder layer behind it; the parameter model does not change.
 *
 * The drawing is deterministic: the same appearance always produces the same portrait, including
 * freckle placement, so nothing shimmers as the player drags a slider.
 */
@Composable
fun AvatarPortrait(
    appearance: CharacterAppearance,
    modifier: Modifier = Modifier,
    showBackdrop: Boolean = true,
) {
    Canvas(modifier = modifier) {
        drawCharacter(appearance, showBackdrop)
    }
}

// --------------------------------------------------------------------------- drawing

private fun DrawScope.drawCharacter(a: CharacterAppearance, showBackdrop: Boolean) {
    val w = size.width
    val h = size.height
    val cx = w / 2f

    val skin = Color(a.skinColor)
    val hair = Color(a.hairColor)
    val eyeColor = Color(a.eyeColor)

    // ---- proportions -------------------------------------------------------
    val faceH = h * mix(0.44f, 0.54f, a[AppearanceParam.FACE_LENGTH])
    val faceW = w * mix(0.42f, 0.56f, a[AppearanceParam.FACE_WIDTH])
    val faceTop = h * 0.13f
    val chinY = faceTop + faceH
    val cheekHalf = faceW / 2f
    val jawHalf = cheekHalf * mix(0.60f, 0.94f, a[AppearanceParam.JAW_WIDTH])
    val chinHalf = jawHalf * mix(0.72f, 0.34f, a[AppearanceParam.JAW_SHARPNESS])
    val cheekY = faceTop + faceH * mix(0.44f, 0.34f, a[AppearanceParam.CHEEKBONES])
    val jawY = faceTop + faceH * mix(0.68f, 0.76f, a[AppearanceParam.CHIN_LENGTH])

    if (showBackdrop) drawBackdrop()

    val facePath = facePath(cx, faceTop, chinY, cheekHalf, jawHalf, chinHalf, cheekY, jawY, a)

    // ---- hair behind the head ---------------------------------------------
    drawBackHair(a, hair, cx, faceTop, chinY, cheekHalf)

    // ---- body --------------------------------------------------------------
    drawShoulders(a, skin, hair, cx, chinY, h, w)

    // ---- head --------------------------------------------------------------
    drawEars(skin, cx, cheekHalf, cheekY, faceH)
    drawPath(facePath, skin)

    // Soft shading along the jaw, so the silhouette reads as a head rather than a flat shape.
    clipPath(facePath) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.55f to Color.Transparent,
                1f to skin.darken(0.86f).copy(alpha = 0.55f),
            ),
            topLeft = Offset(cx - cheekHalf * 1.3f, faceTop),
            size = Size(cheekHalf * 2.6f, chinY - faceTop),
        )
        // Dewy highlight across the cheekbones and brow.
        val glow = a[AppearanceParam.SKIN_GLOW]
        if (glow > 0.05f) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.16f * glow), Color.Transparent),
                    center = Offset(cx, faceTop + faceH * 0.30f),
                    radius = cheekHalf * 1.1f,
                ),
                radius = cheekHalf * 1.1f,
                center = Offset(cx, faceTop + faceH * 0.30f),
            )
        }
    }

    // ---- features ----------------------------------------------------------
    val eyeY = faceTop + faceH * mix(0.54f, 0.44f, a[AppearanceParam.EYE_HEIGHT])
    val eyeOffset = cheekHalf * mix(0.34f, 0.54f, a[AppearanceParam.EYE_SPACING])
    val eyeW = faceW * mix(0.135f, 0.215f, a[AppearanceParam.EYE_SIZE])
    val eyeH = eyeW * mix(0.34f, 0.78f, a[AppearanceParam.EYE_OPENNESS])
    val tilt = mix(-9f, 13f, a[AppearanceParam.EYE_TILT])

    drawBlush(a, cx, eyeY, faceH, cheekHalf, facePath)
    drawFreckles(a, cx, cheekY, faceH, cheekHalf, facePath, skin)

    drawNose(a, cx, eyeY, chinY, faceW, skin)
    drawMouth(a, cx, eyeY, chinY, faceW, skin)

    for (side in listOf(-1f, 1f)) {
        drawEye(a, cx + side * eyeOffset, eyeY, eyeW, eyeH, tilt * side, side, eyeColor)
        drawBrow(a, cx + side * eyeOffset, eyeY, eyeW, eyeH, side, hair)
    }

    // ---- hair in front of the head ----------------------------------------
    drawFrontHair(a, hair, cx, faceTop, chinY, cheekHalf)
}

/** A soft nebula behind the character, so the portrait never floats on flat black. */
private fun DrawScope.drawBackdrop() {
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF171233), Color(0xFF0B0918)),
        ),
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0x557B5CC4), Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.38f),
            radius = size.width * 0.62f,
        ),
        radius = size.width * 0.62f,
        center = Offset(size.width / 2f, size.height * 0.38f),
    )
    // A fixed star field — seeded so it does not twinkle on every recomposition.
    val random = Random(7)
    repeat(40) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        drawCircle(
            color = Color.White.copy(alpha = 0.10f + random.nextFloat() * 0.35f),
            radius = size.width * (0.001f + random.nextFloat() * 0.0035f),
            center = Offset(x, y),
        )
    }
}

/**
 * The face outline: a closed path from the crown, out past the temple and cheekbone, in along the
 * jaw and down to the chin, mirrored.
 */
private fun facePath(
    cx: Float,
    faceTop: Float,
    chinY: Float,
    cheekHalf: Float,
    jawHalf: Float,
    chinHalf: Float,
    cheekY: Float,
    jawY: Float,
    a: CharacterAppearance,
): Path {
    val templeHalf = cheekHalf * 0.93f
    val templeY = faceTop + (cheekY - faceTop) * 0.35f
    // A sharper jaw meets the chin in a tighter corner.
    val jawTension = mix(0.55f, 0.16f, a[AppearanceParam.JAW_SHARPNESS])

    return Path().apply {
        moveTo(cx, faceTop)
        // Crown to left temple.
        cubicTo(
            cx - cheekHalf * 0.72f, faceTop,
            cx - templeHalf, templeY - (cheekY - faceTop) * 0.25f,
            cx - templeHalf, templeY,
        )
        // Temple to cheekbone.
        cubicTo(
            cx - cheekHalf, templeY + (cheekY - templeY) * 0.5f,
            cx - cheekHalf, cheekY - (cheekY - templeY) * 0.15f,
            cx - cheekHalf, cheekY,
        )
        // Cheekbone down to the jaw corner.
        cubicTo(
            cx - cheekHalf, cheekY + (jawY - cheekY) * 0.55f,
            cx - jawHalf * 1.04f, jawY - (jawY - cheekY) * 0.18f,
            cx - jawHalf, jawY,
        )
        // Jaw corner to chin.
        cubicTo(
            cx - jawHalf, jawY + (chinY - jawY) * jawTension,
            cx - chinHalf, chinY - (chinY - jawY) * 0.12f,
            cx, chinY,
        )
        // Mirror back up the right side.
        cubicTo(
            cx + chinHalf, chinY - (chinY - jawY) * 0.12f,
            cx + jawHalf, jawY + (chinY - jawY) * jawTension,
            cx + jawHalf, jawY,
        )
        cubicTo(
            cx + jawHalf * 1.04f, jawY - (jawY - cheekY) * 0.18f,
            cx + cheekHalf, cheekY + (jawY - cheekY) * 0.55f,
            cx + cheekHalf, cheekY,
        )
        cubicTo(
            cx + cheekHalf, cheekY - (cheekY - templeY) * 0.15f,
            cx + cheekHalf, templeY + (cheekY - templeY) * 0.5f,
            cx + templeHalf, templeY,
        )
        cubicTo(
            cx + templeHalf, templeY - (cheekY - faceTop) * 0.25f,
            cx + cheekHalf * 0.72f, faceTop,
            cx, faceTop,
        )
        close()
    }
}

private fun DrawScope.drawEars(skin: Color, cx: Float, cheekHalf: Float, cheekY: Float, faceH: Float) {
    val earH = faceH * 0.17f
    for (side in listOf(-1f, 1f)) {
        drawOval(
            color = skin.darken(0.95f),
            topLeft = Offset(cx + side * cheekHalf - earH * 0.28f, cheekY - earH * 0.2f),
            size = Size(earH * 0.56f, earH),
        )
    }
}

private fun DrawScope.drawShoulders(
    a: CharacterAppearance,
    skin: Color,
    hair: Color,
    cx: Float,
    chinY: Float,
    h: Float,
    w: Float,
) {
    val neckHalf = w * mix(0.055f, 0.085f, a[AppearanceParam.BUILD])
    val neckBottom = chinY + h * 0.075f
    drawRect(
        color = skin.darken(0.90f),
        topLeft = Offset(cx - neckHalf, chinY - h * 0.01f),
        size = Size(neckHalf * 2f, neckBottom - chinY + h * 0.02f),
    )

    val shoulderHalf = w * mix(0.30f, 0.46f, a[AppearanceParam.SHOULDER_WIDTH])
    val outfit = hair.darken(0.45f).mixWith(Color(0xFF221C3D), 0.6f)
    val body = Path().apply {
        moveTo(cx - neckHalf * 1.4f, neckBottom)
        cubicTo(
            cx - shoulderHalf * 0.75f, neckBottom + h * 0.012f,
            cx - shoulderHalf, neckBottom + h * 0.045f,
            cx - shoulderHalf, h,
        )
        lineTo(cx + shoulderHalf, h)
        cubicTo(
            cx + shoulderHalf, neckBottom + h * 0.045f,
            cx + shoulderHalf * 0.75f, neckBottom + h * 0.012f,
            cx + neckHalf * 1.4f, neckBottom,
        )
        close()
    }
    drawPath(body, outfit)
    drawPath(body, outfit.lighten(1.25f), style = Stroke(width = w * 0.006f))
}

private fun DrawScope.drawEye(
    a: CharacterAppearance,
    cx: Float,
    cy: Float,
    eyeW: Float,
    eyeH: Float,
    tiltDeg: Float,
    side: Float,
    irisColor: Color,
) {
    rotate(degrees = tiltDeg, pivot = Offset(cx, cy)) {
        val halfW = eyeW / 2f
        val halfH = eyeH / 2f

        // Almond outline: upper lid arches higher than the lower lid drops.
        val eye = Path().apply {
            moveTo(cx - halfW, cy)
            cubicTo(
                cx - halfW * 0.45f, cy - halfH * 1.25f,
                cx + halfW * 0.45f, cy - halfH * 1.15f,
                cx + halfW, cy,
            )
            cubicTo(
                cx + halfW * 0.45f, cy + halfH * 0.95f,
                cx - halfW * 0.45f, cy + halfH * 1.0f,
                cx - halfW, cy,
            )
            close()
        }

        drawPath(eye, Color(0xFFF7F3FA))

        clipPath(eye) {
            val irisR = min(halfW * 0.62f, halfH * 1.25f)
            val irisCentre = Offset(cx, cy - halfH * 0.05f)
            drawCircle(irisColor, radius = irisR, center = irisCentre)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.Transparent, irisColor.darken(0.55f)),
                    center = irisCentre,
                    radius = irisR,
                ),
                radius = irisR,
                center = irisCentre,
            )
            drawCircle(Color(0xFF120E1C), radius = irisR * 0.42f, center = irisCentre)
            drawCircle(
                Color.White.copy(alpha = 0.9f),
                radius = irisR * 0.22f,
                center = Offset(irisCentre.x - irisR * 0.33f, irisCentre.y - irisR * 0.36f),
            )
            drawCircle(
                Color.White.copy(alpha = 0.45f),
                radius = irisR * 0.11f,
                center = Offset(irisCentre.x + irisR * 0.30f, irisCentre.y + irisR * 0.28f),
            )
            // Upper lid shadow.
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent),
                ),
                topLeft = Offset(cx - halfW, cy - halfH * 1.3f),
                size = Size(eyeW, halfH * 1.1f),
            )
        }

        // Lash line: heavier on the outer half, which is what reads as "eyeliner".
        val lash = a[AppearanceParam.LASH_LENGTH]
        val makeup = a[AppearanceParam.EYE_MAKEUP]
        val lashWidth = eyeH * (0.10f + lash * 0.16f + makeup * 0.10f)
        val upperLid = Path().apply {
            moveTo(cx - halfW, cy)
            cubicTo(
                cx - halfW * 0.45f, cy - halfH * 1.25f,
                cx + halfW * 0.45f, cy - halfH * 1.15f,
                cx + halfW, cy,
            )
        }
        drawPath(
            upperLid,
            color = Color(0xFF1A1426),
            style = Stroke(width = lashWidth, cap = StrokeCap.Round),
        )

        if (makeup > 0.05f) {
            // Winged liner, flicking away from the nose.
            val wingLength = halfW * 0.42f * makeup
            drawLine(
                color = Color(0xFF1A1426),
                start = Offset(cx + side * halfW, cy),
                end = Offset(cx + side * (halfW + wingLength), cy - halfH * 0.55f * makeup),
                strokeWidth = lashWidth * 0.8f,
                cap = StrokeCap.Round,
            )
        }
        if (lash > 0.35f) {
            // A few individual lashes at the outer corner.
            repeat(3) { i ->
                val t = 0.55f + i * 0.15f
                val x = cx + side * (halfW * t)
                val yOnLid = cy - halfH * (1.0f - (t - 0.55f) * 1.6f)
                drawLine(
                    color = Color(0xFF1A1426),
                    start = Offset(x, yOnLid),
                    end = Offset(
                        x + side * halfW * 0.16f * lash,
                        yOnLid - halfH * 0.45f * lash,
                    ),
                    strokeWidth = lashWidth * 0.5f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun DrawScope.drawBrow(
    a: CharacterAppearance,
    cx: Float,
    eyeY: Float,
    eyeW: Float,
    eyeH: Float,
    side: Float,
    hair: Color,
) {
    val gap = eyeH * mix(1.1f, 3.0f, a[AppearanceParam.BROW_HEIGHT])
    val browY = eyeY - gap
    val thickness = eyeH * mix(0.22f, 0.62f, a[AppearanceParam.BROW_THICKNESS])
    val arch = eyeH * mix(-0.1f, 0.85f, a[AppearanceParam.BROW_ANGLE])
    val halfW = eyeW * 0.62f

    val inner = Offset(cx - side * halfW * 0.85f, browY)
    val outer = Offset(cx + side * halfW * 1.1f, browY - arch * 0.35f)
    val peak = Offset(cx + side * halfW * 0.35f, browY - arch)

    val brow = Path().apply {
        moveTo(inner.x, inner.y + thickness * 0.5f)
        quadraticTo(peak.x, peak.y - thickness * 0.1f, outer.x, outer.y)
        quadraticTo(peak.x, peak.y + thickness, inner.x, inner.y + thickness * 0.5f)
        close()
    }
    drawPath(brow, hair.darken(0.85f))
}

private fun DrawScope.drawNose(
    a: CharacterAppearance,
    cx: Float,
    eyeY: Float,
    chinY: Float,
    faceW: Float,
    skin: Color,
) {
    val length = (chinY - eyeY) * mix(0.34f, 0.55f, a[AppearanceParam.NOSE_LENGTH])
    val tipY = eyeY + length
    val halfW = faceW * mix(0.055f, 0.105f, a[AppearanceParam.NOSE_WIDTH])
    val bridge = a[AppearanceParam.NOSE_BRIDGE]

    // Bridge: a soft shadow down one side, stronger the more defined the bridge is.
    if (bridge > 0.1f) {
        drawLine(
            color = skin.darken(0.88f).copy(alpha = 0.30f + bridge * 0.35f),
            start = Offset(cx - halfW * 0.55f, eyeY - length * 0.1f),
            end = Offset(cx - halfW * 0.75f, tipY - halfW * 0.4f),
            strokeWidth = halfW * (0.25f + bridge * 0.3f),
            cap = StrokeCap.Round,
        )
    }

    // Tip and nostril wings.
    val tip = Path().apply {
        moveTo(cx - halfW, tipY)
        quadraticTo(cx - halfW * 0.55f, tipY + halfW * 0.62f, cx, tipY + halfW * 0.30f)
        quadraticTo(cx + halfW * 0.55f, tipY + halfW * 0.62f, cx + halfW, tipY)
    }
    drawPath(
        tip,
        color = skin.darken(0.80f),
        style = Stroke(width = halfW * 0.22f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawMouth(
    a: CharacterAppearance,
    cx: Float,
    eyeY: Float,
    chinY: Float,
    faceW: Float,
    skin: Color,
) {
    val mouthY = eyeY + (chinY - eyeY) * mix(0.60f, 0.78f, a[AppearanceParam.MOUTH_HEIGHT])
    val halfW = faceW * mix(0.13f, 0.22f, a[AppearanceParam.MOUTH_WIDTH])
    val fullness = a[AppearanceParam.LIP_FULLNESS]
    val upperH = halfW * mix(0.16f, 0.42f, fullness)
    val lowerH = halfW * mix(0.20f, 0.52f, fullness)
    val curve = mix(halfW * 0.14f, -halfW * 0.16f, a[AppearanceParam.LIP_CURVE])

    val lipColor = skin.mixWith(Color(0xFFB4485C), 0.55f)

    val lips = Path().apply {
        // Upper lip, with a cupid's bow at the centre.
        moveTo(cx - halfW, mouthY + curve)
        cubicTo(
            cx - halfW * 0.55f, mouthY - upperH,
            cx - halfW * 0.20f, mouthY - upperH * 0.85f,
            cx, mouthY - upperH * 0.35f,
        )
        cubicTo(
            cx + halfW * 0.20f, mouthY - upperH * 0.85f,
            cx + halfW * 0.55f, mouthY - upperH,
            cx + halfW, mouthY + curve,
        )
        // Lower lip.
        cubicTo(
            cx + halfW * 0.55f, mouthY + lowerH,
            cx - halfW * 0.55f, mouthY + lowerH,
            cx - halfW, mouthY + curve,
        )
        close()
    }
    drawPath(lips, lipColor)

    // The mouth line itself, darker than the lips.
    val line = Path().apply {
        moveTo(cx - halfW, mouthY + curve)
        cubicTo(
            cx - halfW * 0.4f, mouthY + upperH * 0.18f,
            cx + halfW * 0.4f, mouthY + upperH * 0.18f,
            cx + halfW, mouthY + curve,
        )
    }
    drawPath(
        line,
        color = lipColor.darken(0.62f),
        style = Stroke(width = halfW * 0.075f, cap = StrokeCap.Round),
    )
    // Highlight on the lower lip.
    drawPath(
        Path().apply {
            moveTo(cx - halfW * 0.35f, mouthY + lowerH * 0.55f)
            quadraticTo(cx, mouthY + lowerH * 0.78f, cx + halfW * 0.35f, mouthY + lowerH * 0.55f)
        },
        color = Color.White.copy(alpha = 0.22f),
        style = Stroke(width = halfW * 0.09f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawBlush(
    a: CharacterAppearance,
    cx: Float,
    eyeY: Float,
    faceH: Float,
    cheekHalf: Float,
    facePath: Path,
) {
    val strength = a[AppearanceParam.BLUSH]
    if (strength <= 0.02f) return
    clipPath(facePath) {
        for (side in listOf(-1f, 1f)) {
            val centre = Offset(cx + side * cheekHalf * 0.58f, eyeY + faceH * 0.17f)
            val radius = cheekHalf * 0.42f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFE8697F).copy(alpha = 0.42f * strength), Color.Transparent),
                    center = centre,
                    radius = radius,
                ),
                radius = radius,
                center = centre,
            )
        }
    }
}

private fun DrawScope.drawFreckles(
    a: CharacterAppearance,
    cx: Float,
    cheekY: Float,
    faceH: Float,
    cheekHalf: Float,
    facePath: Path,
    skin: Color,
) {
    val density = a[AppearanceParam.FRECKLES]
    if (density <= 0.02f) return
    // Seeded so freckles hold still while other sliders move.
    val random = Random(31)
    val count = (density * 46).toInt()
    val color = skin.darken(0.72f).copy(alpha = 0.55f)
    clipPath(facePath) {
        repeat(count) {
            val x = cx + (random.nextFloat() - 0.5f) * cheekHalf * 2.0f
            val y = cheekY + (random.nextFloat() - 0.35f) * faceH * 0.26f
            drawCircle(color, radius = cheekHalf * (0.012f + random.nextFloat() * 0.014f), center = Offset(x, y))
        }
    }
}

// --------------------------------------------------------------------------- hair

private fun DrawScope.drawBackHair(
    a: CharacterAppearance,
    hair: Color,
    cx: Float,
    faceTop: Float,
    chinY: Float,
    cheekHalf: Float,
) {
    val style = a.hairStyle
    val length = when (style) {
        HairStyle.LONG_STRAIGHT, HairStyle.LONG_WAVY -> 1.15f
        HairStyle.TWIN_TAILS -> 0.95f
        HairStyle.SHOULDER_LAYERED, HairStyle.CURLY_CLOUD -> 0.62f
        HairStyle.BOB -> 0.42f
        else -> 0.16f
    }
    if (length <= 0.2f) return

    val bottom = chinY + (chinY - faceTop) * length
    val width = cheekHalf * when (style) {
        HairStyle.CURLY_CLOUD -> 1.75f
        HairStyle.LONG_WAVY -> 1.45f
        else -> 1.28f
    }

    val back = Path().apply {
        moveTo(cx - width, chinY)
        cubicTo(
            cx - width * 1.05f, faceTop + (chinY - faceTop) * 0.25f,
            cx - width * 0.75f, faceTop - (chinY - faceTop) * 0.12f,
            cx, faceTop - (chinY - faceTop) * 0.14f,
        )
        cubicTo(
            cx + width * 0.75f, faceTop - (chinY - faceTop) * 0.12f,
            cx + width * 1.05f, faceTop + (chinY - faceTop) * 0.25f,
            cx + width, chinY,
        )
        lineTo(cx + width * 0.92f, bottom)
        cubicTo(
            cx + width * 0.4f, bottom + (chinY - faceTop) * 0.06f,
            cx - width * 0.4f, bottom + (chinY - faceTop) * 0.06f,
            cx - width * 0.92f, bottom,
        )
        close()
    }
    drawPath(back, hair.darken(0.80f))

    if (style == HairStyle.TWIN_TAILS) {
        for (side in listOf(-1f, 1f)) {
            val tailX = cx + side * cheekHalf * 1.42f
            drawOval(
                color = hair.darken(0.86f),
                topLeft = Offset(tailX - cheekHalf * 0.42f, faceTop + (chinY - faceTop) * 0.28f),
                size = Size(cheekHalf * 0.84f, (chinY - faceTop) * 1.05f),
            )
        }
    }
}

private fun DrawScope.drawFrontHair(
    a: CharacterAppearance,
    hair: Color,
    cx: Float,
    faceTop: Float,
    chinY: Float,
    cheekHalf: Float,
) {
    val faceSpan = chinY - faceTop
    val crownTop = faceTop - faceSpan * 0.10f
    val crownHalf = cheekHalf * 1.06f

    // The cap of hair sitting on the skull, shared by every style.
    val cap = Path().apply {
        moveTo(cx - crownHalf, faceTop + faceSpan * 0.22f)
        cubicTo(
            cx - crownHalf, crownTop + faceSpan * 0.02f,
            cx - crownHalf * 0.55f, crownTop,
            cx, crownTop,
        )
        cubicTo(
            cx + crownHalf * 0.55f, crownTop,
            cx + crownHalf, crownTop + faceSpan * 0.02f,
            cx + crownHalf, faceTop + faceSpan * 0.22f,
        )
        close()
    }
    drawPath(cap, hair)

    // The fringe, which is what actually distinguishes the styles from the front.
    val fringe = when (a.hairStyle) {
        HairStyle.BUZZ -> null

        HairStyle.UNDERCUT, HairStyle.SLICKED_BACK -> Path().apply {
            // Swept clear of the forehead.
            moveTo(cx - crownHalf, faceTop + faceSpan * 0.20f)
            cubicTo(
                cx - crownHalf * 0.5f, faceTop + faceSpan * 0.10f,
                cx + crownHalf * 0.4f, faceTop + faceSpan * 0.06f,
                cx + crownHalf, faceTop + faceSpan * 0.16f,
            )
            lineTo(cx + crownHalf, faceTop - faceSpan * 0.02f)
            lineTo(cx - crownHalf, faceTop - faceSpan * 0.02f)
            close()
        }

        HairStyle.SHORT_MESSY, HairStyle.CURLY_CLOUD -> Path().apply {
            // A broken, tufted edge.
            moveTo(cx - crownHalf, faceTop + faceSpan * 0.10f)
            var x = cx - crownHalf
            val step = (crownHalf * 2f) / 5f
            repeat(5) { i ->
                val depth = if (i % 2 == 0) 0.30f else 0.19f
                quadraticTo(
                    x + step * 0.5f, faceTop + faceSpan * depth,
                    x + step, faceTop + faceSpan * 0.12f,
                )
                x += step
            }
            lineTo(cx + crownHalf, faceTop - faceSpan * 0.02f)
            lineTo(cx - crownHalf, faceTop - faceSpan * 0.02f)
            close()
        }

        else -> Path().apply {
            // A parted fringe: two curtains meeting off-centre.
            moveTo(cx - crownHalf, faceTop + faceSpan * 0.06f)
            cubicTo(
                cx - crownHalf * 0.85f, faceTop + faceSpan * 0.30f,
                cx - crownHalf * 0.30f, faceTop + faceSpan * 0.26f,
                cx - crownHalf * 0.12f, faceTop + faceSpan * 0.05f,
            )
            cubicTo(
                cx + crownHalf * 0.20f, faceTop + faceSpan * 0.30f,
                cx + crownHalf * 0.80f, faceTop + faceSpan * 0.34f,
                cx + crownHalf, faceTop + faceSpan * 0.08f,
            )
            lineTo(cx + crownHalf, faceTop - faceSpan * 0.02f)
            lineTo(cx - crownHalf, faceTop - faceSpan * 0.02f)
            close()
        }
    }
    fringe?.let { drawPath(it, hair) }

    // A sheen across the crown so flat fills read as hair.
    val sheen = Path().apply {
        addOval(
            Rect(
                left = cx - crownHalf * 0.62f,
                top = crownTop + faceSpan * 0.03f,
                right = cx + crownHalf * 0.62f,
                bottom = crownTop + faceSpan * 0.20f,
            ),
        )
    }
    val clipped = Path().apply {
        op(sheen, cap, PathOperation.Intersect)
    }
    drawPath(clipped, hair.lighten(1.35f).copy(alpha = 0.35f))
}

// --------------------------------------------------------------------------- helpers

private fun mix(from: Float, to: Float, t: Float): Float = from + (to - from) * t.coerceIn(0f, 1f)

private fun Color.darken(factor: Float) = Color(
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

private fun Color.mixWith(other: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * t,
        green = green + (other.green - green) * t,
        blue = blue + (other.blue - blue) * t,
        alpha = max(alpha, other.alpha),
    )
}
