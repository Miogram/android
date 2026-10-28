package app.miogram.push

import android.content.Context
import app.miogram.data.telegram.peerAvatarFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationAvatars(private val filesDir: File) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.filesDir)

    suspend fun read(peerKey: String): ByteArray? = withContext(Dispatchers.IO) {
        peerAvatarFile(filesDir, peerKey)?.takeIf { it.isFile }?.let { file ->
            file.setLastModified(System.currentTimeMillis())
            runCatching { file.readBytes() }.getOrNull()
        }
    }
}
