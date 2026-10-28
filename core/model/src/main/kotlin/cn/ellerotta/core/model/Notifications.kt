package cn.ellerotta.core.model

data class ScopeNotifySettings(val muteUntil: Int = 0, val showPreviews: Boolean = true) {
    fun muted(now: Long): Boolean = Mutes.muted(null, muteUntil, now)
}

data class NotifyException(
    val key: String,
    val name: String,
    val kind: PeerKind,
    val avatarKey: String? = null,
    val muteUntil: Int? = null,
    val showPreviews: Boolean? = null,
) {
    fun muted(default: ScopeNotifySettings?, now: Long): Boolean = Mutes.muted(muteUntil, default?.muteUntil, now)

    fun differs(default: ScopeNotifySettings?, now: Long): Boolean =
        (muteUntil != null && muted(default, now) != (default?.muted(now) ?: false)) || (showPreviews != null && showPreviews != (default?.showPreviews ?: true))
}

data class NotificationSettingsState(
    val scopes: Map<NotifyScope, ScopeNotifySettings> = emptyMap(),
    val exceptions: Map<NotifyScope, List<NotifyException>>? = null,
) {
    fun exceptionsOf(scope: NotifyScope, now: Long): List<NotifyException>? = exceptions?.let { all ->
        val default = scopes[scope]
        all[scope].orEmpty().filter { it.differs(default, now) }.sortedWith(compareBy({ !it.muted(default, now) }, { it.name.lowercase() }))
    }
}
