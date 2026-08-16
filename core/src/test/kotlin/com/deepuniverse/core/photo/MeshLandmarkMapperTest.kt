package com.deepuniverse.core.photo

import com.deepuniverse.core.character.AppearanceParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Covers the step between the vision SDK and the character generator.
 *
 * A synthetic 468-point mesh is built by scattering the known-good face from [SyntheticFace] across
 * the mesh indices the mapper reads. Mapping it back must reproduce that face exactly — which is
 * what catches an index landing in the wrong region, or left/right being swapped.
 */
class MeshLandmarkMapperTest {

    private val meshSize = 468

    /**
     * Builds a mesh where every index the mapper reads carries the right canonical point.
     *
     * Each region's index group is filled with the landmarks belonging to that region, so the
     * mapper's "take the extreme of the group" logic has something meaningful to pick from.
     */
    private fun syntheticMesh(mirrored: Boolean = false): List<Vec2?> {
        val face = SyntheticFace.landmarks()
        fun p(point: FacePoint): Vec2 {
            val v = face[point]
            return if (mirrored) Vec2(SyntheticFace.IMAGE_WIDTH - v.x, v.y) else v
        }

        // In a mirrored image the "A" index groups (the subject's right) appear on the viewer's
        // right, which is exactly the situation the mapper is supposed to detect and correct.
        fun side(normal: FacePoint, flipped: FacePoint) = if (mirrored) p(flipped) else p(normal)

        val regions: Map<String, List<Vec2>> = mapOf(
            "faceTop" to listOf(p(FacePoint.FOREHEAD_TOP)),
            "chin" to listOf(p(FacePoint.CHIN_BOTTOM)),
            "cheekA" to listOf(side(FacePoint.CHEEK_LEFT, FacePoint.CHEEK_RIGHT)),
            "cheekB" to listOf(side(FacePoint.CHEEK_RIGHT, FacePoint.CHEEK_LEFT)),
            "jawA" to listOf(side(FacePoint.JAW_LEFT, FacePoint.JAW_RIGHT)),
            "jawB" to listOf(side(FacePoint.JAW_RIGHT, FacePoint.JAW_LEFT)),
            "eyeRingA" to if (mirrored) {
                listOf(
                    p(FacePoint.RIGHT_EYE_INNER), p(FacePoint.RIGHT_EYE_OUTER),
                    p(FacePoint.RIGHT_EYE_TOP), p(FacePoint.RIGHT_EYE_BOTTOM),
                )
            } else {
                listOf(
                    p(FacePoint.LEFT_EYE_INNER), p(FacePoint.LEFT_EYE_OUTER),
                    p(FacePoint.LEFT_EYE_TOP), p(FacePoint.LEFT_EYE_BOTTOM),
                )
            },
            "eyeRingB" to if (mirrored) {
                listOf(
                    p(FacePoint.LEFT_EYE_INNER), p(FacePoint.LEFT_EYE_OUTER),
                    p(FacePoint.LEFT_EYE_TOP), p(FacePoint.LEFT_EYE_BOTTOM),
                )
            } else {
                listOf(
                    p(FacePoint.RIGHT_EYE_INNER), p(FacePoint.RIGHT_EYE_OUTER),
                    p(FacePoint.RIGHT_EYE_TOP), p(FacePoint.RIGHT_EYE_BOTTOM),
                )
            },
            "browA" to if (mirrored) {
                listOf(
                    p(FacePoint.RIGHT_BROW_INNER), p(FacePoint.RIGHT_BROW_OUTER),
                    p(FacePoint.RIGHT_BROW_TOP), p(FacePoint.RIGHT_BROW_BOTTOM),
                )
            } else {
                listOf(
                    p(FacePoint.LEFT_BROW_INNER), p(FacePoint.LEFT_BROW_OUTER),
                    p(FacePoint.LEFT_BROW_TOP), p(FacePoint.LEFT_BROW_BOTTOM),
                )
            },
            "browB" to if (mirrored) {
                listOf(
                    p(FacePoint.LEFT_BROW_INNER), p(FacePoint.LEFT_BROW_OUTER),
                    p(FacePoint.LEFT_BROW_TOP), p(FacePoint.LEFT_BROW_BOTTOM),
                )
            } else {
                listOf(
                    p(FacePoint.RIGHT_BROW_INNER), p(FacePoint.RIGHT_BROW_OUTER),
                    p(FacePoint.RIGHT_BROW_TOP), p(FacePoint.RIGHT_BROW_BOTTOM),
                )
            },
            "noseBridge" to listOf(p(FacePoint.NOSE_BRIDGE_TOP)),
            "noseTip" to listOf(p(FacePoint.NOSE_TIP)),
            "alaA" to listOf(side(FacePoint.NOSE_LEFT, FacePoint.NOSE_RIGHT)),
            "alaB" to listOf(side(FacePoint.NOSE_RIGHT, FacePoint.NOSE_LEFT)),
            "mouthCornerA" to listOf(side(FacePoint.MOUTH_LEFT, FacePoint.MOUTH_RIGHT)),
            "mouthCornerB" to listOf(side(FacePoint.MOUTH_RIGHT, FacePoint.MOUTH_LEFT)),
            "lipTop" to listOf(p(FacePoint.LIP_UPPER_TOP)),
            "lipBottom" to listOf(p(FacePoint.LIP_LOWER_BOTTOM)),
        )

        val mesh = arrayOfNulls<Vec2>(meshSize)
        for ((name, indices) in MeshLandmarkMapper.indexGroups()) {
            val values = regions[name] ?: fail("Test mesh has no points for region '$name'")
            indices.forEachIndexed { i, meshIndex ->
                mesh[meshIndex] = values[i % values.size]
            }
        }
        return mesh.toList()
    }

    @Test
    fun `a well-formed mesh maps to a complete set of landmarks`() {
        val mapped = MeshLandmarkMapper.map(syntheticMesh(), 400, 520)
            ?: fail("A full mesh should map")
        assertTrue(
            mapped.isComplete,
            "Every landmark should be filled in, missing: ${mapped.missingPoints}",
        )
    }

    @Test
    fun `mapping recovers exactly the face that was scattered into the mesh`() {
        val expected = SyntheticFace.landmarks()
        val mapped = MeshLandmarkMapper.map(syntheticMesh(), 400, 520)
            ?: fail("A full mesh should map")

        for (point in FacePoint.entries) {
            assertEquals(expected[point], mapped[point], "$point was mapped to the wrong position")
        }
    }

    @Test
    fun `a mirrored selfie produces the same character as an unmirrored one`() {
        val normal = MeshLandmarkMapper.map(syntheticMesh(mirrored = false), 400, 520)
            ?: fail("A full mesh should map")
        val mirrored = MeshLandmarkMapper.map(syntheticMesh(mirrored = true), 400, 520)
            ?: fail("A mirrored mesh should map")

        val a = PhotoToAppearance.analyze(normal, ColorSamples(null, null, null))
        val b = PhotoToAppearance.analyze(mirrored, ColorSamples(null, null, null))

        val first = (a as? AnalysisResult.Success)?.appearance ?: fail("Expected success, got $a")
        val second = (b as? AnalysisResult.Success)?.appearance ?: fail("Expected success, got $b")

        for (param in AppearanceParam.photoInferable) {
            assertEquals(
                first[param],
                second[param],
                0.001f,
                "$param differed between a mirrored and an unmirrored capture",
            )
        }
    }

    @Test
    fun `left and right are assigned by image position, not by index group`() {
        val mapped = MeshLandmarkMapper.map(syntheticMesh(), 400, 520)
            ?: fail("A full mesh should map")
        assertTrue(mapped[FacePoint.CHEEK_LEFT].x < mapped[FacePoint.CHEEK_RIGHT].x)
        assertTrue(mapped[FacePoint.LEFT_EYE_OUTER].x < mapped[FacePoint.LEFT_EYE_INNER].x)
        assertTrue(mapped[FacePoint.RIGHT_EYE_INNER].x < mapped[FacePoint.RIGHT_EYE_OUTER].x)
        assertTrue(mapped[FacePoint.MOUTH_LEFT].x < mapped[FacePoint.MOUTH_RIGHT].x)
    }

    @Test
    fun `a mesh of the wrong shape is rejected rather than half-read`() {
        assertNull(MeshLandmarkMapper.map(emptyList(), 400, 520))
        assertNull(MeshLandmarkMapper.map(List(120) { Vec2(0f, 0f) }, 400, 520))
    }

    @Test
    fun `a mesh missing a region maps incompletely rather than inventing points`() {
        val mesh = syntheticMesh().toMutableList()
        MeshLandmarkMapper.indexGroups().getValue("lipBottom").forEach { mesh[it] = null }
        val mapped = MeshLandmarkMapper.map(mesh, 400, 520)
            ?: fail("The mesh is still the right size, so it should map")
        assertTrue(FacePoint.LIP_LOWER_BOTTOM in mapped.missingPoints)
        // And the pipeline must refuse it rather than measuring a face with no lower lip.
        assertEquals(
            AnalysisResult.Failed(AnalysisFailure.INCOMPLETE_FACE),
            PhotoToAppearance.analyze(mapped, ColorSamples(null, null, null)),
        )
    }

    @Test
    fun `every referenced index is inside the mesh the detector returns`() {
        val indices = MeshLandmarkMapper.referencedIndices.flatMap { it.toList() }
        assertTrue(indices.isNotEmpty())
        assertTrue(
            indices.all { it in 0 until meshSize },
            "Indices outside the 468-point mesh: ${indices.filter { it !in 0 until meshSize }}",
        )
        assertEquals(
            indices.size,
            indices.toSet().size,
            "An index is used by two regions, which would make one of them ambiguous",
        )
    }
}
