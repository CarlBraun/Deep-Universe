package com.deepuniverse.core.character

import kotlinx.serialization.Serializable

/**
 * A face a character can pull.
 *
 * Expressions are the main collectable in the game. They cost nothing to draw — the portrait
 * renderer is parametric, so an expression is a handful of deltas applied on top of a character's
 * existing face rather than a new illustration. That is what makes an *infinite* reward loop
 * affordable: every new face is data, not art hours.
 *
 * [unlockRank] is the bond rank at which this face becomes available on a character's route.
 */
@Serializable
enum class Expression(
    val label: String,
    val unlockRank: Int,
    val description: String,
) {
    NEUTRAL("Composed", 0, "How they look when they think no one is watching"),
    SOFT_SMILE("Soft smile", 1, "The one that slips out before they can stop it"),
    AMUSED("Amused", 2, "Something you said landed"),
    BLUSH("Caught out", 3, "You saw something they meant to keep"),
    LAUGH("Laughing", 4, "Head back, guard entirely down"),
    SURPRISED("Surprised", 5, "You did something they did not predict"),
    TENDER("Tender", 6, "Looking at you like you are the answer"),
    SMOULDER("Smouldering", 8, "Deliberate. They know exactly what they are doing"),
    WISTFUL("Wistful", 10, "Thinking about the day you leave"),
    TEARFUL("Overwhelmed", 12, "Happy. Mostly happy"),
    DEVOTED("Devoted", 15, "No performance left in it at all"),
    ;

    companion object {
        /** Faces unlocked at [rank] or below, in unlock order. */
        fun unlockedAt(rank: Int): List<Expression> =
            entries.filter { it.unlockRank <= rank }.sortedBy { it.unlockRank }

        /** The next face still to earn, or null once they are all unlocked. */
        fun nextAfter(rank: Int): Expression? =
            entries.filter { it.unlockRank > rank }.minByOrNull { it.unlockRank }
    }
}

/**
 * How an [Expression] bends a face, as offsets applied at draw time.
 *
 * Offsets rather than absolute values, so every expression works on every character — a smouldering
 * look on a wide-eyed face and on a hooded one are recognisably the same expression *and*
 * recognisably still those two people.
 */
data class ExpressionShape(
    val eyeOpenness: Float = 0f,
    val eyeTilt: Float = 0f,
    val browHeight: Float = 0f,
    val browAngle: Float = 0f,
    val mouthCurve: Float = 0f,
    val mouthWidth: Float = 0f,
    /** How far the mouth hangs open, `0f` closed. */
    val mouthOpen: Float = 0f,
    val blush: Float = 0f,
    /** Lids fully shut — drawn as curved lashes rather than eyes. */
    val eyesClosed: Boolean = false,
    /** Extra catchlights, for the faces meant to land hardest. */
    val sparkle: Float = 0f,
    /** A held tear on the lower lid. */
    val tears: Float = 0f,
    /** Head tilt in degrees, which does more for personality than any single feature. */
    val headTilt: Float = 0f,
) {
    companion object {
        fun of(expression: Expression): ExpressionShape = when (expression) {
            Expression.NEUTRAL -> ExpressionShape()

            Expression.SOFT_SMILE -> ExpressionShape(
                mouthCurve = 0.28f,
                eyeOpenness = -0.06f,
                blush = 0.08f,
                headTilt = -2f,
            )

            Expression.AMUSED -> ExpressionShape(
                mouthCurve = 0.34f,
                mouthWidth = 0.10f,
                browAngle = 0.16f,
                eyeOpenness = -0.12f,
                headTilt = -4f,
            )

            Expression.BLUSH -> ExpressionShape(
                mouthCurve = 0.10f,
                mouthWidth = -0.08f,
                browHeight = 0.14f,
                eyeOpenness = -0.10f,
                blush = 0.72f,
                headTilt = 3f,
            )

            Expression.LAUGH -> ExpressionShape(
                mouthCurve = 0.46f,
                mouthWidth = 0.22f,
                mouthOpen = 0.55f,
                browHeight = 0.10f,
                blush = 0.24f,
                eyesClosed = true,
                headTilt = -6f,
            )

            Expression.SURPRISED -> ExpressionShape(
                eyeOpenness = 0.34f,
                browHeight = 0.30f,
                mouthOpen = 0.30f,
                mouthWidth = -0.10f,
                headTilt = 0f,
            )

            Expression.TENDER -> ExpressionShape(
                eyeOpenness = -0.14f,
                browAngle = -0.10f,
                browHeight = 0.06f,
                mouthCurve = 0.22f,
                blush = 0.26f,
                sparkle = 0.5f,
                headTilt = -5f,
            )

            Expression.SMOULDER -> ExpressionShape(
                eyeOpenness = -0.26f,
                eyeTilt = 0.12f,
                browHeight = -0.16f,
                browAngle = 0.10f,
                mouthCurve = 0.14f,
                sparkle = 0.35f,
                headTilt = 4f,
            )

            Expression.WISTFUL -> ExpressionShape(
                eyeOpenness = -0.10f,
                browAngle = -0.22f,
                browHeight = 0.10f,
                mouthCurve = 0.06f,
                mouthWidth = -0.06f,
                headTilt = 6f,
            )

            Expression.TEARFUL -> ExpressionShape(
                eyeOpenness = 0.12f,
                browAngle = -0.30f,
                browHeight = 0.16f,
                mouthCurve = 0.20f,
                blush = 0.44f,
                sparkle = 0.8f,
                tears = 0.8f,
                headTilt = -3f,
            )

            Expression.DEVOTED -> ExpressionShape(
                eyeOpenness = -0.08f,
                browAngle = -0.14f,
                mouthCurve = 0.30f,
                blush = 0.34f,
                sparkle = 1f,
                headTilt = -4f,
            )
        }
    }
}
