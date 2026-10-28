package app.miogram

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import org.robolectric.Robolectric

class FakeMediaStore : ContentProvider() {
    class Row(val id: Long, val collection: Uri, val inserted: ContentValues, val values: ContentValues, val file: File)

    val rows: MutableList<Row> = CopyOnWriteArrayList()
    private val dir = Files.createTempDirectory("media").toFile()

    override fun onCreate(): Boolean = true

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val id = rows.size + 1L
        val stored = ContentValues(values)
        stored.getAsString(MediaStore.MediaColumns.RELATIVE_PATH)?.let { if (!it.endsWith("/")) stored.put(MediaStore.MediaColumns.RELATIVE_PATH, "$it/") }
        rows += Row(id, uri, ContentValues(values), stored, File(dir, "row$id"))
        return ContentUris.withAppendedId(uri, id)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val columns = projection ?: arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        val cursor = MatrixCursor(columns)
        val path = selectionArgs?.firstOrNull()?.takeIf { selection?.startsWith(MediaStore.MediaColumns.RELATIVE_PATH) == true }
        rows.filter { it.collection == uri && (path == null || it.values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH) == path) }
            .forEach { row -> cursor.addRow(columns.map { row.values.get(it) }) }
        return cursor
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val row = rowOf(uri) ?: return 0
        values?.let(row.values::putAll)
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = if (rowOf(uri)?.let(rows::remove) == true) 1 else 0

    override fun getType(uri: Uri): String? = rowOf(uri)?.values?.getAsString(MediaStore.MediaColumns.MIME_TYPE)

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val row = rowOf(uri) ?: throw java.io.FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(row.file, ParcelFileDescriptor.parseMode(mode))
    }

    private fun rowOf(uri: Uri): Row? = runCatching { ContentUris.parseId(uri) }.getOrNull()?.let { id -> rows.firstOrNull { it.id == id } }

    companion object {
        fun install(): FakeMediaStore = Robolectric.buildContentProvider(FakeMediaStore::class.java).create(MediaStore.AUTHORITY).get()
    }
}
