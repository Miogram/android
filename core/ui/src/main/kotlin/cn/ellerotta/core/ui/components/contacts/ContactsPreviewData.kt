package cn.ellerotta.core.ui.components.contacts

import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.sortedByPresence
import cn.ellerotta.core.ui.components.chats.ChatsPreviewData

internal object ContactsPreviewData {
    const val NOW = ChatsPreviewData.NOW
    private val nowSeconds = (NOW / 1000).toInt()

    val avatars: Map<String, ByteArray> get() = ChatsPreviewData.avatars

    val online = ContactItem(key = "u101", userId = 101, name = "Alice Johnson", presence = Presence.Online, avatarKey = "photo_alice", phone = "447700900101", mutual = true)

    val contacts: List<ContactItem> = listOf(
        online,
        ContactItem(key = "u404", userId = 404, name = "Mom", presence = Presence.Offline(nowSeconds - 900), avatarKey = "photo_mom", mutual = true),
        ContactItem(key = "u505", userId = 505, name = "Bob Carter", presence = Presence.Offline(nowSeconds - 90_000), username = "bobc"),
        ContactItem(key = "u606", userId = 606, name = "Kate Wilson", presence = Presence.Recently, mutual = true),
        ContactItem(key = "u707", userId = 707, name = "Leo Park", presence = Presence.LastWeek),
        ContactItem(key = "u708", userId = 708, name = "Nina Brooks", presence = Presence.LastMonth),
        ContactItem(key = "u709", userId = 709, name = "Oscar Reed", presence = Presence.LongAgo),
    ).sortedByPresence()

    val globalResults: List<ContactItem> = listOf(
        ContactItem(key = "u910", userId = 910, name = "Alicia Keller", presence = Presence.Recently, username = "alicia_k", contact = false),
        ContactItem(key = "u911", userId = 911, name = "Alistair Grant", presence = Presence.LongAgo, username = "agrant", contact = false),
    )

    val state = ContactsPageState(contacts = contacts, globalResults = globalResults)
}
