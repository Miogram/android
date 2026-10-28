package app.miogram.data.telegram

import cn.ellerotta.core.model.InviteLink
import cn.ellerotta.core.model.InviteLinks
import cn.ellerotta.core.model.JoinRequest
import cn.ellerotta.core.model.JoinRequestsPage
import cn.ellerotta.core.model.NewInviteLink
import cn.ellerotta.ktproto.api.functions.messages.DeleteExportedChatInvite
import cn.ellerotta.ktproto.api.functions.messages.EditExportedChatInvite
import cn.ellerotta.ktproto.api.functions.messages.ExportChatInvite
import cn.ellerotta.ktproto.api.functions.messages.GetChatInviteImporters
import cn.ellerotta.ktproto.api.functions.messages.GetExportedChatInvites
import cn.ellerotta.ktproto.api.functions.messages.HideChatJoinRequest
import cn.ellerotta.ktproto.api.types.ChatInviteExported
import cn.ellerotta.ktproto.api.types.InputUserEmpty
import cn.ellerotta.ktproto.api.types.InputUserSelf
import cn.ellerotta.ktproto.api.types.TypeExportedChatInvite
import cn.ellerotta.ktproto.api.types.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvitesRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    suspend fun links(dialogKey: String, limit: Int = 50): InviteLinks {
        val peer = registry.peer(dialogKey)
        val working = telegram.client().invoke(GetExportedChatInvites(peer = peer, adminId = InputUserSelf, limit = limit)).invites.mapNotNull(::linkOf)
        val revoked = telegram.client().invoke(GetExportedChatInvites(revoked = true, peer = peer, adminId = InputUserSelf, limit = limit)).invites.mapNotNull(::linkOf)
        val primary = working.firstOrNull { it.primary } ?: linkOf(telegram.client().invoke(ExportChatInvite(peer = peer)))
        return InviteLinks(primary, working.filter { !it.primary }, revoked).also { telegram.memory["links:$dialogKey"] = it }
    }

    fun storedLinks(dialogKey: String): InviteLinks? = telegram.memory["links:$dialogKey"]

    fun storedRequests(dialogKey: String): JoinRequestsPage? = telegram.memory["requests:$dialogKey"]

    fun storedPending(dialogKey: String): Int? = telegram.memory["pending:$dialogKey"]

    suspend fun create(dialogKey: String, link: NewInviteLink): InviteLink? {
        val expires = link.expiresIn?.let { (System.currentTimeMillis() / 1000).toInt() + it }
        val created = telegram.client().invoke(
            ExportChatInvite(
                requestNeeded = link.requestNeeded,
                peer = registry.peer(dialogKey),
                expireDate = expires,
                usageLimit = link.usageLimit.takeIf { !link.requestNeeded },
                title = link.title.trim().ifEmpty { null },
            ),
        )
        return linkOf(created)
    }

    suspend fun revoke(dialogKey: String, link: String) {
        telegram.client().invoke(EditExportedChatInvite(revoked = true, peer = registry.peer(dialogKey), link = link))
    }

    suspend fun delete(dialogKey: String, link: String) {
        telegram.client().invoke(DeleteExportedChatInvite(registry.peer(dialogKey), link))
    }

    suspend fun requests(dialogKey: String, after: JoinRequest? = null, limit: Int = 30): JoinRequestsPage {
        val result = telegram.client().invoke(
            GetChatInviteImporters(
                requested = true,
                peer = registry.peer(dialogKey),
                offsetDate = after?.date ?: 0,
                offsetUser = after?.let { registry.user(it.userKey) } ?: InputUserEmpty,
                limit = limit,
            ),
        )
        registry.rememberUsers(result.users)
        val users = result.users.filterIsInstance<User>().associateBy { it.id }
        val requests = result.importers.map { importer ->
            val key = "u${importer.userId}"
            val user = users[importer.userId]
            JoinRequest(
                userKey = key,
                name = user?.let(::nameOf) ?: registry.peerNames[key].orEmpty(),
                avatarKey = user?.let { registry.avatarKey(avatarOf(key, it)) },
                date = importer.date,
                about = importer.about?.ifBlank { null },
            )
        }
        val page = JoinRequestsPage(requests, result.count)
        telegram.memory["pending:$dialogKey"] = result.count
        if (after == null && limit > 1) telegram.memory["requests:$dialogKey"] = page
        return page
    }

    suspend fun decide(dialogKey: String, userKey: String, approve: Boolean) {
        telegram.client().invoke(HideChatJoinRequest(approved = approve, peer = registry.peer(dialogKey), userId = registry.user(userKey)))
        storedRequests(dialogKey)?.let { page ->
            telegram.memory["requests:$dialogKey"] = JoinRequestsPage(page.requests.filter { it.userKey != userKey }, (page.total - 1).coerceAtLeast(0))
        }
        storedPending(dialogKey)?.let { telegram.memory["pending:$dialogKey"] = (it - 1).coerceAtLeast(0) }
    }
}

private fun linkOf(result: cn.ellerotta.ktproto.api.types.messages.TypeExportedChatInvite): InviteLink? = when (result) {
    is cn.ellerotta.ktproto.api.types.messages.ExportedChatInvite -> linkOf(result.invite)
    is cn.ellerotta.ktproto.api.types.messages.ExportedChatInviteReplaced -> linkOf(result.newInvite)
}

private fun linkOf(invite: TypeExportedChatInvite): InviteLink? = (invite as? ChatInviteExported)?.let {
    InviteLink(
        link = it.link,
        title = it.title?.ifBlank { null },
        primary = it.permanent,
        revoked = it.revoked,
        requestNeeded = it.requestNeeded,
        date = it.date,
        expireDate = it.expireDate,
        usageLimit = it.usageLimit,
        usage = it.usage ?: 0,
        requested = it.requested ?: 0,
    )
}
