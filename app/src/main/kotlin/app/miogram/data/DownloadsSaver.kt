package app.miogram.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import app.miogram.di.ApplicationScope
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.autoSaves
import cn.ellerotta.core.model.saveMimeType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SavedFile(val uri: Uri, val name: String, val mimeType: String)

@Singleton
class DownloadsSaver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val events = MutableSharedFlow<SavedFile>(extraBufferCapacity = 8)

    val saved: SharedFlow<SavedFile> = events.asSharedFlow()

    suspend fun save(file: File, media: MediaItem): SavedFile = save(file, media.fileName, media.saveMimeType)

    suspend fun save(file: File, name: String, mimeType: String): SavedFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val free = freeName(name, takenNames(collection))
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, free)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("the downloads refused $free")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("cannot write $free")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        SavedFile(uri, free, mimeType).also { events.tryEmit(it) }
    }

    fun downloaded(media: MediaItem, file: File) {
        if (!settings.autoSaveDownloads.value || !media.autoSaves) return
        scope.launch { runCatching { save(file, media) } }
    }

    private fun takenNames(collection: Uri): Set<String> {
        val cursor = context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf("$RELATIVE_PATH/"),
            null,
        ) ?: return emptySet()
        return cursor.use { rows -> buildSet { while (rows.moveToNext()) rows.getString(0)?.let { add(it.lowercase()) } } }
    }

    companion object {
        val RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/ktproto"
    }
}

internal fun freeName(name: String, taken: Set<String>): String {
    if (name.lowercase() !in taken) return name
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    val base = name.substring(0, dot)
    val extension = name.substring(dot)
    return generateSequence(1) { it + 1 }.map { "$base ($it)$extension" }.first { it.lowercase() !in taken }
}
