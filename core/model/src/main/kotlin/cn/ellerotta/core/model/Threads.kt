package cn.ellerotta.core.model

data class RepliesInfo(
    val count: Int,
    val comments: Boolean = false,
    val recent: List<Reactor> = emptyList(),
    val discussionKey: String? = null,
    val maxId: Int? = null,
    val readMaxId: Int? = null,
) {
    val unread: Boolean get() = (maxId ?: 0) > (readMaxId ?: 0) && readMaxId != null
}

data class ThreadTitle(val comments: Boolean, val count: Int? = null)

data class ReplyThread(
    val dialogKey: String,
    val topId: Int,
    val messages: List<MessageItem>,
    val maxId: Int? = null,
    val readInboxMaxId: Int? = null,
    val readOutboxMaxId: Int? = null,
    val unread: Int = 0,
    val comments: Boolean = false,
)
