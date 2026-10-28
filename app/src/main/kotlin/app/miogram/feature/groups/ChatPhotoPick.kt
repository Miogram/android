package app.miogram.feature.groups

import android.net.Uri
import app.miogram.data.Attachments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun Attachments.squareJpeg(uri: Uri): ByteArray =
    withContext(Dispatchers.IO) { squarePhoto(uri) } ?: error("the photo cannot be read")
