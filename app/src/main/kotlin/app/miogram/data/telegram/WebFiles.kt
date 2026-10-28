package app.miogram.data.telegram

import cn.ellerotta.ktproto.api.functions.upload.GetWebFile
import cn.ellerotta.ktproto.api.types.TypeInputWebFileLocation
import java.io.ByteArrayOutputStream

private const val WEB_PART = 128 * 1024

internal suspend fun TelegramClientHolder.webFile(location: TypeInputWebFileLocation, max: Int): ByteArray {
    val client = client()
    val out = ByteArrayOutputStream()
    var offset = 0
    while (true) {
        val part = client.invokeOn(webfileDcId, GetWebFile(location, offset, WEB_PART))
        out.write(part.bytes)
        if (out.size() > max) error("web file too big")
        if (part.bytes.size < WEB_PART) break
        offset += part.bytes.size
    }
    return out.toByteArray()
}
