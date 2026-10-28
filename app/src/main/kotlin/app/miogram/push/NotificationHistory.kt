package app.miogram.push

import android.content.Context
import cn.ellerotta.core.model.PeerKind
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class NotifiedMessage(
    val id: Int,
    val text: String,
    val date: Long,
    val sender: String? = null,
    val senderKey: String? = null,
    val own: Boolean = false,
    val image: String? = null,
    val imageMime: String? = null,
)

data class NotifiedChat(val key: String, val title: String, val kind: PeerKind?, val messages: List<NotifiedMessage>) {
    val unread: Int get() = messages.count { !it.own }

    val firstId: Int? get() = messages.firstOrNull { !it.own && it.id != 0 }?.id

    val newestId: Int? get() = messages.filter { !it.own && it.id != 0 }.maxOfOrNull { it.id }
}

@Singleton
class NotificationHistory @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val seen = context.getSharedPreferences(SEEN_PREFS, Context.MODE_PRIVATE)
    private val reads = context.getSharedPreferences(READ_PREFS, Context.MODE_PRIVATE)

    fun readMax(key: String): Int = reads.getInt(key, 0)

    fun markRead(key: String, maxId: Int) {
        if (maxId > readMax(key)) reads.edit().putInt(key, maxId).apply()
    }

    fun seen(key: String, id: Int): Boolean = id in idsOf(key)

    fun markSeen(key: String, id: Int) {
        val ids = idsOf(key)
        if (id in ids) return
        seen.edit().putString(key, (ids + id).takeLast(SEEN).joinToString(",")).apply()
    }

    private fun idsOf(key: String): List<Int> = seen.getString(key, null)?.split(',')?.mapNotNull(String::toIntOrNull).orEmpty()

    fun chat(key: String): NotifiedChat? = prefs.getString(key, null)?.let { decode(key, it) }

    fun chats(): List<NotifiedChat> = prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { decode(key, it) } }
        .sortedByDescending { chat -> chat.messages.maxOfOrNull { it.date } ?: 0 }

    fun put(chat: NotifiedChat) {
        if (chat.messages.isEmpty()) return remove(chat.key)
        prefs.edit().putString(chat.key, encode(chat.copy(messages = chat.messages.takeLast(KEEP)))).apply()
    }

    fun remove(key: String) = prefs.edit().remove(key).apply()

    fun clear() {
        prefs.edit().clear().apply()
        seen.edit().clear().apply()
        reads.edit().clear().apply()
    }

    private fun encode(chat: NotifiedChat): String = JSONObject()
        .put("title", chat.title)
        .put("kind", chat.kind?.name)
        .put(
            "messages",
            JSONArray(
                chat.messages.map { message ->
                    JSONObject()
                        .put("id", message.id)
                        .put("text", message.text)
                        .put("date", message.date)
                        .put("sender", message.sender)
                        .put("senderKey", message.senderKey)
                        .put("own", message.own)
                        .put("image", message.image)
                        .put("imageMime", message.imageMime)
                },
            ),
        )
        .toString()

    private fun decode(key: String, json: String): NotifiedChat? = runCatching {
        val root = JSONObject(json)
        val messages = root.getJSONArray("messages")
        NotifiedChat(
            key = key,
            title = root.getString("title"),
            kind = root.optString("kind").takeIf { it.isNotEmpty() }?.let { name -> PeerKind.entries.firstOrNull { it.name == name } },
            messages = List(messages.length()) { index ->
                val message = messages.getJSONObject(index)
                NotifiedMessage(
                    id = message.getInt("id"),
                    text = message.getString("text"),
                    date = message.getLong("date"),
                    sender = message.optString("sender").takeIf { message.has("sender") && it.isNotEmpty() },
                    senderKey = message.optString("senderKey").takeIf { message.has("senderKey") && it.isNotEmpty() },
                    own = message.optBoolean("own"),
                    image = message.optString("image").takeIf { message.has("image") && it.isNotEmpty() },
                    imageMime = message.optString("imageMime").takeIf { message.has("imageMime") && it.isNotEmpty() },
                )
            },
        )
    }.getOrNull()

    companion object {
        const val KEEP = 8

        const val SEEN = 64

        private const val PREFS = "notification_history"
        private const val SEEN_PREFS = "notification_seen"
        private const val READ_PREFS = "notification_read"
    }
}
