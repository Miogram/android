package app.miogram

import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream
import org.robolectric.nativeruntime.DefaultNativeRuntimeLoader

class WarmNativeRuntimeLoader : DefaultNativeRuntimeLoader() {
    private var warm = false

    @Synchronized
    override fun ensureLoaded() {
        super.ensureLoaded()
        if (warm) return
        warm = true
        val empty = File.createTempFile("jni-constants", null)
        try {
            FileInputStream(empty).use { BitmapFactory.decodeFileDescriptor(it.fd) }
        } finally {
            empty.delete()
        }
    }
}
