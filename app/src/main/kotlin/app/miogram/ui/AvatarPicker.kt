package app.miogram.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

internal class AvatarSources(val gallery: () -> Unit, val camera: () -> Unit)

@Composable
internal fun rememberAvatarSources(captureUri: () -> Uri, onCameraDenied: () -> Unit, onPicked: (uri: Uri, captured: Boolean) -> Unit): AvatarSources {
    var capturing by rememberSaveable { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { onPicked(it, false) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken && capturing) onPicked(captureUri(), true)
        capturing = false
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            capturing = true
            camera.launch(captureUri())
        } else {
            onCameraDenied()
        }
    }
    return remember(gallery, permission) {
        AvatarSources(
            gallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            camera = { permission.launch(Manifest.permission.CAMERA) },
        )
    }
}
