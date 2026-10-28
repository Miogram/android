package cn.ellerotta.core.model

sealed interface LinkTarget {
    data class Chat(
        val dialog: DialogItem,
        val messageId: Int? = null,
        val profile: Boolean = false,
        val draft: String? = null,
        val startParameter: String? = null,
        val topicId: Int? = null,
        val topicTitle: String? = null,
    ) : LinkTarget

    data class Invite(val invite: ChatInvitePreview) : LinkTarget

    data class StickerSet(val detail: StickerSetDetail) : LinkTarget

    data class Share(val text: String) : LinkTarget

    data class LoginCode(val code: String) : LinkTarget

    data class Unsupported(val url: String) : LinkTarget

    data class BotApp(val bot: DialogItem, val shortName: String, val startParameter: String? = null, val mode: WebAppMode = WebAppMode.Default) : LinkTarget

    data class MainApp(val bot: DialogItem, val startParameter: String? = null, val mode: WebAppMode = WebAppMode.Default) : LinkTarget

    data class AttachApp(val bot: DialogItem, val startParameter: String? = null, val chat: DialogItem? = null, val choose: Set<AttachPeerType> = emptySet()) : LinkTarget

    data object PrivateChat : LinkTarget

    data class External(val url: String) : LinkTarget
}

data class ChatInvitePreview(
    val hash: String,
    val title: String,
    val about: String? = null,
    val participants: Int = 0,
    val channel: Boolean = false,
    val requestNeeded: Boolean = false,
    val photo: ByteArray? = null,
)
