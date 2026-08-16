package com.deepuniverse.core.color

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Minimal colour maths over packed ARGB [Int]s.
 *
 * The core module is plain Kotlin so that the character pipeline can be unit-tested without an
 * Android device; that rules out `android.graphics.Color`, so the handful of operations the photo
 * analyzer needs live here.
 */

fun argb(r: Int, g: Int, b: Int, a: Int = 255): Int =
    ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

val Int.alpha: Int get() = (this ushr 24) and 0xFF
val Int.red: Int get() = (this ushr 16) and 0xFF
val Int.green: Int get() = (this ushr 8) and 0xFF
val Int.blue: Int get() = this and 0xFF

/** Perceived brightness, `0f..1f`, using the Rec. 601 luma weights. */
fun luminance(color: Int): Float =
    (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue) / 255f

/** Hue in degrees `0f..360f`. Returns `0f` for greys. */
fun hue(color: Int): Float {
    val r = color.red / 255f
    val g = color.green / 255f
    val b = color.blue / 255f
    val maxV = max(r, max(g, b))
    val minV = min(r, min(g, b))
    val delta = maxV - minV
    if (delta < 1e-6f) return 0f
    val h = when (maxV) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return (h + 360f) % 360f
}

/** Saturation, `0f..1f`, on the HSV cone. */
fun saturation(color: Int): Float {
    val maxV = max(color.red, max(color.green, color.blue))
    val minV = min(color.red, min(color.green, color.blue))
    if (maxV == 0) return 0f
    return (maxV - minV).toFloat() / maxV.toFloat()
}

fun blendArgb(from: Int, to: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    fun mix(a: Int, b: Int) = (a + (b - a) * k).roundToInt().coerceIn(0, 255)
    return argb(
        r = mix(from.red, to.red),
        g = mix(from.green, to.green),
        b = mix(from.blue, to.blue),
        a = mix(from.alpha, to.alpha),
    )
}

/** Multiplies brightness, keeping hue. `factor > 1` lightens, `< 1` darkens. */
fun shade(color: Int, factor: Float): Int = argb(
    r = (color.red * factor).roundToInt().coerceIn(0, 255),
    g = (color.green * factor).roundToInt().coerceIn(0, 255),
    b = (color.blue * factor).roundToInt().coerceIn(0, 255),
    a = color.alpha,
)

/**
 * Distance between two colours, weighted so that hue differences matter more than brightness.
 *
 * Photographs vary wildly in exposure, so a plain RGB distance would match a well-lit warm skin
 * tone to a pale swatch simply because both are bright. Weighting chroma over luma makes the
 * nearest-swatch lookup far more stable across lighting conditions.
 */
fun perceptualDistance(a: Int, b: Int): Float {
    val dl = luminance(a) - luminance(b)
    val ds = saturation(a) - saturation(b)
    var dh = abs(hue(a) - hue(b))
    if (dh > 180f) dh = 360f - dh
    val dhNorm = dh / 180f
    // Grey-ish colours have meaningless hue, so fade the hue term out as saturation drops.
    val chromaWeight = min(saturation(a), saturation(b))
    return dl * dl * 1.0f + ds * ds * 1.5f + dhNorm * dhNorm * 3.0f * chromaWeight
}
