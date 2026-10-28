package app.miogram.data

import java.io.File

class FileCache(private val dir: File, private val maxBytes: Long) {
    private var known = -1L

    fun file(name: String): File = File(dir.apply { mkdirs() }, name)

    fun hit(file: File, size: Long? = null): File? {
        if (!file.exists() || (size != null && file.length() != size)) return null
        file.setLastModified(System.currentTimeMillis())
        return file
    }

    @Synchronized
    fun trim(keep: File? = null) {
        if (known >= 0 && keep != null && keep.isFile) {
            known += keep.length()
            if (known <= maxBytes) return
        }
        val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(PART) }.orEmpty()
        var total = files.sumOf { it.length() }
        if (total > maxBytes) {
            for (file in files.sortedBy { it.lastModified() }) {
                if (total <= maxBytes) break
                if (file == keep) continue
                val length = file.length()
                if (file.delete()) total -= length
            }
        }
        known = total
    }

    fun size(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0

    @Synchronized
    fun clear() {
        dir.listFiles()?.filter { it.isFile && !it.name.endsWith(PART) }?.forEach(File::delete)
        known = -1
    }

    companion object {
        const val PART = ".part"
    }
}
