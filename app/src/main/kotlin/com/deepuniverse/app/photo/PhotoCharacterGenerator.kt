package com.deepuniverse.app.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.photo.AnalysisFailure
import com.deepuniverse.core.photo.AnalysisResult
import com.deepuniverse.core.photo.PhotoToAppearance
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Turns a photo the player just took into a character.
 *
 * ### What leaves the device
 * Nothing. The face mesh model is bundled in the APK and runs locally, the measurements and colour
 * sampling happen in-process, and the bitmap is released as soon as the analysis returns. No
 * network call is made at any point, no photo is written to the gallery or to app storage beyond
 * the camera's own temporary file, and that file is deleted by the caller once this returns. The
 * only thing that outlives the call is a set of slider values.
 */
class PhotoCharacterGenerator(private val context: Context) {

    /** Faces smaller than this in the source image are hard to measure, so we downscale no further. */
    private val maxDimension = 1280

    private val detector by lazy {
        FaceMeshDetection.getClient(
            FaceMeshDetectorOptions.Builder()
                .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
                .build(),
        )
    }

    /**
     * Reads [uri], finds a face and maps it onto [base].
     *
     * Runs entirely off the main thread. Any failure — unreadable file, no face, face too small —
     * comes back as [AnalysisResult.Failed] with a message written for the player rather than as an
     * exception, because every one of these is a normal thing for a player to do.
     */
    suspend fun generate(
        uri: Uri,
        base: CharacterAppearance,
    ): AnalysisResult = withContext(Dispatchers.Default) {
        val bitmap = decodeUpright(uri)
            ?: return@withContext AnalysisResult.Failed(AnalysisFailure.NO_FACE)

        try {
            val mesh = detectLargestFace(bitmap)
                ?: return@withContext AnalysisResult.Failed(AnalysisFailure.NO_FACE)

            val landmarks = FaceMeshMapper.map(mesh, bitmap.width, bitmap.height)
                ?: return@withContext AnalysisResult.Failed(AnalysisFailure.INCOMPLETE_FACE)

            val colors = BitmapColorSampler.sample(bitmap, landmarks)
            PhotoToAppearance.analyze(landmarks, colors, base)
        } finally {
            bitmap.recycle()
        }
    }

    /** The largest detected face, so a photo with a bystander in it still picks the player. */
    private suspend fun detectLargestFace(bitmap: Bitmap) =
        suspendCancellableCoroutine { continuation ->
            // The bitmap is already rotated upright, so no further rotation is declared here.
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image)
                .addOnSuccessListener { meshes ->
                    continuation.resume(
                        meshes.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() },
                    )
                }
                .addOnFailureListener { continuation.resume(null) }
                .addOnCanceledListener { continuation.resume(null) }
        }

    /**
     * Decodes [uri] downscaled and rotated according to its EXIF orientation.
     *
     * Both steps matter: full-resolution camera bitmaps are large enough to push a mid-range phone
     * into an OOM during detection, and a photo whose orientation lives only in EXIF would
     * otherwise be measured sideways — which the geometry's tilt correction would dutifully
     * "correct" into a face lying on its side.
     */
    private fun decodeUpright(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        applyOrientation(decoded, orientation)
    }.getOrNull()

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > maxDimension) {
            sample *= 2
        }
        return sample
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    fun close() {
        detector.close()
    }
}
