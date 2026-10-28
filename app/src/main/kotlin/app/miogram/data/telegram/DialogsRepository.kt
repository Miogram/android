package app.miogram.data.telegram

import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.ChatRemoval
import cn.ellerotta.core.model.FolderLimits
import cn.ellerotta.core.model.SuggestedFolder
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MessageHit
import cn.ellerotta.core.model.MessageHits
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Preview
import cn.ellerotta.core.model.SearchCursor
import cn.ellerotta.core.model.SearchFilter
import cn.ellerotta.ktproto.api.functions.account.UpdateNotifySettings
import cn.ellerotta.ktproto.api.functions.channels.DeleteChannel
import cn.ellerotta.ktproto.api.functions.channels.DeleteHistory as ChannelsDeleteHistory
import cn.ellerotta.ktproto.api.functions.channels.LeaveChannel
import cn.ellerotta.ktproto.api.functions.channels.ReadHistory as ChannelsReadHistory
import cn.ellerotta.ktproto.api.functions.contacts.GetTopPeers
import cn.ellerotta.ktproto.api.functions.contacts.Search as ContactsSearch
import cn.ellerotta.ktproto.api.functions.folders.EditPeerFolders
import cn.ellerotta.ktproto.api.functions.messages.DeleteChat
import cn.ellerotta.ktproto.api.functions.messages.DeleteChatUser
import cn.ellerotta.ktproto.api.functions.messages.DeleteHistory
import cn.ellerotta.ktproto.api.functions.messages.GetDialogFilters
import cn.ellerotta.ktproto.api.functions.messages.GetSuggestedDialogFilters
import cn.ellerotta.ktproto.api.functions.messages.UpdateDialogFilter
import cn.ellerotta.ktproto.api.functions.messages.UpdateDialogFiltersOrder
import cn.ellerotta.ktproto.api.functions.messages.GetDialogs
import cn.ellerotta.ktproto.api.functions.messages.GetPeerDialogs
import cn.ellerotta.ktproto.api.functions.messages.GetPinnedDialogs
import cn.ellerotta.ktproto.api.functions.messages.MarkDialogUnread
import cn.ellerotta.ktproto.api.functions.messages.ReadHistory
import cn.ellerotta.ktproto.api.functions.messages.SearchGlobal
import cn.ellerotta.ktproto.api.functions.messages.ToggleDialogPin
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.Chat
import cn.ellerotta.ktproto.api.types.Dialog
import cn.ellerotta.ktproto.api.types.DialogFilter
import cn.ellerotta.ktproto.api.types.DialogFilterChatlist
import cn.ellerotta.ktproto.api.types.DialogFilterDefault
import cn.ellerotta.ktproto.api.types.DraftMessage
import cn.ellerotta.ktproto.api.types.InputDialogPeer
import cn.ellerotta.ktproto.api.types.InputFolderPeer
import cn.ellerotta.ktproto.api.types.InputNotifyPeer
import cn.ellerotta.ktproto.api.types.InputMessagesFilterDocument
import cn.ellerotta.ktproto.api.types.InputMessagesFilterEmpty
import cn.ellerotta.ktproto.api.types.InputMessagesFilterMusic
import cn.ellerotta.ktproto.api.types.InputMessagesFilterPhotoVideo
import cn.ellerotta.ktproto.api.types.InputMessagesFilterUrl
import cn.ellerotta.ktproto.api.types.InputMessagesFilterVoice
import cn.ellerotta.ktproto.api.types.InputPeerEmpty
import cn.ellerotta.ktproto.api.types.InputPeerNotifySettings
import cn.ellerotta.ktproto.api.types.InputUserSelf
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageService
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.TopPeerCategoryCorrespondents
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.TypeDialog
import cn.ellerotta.ktproto.api.types.TextWithEntities
import cn.ellerotta.ktproto.api.types.TypeDialogFilter
import cn.ellerotta.ktproto.api.types.TypeMessage
import cn.ellerotta.ktproto.api.types.TypePeer
import cn.ellerotta.ktproto.api.types.TypeUser
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.contacts.TopPeers
import cn.ellerotta.ktproto.api.types.messages.ChannelMessages
import cn.ellerotta.ktproto.api.types.messages.Dialogs
import cn.ellerotta.ktproto.api.types.messages.DialogsSlice
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.api.types.messages.MessagesSlice
import cn.ellerotta.ktproto.session.FloodWaitException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class DialogPage(val dialogs: List<DialogItem>, val complete: Boolean)

data class StoredDialogs(val folders: List<ChatFolder>?, val dialogs: List<DialogItem>, val archived: List<DialogItem>)

@Singleton
class DialogsRepository @Inject constructor(private val telegram: TelegramClientHolder, private val appConfig: AppConfigRepository) {
    private val registry get() = telegram.peers

    private val folderPeers = ConcurrentHashMap<String, TypeInputPeer>()

    private val filters = ConcurrentHashMap<Int, TypeDialogFilter>()

    suspend fun dialogs(archived: Boolean = false, limit: Int = 100, after: DialogItem? = null): DialogPage {
        val offsetPeer = after?.let { registry.inputPeer(it.key) } ?: InputPeerEmpty
        val result = telegram.client().invoke(
            GetDialogs(
                folderId = if (archived) 1 else 0,
                offsetDate = after?.date ?: 0,
                offsetId = after?.topMessage ?: 0,
                offsetPeer = offsetPeer,
                limit = limit,
                hash = 0,
            ),
        )
        val items = when (result) {
            is Dialogs -> stored(result.dialogs, result.messages, result.chats, result.users)
            is DialogsSlice -> stored(result.dialogs, result.messages, result.chats, result.users)
            else -> return DialogPage(emptyList(), true)
        }
        return DialogPage(items, result is Dialogs || items.size < limit)
    }

    suspend fun peerDialogs(keys: Collection<String>): List<DialogItem> {
        val peers = keys.mapNotNull { key -> (registry.inputPeer(key) ?: folderPeers[key])?.let(::InputDialogPeer) }
        if (peers.isEmpty()) return emptyList()
        val result = telegram.client().invoke(GetPeerDialogs(peers))
        return stored(result.dialogs, result.messages, result.chats, result.users)
    }

    suspend fun pinnedDialogs(archived: Boolean): List<DialogItem> {
        val result = telegram.client().invoke(GetPinnedDialogs(if (archived) 1 else 0))
        return stored(result.dialogs, result.messages, result.chats, result.users)
    }

    private suspend fun stored(dialogs: List<TypeDialog>, messages: List<TypeMessage>, chats: List<TypeChat>, users: List<TypeUser>): List<DialogItem> {
        telegram.cache?.putDialogs(dialogs, messages, users, chats)
        dialogs.filterIsInstance<Dialog>().forEach { registry.rememberMute(peerKey(it.peer), it.notifySettings) }
        telegram.loadDefaultMutes()
        return itemsOf(dialogs, messages, chats, users)
    }

    suspend fun stored(): StoredDialogs? {
        val cache = telegram.cache ?: return null
        val stored = cache.dialogs() ?: return null
        registry.rememberChats(stored.chats)
        val items = itemsOf(stored.dialogs, stored.messages, stored.chats, stored.users)
        return StoredDialogs(cache.folders()?.let(::foldersOf), items.filter { !it.archived }, items.filter { it.archived })
    }

    suspend fun keep(dialogs: List<DialogItem>, archived: List<DialogItem>, since: Long) {
        telegram.cache?.keepDialogs(dialogs, archived, since)
    }

    private fun itemsOf(dialogs: List<TypeDialog>, messages: List<TypeMessage>, chats: List<TypeChat>, users: List<TypeUser>): List<DialogItem> {
        val now = System.currentTimeMillis() / 1000
        val userById = users.filterIsInstance<User>().associateBy { it.id }
        registry.rememberUsers(users)
        val chatById = chats.associateBy { chatId(it) }
        val lastByPeer = messages.associateBy { peerKey(peerOf(it)) to idOf(it) }
        return dialogs.filterIsInstance<Dialog>().mapNotNull { dialog ->
            val key = peerKey(dialog.peer)
            val last = lastByPeer[key to dialog.topMessage]
            registry.topMessages[key] = dialog.topMessage
            registry.readOutbox[key] = dialog.readOutboxMaxId
            val outgoing = (last as? Message)?.out ?: (last as? MessageService)?.out ?: false
            val savedDraft = (dialog.draft as? DraftMessage)?.takeIf { it.message.isNotBlank() || it.richMessage != null }
            val base = DialogItem(
                key = key,
                title = "",
                unread = dialog.unreadCount,
                date = maxOf(dateOf(last), savedDraft?.date ?: 0),
                mentions = dialog.unreadMentionsCount,
                reactions = dialog.unreadReactionsCount,
                unreadMark = dialog.unreadMark,
                pinned = dialog.pinned,
                archived = dialog.folderId == 1,
                outgoing = outgoing,
                read = outgoing && dialog.topMessage <= dialog.readOutboxMaxId,
                draft = savedDraftOf(savedDraft, registry),
                topMessage = dialog.topMessage,
            )
            val preview = last?.let { registry.rowPreviewOf(it) } ?: Preview()
            val item = when (val peer = dialog.peer) {
                is PeerUser -> {
                    val user = userById[peer.userId] ?: return@mapNotNull null
                    registry.register(
                        base.copy(
                            title = nameOf(user),
                            preview = preview,
                            isSaved = user.self,
                            peerId = user.id,
                            kind = when {
                                user.self -> PeerKind.Saved
                                user.bot -> PeerKind.Bot
                                else -> PeerKind.User
                            },
                            verified = user.verified,
                            premium = user.premium,
                            emojiStatus = emojiStatusId(user.emojiStatus, now),
                            scam = user.scam,
                            fake = user.fake,
                            botUsers = user.botActiveUsers ?: 0,
                            online = !user.self && !user.bot && isOnline(user, now),
                            presence = if (user.self || user.bot) null else presenceOf(user.status, now),
                            contact = user.contact,
                            phone = user.phone,
                            support = user.support,
                        ),
                        avatarOf(key, user),
                    )
                }
                is PeerChat -> {
                    val chat = chatById[peer.chatId] ?: return@mapNotNull null
                    registry.register(
                        base.copy(title = titleOf(chat), preview = preview, peerId = peer.chatId, kind = PeerKind.Group, members = (chat as? Chat)?.participantsCount ?: 0, owner = (chat as? Chat)?.creator ?: false),
                        (chat as? Chat)?.photo?.let { photoRef(key, it) },
                    )
                }
                is PeerChannel -> {
                    val channel = chatById[peer.channelId] as? Channel ?: return@mapNotNull null
                    telegram.updates.value?.rememberChannel(channel.id, channel.accessHash, dialog.pts)
                    registry.register(
                        base.copy(
                            title = channel.title,
                            preview = if (channel.broadcast) preview.copy(sender = null) else preview,
                            peerId = channel.id,
                            kind = if (channel.broadcast) PeerKind.Channel else PeerKind.Group,
                            verified = channel.verified,
                            forum = channel.forum,
                            emojiStatus = emojiStatusId(channel.emojiStatus, now),
                            scam = channel.scam,
                            fake = channel.fake,
                            members = channel.participantsCount ?: 0,
                            owner = channel.creator,
                        ),
                        photoRef(key, channel.photo),
                    )
                }
            }
            val own = dialog.notifySettings.muteUntil
            item.copy(muted = registry.muted(own, item.kind), followsDefault = own == null)
        }.distinctBy { it.key }
    }

    suspend fun search(query: String, limit: Int = 20): List<DialogItem> {
        val found = telegram.client().invoke(ContactsSearch(q = query, limit = limit))
        val users = found.users.filterIsInstance<User>().associateBy { it.id }
        val chats = found.chats.associateBy { chatId(it) }
        val now = System.currentTimeMillis() / 1000
        return (found.myResults + found.results)
            .filter { peer -> peer !is PeerUser || users[peer.userId]?.self != true }
            .mapNotNull { peer -> registry.dialogOf(peer, users, chats, now) }
            .distinctBy { it.key }
    }

    suspend fun searchMessages(query: String, filter: SearchFilter = SearchFilter.All, after: SearchCursor? = null, limit: Int = 30): MessageHits {
        val call = SearchGlobal(
            q = query,
            filter = when (filter) {
                SearchFilter.All -> InputMessagesFilterEmpty
                SearchFilter.Media -> InputMessagesFilterPhotoVideo
                SearchFilter.Links -> InputMessagesFilterUrl
                SearchFilter.Files -> InputMessagesFilterDocument
                SearchFilter.Music -> InputMessagesFilterMusic
                SearchFilter.Voice -> InputMessagesFilterVoice
            },
            minDate = 0,
            maxDate = 0,
            offsetRate = after?.rate ?: 0,
            offsetPeer = after?.let { registry.inputPeer(it.peerKey) } ?: InputPeerEmpty,
            offsetId = after?.messageId ?: 0,
            limit = limit,
        )
        val result = telegram.client().invoke(call)
        val (messages, users, chats) = when (result) {
            is Messages -> Triple(result.messages, result.users, result.chats)
            is MessagesSlice -> Triple(result.messages, result.users, result.chats)
            is ChannelMessages -> Triple(result.messages, result.users, result.chats)
            else -> return MessageHits(emptyList())
        }
        registry.rememberUsers(users)
        registry.rememberChats(chats)
        val userById = users.filterIsInstance<User>().associateBy { it.id }
        val chatById = chats.associateBy { chatId(it) }
        val now = System.currentTimeMillis() / 1000
        val hits = messages.mapNotNull { message ->
            val peer = peerOf(message) ?: return@mapNotNull null
            val row = registry.dialogOf(peer, userById, chatById, now) ?: return@mapNotNull null
            MessageHit(row.copy(preview = registry.rowPreviewOf(message), date = dateOf(message), topMessage = idOf(message), pinned = false), idOf(message))
        }
        val tail = messages.lastOrNull { peerOf(it) != null }
        val rate = (result as? MessagesSlice)?.nextRate
        return MessageHits(hits, if (rate != null && tail != null) SearchCursor(rate, peerKey(peerOf(tail)!!), idOf(tail)) else null)
    }

    suspend fun topPeers(limit: Int = 10): List<DialogItem> {
        val top = telegram.client().invoke(GetTopPeers(correspondents = true, offset = 0, limit = limit, hash = 0)) as? TopPeers ?: return emptyList()
        registry.rememberUsers(top.users)
        val users = top.users.filterIsInstance<User>().associateBy { it.id }
        val chats = top.chats.associateBy { chatId(it) }
        val now = System.currentTimeMillis() / 1000
        return top.categories.filter { it.category == TopPeerCategoryCorrespondents }
            .flatMap { it.peers }
            .filter { peer -> (peer.peer as? PeerUser)?.let { users[it.userId]?.self } != true }
            .mapNotNull { peer -> registry.dialogOf(peer.peer, users, chats, now) }
            .distinctBy { it.key }
    }

    suspend fun folders(): List<ChatFolder> {
        val result = telegram.client().invoke(GetDialogFilters)
        telegram.cache?.putFolders(result.filters)
        return foldersOf(result.filters)
    }

    suspend fun saveFolder(folder: ChatFolder): List<ChatFolder> {
        if (filters.isEmpty()) folders()
        val id = if (filters.containsKey(folder.id)) folder.id else (filters.keys.maxOrNull() ?: 1).coerceAtLeast(1) + 1
        telegram.client().invoke(UpdateDialogFilter(id, filterOf(folder, id)))
        return folders()
    }

    suspend fun deleteFolder(id: Int): List<ChatFolder> {
        telegram.client().invoke(UpdateDialogFilter(id))
        return folders()
    }

    suspend fun reorderFolders(ids: List<Int>): List<ChatFolder> {
        telegram.client().invoke(UpdateDialogFiltersOrder(ids))
        return folders()
    }

    suspend fun suggestedFolders(): List<SuggestedFolder> {
        val self = telegram.requireClient().userId
        return telegram.client().invoke(GetSuggestedDialogFilters).mapNotNull { suggested ->
            val folder = foldersOf(listOf(suggested.filter), self, remember = false).firstOrNull { !it.isAll } ?: return@mapNotNull null
            SuggestedFolder(folder, suggested.description)
        }.also { telegram.memory["suggestedFolders"] = it }
    }

    fun storedSuggestedFolders(): List<SuggestedFolder>? = telegram.memory["suggestedFolders"]

    suspend fun folderLimits(): FolderLimits = appConfig.config().folderLimits(telegram.selfPremium())

    private fun filterOf(folder: ChatFolder, id: Int): TypeDialogFilter {
        val original = filters[id]
        fun peers(keys: Collection<String>) = keys.mapNotNull { registry.inputPeer(it) ?: folderPeers[it] }
        val title = TextWithEntities(folder.title.trim(), (original?.titleOf()?.takeIf { it.text == folder.title.trim() })?.entities.orEmpty())
        val pinned = peers(folder.pinned)
        val include = peers(folder.include.filter { it !in folder.pinned })
        val emoticon = folder.emoticon?.takeIf { it.isNotEmpty() }
        val color = (original as? DialogFilter)?.color ?: (original as? DialogFilterChatlist)?.color
        val noanimate = (original as? DialogFilter)?.titleNoanimate ?: (original as? DialogFilterChatlist)?.titleNoanimate ?: false
        return if (folder.shared) {
            DialogFilterChatlist(
                hasMyInvites = (original as? DialogFilterChatlist)?.hasMyInvites ?: false,
                titleNoanimate = noanimate, id = id, title = title, emoticon = emoticon, color = color,
                pinnedPeers = pinned, includePeers = include,
            )
        } else {
            DialogFilter(
                contacts = folder.contacts, nonContacts = folder.nonContacts, groups = folder.groups, broadcasts = folder.broadcasts, bots = folder.bots,
                excludeMuted = folder.excludeMuted, excludeRead = folder.excludeRead, excludeArchived = folder.excludeArchived,
                titleNoanimate = noanimate, id = id, title = title, emoticon = emoticon, color = color,
                pinnedPeers = pinned, includePeers = include, excludePeers = peers(folder.exclude),
            )
        }
    }

    private fun TypeDialogFilter.titleOf(): TextWithEntities? = when (this) {
        is DialogFilter -> title
        is DialogFilterChatlist -> title
        else -> null
    }

    private fun foldersOf(filters: List<TypeDialogFilter>, self: Long? = telegram.requireClient().userId, remember: Boolean = true): List<ChatFolder> {
        if (remember) {
            this.filters.clear()
            filters.forEach { filter ->
                when (filter) {
                    is DialogFilter -> this.filters[filter.id] = filter
                    is DialogFilterChatlist -> this.filters[filter.id] = filter
                    else -> Unit
                }
            }
        }
        fun keys(peers: List<TypeInputPeer>) = peers.mapNotNull { peer -> inputPeerKey(peer, self)?.also { folderPeers[it] = peer } }.distinct()
        val folders = filters.mapNotNull { filter ->
            when (filter) {
                is DialogFilterDefault -> ChatFolder.all("")
                is DialogFilter -> ChatFolder(
                    id = filter.id,
                    title = filter.title.text,
                    emoticon = filter.emoticon,
                    pinned = keys(filter.pinnedPeers),
                    include = keys(filter.includePeers).toSet(),
                    exclude = keys(filter.excludePeers).toSet(),
                    contacts = filter.contacts,
                    nonContacts = filter.nonContacts,
                    groups = filter.groups,
                    broadcasts = filter.broadcasts,
                    bots = filter.bots,
                    excludeMuted = filter.excludeMuted,
                    excludeRead = filter.excludeRead,
                    excludeArchived = filter.excludeArchived,
                )
                is DialogFilterChatlist -> ChatFolder(
                    id = filter.id,
                    title = filter.title.text,
                    emoticon = filter.emoticon,
                    pinned = keys(filter.pinnedPeers),
                    include = keys(filter.includePeers).toSet(),
                    shared = true,
                )
            }
        }
        return if (folders.any { it.isAll }) folders else listOf(ChatFolder.all("")) + folders
    }

    suspend fun setPinned(dialogKey: String, pinned: Boolean) {
        telegram.client().invoke(ToggleDialogPin(pinned = pinned, peer = InputDialogPeer(registry.peer(dialogKey))))
    }

    suspend fun setArchived(dialogKey: String, archived: Boolean) {
        telegram.client().invoke(EditPeerFolders(listOf(InputFolderPeer(registry.peer(dialogKey), if (archived) 1 else 0))))
    }

    fun muted(own: Int?, kind: PeerKind): Boolean = registry.muted(own, kind)

    suspend fun setMuted(dialogKey: String, muted: Boolean, forSeconds: Int? = null) {
        val muteUntil = when {
            !muted -> 0
            forSeconds == null -> Int.MAX_VALUE
            else -> (System.currentTimeMillis() / 1000).toInt() + forSeconds
        }
        telegram.client().invoke(UpdateNotifySettings(InputNotifyPeer(registry.peer(dialogKey)), InputPeerNotifySettings(muteUntil = muteUntil)))
        registry.rememberMute(dialogKey, muteUntil)
    }

    suspend fun markRead(dialogKey: String, clearMark: Boolean = true, upTo: Int? = null) {
        val maxId = upTo ?: registry.topMessages[dialogKey] ?: 0
        if (peerOfKey(dialogKey) is PeerChannel) {
            telegram.client().invoke(ChannelsReadHistory(registry.channel(dialogKey), maxId))
        } else {
            telegram.client().invoke(ReadHistory(registry.peer(dialogKey), maxId))
        }
        if (clearMark) telegram.client().invoke(MarkDialogUnread(unread = false, peer = InputDialogPeer(registry.peer(dialogKey))))
    }

    suspend fun markUnread(dialogKey: String) {
        telegram.client().invoke(MarkDialogUnread(unread = true, peer = InputDialogPeer(registry.peer(dialogKey))))
    }

    suspend fun remove(dialogKey: String, removal: ChatRemoval, revoke: Boolean = false) {
        val peer = peerOfKey(dialogKey)
        when (removal) {
            ChatRemoval.DeleteForEveryone -> when (peer) {
                is PeerChannel -> telegram.client().invoke(DeleteChannel(registry.channel(dialogKey)))
                is PeerChat -> telegram.client().invoke(DeleteChat(peer.chatId))
                else -> error("not a group or channel: $dialogKey")
            }
            ChatRemoval.Clear -> deleteHistory(dialogKey, justClear = true, revoke = revoke)
            ChatRemoval.Delete -> when (peer) {
                is PeerChannel -> telegram.client().invoke(LeaveChannel(registry.channel(dialogKey)))
                is PeerChat -> {
                    telegram.client().invoke(DeleteChatUser(chatId = peer.chatId, userId = InputUserSelf))
                    deleteHistory(dialogKey, justClear = false, revoke = false)
                }
                else -> deleteHistory(dialogKey, justClear = false, revoke = revoke)
            }
        }
        telegram.cache?.forgetHistory(dialogKey)
    }

    private suspend fun deleteHistory(dialogKey: String, justClear: Boolean, revoke: Boolean) {
        if (peerOfKey(dialogKey) is PeerChannel) {
            telegram.client().invoke(ChannelsDeleteHistory(channel = registry.channel(dialogKey), maxId = Int.MAX_VALUE))
            return
        }
        val peer = registry.peer(dialogKey)
        do {
            val affected = telegram.client().invoke(DeleteHistory(justClear = justClear, revoke = revoke, peer = peer, maxId = Int.MAX_VALUE))
        } while (affected.offset > 0)
    }
}

fun floodWaitOf(error: Throwable): Int? = (error as? FloodWaitException)?.seconds

internal fun PeerRegistry.rowPreviewOf(message: TypeMessage): Preview {
    val out = (message as? Message)?.out ?: (message as? MessageService)?.out ?: false
    val post = (message as? Message)?.post ?: (message as? MessageService)?.post ?: false
    val sender = when {
        peerOf(message) is PeerUser || post -> null
        out -> ""
        else -> ((message as? Message)?.fromId as? PeerUser)?.let { userNames[it.userId] }
    }
    return previewOf(message, sender, this)
}

internal fun PeerRegistry.dialogOf(peer: TypePeer, users: Map<Long, User>, chats: Map<Long, TypeChat>, now: Long): DialogItem? = when (peer) {
    is PeerUser -> users[peer.userId]?.let { user ->
        val kind = when {
            user.self -> PeerKind.Saved
            user.bot -> PeerKind.Bot
            else -> PeerKind.User
        }
        val item = DialogItem(
            key = peerKey(peer), title = nameOf(user), peerId = user.id, kind = kind, verified = user.verified, isSaved = user.self,
            premium = user.premium, emojiStatus = emojiStatusId(user.emojiStatus, now), scam = user.scam, fake = user.fake, botUsers = user.botActiveUsers ?: 0,
            contact = user.contact, phone = user.phone, support = user.support, online = !user.self && !user.bot && isOnline(user, now),
            presence = if (user.self || user.bot) null else presenceOf(user.status, now),
            preview = Preview(text = user.username?.let { "@$it" }.orEmpty()),
        )
        register(item, avatarOf(item.key, user))
    }
    is PeerChannel -> (chats[peer.channelId] as? Channel)?.let { channel ->
        val item = DialogItem(
            key = peerKey(peer), title = channel.title, peerId = channel.id,
            kind = if (channel.broadcast) PeerKind.Channel else PeerKind.Group, verified = channel.verified, forum = channel.forum,
            emojiStatus = emojiStatusId(channel.emojiStatus, now), scam = channel.scam, fake = channel.fake, members = channel.participantsCount ?: 0,
            preview = Preview(text = channel.username?.let { "@$it" }.orEmpty()),
        )
        register(item, photoRef(item.key, channel.photo))
    }
    is PeerChat -> chats[peer.chatId]?.let { chat ->
        val key = peerKey(peer)
        register(DialogItem(key = key, title = titleOf(chat), peerId = peer.chatId, kind = PeerKind.Group, members = (chat as? Chat)?.participantsCount ?: 0), (chat as? Chat)?.photo?.let { photoRef(key, it) })
    }
}
