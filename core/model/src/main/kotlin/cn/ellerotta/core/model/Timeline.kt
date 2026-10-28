package cn.ellerotta.core.model

const val EPHEMERAL_IDS = 0x60000000

data class EphemeralInfo(val id: Int, val botKey: String, val botUsername: String? = null, val welcome: Boolean = false)

fun timelineOf(messages: List<MessageItem>, ephemeral: List<MessageItem>, streaming: List<MessageItem>): List<MessageItem> {
    if (ephemeral.isEmpty() && streaming.isEmpty()) return messages
    val placed = ephemeral.sortedWith(compareBy({ it.date }, { it.id }))
    val out = ArrayList<MessageItem>(messages.size + placed.size + streaming.size)
    var next = 0
    for (message in messages) {
        while (next < placed.size && placed[next].date < message.date) out += placed[next++]
        out += message
    }
    while (next < placed.size) out += placed[next++]
    out += streaming
    return out
}
