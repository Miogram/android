package app.miogram.data.telegram

import android.util.Base64
import cn.ellerotta.core.database.ChatDatabase
import cn.ellerotta.core.database.ContactRow
import cn.ellerotta.core.database.DialogRow
import cn.ellerotta.core.database.FolderRow
import cn.ellerotta.core.database.HistoryKey
import cn.ellerotta.core.database.MessageRow
import cn.ellerotta.core.database.CustomEmojiRow
import cn.ellerotta.core.database.MetaRow
import cn.ellerotta.core.database.PeerRow
import cn.ellerotta.core.database.ProfileRow
import cn.ellerotta.core.database.StoredPage
import cn.ellerotta.core.database.StoryRow
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.ForumTopic
import cn.ellerotta.ktproto.api.LAYER
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.ChannelForbidden
import cn.ellerotta.ktproto.api.types.Chat
import cn.ellerotta.ktproto.api.types.ChatForbidden
import cn.ellerotta.ktproto.api.types.Contact
import cn.ellerotta.ktproto.api.types.Dialog
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.DraftMessage
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageReactions
import cn.ellerotta.ktproto.api.types.PeerNotifySettings
import cn.ellerotta.ktproto.api.types.ReactionCount
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.TypeDialog
import cn.ellerotta.ktproto.api.types.TypeDialogFilter
import cn.ellerotta.ktproto.api.types.TypeDocument
import cn.ellerotta.ktproto.api.types.TypeDraftMessage
import cn.ellerotta.ktproto.api.types.TypeMessage
import cn.ellerotta.ktproto.api.types.TypeUser
import cn.ellerotta.ktproto.api.types.TypePeer
import cn.ellerotta.ktproto.api.types.TypeStoryItem
import cn.ellerotta.ktproto.api.types.UpdateDeleteChannelMessages
import cn.ellerotta.ktproto.api.types.UpdateDeleteMessages
import cn.ellerotta.ktproto.api.types.UpdateDraftMessage
import cn.ellerotta.ktproto.api.types.UpdateEditChannelMessage
import cn.ellerotta.ktproto.api.types.UpdateEditMessage
import cn.ellerotta.ktproto.api.types.UpdateMessageReactions
import cn.ellerotta.ktproto.api.types.UpdateNewChannelMessage
import cn.ellerotta.ktproto.api.types.UpdateNewMessage
import cn.ellerotta.ktproto.api.types.UpdateReadChannelOutbox
import cn.ellerotta.ktproto.api.types.UpdateReadHistoryOutbox
import cn.ellerotta.ktproto.api.types.UpdateReadStories
import cn.ellerotta.ktproto.api.types.UpdateSentStoryReaction
import cn.ellerotta.ktproto.api.types.UpdateStory
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.contacts.Contacts
import cn.ellerotta.ktproto.api.types.PeerStories as TlPeerStories
import cn.ellerotta.ktproto.client.UpdateEvent
import cn.ellerotta.ktproto.tl.TlObject
import cn.ellerotta.ktproto.tl.decode
import cn.ellerotta.ktproto.tl.toByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

internal class CachedDialogs(val dialogs: List<Dialog>, val messages: List<TypeMessage>, val users: List<TypeUser>, val chats: List<TypeChat>)

internal class CachedMessages(
    val messages: List<TypeMessage>,
    val reactions: Map<Int, MessageReactions>,
    val users: List<TypeUser>,
    val chats: List<TypeChat>,
    val complete: Boolean,
)

internal class StoredStories(val stories: TlPeerStories, val hidden: Boolean)

internal class CachedStories(val peers: List<StoredStories>, val users: List<TypeUser>, val chats: List<TypeChat>)

internal class ChatCache(
    private val database: ChatDatabase,
    private val log: (String) -> Unit = {},
    private val layer: Int = LAYER,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = database.chats()
    private val opened = CompletableDeferred<Unit>()

    private class Live {
        var loads = 0
        var synced = false
        var resynced = false
        val waiting = ArrayList<MessageRow>()
    }

    private val live = HashMap<HistoryKey, Live>()

    @Volatile private var listed: Set<String> = emptySet()

    @Volatile private var account: Long? = null

    suspend fun open(owner: Long?) {
        try {
            guard("open") {
                dao.dropOtherLayers(layer)
                if (owner == null) {
                    account = null
                    clearRows()
                } else {
                    owned(owner)
                }
                dao.pruneHistories(MAX_HISTORIES)
                dao.prunePeers(MAX_PEERS)
            }
        } finally {
            opened.complete(Unit)
        }
    }

    suspend fun owner(userId: Long) {
        opened.await()
        if (account == userId) return
        guard("owner") { owned(userId) }
    }

    private suspend fun owned(userId: Long) {
        if (dao.meta(OWNER) != userId.toString()) {
            clearRows()
            dao.putMeta(MetaRow(OWNER, userId.toString()))
        }
        account = userId
    }

    suspend fun clear() {
        account = null
        guard("clear") { clearRows() }
    }

    private suspend fun clearRows() {
        synchronized(live) { live.clear() }
        listed = emptySet()
        dao.clear()
    }

    suspend fun putDialogs(dialogs: List<TypeDialog>, messages: List<TypeMessage>, users: List<TypeUser>, chats: List<TypeChat>) = write("dialogs") {
        val written = clock()
        val ids = idsOf(users, chats)
        val top = messages.associateBy { peerKey(peerOf(it)) to idOf(it) }
        val rows = dialogs.filterIsInstance<Dialog>().map { dialog ->
            val key = peerKey(dialog.peer)
            val message = top[key to dialog.topMessage]?.toByteArray()
            val draft = (dialog.draft as? DraftMessage)?.toByteArray()
            DialogRow(
                dialog = key,
                folder = dialog.folderId ?: 0,
                position = Int.MAX_VALUE,
                pinned = dialog.pinned,
                topMessage = dialog.topMessage,
                unread = dialog.unreadCount,
                mentions = dialog.unreadMentionsCount,
                unreadMark = dialog.unreadMark,
                muteUntil = dialog.notifySettings.muteUntil ?: DialogRow.FOLLOWS_DEFAULT,
                readOutbox = dialog.readOutboxMaxId,
                message = message,
                draft = draft,
                peers = peersIn(key, ids, message, draft),
                written = written,
                layer = layer,
            )
        }
        dao.putPeers(peerRows(users, chats))
        dao.putDialogs(rows)
    }

    suspend fun dialogs(): CachedDialogs? = read("dialogs") {
        val rows = dao.dialogs(layer)
        if (rows.isEmpty()) return@read null
        listed = rows.mapTo(HashSet()) { it.dialog }
        val tops = rows.associate { row -> row.dialog to row.message?.let { decoded("message") { TypeMessage.decode(it) } } }
        val (users, chats) = peersOf(rows.flatMap { it.peers.split(' ') })
        val dialogs = rows.mapNotNull { row ->
            val peer = peerOfKey(row.dialog) ?: return@mapNotNull null
            Dialog(
                pinned = row.pinned,
                unreadMark = row.unreadMark,
                peer = peer,
                topMessage = tops[row.dialog]?.let(::idOf) ?: row.topMessage,
                readInboxMaxId = 0,
                readOutboxMaxId = row.readOutbox,
                unreadCount = row.unread,
                unreadMentionsCount = row.mentions,
                unreadReactionsCount = 0,
                unreadPollVotesCount = 0,
                notifySettings = PeerNotifySettings(muteUntil = row.muteUntil.takeIf { it >= 0 }),
                draft = row.draft?.let { decoded("draft") { TypeDraftMessage.decode(it) } },
                folderId = row.folder.takeIf { it != 0 },
            )
        }
        CachedDialogs(dialogs, tops.values.filterNotNull(), users, chats)
    }

    suspend fun keepDialogs(main: List<DialogItem>, archived: List<DialogItem>, since: Long) = write("list") {
        val shown = main.withIndex() + archived.withIndex()
        val keys = shown.mapTo(HashSet()) { it.value.key }
        val gone = listed - keys
        listed = keys
        val now = clock() / 1000
        dao.keepDialogs(gone.toList(), since) { stored ->
            val byKey = stored.associateBy { it.dialog }
            shown.mapNotNull { (index, item) ->
                byKey[item.key]?.let { row ->
                    row.copy(
                        folder = if (item.archived) 1 else 0,
                        position = index,
                        pinned = item.pinned,
                        unread = item.unread,
                        mentions = item.mentions,
                        unreadMark = item.unreadMark,
                        muteUntil = when {
                            item.followsDefault -> DialogRow.FOLLOWS_DEFAULT
                            !item.muted -> if (row.muteUntil > now || row.muteUntil == DialogRow.FOLLOWS_DEFAULT) 0 else row.muteUntil
                            row.muteUntil > now -> row.muteUntil
                            else -> Int.MAX_VALUE
                        },
                        readOutbox = if (item.outgoing && item.read) maxOf(row.readOutbox, item.topMessage) else row.readOutbox,
                    )
                }
            }
        }
    }

    suspend fun muteUntil(dialog: String): Int? = read("mute") { dao.dialogsOf(listOf(dialog)).firstOrNull()?.muteUntil }

    suspend fun forgetHistory(dialog: String) {
        unsync { it.dialog == dialog }
        write("history") { dao.dropDialogHistory(dialog) }
    }

    suspend fun draft(dialog: String, draft: DraftMessage?) = write("draft") { dao.draft(dialog, draft?.toByteArray()) }

    suspend fun putFolders(filters: List<TypeDialogFilter>) = write("folders") {
        dao.putFolders(filters.mapIndexed { index, filter -> FolderRow(index, filter.toByteArray(), layer) })
    }

    suspend fun folders(): List<TypeDialogFilter>? = read("folders") {
        dao.folders(layer).mapNotNull { row -> decoded("folder") { TypeDialogFilter.decode(row.tl) } }.ifEmpty { null }
    }

    fun loading(dialog: String, topic: Int) {
        synchronized(live) { live.getOrPut(HistoryKey(dialog, topic), ::Live).loads++ }
    }

    suspend fun failed(dialog: String, topic: Int) = loaded(HistoryKey(dialog, topic), stored = false)

    suspend fun putNewest(dialog: String, topic: Int, messages: List<TypeMessage>, users: List<TypeUser>, chats: List<TypeChat>, limit: Int) {
        val stored = write("history") {
            dao.putPeers(peerRows(users, chats))
            dao.putNewest(dialog, topic, rowsOf(dialog, topic, messages, users, chats), limit, MAX_MESSAGES, clock())
            dao.pruneHistories(MAX_HISTORIES)
        } != null
        loaded(HistoryKey(dialog, topic), stored)
    }

    private suspend fun loaded(key: HistoryKey, stored: Boolean) {
        var stale = false
        val rows = synchronized(live) {
            val state = live[key] ?: return
            state.loads--
            if (stored && !state.resynced) state.synced = true
            if (stored && state.resynced) stale = true
            if (state.loads > 0) return@synchronized emptyList()
            state.resynced = false
            val rows = if (state.synced) state.waiting.toList() else emptyList()
            state.waiting.clear()
            if (!state.synced) live.remove(key)
            rows
        }
        if (stale) write("stale") { dao.stale(key.dialog) }
        if (rows.isNotEmpty()) write("live") { dao.append(rows, MAX_MESSAGES) }
    }

    suspend fun newest(dialog: String, topic: Int, limit: Int): CachedMessages? = older(dialog, topic, Int.MAX_VALUE, limit)

    suspend fun older(dialog: String, topic: Int, before: Int, limit: Int): CachedMessages? = read("history") {
        dao.older(dialog, topic, before, limit, layer, clock())?.let { messagesOf(it) }
    }

    suspend fun putOlder(dialog: String, topic: Int, before: Int, messages: List<TypeMessage>, users: List<TypeUser>, chats: List<TypeChat>, limit: Int) = write("history") {
        if (dao.putOlder(dialog, topic, before, rowsOf(dialog, topic, messages, users, chats), limit, MAX_MESSAGES, clock())) dao.putPeers(peerRows(users, chats))
    }

    suspend fun apply(event: UpdateEvent) {
        opened.await()
        if (account == null) return
        guard("update") {
            when (event) {
                is UpdateEvent.Batch -> batch(event)
                UpdateEvent.Resync -> {
                    unsync { true }
                    dao.staleAll()
                }
                is UpdateEvent.ChannelResync -> {
                    val key = "ch${event.channelId}"
                    unsync { it.dialog == key }
                    dao.stale(key)
                }
            }
        }
    }

    private fun unsync(which: (HistoryKey) -> Boolean) = synchronized(live) {
        live.entries.removeAll { (key, state) ->
            if (!which(key)) return@removeAll false
            state.synced = false
            state.waiting.clear()
            if (state.loads > 0) state.resynced = true
            state.loads == 0
        }
    }

    private suspend fun batch(event: UpdateEvent.Batch) {
        val ids = idsOf(event.users, event.chats)
        if (event.users.isNotEmpty() || event.chats.isNotEmpty()) dao.putPeers(peerRows(event.users, event.chats))
        for (update in event.updates) {
            when (update) {
                is UpdateNewMessage -> arrived(update.message, ids)
                is UpdateNewChannelMessage -> arrived(update.message, ids)
                is UpdateEditMessage -> edited(update.message, ids)
                is UpdateEditChannelMessage -> edited(update.message, ids)
                is UpdateDeleteMessages -> dao.deleteCommon(update.messages)
                is UpdateDeleteChannelMessages -> dao.delete("ch${update.channelId}", update.messages)
                is UpdateReadHistoryOutbox -> dao.readOutbox(peerKey(update.peer), update.maxId)
                is UpdateReadChannelOutbox -> dao.readOutbox("ch${update.channelId}", update.maxId)
                is UpdateMessageReactions -> reacted(peerKey(update.peer), update.msgId, update.reactions)
                is UpdateDraftMessage -> if (update.topMsgId == null && update.savedPeerId == null) dao.draft(peerKey(update.peer), (update.draft as? DraftMessage)?.toByteArray())
                is UpdateStory -> storyArrived(update.peer, update.story, event.users, event.chats)
                is UpdateReadStories -> dao.readStories(peerKey(update.peer), update.maxId)
                is UpdateSentStoryReaction -> changeStored(peerKey(update.peer)) { it.reacted(update.storyId, update.reaction) }
                else -> Unit
            }
        }
    }

    private suspend fun arrived(message: TypeMessage, ids: Map<Long, List<String>>) {
        val key = keyOf(message) ?: return
        val bytes = message.toByteArray()
        val peers = peersIn(key, ids, bytes)
        dao.newTop(key, idOf(message), bytes, peers)
        val thread = (message as? Message)?.let { threadOf(it.replyTo) }
        val topics = setOfNotNull(0, topicIdOf(message) ?: ForumTopic.GENERAL_TOPIC, thread)
        val rows = synchronized(live) {
            topics.mapNotNull { topic ->
                val state = live[HistoryKey(key, topic)] ?: return@mapNotNull null
                val row = MessageRow(key, topic, idOf(message), bytes, peers, layer)
                when {
                    state.loads > 0 -> null.also { state.waiting += row }
                    state.synced -> row
                    else -> null
                }
            }
        }
        if (rows.isNotEmpty()) dao.append(rows, MAX_MESSAGES)
    }

    private suspend fun edited(message: TypeMessage, ids: Map<Long, List<String>>) {
        val key = keyOf(message) ?: return
        val bytes = message.toByteArray()
        val peers = peersIn(key, ids, bytes)
        dao.edit(key, idOf(message), bytes, peers)
        dao.editedTop(key, idOf(message), bytes, peers)
    }

    private suspend fun reacted(key: String, id: Int, reactions: MessageReactions) {
        val row = dao.message(key, id).firstOrNull() ?: return
        val stored = if (reactions.min) row.reactions?.let { decoded("reactions") { MessageReactions.decode(it) } } ?: decoded("message") { TypeMessage.decode(row.tl) as? Message }?.reactions else null
        val merged = if (stored == null) {
            reactions
        } else {
            MessageReactions(
                min = false,
                canSeeList = reactions.canSeeList,
                reactionsAsTags = reactions.reactionsAsTags,
                results = reactions.results.map { result ->
                    val chosen = stored.results.firstOrNull { it.reaction.toByteArray().contentEquals(result.reaction.toByteArray()) }?.chosenOrder
                    ReactionCount(chosenOrder = chosen, reaction = result.reaction, count = result.count)
                },
                recentReactions = reactions.recentReactions,
                topReactors = reactions.topReactors,
            )
        }
        dao.react(key, id, merged.toByteArray())
    }

    private fun rowsOf(dialog: String, topic: Int, messages: List<TypeMessage>, users: List<TypeUser>, chats: List<TypeChat>): List<MessageRow> {
        val ids = idsOf(users, chats)
        return messages.filter { keyOf(it) != null }.map { message ->
            val bytes = message.toByteArray()
            MessageRow(dialog, topic, idOf(message), bytes, peersIn(dialog, ids, bytes), layer)
        }
    }

    private suspend fun messagesOf(page: StoredPage): CachedMessages {
        val messages = page.rows.mapNotNull { row -> decoded("message") { TypeMessage.decode(row.tl) } }
        val reactions = page.rows.mapNotNull { row -> row.reactions?.let { bytes -> decoded("reactions") { MessageReactions.decode(bytes) }?.let { row.id to it } } }.toMap()
        val (users, chats) = peersOf(page.rows.flatMap { it.peers.split(' ') })
        return CachedMessages(messages, reactions, users, chats, page.complete)
    }

    suspend fun profile(peer: String): ProfileRow? = read("profile") { dao.profile(peer, layer) }

    suspend fun changeProfile(peer: String, change: (ProfileRow) -> ProfileRow) = write("profile") { dao.changeProfile(peer, layer, clock(), MAX_PROFILES, change) }

    suspend fun customEmoji(ids: Collection<Long>): List<Document> = read("custom emoji") {
        ids.distinct().chunked(CHUNK).flatMap { dao.customEmoji(it, layer) }.mapNotNull { row -> decoded("custom emoji") { TypeDocument.decode(row.tl) as? Document } }
    }.orEmpty()

    suspend fun putCustomEmoji(documents: List<Document>) = write("custom emoji") { dao.putCustomEmoji(documents.map { CustomEmojiRow(it.id, it.toByteArray(), layer) }) }

    suspend fun <T : Any> stickerList(name: String, decode: (ByteArray) -> T): T? = read("sticker list") {
        dao.meta(stickerListKey(name))?.let { text -> decoded("sticker list") { decode(Base64.decode(text, Base64.NO_WRAP)) } }
    }

    suspend fun putStickerList(name: String, list: TlObject) = write("sticker list") {
        dao.putMeta(MetaRow(stickerListKey(name), Base64.encodeToString(list.toByteArray(), Base64.NO_WRAP)))
    }

    private fun stickerListKey(name: String) = "$STICKER_LIST$layer:$name"

    suspend fun contacts(): Contacts? = read("contacts") {
        val saved = dao.meta(CONTACTS_SAVED)?.toIntOrNull() ?: return@read null
        val rows = dao.contacts(layer)
        val users = peersOf(rows.map { it.peer }).first
        if (users.size < rows.size) return@read null
        Contacts(rows.map { Contact(it.peer.removePrefix("u").toLong(), it.mutual) }, saved, users)
    }

    suspend fun putContacts(contacts: Contacts) = write("contacts") {
        val rows = contacts.contacts.map { ContactRow("u${it.userId}", it.mutual, layer) }
        dao.putContacts(rows, peerRows(contacts.users, emptyList()), CONTACTS_SAVED, contacts.savedCount)
    }

    suspend fun stories(): CachedStories? = read("stories") {
        val rows = dao.stories(layer)
        if (rows.isEmpty()) return@read null
        val peers = rows.mapNotNull { row -> decoded("stories") { TlPeerStories.decode(row.tl) }?.let { StoredStories(it.read(row.maxRead), row.hidden) } }
        val (users, chats) = peersOf(rows.map { it.peer })
        CachedStories(peers, users, chats)
    }

    suspend fun storyState(hidden: Boolean): String? = read("stories") {
        if (dao.stories(layer).none { it.hidden == hidden }) return@read null
        dao.meta(if (hidden) HIDDEN_STORIES_STATE else STORIES_STATE)
    }

    suspend fun putStoryState(hidden: Boolean, state: String) = write("stories") { dao.putMeta(MetaRow(if (hidden) HIDDEN_STORIES_STATE else STORIES_STATE, state)) }

    suspend fun pinsHiddenUpTo(dialog: String, topic: Int): Int? = read("pins") { dao.meta(pinsHiddenKey(dialog, topic))?.toIntOrNull() }

    suspend fun putPinsHiddenUpTo(dialog: String, topic: Int, upTo: Int) = write("pins") { dao.putMeta(MetaRow(pinsHiddenKey(dialog, topic), upTo.toString())) }

    private fun pinsHiddenKey(dialog: String, topic: Int) = "$PINS_HIDDEN$dialog:$topic"

    suspend fun putStories(hidden: Boolean, stories: List<TlPeerStories>, users: List<TypeUser>, chats: List<TypeChat>, replace: Boolean) = write("stories") {
        dao.putStories(hidden, stories.filter { it.stories.isNotEmpty() }.map { storyRow(it, hidden) }, peerRows(users, chats), replace)
    }

    suspend fun putPeerStories(stories: TlPeerStories, hidden: Boolean, users: List<TypeUser>, chats: List<TypeChat>) = write("stories") {
        dao.putPeers(peerRows(users, chats))
        dao.changeStories(peerKey(stories.peer), layer) { if (stories.stories.isEmpty()) null else storyRow(stories, hidden) }
    }

    suspend fun changeStories(peer: String, change: (TlPeerStories) -> TlPeerStories) = write("stories") { changeStored(peer, change) }

    suspend fun readStories(peer: String, maxId: Int) = write("stories") { dao.readStories(peer, maxId) }

    suspend fun hideStories(peer: String, hidden: Boolean) = write("stories") { dao.hideStories(peer, hidden) }

    private suspend fun changeStored(peer: String, change: (TlPeerStories) -> TlPeerStories) = dao.changeStories(peer, layer) { row ->
        if (row == null) return@changeStories null
        val stories = decoded("stories") { TlPeerStories.decode(row.tl) }?.let { change(it.read(row.maxRead)) } ?: return@changeStories row
        if (stories.stories.isEmpty()) null else storyRow(stories, row.hidden)
    }

    private suspend fun storyArrived(peer: TypePeer, story: TypeStoryItem, users: List<TypeUser>, chats: List<TypeChat>) {
        val key = peerKey(peer)
        dao.changeStories(key, layer) { row ->
            val stored = row?.let { decoded("stories") { TlPeerStories.decode(it.tl) }?.read(it.maxRead) }
            val merged = (stored ?: TlPeerStories(peer = peer, stories = emptyList())).merged(listOf(story))
            if (merged.stories.isEmpty()) null else storyRow(merged, row?.hidden ?: storiesHiddenIn(key, users, chats))
        }
    }

    private fun storyRow(stories: TlPeerStories, hidden: Boolean) = StoryRow(peerKey(stories.peer), hidden, stories.maxReadId ?: 0, stories.toByteArray(), layer)

    suspend fun peers(keys: List<String>): Pair<List<TypeUser>, List<TypeChat>>? = read("peers") { peersOf(keys) }

    private suspend fun peersOf(keys: List<String>): Pair<List<TypeUser>, List<TypeChat>> {
        val rows = keys.filter { it.isNotEmpty() }.distinct().chunked(CHUNK).flatMap { dao.peers(it, layer) }
        val users = rows.filter { it.peer.startsWith("u") }.mapNotNull { decoded("user") { TypeUser.decode(it.tl) } }
        val chats = rows.filter { it.peer.startsWith("c") }.mapNotNull { decoded("chat") { TypeChat.decode(it.tl) } }
        return users to chats
    }

    private fun peerRows(users: List<TypeUser>, chats: List<TypeChat>): List<PeerRow> {
        val seen = clock()
        return users.filterIsInstance<User>().map { PeerRow("u${it.id}", nameOf(it), it.min, it.toByteArray(), seen, layer) } +
            chats.mapNotNull { chat -> chatKeyOf(chat)?.let { PeerRow(it, titleOf(chat), (chat as? Channel)?.min == true, chat.toByteArray(), seen, layer) } }
    }

    private inline fun <T> decoded(what: String, decode: () -> T): T? = try {
        decode()
    } catch (e: Exception) {
        log("cache: cannot decode a stored $what: ${e.message}")
        null
    }

    private suspend fun <T> write(what: String, block: suspend () -> T): T? {
        opened.await()
        if (account == null) return null
        return guard(what, block)
    }

    private suspend fun <T> read(what: String, block: suspend () -> T?): T? {
        opened.await()
        if (account == null) return null
        return guard(what, block)
    }

    private suspend fun <T> guard(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("cache: $what failed: ${e.message}")
        null
    }

    companion object {
        const val MAX_MESSAGES = 500
        const val MAX_HISTORIES = 100
        const val MAX_PEERS = 5_000

        const val MAX_PROFILES = 200
        private const val OWNER = "owner"
        private const val STICKER_LIST = "sticker_list:"
        private const val PINS_HIDDEN = "pins_hidden:"
        private const val CONTACTS_SAVED = "contacts_saved"
        private const val STORIES_STATE = "stories_state"
        private const val HIDDEN_STORIES_STATE = "stories_hidden_state"
        private const val CHUNK = 500
    }
}

private fun chatKeyOf(chat: TypeChat): String? = when (chat) {
    is Chat, is ChatForbidden -> "c${chatId(chat)}"
    is Channel, is ChannelForbidden -> "ch${chatId(chat)}"
    else -> null
}

private fun idsOf(users: List<TypeUser>, chats: List<TypeChat>): Map<Long, List<String>> {
    val ids = HashMap<Long, MutableList<String>>()
    users.filterIsInstance<User>().forEach { ids.getOrPut(it.id, ::ArrayList) += "u${it.id}" }
    chats.forEach { chat -> chatKeyOf(chat)?.let { ids.getOrPut(chatId(chat), ::ArrayList) += it } }
    return ids
}

private fun peersIn(dialog: String, ids: Map<Long, List<String>>, vararg parts: ByteArray?): String {
    val keys = LinkedHashSet<String>()
    keys += dialog
    for (bytes in parts) {
        if (bytes == null) continue
        var i = 0
        while (i + 8 <= bytes.size) {
            var value = 0L
            for (b in 7 downTo 0) value = (value shl 8) or (bytes[i + b].toLong() and 0xff)
            ids[value]?.let(keys::addAll)
            i += 4
        }
    }
    return keys.joinToString(" ")
}
