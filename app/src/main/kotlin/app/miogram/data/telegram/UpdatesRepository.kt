package app.miogram.data.telegram

import cn.ellerotta.core.model.ActivityKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.NotifyScope
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.Poll
import cn.ellerotta.core.model.PollResults
import cn.ellerotta.core.model.Preview
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.ReactionCount
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.ktproto.api.types.DialogPeer
import cn.ellerotta.ktproto.api.types.DraftMessage
import cn.ellerotta.ktproto.api.types.DraftMessageEmpty
import cn.ellerotta.ktproto.api.types.EphemeralMessage
import cn.ellerotta.ktproto.api.types.UpdateDeleteEphemeralMessages
import cn.ellerotta.ktproto.api.types.UpdateEditEphemeralMessage
import cn.ellerotta.ktproto.api.types.UpdateNewEphemeralMessage
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.NotifyPeer
import cn.ellerotta.ktproto.api.types.NotifyBroadcasts
import cn.ellerotta.ktproto.api.types.NotifyChats
import cn.ellerotta.ktproto.api.types.NotifyUsers
import cn.ellerotta.ktproto.api.types.TypeDraftMessage
import cn.ellerotta.ktproto.api.types.StoryItemDeleted
import cn.ellerotta.ktproto.api.types.TypeStoryItem
import cn.ellerotta.ktproto.api.types.PeerNotifySettings
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.SendMessageCancelAction
import cn.ellerotta.ktproto.api.types.SendMessageRichMessageDraftAction
import cn.ellerotta.ktproto.api.types.SendMessageStopDraftAction
import cn.ellerotta.ktproto.api.types.SendMessageTextDraftAction
import cn.ellerotta.ktproto.api.types.TypeMessage
import cn.ellerotta.ktproto.api.types.TypePeer
import cn.ellerotta.ktproto.api.types.TypeSendMessageAction
import cn.ellerotta.ktproto.api.types.TypeUpdate
import cn.ellerotta.ktproto.api.types.UpdateChannelUserTyping
import cn.ellerotta.ktproto.api.types.UpdateChatUserTyping
import cn.ellerotta.ktproto.api.types.UpdateDeleteChannelMessages
import cn.ellerotta.ktproto.api.types.UpdateDeleteMessages
import cn.ellerotta.ktproto.api.types.UpdateDialogFilter
import cn.ellerotta.ktproto.api.types.UpdateDialogFilterOrder
import cn.ellerotta.ktproto.api.types.UpdateDialogFilters
import cn.ellerotta.ktproto.api.types.UpdateDialogPinned
import cn.ellerotta.ktproto.api.types.UpdateDialogUnreadMark
import cn.ellerotta.ktproto.api.types.UpdateDraftMessage
import cn.ellerotta.ktproto.api.types.UpdateEditChannelMessage
import cn.ellerotta.ktproto.api.types.UpdateEditMessage
import cn.ellerotta.ktproto.api.types.UpdateFolderPeers
import cn.ellerotta.ktproto.api.types.UpdateMessagePoll
import cn.ellerotta.ktproto.api.types.UpdateMessageReactions
import cn.ellerotta.ktproto.api.types.UpdateNewChannelMessage
import cn.ellerotta.ktproto.api.types.UpdateNewMessage
import cn.ellerotta.ktproto.api.types.UpdateNotifySettings as UpdateNotifySettingsUpdate
import cn.ellerotta.ktproto.api.types.UpdatePeerBlocked
import cn.ellerotta.ktproto.api.types.UpdatePeerSettings
import cn.ellerotta.ktproto.api.types.UpdatePinnedDialogs
import cn.ellerotta.ktproto.api.types.UpdatePinnedChannelMessages
import cn.ellerotta.ktproto.api.types.UpdatePeerWallpaper
import cn.ellerotta.ktproto.api.types.UpdatePinnedMessages
import cn.ellerotta.ktproto.api.types.UpdateReadChannelInbox
import cn.ellerotta.ktproto.api.types.UpdateReadChannelOutbox
import cn.ellerotta.ktproto.api.types.UpdateReadHistoryInbox
import cn.ellerotta.ktproto.api.types.UpdateReadHistoryOutbox
import cn.ellerotta.ktproto.api.types.UpdateReadChannelDiscussionInbox
import cn.ellerotta.ktproto.api.types.UpdateReadChannelDiscussionOutbox
import cn.ellerotta.ktproto.api.types.UpdateUserStatus
import cn.ellerotta.ktproto.api.types.UpdateUserTyping
import cn.ellerotta.ktproto.api.types.UpdateReadStories
import cn.ellerotta.ktproto.api.types.UpdateSentStoryReaction
import cn.ellerotta.ktproto.api.types.UpdateStory
import cn.ellerotta.ktproto.api.types.UpdateNewScheduledMessage
import cn.ellerotta.ktproto.api.types.UpdateDeleteScheduledMessages
import cn.ellerotta.ktproto.api.types.StoryItem as TlStoryItem
import cn.ellerotta.ktproto.client.UpdateEvent
import cn.ellerotta.ktproto.client.UpdateState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.MutableSharedFlow

sealed interface ChatEvent {
    data object DialogsChanged : ChatEvent

    data object FoldersChanged : ChatEvent

    data class Typing(val key: String, val who: String?, val kind: ActivityKind = ActivityKind.Typing, val userId: Long = 0) : ChatEvent

    data class Status(val key: String, val presence: Presence) : ChatEvent

    data class NewMessage(
        val key: String,
        val message: MessageItem,
        val preview: Preview,
        val mention: Boolean = false,
        val silent: Boolean = false,
        val post: Boolean = false,
    ) : ChatEvent

    data class Edited(val key: String, val message: MessageItem, val preview: Preview) : ChatEvent

    data class Deleted(val key: String?, val ids: List<Int>) : ChatEvent

    data class ReadOutbox(val key: String, val maxId: Int) : ChatEvent

    data class ReadInbox(val key: String, val maxId: Int, val unread: Int) : ChatEvent

    data class Pinned(val key: String, val pinned: Boolean) : ChatEvent

    data class PinnedOrder(val archived: Boolean, val keys: List<String>?) : ChatEvent

    data class UnreadMark(val key: String, val marked: Boolean) : ChatEvent

    data class Muted(val key: String, val muteUntil: Int?) : ChatEvent

    data class DefaultMute(val scope: NotifyScope, val muteUntil: Int?) : ChatEvent

    data class Archived(val key: String, val archived: Boolean) : ChatEvent

    data class DialogStale(val key: String) : ChatEvent

    data class Reactions(val key: String, val id: Int, val reactions: List<ReactionCount>, val min: Boolean, val listed: Boolean = false) : ChatEvent

    data class ChatWallpaperChanged(val key: String) : ChatEvent

    data class Draft(val key: String, val draft: cn.ellerotta.core.model.Draft?, val date: Int) : ChatEvent

    data class BotDraft(
        val key: String,
        val topicId: Int?,
        val botId: Long,
        val randomId: Long,
        val message: MessageItem,
        val canStop: Boolean,
        val keepOnStop: Boolean,
    ) : ChatEvent

    data class Ephemeral(val key: String, val message: MessageItem) : ChatEvent

    data class EphemeralDeleted(val key: String, val ids: List<Int>) : ChatEvent

    data class ThreadRead(val key: String, val topId: Int, val maxId: Int, val outbox: Boolean, val postKey: String? = null, val postId: Int? = null) : ChatEvent

    data class PollChanged(val pollId: Long, val results: PollResults, val poll: Poll? = null, val key: String? = null, val id: Int? = null) : ChatEvent

    data class MessagesPinned(val key: String, val ids: List<Int>, val pinned: Boolean) : ChatEvent

    data class Story(val key: String, val story: StoryItem, val hidden: Boolean = false, val min: Boolean = false) : ChatEvent

    data class StoryDeleted(val key: String, val id: Int) : ChatEvent

    data class StoriesRead(val key: String, val maxId: Int) : ChatEvent

    data class StoryReaction(val key: String, val storyId: Int, val reaction: Reaction?) : ChatEvent

    data class Blocked(val key: String, val blocked: Boolean) : ChatEvent

    data class PeerBarChanged(val key: String, val bar: cn.ellerotta.core.model.PeerBar) : ChatEvent

    data class ScheduledNew(val key: String, val message: MessageItem) : ChatEvent

    data class ScheduledDeleted(val key: String, val ids: List<Int>, val sent: List<Int> = emptyList()) : ChatEvent

    data object Reload : ChatEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class UpdatesRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    val updateState: Flow<UpdateState?> = telegram.updates.flatMapLatest { manager -> manager?.state ?: flowOf(null) }

    private val localEvents = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 16)

    fun events(): Flow<ChatEvent> = merge(
        telegram.updates.flatMapLatest { manager -> manager?.events ?: emptyFlow() }.flatMapConcat { event -> eventsOf(event).asFlow() },
        localEvents,
    )

    fun local(event: ChatEvent) {
        localEvents.tryEmit(event)
    }

    fun follow(dialogKey: String, open: Boolean) {
        val id = dialogKey.removePrefix("ch").takeIf { dialogKey.startsWith("ch") }?.toLongOrNull() ?: return
        val manager = telegram.updates.value ?: return
        if (open) manager.openChannel(id) else manager.closeChannel(id)
    }

    private fun eventsOf(event: UpdateEvent): List<ChatEvent> = when (event) {
        is UpdateEvent.Batch -> {
            registry.rememberUsers(event.users)
            registry.rememberChats(event.chats)
            event.updates.flatMap(::eventsOf).distinct()
        }
        UpdateEvent.Resync -> listOf(ChatEvent.DialogsChanged, ChatEvent.Reload)
        is UpdateEvent.ChannelResync -> listOf(ChatEvent.DialogStale("ch${event.channelId}"), ChatEvent.Reload)
    }

    private fun eventsOf(update: TypeUpdate): List<ChatEvent> = when (update) {
        is UpdateUserTyping -> if (isDraft(update.action)) botDraft("u${update.userId}", update.topMsgId, update.userId, update.action) else listOf(ChatEvent.Typing("u${update.userId}", if (update.action is SendMessageCancelAction) null else "", activityOf(update.action), update.userId))
        is UpdateChatUserTyping -> if (isDraft(update.action)) botDraft("c${update.chatId}", null, (update.fromId as? PeerUser)?.userId, update.action) else listOf(ChatEvent.Typing("c${update.chatId}", typer(update.fromId, update.action), activityOf(update.action), (update.fromId as? PeerUser)?.userId ?: 0))
        is UpdateChannelUserTyping -> if (isDraft(update.action)) {
            botDraft("ch${update.channelId}", update.topMsgId, (update.fromId as? PeerUser)?.userId, update.action)
        } else {
            listOf(ChatEvent.Typing("ch${update.channelId}", typer(update.fromId, update.action), activityOf(update.action), (update.fromId as? PeerUser)?.userId ?: 0))
        }
        is UpdateUserStatus -> listOf(ChatEvent.Status("u${update.userId}", presenceOf(update.status, System.currentTimeMillis() / 1000)))
        is UpdateDialogFilter, is UpdateDialogFilters, is UpdateDialogFilterOrder -> listOf(ChatEvent.FoldersChanged)
        is UpdateNewMessage -> messageEvent(update.message, edited = false)
        is UpdateNewChannelMessage -> messageEvent(update.message, edited = false)
        is UpdateEditMessage -> messageEvent(update.message, edited = true)
        is UpdateEditChannelMessage -> messageEvent(update.message, edited = true)
        is UpdateDeleteMessages -> listOf(ChatEvent.Deleted(null, update.messages))
        is UpdateDeleteChannelMessages -> listOf(ChatEvent.Deleted("ch${update.channelId}", update.messages))
        is UpdateReadHistoryOutbox -> peerKey(update.peer).let { key ->
            registry.readOutbox.merge(key, update.maxId, ::maxOf)
            listOf(ChatEvent.ReadOutbox(key, update.maxId))
        }
        is UpdateReadChannelOutbox -> "ch${update.channelId}".let { key ->
            registry.readOutbox.merge(key, update.maxId, ::maxOf)
            listOf(ChatEvent.ReadOutbox(key, update.maxId))
        }
        is UpdateMessageReactions -> listOf(ChatEvent.Reactions(peerKey(update.peer), update.msgId, registry.reactionsOf(update.reactions), update.reactions.min, update.reactions.canSeeList))
        is UpdateDraftMessage -> if (update.topMsgId != null || update.savedPeerId != null) {
            emptyList()
        } else {
            listOf(ChatEvent.Draft(peerKey(update.peer), savedDraftOf(update.draft, registry), draftDate(update.draft)))
        }
        is UpdateReadHistoryInbox -> if (update.topMsgId != null) emptyList() else listOf(ChatEvent.ReadInbox(peerKey(update.peer), update.maxId, update.stillUnreadCount))
        is UpdateReadChannelInbox -> listOf(ChatEvent.ReadInbox("ch${update.channelId}", update.maxId, update.stillUnreadCount))
        is UpdateDialogPinned -> listOfNotNull((update.peer as? DialogPeer)?.let { ChatEvent.Pinned(peerKey(it.peer), update.pinned) })
        is UpdatePinnedDialogs -> listOf(ChatEvent.PinnedOrder(update.folderId == 1, update.order?.mapNotNull { (it as? DialogPeer)?.peer?.let(::peerKey) }))
        is UpdateDialogUnreadMark -> if (update.savedPeerId != null) emptyList() else listOfNotNull((update.peer as? DialogPeer)?.let { ChatEvent.UnreadMark(peerKey(it.peer), update.unread) })
        is UpdateNotifySettingsUpdate -> when (val peer = update.peer) {
            is NotifyPeer -> peerKey(peer.peer).let { key ->
                registry.rememberMute(key, update.notifySettings)
                listOf(ChatEvent.Muted(key, update.notifySettings.muteUntil))
            }
            NotifyUsers -> defaultMute(NotifyScope.Users, update.notifySettings)
            NotifyChats -> defaultMute(NotifyScope.Chats, update.notifySettings)
            NotifyBroadcasts -> defaultMute(NotifyScope.Broadcasts, update.notifySettings)
            else -> emptyList()
        }
        is UpdateFolderPeers -> update.folderPeers.map { ChatEvent.Archived(peerKey(it.peer), it.folderId == 1) }
        is UpdateNewEphemeralMessage -> ephemeralEvent(update.message)
        is UpdateEditEphemeralMessage -> ephemeralEvent(update.message)
        is UpdateDeleteEphemeralMessages -> listOf(ChatEvent.EphemeralDeleted(peerKey(update.peer), update.ids))
        is UpdateReadChannelDiscussionInbox -> listOf(
            ChatEvent.ThreadRead("ch${update.channelId}", update.topMsgId, update.readMaxId, outbox = false, update.broadcastId?.let { "ch$it" }, update.broadcastPost),
        )
        is UpdateReadChannelDiscussionOutbox -> listOf(ChatEvent.ThreadRead("ch${update.channelId}", update.topMsgId, update.readMaxId, outbox = true))
        is UpdateMessagePoll -> listOf(
            ChatEvent.PollChanged(update.pollId, registry.pollResultsOf(update.results), update.poll?.let { registry.pollOf(it, update.results) }, update.peer?.let(::peerKey), update.msgId),
        )
        is UpdatePinnedMessages -> listOf(ChatEvent.MessagesPinned(peerKey(update.peer), update.messages, update.pinned))
        is UpdatePinnedChannelMessages -> listOf(ChatEvent.MessagesPinned("ch${update.channelId}", update.messages, update.pinned))
        is UpdateStory -> storyEvent(peerKey(update.peer), update.story)
        is UpdateReadStories -> listOf(ChatEvent.StoriesRead(peerKey(update.peer), update.maxId))
        is UpdateSentStoryReaction -> listOf(ChatEvent.StoryReaction(peerKey(update.peer), update.storyId, reactionOf(update.reaction)))
        is UpdatePeerBlocked -> peerKey(update.peerId).let { key ->
            registry.blocked[key] = update.blocked
            listOf(ChatEvent.Blocked(key, update.blocked))
        }
        is UpdatePeerSettings -> listOf(ChatEvent.PeerBarChanged(peerKey(update.peer), barOf(update.settings)))
        is UpdatePeerWallpaper -> peerKey(update.peer).let { key ->
            registry.rememberChatWallpaper(key, update.wallpaper, update.wallpaperOverridden)
            listOf(ChatEvent.ChatWallpaperChanged(key))
        }
        is UpdateNewScheduledMessage -> keyOf(update.message)?.let { key -> registry.itemOf(update.message, key)?.let { listOf(ChatEvent.ScheduledNew(key, it)) } } ?: emptyList()
        is UpdateDeleteScheduledMessages -> listOf(ChatEvent.ScheduledDeleted(peerKey(update.peer), update.messages, update.sentMessages.orEmpty()))
        else -> emptyList()
    }

    private fun messageEvent(message: TypeMessage, edited: Boolean): List<ChatEvent> {
        val key = keyOf(message) ?: return emptyList()
        val item = registry.itemOf(message, key) ?: return emptyList()
        val preview = registry.rowPreviewOf(message)
        if (edited) return listOf(ChatEvent.Edited(key, item, preview))
        registry.topMessages.merge(key, item.id, ::maxOf)
        val sent = message as? Message
        val mention = sent?.let { it.mentioned && it.mediaUnread } ?: false
        return listOf(ChatEvent.NewMessage(key, item, preview, mention, silent = sent?.silent == true, post = sent?.post == true))
    }

    private fun storyEvent(key: String, story: TypeStoryItem): List<ChatEvent> = when (story) {
        is StoryItemDeleted -> listOf(ChatEvent.StoryDeleted(key, story.id))
        else -> listOfNotNull(registry.storyOf(key, story)?.let { ChatEvent.Story(key, it, hidden = key in registry.storiesHidden, min = (story as? TlStoryItem)?.min == true) })
    }

    private fun draftDate(draft: TypeDraftMessage): Int = when (draft) {
        is DraftMessage -> draft.date
        is DraftMessageEmpty -> draft.date ?: 0
        else -> 0
    }

    private fun defaultMute(scope: NotifyScope, settings: PeerNotifySettings): List<ChatEvent> {
        registry.rememberDefaultMute(scope, settings.muteUntil ?: 0)
        return listOf(ChatEvent.DefaultMute(scope, settings.muteUntil))
    }

    private fun ephemeralEvent(message: EphemeralMessage): List<ChatEvent> =
        listOfNotNull(registry.ephemeralOf(message, telegram.requireClient().userId)?.let { (key, item) -> ChatEvent.Ephemeral(key, item) })

    private fun typer(from: TypePeer, action: TypeSendMessageAction): String? =
        if (action is SendMessageCancelAction) null else (from as? PeerUser)?.let { registry.userNames[it.userId] }.orEmpty()

    private fun isDraft(action: TypeSendMessageAction) =
        action is SendMessageTextDraftAction || action is SendMessageRichMessageDraftAction || action is SendMessageStopDraftAction

    private fun botDraft(key: String, topicId: Int?, from: Long?, action: TypeSendMessageAction): List<ChatEvent> {
        val bot = from ?: return emptyList()
        val message = MessageItem(
            id = 0,
            text = "",
            out = false,
            date = (System.currentTimeMillis() / 1000).toInt(),
            senderName = if (key.startsWith("u")) null else registry.userNames[bot],
            senderId = bot,
            topicId = topicId,
            streaming = true,
        )
        val event = when (action) {
            is SendMessageTextDraftAction -> ChatEvent.BotDraft(
                key, topicId, bot, action.randomId,
                message.copy(text = action.text.text, entities = entitiesOf(action.text.text, action.text.entities)),
                action.canStop, action.keepOnStop,
            )
            is SendMessageRichMessageDraftAction -> ChatEvent.BotDraft(
                key, topicId, bot, action.randomId,
                message.copy(article = articleOf(action.richMessage, registry, null, registry.usernames[bot])),
                action.canStop, action.keepOnStop,
            )
            else -> return emptyList()
        }
        return listOf(event)
    }
}
