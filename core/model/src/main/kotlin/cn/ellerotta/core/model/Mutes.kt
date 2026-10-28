package cn.ellerotta.core.model

enum class NotifyScope {
    Users,
    Chats,
    Broadcasts,
    ;

    companion object {
        fun of(kind: PeerKind): NotifyScope = when (kind) {
            PeerKind.User, PeerKind.Bot, PeerKind.Saved -> Users
            PeerKind.Group -> Chats
            PeerKind.Channel -> Broadcasts
        }
    }
}

object Mutes {
    fun muted(own: Int?, default: Int?, now: Long): Boolean = (own ?: default ?: 0) > now
}
