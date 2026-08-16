package com.deepuniverse.app.photo

import com.deepuniverse.core.photo.FaceLandmarks
import com.deepuniverse.core.photo.MeshLandmarkMapper
import com.deepuniverse.core.photo.Vec2
import com.google.mlkit.vision.facemesh.FaceMesh

/**
 * Adapts ML Kit's [FaceMesh] to the platform-independent
 * [MeshLandmarkMapper][com.deepuniverse.core.photo.MeshLandmarkMapper].
 *
 * This is deliberately the thinnest possible layer: it converts ML Kit's point type into plain
 * coordinates and delegates. All the judgement about which mesh index means what lives in the core
 * module, where it is covered by unit tests that run without a device.
 */
object FaceMeshMapper {

    fun map(mesh: FaceMesh, imageWidth: Int, imageHeight: Int): FaceLandmarks? {
        val points = mesh.allPoints.map { point ->
            point.position.let { Vec2(it.x, it.y) }
        }
        return MeshLandmarkMapper.map(points, imageWidth, imageHeight)
    }
}
