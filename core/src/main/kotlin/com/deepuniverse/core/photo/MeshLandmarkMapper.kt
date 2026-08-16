package com.deepuniverse.core.photo

/**
 * Reduces a dense 468-point face mesh to the named points the character generator reasons about.
 *
 * ### Why regions rather than single indices
 * It is tempting to write `LEFT_EYE_OUTER = mesh[33]`, but the exact semantics of any one index in
 * the canonical mesh are easy to get subtly wrong, and a single mis-picked index would silently
 * skew a slider for every player. Instead each feature is described by a *set* of indices covering
 * that region and the specific point is taken as an extreme of that set — the leftmost point of the
 * eye ring is its outer corner, the topmost point of the brow region is its top edge. That is
 * robust to a point or two being poorly placed.
 *
 * ### Why left and right are decided at runtime
 * Front-camera captures are frequently mirrored, and mesh index groups are labelled in the
 * *subject's* frame. Rather than guess, both candidate groups are measured and whichever ends up
 * with the smaller x becomes the viewer's left. [FaceGeometry] averages the two sides for every
 * symmetric measurement, so a mirrored photo yields the same character either way.
 *
 * This lives in the core module, away from any vision SDK, so the whole mapping can be unit-tested
 * against a synthetic mesh. The platform adapter only has to turn its own point type into a list.
 */
object MeshLandmarkMapper {

    // Canonical MediaPipe mesh indices, the topology ML Kit's face mesh also uses.
    private val FACE_TOP = intArrayOf(10, 109, 338, 151)
    private val CHIN = intArrayOf(152, 175, 148, 377)
    private val CHEEK_A = intArrayOf(234, 227, 137, 177)
    private val CHEEK_B = intArrayOf(454, 447, 366, 401)
    private val JAW_A = intArrayOf(172, 136, 150)
    private val JAW_B = intArrayOf(397, 365, 379)

    private val EYE_RING_A = intArrayOf(33, 133, 159, 145, 158, 160, 144, 153, 246, 7)
    private val EYE_RING_B = intArrayOf(263, 362, 386, 374, 385, 387, 380, 373, 466, 249)

    private val BROW_A = intArrayOf(70, 63, 105, 66, 107, 46, 53, 52, 65, 55)
    private val BROW_B = intArrayOf(300, 293, 334, 296, 336, 276, 283, 282, 295, 285)

    private val NOSE_BRIDGE = intArrayOf(168, 6, 197)
    private val NOSE_TIP = intArrayOf(1, 4, 5)
    private val NOSE_ALA_A = intArrayOf(48, 115, 98)
    private val NOSE_ALA_B = intArrayOf(278, 344, 327)

    private val MOUTH_CORNER_A = intArrayOf(61, 76, 62)
    private val MOUTH_CORNER_B = intArrayOf(291, 306, 292)
    private val LIP_TOP = intArrayOf(0, 37, 267)
    private val LIP_BOTTOM = intArrayOf(17, 84, 314)

    /** The highest index referenced above; a shorter mesh is not the topology we expect. */
    const val REQUIRED_MESH_SIZE = 467

    /**
     * Maps a mesh onto [FaceLandmarks].
     *
     * @param points mesh points by index. Entries may be null where the detector had nothing.
     * @return the mapped landmarks, or null if the mesh is the wrong shape. The result may still be
     *   incomplete — callers should let [PhotoToAppearance] report that to the player rather than
     *   measuring a half-detected face.
     */
    fun map(points: List<Vec2?>, imageWidth: Int, imageHeight: Int): FaceLandmarks? {
        if (points.size < REQUIRED_MESH_SIZE) return null

        fun group(indices: IntArray): List<Vec2> =
            indices.map { points.getOrNull(it) }.filterNotNull()

        val faceTop = group(FACE_TOP).minByOrNull { it.y } ?: return null
        val chin = group(CHIN).maxByOrNull { it.y } ?: return null

        val (cheekL, cheekR) = orderByX(group(CHEEK_A), group(CHEEK_B)) ?: return null
        val (jawL, jawR) = orderByX(group(JAW_A), group(JAW_B)) ?: return null
        val (eyeL, eyeR) = orderByX(group(EYE_RING_A), group(EYE_RING_B)) ?: return null
        val (browL, browR) = orderByX(group(BROW_A), group(BROW_B)) ?: return null
        val (alaL, alaR) = orderByX(group(NOSE_ALA_A), group(NOSE_ALA_B)) ?: return null
        val (mouthL, mouthR) = orderByX(group(MOUTH_CORNER_A), group(MOUTH_CORNER_B)) ?: return null

        val mapped = buildMap {
            put(FacePoint.FOREHEAD_TOP, faceTop)
            put(FacePoint.CHIN_BOTTOM, chin)

            put(FacePoint.CHEEK_LEFT, cheekL.minBy { it.x })
            put(FacePoint.CHEEK_RIGHT, cheekR.maxBy { it.x })
            put(FacePoint.JAW_LEFT, jawL.minBy { it.x })
            put(FacePoint.JAW_RIGHT, jawR.maxBy { it.x })

            // The outer corner is the end of the ring furthest from the face's midline.
            put(FacePoint.LEFT_EYE_OUTER, eyeL.minBy { it.x })
            put(FacePoint.LEFT_EYE_INNER, eyeL.maxBy { it.x })
            put(FacePoint.LEFT_EYE_TOP, eyeL.minBy { it.y })
            put(FacePoint.LEFT_EYE_BOTTOM, eyeL.maxBy { it.y })
            put(FacePoint.RIGHT_EYE_INNER, eyeR.minBy { it.x })
            put(FacePoint.RIGHT_EYE_OUTER, eyeR.maxBy { it.x })
            put(FacePoint.RIGHT_EYE_TOP, eyeR.minBy { it.y })
            put(FacePoint.RIGHT_EYE_BOTTOM, eyeR.maxBy { it.y })

            put(FacePoint.LEFT_BROW_OUTER, browL.minBy { it.x })
            put(FacePoint.LEFT_BROW_INNER, browL.maxBy { it.x })
            put(FacePoint.LEFT_BROW_TOP, browL.minBy { it.y })
            put(FacePoint.LEFT_BROW_BOTTOM, browL.maxBy { it.y })
            put(FacePoint.RIGHT_BROW_INNER, browR.minBy { it.x })
            put(FacePoint.RIGHT_BROW_OUTER, browR.maxBy { it.x })
            put(FacePoint.RIGHT_BROW_TOP, browR.minBy { it.y })
            put(FacePoint.RIGHT_BROW_BOTTOM, browR.maxBy { it.y })

            group(NOSE_BRIDGE).minByOrNull { it.y }?.let { put(FacePoint.NOSE_BRIDGE_TOP, it) }
            group(NOSE_TIP).maxByOrNull { it.y }?.let { put(FacePoint.NOSE_TIP, it) }
            put(FacePoint.NOSE_LEFT, alaL.minBy { it.x })
            put(FacePoint.NOSE_RIGHT, alaR.maxBy { it.x })

            put(FacePoint.MOUTH_LEFT, mouthL.minBy { it.x })
            put(FacePoint.MOUTH_RIGHT, mouthR.maxBy { it.x })
            group(LIP_TOP).minByOrNull { it.y }?.let { put(FacePoint.LIP_UPPER_TOP, it) }
            group(LIP_BOTTOM).maxByOrNull { it.y }?.let { put(FacePoint.LIP_LOWER_BOTTOM, it) }
        }

        return FaceLandmarks(mapped, imageWidth, imageHeight)
    }

    /** Returns the two groups ordered so the first sits further left in the image. */
    private fun orderByX(a: List<Vec2>, b: List<Vec2>): Pair<List<Vec2>, List<Vec2>>? {
        if (a.isEmpty() || b.isEmpty()) return null
        return if (a.map { it.x }.average() <= b.map { it.x }.average()) a to b else b to a
    }

    /** Every mesh index the mapper reads, for tests and for building synthetic meshes. */
    internal val referencedIndices: List<IntArray> = listOf(
        FACE_TOP, CHIN, CHEEK_A, CHEEK_B, JAW_A, JAW_B,
        EYE_RING_A, EYE_RING_B, BROW_A, BROW_B,
        NOSE_BRIDGE, NOSE_TIP, NOSE_ALA_A, NOSE_ALA_B,
        MOUTH_CORNER_A, MOUTH_CORNER_B, LIP_TOP, LIP_BOTTOM,
    )

    /** Index groups paired with the face region they describe, used to build test meshes. */
    internal fun indexGroups(): Map<String, IntArray> = mapOf(
        "faceTop" to FACE_TOP,
        "chin" to CHIN,
        "cheekA" to CHEEK_A,
        "cheekB" to CHEEK_B,
        "jawA" to JAW_A,
        "jawB" to JAW_B,
        "eyeRingA" to EYE_RING_A,
        "eyeRingB" to EYE_RING_B,
        "browA" to BROW_A,
        "browB" to BROW_B,
        "noseBridge" to NOSE_BRIDGE,
        "noseTip" to NOSE_TIP,
        "alaA" to NOSE_ALA_A,
        "alaB" to NOSE_ALA_B,
        "mouthCornerA" to MOUTH_CORNER_A,
        "mouthCornerB" to MOUTH_CORNER_B,
        "lipTop" to LIP_TOP,
        "lipBottom" to LIP_BOTTOM,
    )
}
