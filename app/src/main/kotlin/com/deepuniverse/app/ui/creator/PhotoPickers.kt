package com.deepuniverse.app.ui.creator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * The two ways a player can hand the creator a photo, and the plumbing each needs.
 *
 * Gallery is offered first and needs no permission at all — the system photo picker returns a
 * single image the player chose, and the app never sees the rest of their library. Camera capture
 * is the second option because it requires a runtime permission, and a player who declines it can
 * still use every other part of the creator.
 */
class PhotoSources(
    private val context: Context,
    private val pickFromGallery: () -> Unit,
    private val requestCamera: () -> Unit,
) {
    fun chooseFromGallery() = pickFromGallery()

    fun takePhoto() = requestCamera()

    val hasCamera: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
}

/**
 * Wires up the gallery picker and the camera, and hands [onPhoto] whichever URI the player produced.
 *
 * The camera writes into the app's own cache directory through a [FileProvider], so no storage
 * permission is involved and the photo never lands in the player's gallery. [onPhotoConsumed] is
 * called once the analysis is done so the temporary file can be deleted.
 */
@Composable
fun rememberPhotoSources(
    onPhoto: (Uri) -> Unit,
    onCameraDenied: () -> Unit,
): PhotoSources {
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onPhoto) }

    // Held across the permission round-trip so the capture can start once permission is granted.
    val pendingCapture = remember { arrayOfNulls<Uri>(1) }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCapture[0]
        pendingCapture[0] = null
        if (success && uri != null) onPhoto(uri)
    }

    fun launchCapture() {
        val uri = newCaptureUri(context)
        pendingCapture[0] = uri
        captureLauncher.launch(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCapture() else onCameraDenied()
    }

    return remember(context) {
        PhotoSources(
            context = context,
            pickFromGallery = {
                galleryLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
            requestCamera = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) launchCapture() else permissionLauncher.launch(Manifest.permission.CAMERA)
            },
        )
    }
}

/** A fresh file in the app's private cache for the camera to write into. */
private fun newCaptureUri(context: Context): Uri {
    val directory = File(context.cacheDir, "character_photos").apply { mkdirs() }
    // One file, overwritten each time: there is no reason to accumulate face photos on disk.
    val file = File(directory, "capture.jpg")
    if (file.exists()) file.delete()
    file.createNewFile()
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Deletes any photo the camera left in the cache. Called once an analysis finishes. */
fun clearCapturedPhotos(context: Context) {
    runCatching {
        File(context.cacheDir, "character_photos").listFiles()?.forEach { it.delete() }
    }
}
