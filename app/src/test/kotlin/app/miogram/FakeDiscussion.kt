package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.messages.GetDiscussionMessage
import cn.ellerotta.ktproto.api.functions.messages.GetReplies
import cn.ellerotta.ktproto.api.functions.messages.ReadDiscussion
import cn.ellerotta.ktproto.api.functions.messages.SendMessage
import cn.ellerotta.ktproto.api.functions.updates.GetChannelDifference
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.ChatPhotoEmpty
import cn.ellerotta.ktproto.api.types.Dialog
import cn.ellerotta.ktproto.api.types.InputChannel
import cn.ellerotta.ktproto.api.types.InputPeerChannel
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageFwdHeader
import cn.ellerotta.ktproto.api.types.MessageReplies
import cn.ellerotta.ktproto.api.types.MessageReplyHeader
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerNotifySettings
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.TypeUpdate
import cn.ellerotta.ktproto.api.types.UpdateEditChannelMessage
import cn.ellerotta.ktproto.api.types.UpdateMessageID
import cn.ellerotta.ktproto.api.types.UpdateNewChannelMessage
import cn.ellerotta.ktproto.api.types.UpdateReadChannelDiscussionInbox
import cn.ellerotta.ktproto.api.types.UpdateReadChannelDiscussionOutbox
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.messages.ChannelMessages
import cn.ellerotta.ktproto.api.types.messages.DiscussionMessage
import cn.ellerotta.ktproto.api.types.updates.ChannelDifference
import cn.ellerotta.ktproto.api.types.updates.ChannelDifferenceEmpty
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakeDiscussion(private val fake: FakeTelegram) {
    @Volatile var inDialogs = false

    val readInbox: MutableMap<Int, Int> = ConcurrentHashMap(mapOf(TOP to 101, QUESTION to 103))
    val readOutbox: MutableMap<Int, Int> = ConcurrentHashMap()

    val discussionCalls: MutableList<GetDiscussionMessage> = CopyOnWriteArrayList()
    val replyCalls: MutableList<GetReplies> = CopyOnWriteArrayList()
    val sendCalls: MutableList<SendMessage> = CopyOnWriteArrayList()
    val readCalls: MutableList<ReadDiscussion> = CopyOnWriteArrayList()

    private val pts = ConcurrentHashMap(mapOf(BLOG_ID to START_PTS, CHAT_ID to START_PTS))
    private val changes = CopyOnWriteArrayList<Triple<Long, Int, TypeUpdate>>()
    private var nextId = 200

    private val ownPosts = CopyOnWriteArrayList<Message>()

    fun blog() = Channel(broadcast = true, id = BLOG_ID, accessHash = BLOG_HASH, title = "Kotlin blog", username = "kotlinblog", photo = ChatPhotoEmpty, date = NOW, participantsCount = 300)

    fun chat() = Channel(megagroup = true, id = CHAT_ID, accessHash = CHAT_HASH, title = "Kotlin blog chat", username = "kotlinblogchat", photo = ChatPhotoEmpty, date = NOW, participantsCount = 40)

    val chatMessages: MutableList<Message> = CopyOnWriteArrayList(
        listOf(
            Message(
                id = TOP, fromId = PeerChannel(BLOG_ID), peerId = PeerChannel(CHAT_ID), date = NOW - 900, message = "post 3",
                fwdFrom = MessageFwdHeader(fromId = PeerChannel(BLOG_ID), date = NOW - 900, channelPost = 3, savedFromPeer = PeerChannel(BLOG_ID), savedFromMsgId = 3),
            ),
            comment(101, fake.alice, "great post", TOP),
            comment(102, fake.bob, "agreed", TOP),
            Message(id = QUESTION, fromId = PeerUser(fake.alice.id), peerId = PeerChannel(CHAT_ID), date = NOW - 300, message = "who reads the blog"),
            comment(104, fake.bob, "me", QUESTION),
        ),
    )

    private fun comment(id: Int, from: User, text: String, top: Int, replyTo: Int = top, out: Boolean = false) = Message(
        out = out, id = id, fromId = PeerUser(from.id), peerId = PeerChannel(CHAT_ID), date = NOW - 600 + id, message = text,
        replyTo = MessageReplyHeader(replyToMsgId = replyTo, replyToTopId = top.takeIf { it != replyTo }),
    )

    private fun threadOf(message: Message): Int? = (message.replyTo as? MessageReplyHeader)?.let { it.replyToTopId ?: it.replyToMsgId }

    private fun repliesIn(top: Int) = chatMessages.filter { threadOf(it) == top }

    private fun replies(top: Int, comments: Boolean): MessageReplies {
        val all = repliesIn(top)
        return MessageReplies(
            comments = comments, replies = all.size, repliesPts = 1, recentRepliers = all.takeLast(3).mapNotNull { it.fromId }.distinct().reversed(),
            channelId = if (comments) CHAT_ID else null, maxId = all.maxOfOrNull { it.id }, readMaxId = readInbox[top],
        )
    }

    fun posts(): List<Message> = (1..3).map { id ->
        Message(post = true, id = id, peerId = PeerChannel(BLOG_ID), date = NOW - 1_000 + id, message = "post $id", replies = replies(if (id == 3) TOP else -id, comments = true))
    }

    private fun withReplies(message: Message) = if (repliesIn(message.id).isEmpty()) message else Message(
        out = message.out, id = message.id, fromId = message.fromId, peerId = message.peerId, date = message.date, message = message.message, fwdFrom = message.fwdFrom,
        replyTo = message.replyTo, replies = replies(message.id, comments = false),
    )

    private val users get() = listOf(User(self = true, id = fake.selfId, accessHash = 1, firstName = "Test", lastName = "User"), fake.alice, fake.bob)

    private fun isChat(peer: TypeInputPeer) = peer is InputPeerChannel && peer.channelId == CHAT_ID

    private fun isBlog(peer: TypeInputPeer) = peer is InputPeerChannel && peer.channelId == BLOG_ID

    fun dialogs(): Triple<List<Dialog>, List<Message>, List<Channel>> {
        if (!inDialogs) return Triple(emptyList(), emptyList(), emptyList())
        fun dialog(id: Long, top: Int) = Dialog(
            peer = PeerChannel(id), topMessage = top, readInboxMaxId = top, readOutboxMaxId = 0, unreadCount = 0, unreadMentionsCount = 0, unreadReactionsCount = 0,
            unreadPollVotesCount = 0, notifySettings = PeerNotifySettings(muteUntil = fake.muteUntilOf(id)), pts = pts[id],
        )
        val top = ownPosts.lastOrNull() ?: posts().last()
        return Triple(listOf(dialog(BLOG_ID, top.id), dialog(CHAT_ID, chatMessages.maxOf { it.id })), listOf(top, chatMessages.maxBy { it.id }), listOf(blog(), chat()))
    }

    fun history(peer: TypeInputPeer): Pair<List<Message>, List<Channel>>? = when {
        isBlog(peer) -> posts() + ownPosts to listOf(blog(), chat())
        isChat(peer) -> chatMessages.map(::withReplies).sortedBy { it.id } to listOf(blog(), chat())
        else -> null
    }

    fun replies(call: GetReplies): FakeAnswer? {
        if (!isChat(call.peer)) return null
        replyCalls += call
        val all = repliesIn(call.msgId).sortedByDescending { it.id }
        val first = if (call.offsetId == 0) 0 else all.indexOfFirst { it.id < call.offsetId }.let { if (it < 0) all.size else it }
        val start = first + call.addOffset
        val window = all.subList(start.coerceIn(0, all.size), (start + call.limit).coerceIn(0, all.size)).filter { call.minId == 0 || it.id > call.minId }
        return FakeAnswer.Result(ChannelMessages(pts = pts.getValue(CHAT_ID), count = all.size, messages = window, topics = emptyList(), chats = listOf(blog(), chat()), users = users))
    }

    fun read(call: ReadDiscussion): Boolean {
        if (!isChat(call.peer)) return false
        readCalls += call
        readInbox.merge(call.msgId, call.readMaxId, ::maxOf)
        return true
    }

    fun send(call: SendMessage): FakeAnswer? {
        if (!isChat(call.peer)) return null
        sendCalls += call
        val reply = call.replyTo as? InputReplyToMessage
        val message = comment(nextId++, users.first(), call.message, reply?.topMsgId ?: reply?.replyToMsgId ?: 0, reply?.replyToMsgId ?: 0, out = true).let {
            if (reply == null) Message(out = true, id = it.id, fromId = it.fromId, peerId = it.peerId, date = fake.sentDate(), message = call.message) else it
        }
        chatMessages += message
        return FakeAnswer.Result(stored(CHAT_ID, UpdateNewChannelMessage(message, 0, 1), listOf(UpdateMessageID(message.id, call.randomId))))
    }

    fun posted(channel: Long, message: Message): UpdateNewChannelMessage? {
        when (channel) {
            BLOG_ID -> ownPosts += message
            CHAT_ID -> chatMessages += message
            else -> return null
        }
        return stored(channel, UpdateNewChannelMessage(message, 0, 1)).updates.last() as UpdateNewChannelMessage
    }

    suspend fun pushComment(text: String, from: User = fake.alice): Message {
        val message = comment(nextId++, from, text, TOP)
        chatMessages += message
        fake.cluster.updates.push(stored(CHAT_ID, UpdateNewChannelMessage(message, 0, 1)))
        return message
    }

    suspend fun pushPostEdit() {
        fake.cluster.updates.push(stored(BLOG_ID, UpdateEditChannelMessage(posts().last(), 0, 1)))
    }

    suspend fun pushReadInbox(maxId: Int) {
        readInbox.merge(TOP, maxId, ::maxOf)
        fake.cluster.updates.push(Updates(listOf(UpdateReadChannelDiscussionInbox(CHAT_ID, TOP, maxId, BLOG_ID, 3)), users, listOf(blog(), chat()), NOW, 0))
    }

    suspend fun pushReadOutbox(maxId: Int) {
        readOutbox.merge(TOP, maxId, ::maxOf)
        fake.cluster.updates.push(Updates(listOf(UpdateReadChannelDiscussionOutbox(CHAT_ID, TOP, maxId)), users, listOf(blog(), chat()), NOW, 0))
    }

    private fun stored(channel: Long, update: TypeUpdate, before: List<TypeUpdate> = emptyList()): Updates = synchronized(this) {
        val next = pts.getValue(channel) + 1
        pts[channel] = next
        val numbered = when (update) {
            is UpdateNewChannelMessage -> UpdateNewChannelMessage(update.message, next, 1)
            is UpdateEditChannelMessage -> UpdateEditChannelMessage(update.message, next, 1)
            else -> update
        }
        changes += Triple(channel, next, numbered)
        Updates(before + numbered, users, listOf(blog(), chat()), NOW, 0)
    }

    fun difference(call: GetChannelDifference): FakeAnswer? = synchronized(this) {
        val channel = (call.channel as? InputChannel)?.channelId?.takeIf { it == BLOG_ID || it == CHAT_ID } ?: return null
        val now = pts.getValue(channel)
        if (call.pts > now) return FakeAnswer.Error(400, "PERSISTENT_TIMESTAMP_INVALID")
        val missing = changes.filter { it.first == channel && it.second > call.pts }.map { it.third }
        val answer = if (missing.isEmpty()) {
            ChannelDifferenceEmpty(final = true, pts = now, timeout = FakeForum.POLL_TIMEOUT)
        } else {
            ChannelDifference(
                final = true, timeout = FakeForum.POLL_TIMEOUT, pts = now,
                newMessages = missing.filterIsInstance<UpdateNewChannelMessage>().map { it.message },
                otherUpdates = missing.filterIsInstance<UpdateEditChannelMessage>(), chats = listOf(blog(), chat()), users = users,
            )
        }
        FakeAnswer.Result(answer)
    }

    fun register(server: FakeTelegramServer) {
        server.rpc.on(GetDiscussionMessage.CONSTRUCTOR_ID) { request ->
            val call = GetDiscussionMessage.decode(request)
            discussionCalls += call
            val top = when {
                isBlog(call.peer) && call.msgId == 3 -> TOP
                isChat(call.peer) && chatMessages.any { it.id == call.msgId } -> call.msgId
                else -> return@on FakeAnswer.Error(400, "MSG_ID_INVALID")
            }
            val all = repliesIn(top)
            val answer = DiscussionMessage(
                messages = chatMessages.filter { it.id == top }.map(::withReplies), maxId = all.maxOfOrNull { it.id }, readInboxMaxId = readInbox[top],
                readOutboxMaxId = readOutbox[top], unreadCount = all.count { !it.out && it.id > (readInbox[top] ?: 0) }, chats = listOf(blog(), chat()), users = users,
            )
            FakeAnswer.Result(answer)
        }
    }

    companion object {
        const val BLOG_ID = 9_011L
        const val BLOG_HASH = 21L
        const val CHAT_ID = 9_012L
        const val CHAT_HASH = 22L
        const val TOP = 100
        const val QUESTION = 103
        const val START_PTS = 60
    }
}
