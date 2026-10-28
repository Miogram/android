package app.miogram

import app.miogram.FakeTelegram.Companion.EMOJI_STAR
import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.channels.GetMessages as ChannelsGetMessages
import cn.ellerotta.ktproto.api.functions.messages.CreateForumTopic
import cn.ellerotta.ktproto.api.functions.messages.GetForumTopics
import cn.ellerotta.ktproto.api.functions.messages.GetForumTopicsByID
import cn.ellerotta.ktproto.api.functions.messages.GetReplies
import cn.ellerotta.ktproto.api.functions.messages.ReadDiscussion
import cn.ellerotta.ktproto.api.functions.messages.SendMessage
import cn.ellerotta.ktproto.api.functions.updates.GetChannelDifference
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.ChatAdminRights
import cn.ellerotta.ktproto.api.types.ChatPhotoEmpty
import cn.ellerotta.ktproto.api.types.Dialog
import cn.ellerotta.ktproto.api.types.InputChannel
import cn.ellerotta.ktproto.api.types.InputMessageID
import cn.ellerotta.ktproto.api.types.InputPeerChannel
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageActionTopicCreate
import cn.ellerotta.ktproto.api.types.MessageReplyHeader
import cn.ellerotta.ktproto.api.types.MessageService
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerNotifySettings
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.TypeMessage
import cn.ellerotta.ktproto.api.types.UpdateMessageID
import cn.ellerotta.ktproto.api.types.UpdateNewChannelMessage
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.messages.ChannelMessages
import cn.ellerotta.ktproto.api.types.messages.ForumTopics
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.api.types.updates.ChannelDifference
import cn.ellerotta.ktproto.api.types.updates.ChannelDifferenceEmpty
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import cn.ellerotta.ktproto.api.types.ForumTopic as TlForumTopic

class FakeForum(private val fake: FakeTelegram) {
    data class Topic(val id: Int, val title: String, val color: Int = 0x6FB9F0, val emoji: Long? = null, val pinned: Boolean = false, val closed: Boolean = false, val unread: Int = 0)

    val topics: MutableList<Topic> = CopyOnWriteArrayList(
        listOf(
            Topic(1, "General"),
            Topic(RELEASES, "Releases", emoji = EMOJI_STAR, pinned = true, unread = 2),
            Topic(HELP, "Help", color = 0xFB6F5F, closed = true),
        ) + (1..30).map { Topic(1_000 + it, "Topic $it", color = 0x8EEE98) },
    )

    val messages: MutableList<TypeMessage> = CopyOnWriteArrayList(
        listOf<TypeMessage>(
            Message(id = 2, fromId = PeerUser(fake.alice.id), peerId = PeerChannel(FORUM_ID), date = NOW - 600, message = "welcome to the forum"),
            Message(id = 3, fromId = PeerUser(fake.bob.id), peerId = PeerChannel(FORUM_ID), date = NOW - 500, message = "general chatter"),
            created(RELEASES, "Releases", NOW - 400),
            Message(id = 11, fromId = PeerUser(fake.alice.id), peerId = PeerChannel(FORUM_ID), date = NOW - 300, message = "kotlin 2.3 is out", replyTo = inTopic(RELEASES)),
            Message(id = 12, fromId = PeerUser(fake.bob.id), peerId = PeerChannel(FORUM_ID), date = NOW - 200, message = "compose too", replyTo = inTopic(RELEASES)),
            created(HELP, "Help", NOW - 900),
            Message(id = 21, fromId = PeerUser(fake.bob.id), peerId = PeerChannel(FORUM_ID), date = NOW - 800, message = "how do flows work", replyTo = inTopic(HELP)),
        ) + (1..30).map { created(1_000 + it, "Topic $it", NOW - 10_000 - it * 60) },
    )

    val topicCalls: MutableList<GetForumTopics> = CopyOnWriteArrayList()

    val replyCalls: MutableList<GetReplies> = CopyOnWriteArrayList()

    val sendCalls: MutableList<SendMessage> = CopyOnWriteArrayList()

    val createCalls: MutableList<CreateForumTopic> = CopyOnWriteArrayList()

    val readCalls: MutableList<ReadDiscussion> = CopyOnWriteArrayList()

    val read: MutableMap<Int, Int> = ConcurrentHashMap()

    @Volatile var beforeReplies: (suspend () -> Unit)? = null

    @Volatile var pts = START_PTS
        private set

    private var nextId = 5_000

    fun forum() = Channel(
        megagroup = true, forum = true, creator = true, id = FORUM_ID, accessHash = FORUM_HASH, title = "Kotlin forum", username = "kotlinforum", photo = ChatPhotoEmpty, date = NOW,
        participantsCount = 12, adminRights = ChatAdminRights(changeInfo = true, deleteMessages = true, banUsers = true, inviteUsers = true, pinMessages = true, manageTopics = true),
    )

    fun dialog(): Pair<Dialog, TypeMessage> {
        val top = messages.maxWith(compareBy<TypeMessage>(::dateOf).thenBy(::idOf))
        val unread = topics.sumOf { it.unread }
        val dialog = Dialog(
            peer = PeerChannel(FORUM_ID), topMessage = idOf(top), readInboxMaxId = 0, readOutboxMaxId = 0, unreadCount = unread, unreadMentionsCount = 0, unreadReactionsCount = 0,
            unreadPollVotesCount = 0, notifySettings = PeerNotifySettings(), pts = pts,
        )
        return dialog to top
    }

    private val me get() = User(self = true, id = fake.selfId, accessHash = 1, firstName = "Test", lastName = "User")
    private val users get() = listOf(me, fake.alice, fake.bob)

    fun isForum(peer: TypeInputPeer) = peer is InputPeerChannel && peer.channelId == FORUM_ID

    fun topicOf(message: TypeMessage): Int {
        if (message is MessageService && message.action is MessageActionTopicCreate) return message.id
        val header = (if (message is Message) message.replyTo else (message as? MessageService)?.replyTo) as? MessageReplyHeader
        return if (header?.forumTopic == true) header.replyToTopId ?: header.replyToMsgId ?: 1 else 1
    }

    private fun lastOf(topic: Int) = messages.filter { topicOf(it) == topic }.maxBy(::idOf)

    private fun ordered(): List<Topic> = topics.sortedWith(compareByDescending<Topic> { it.pinned }.thenByDescending { dateOf(lastOf(it.id)) })

    private fun tl(topic: Topic) = TlForumTopic(
        closed = topic.closed, pinned = topic.pinned, id = topic.id, date = NOW - 100_000, peer = PeerChannel(FORUM_ID), title = topic.title, iconColor = topic.color,
        iconEmojiId = topic.emoji, topMessage = idOf(lastOf(topic.id)), readInboxMaxId = read[topic.id] ?: 0, readOutboxMaxId = 0,
        unreadCount = if ((read[topic.id] ?: 0) >= idOf(lastOf(topic.id))) 0 else topic.unread, unreadMentionsCount = 0, unreadReactionsCount = 0, unreadPollVotesCount = 0,
        fromId = PeerUser(fake.selfId), notifySettings = PeerNotifySettings(),
    )

    private fun answer(page: List<Topic>) = ForumTopics(
        count = topics.size, topics = page.map(::tl), messages = page.map { lastOf(it.id) }.distinctBy(::idOf), chats = listOf(forum()), users = users, pts = pts,
    )

    fun send(call: SendMessage): FakeAnswer? {
        if (!isForum(call.peer)) return null
        sendCalls += call
        val reply = call.replyTo as? InputReplyToMessage
        val topic = reply?.topMsgId ?: reply?.replyToMsgId?.takeIf { id -> topics.any { it.id == id } }
        val header = when {
            topic != null && reply?.replyToMsgId != topic -> MessageReplyHeader(forumTopic = true, replyToMsgId = reply?.replyToMsgId, replyToTopId = topic)
            topic != null -> MessageReplyHeader(forumTopic = true, replyToMsgId = topic)
            reply != null -> MessageReplyHeader(replyToMsgId = reply.replyToMsgId)
            else -> null
        }
        val message = Message(out = true, id = nextId++, fromId = PeerUser(fake.selfId), peerId = PeerChannel(FORUM_ID), date = NOW, message = call.message, replyTo = header)
        return FakeAnswer.Result(stored(message, listOf(UpdateMessageID(message.id, call.randomId))))
    }

    suspend fun push(topic: Int, text: String, from: User = fake.bob): Message {
        val message = Message(
            id = nextId++, fromId = PeerUser(from.id), peerId = PeerChannel(FORUM_ID), date = NOW, message = text,
            replyTo = if (topic == 1) null else inTopic(topic),
        )
        fake.cluster.updates.push(stored(message))
        return message
    }

    fun posted(channel: Long, message: Message): UpdateNewChannelMessage? =
        if (channel == FORUM_ID) stored(message).updates.last() as UpdateNewChannelMessage else null

    private val changes = CopyOnWriteArrayList<Pair<Int, TypeMessage>>()

    private fun stored(message: TypeMessage, before: List<cn.ellerotta.ktproto.api.types.TypeUpdate> = emptyList()): Updates = synchronized(this) {
        messages += message
        pts += 1
        changes += pts to message
        Updates(before + UpdateNewChannelMessage(message, pts, 1), users, listOf(forum()), NOW, 0)
    }

    private fun difference(call: GetChannelDifference): FakeAnswer = synchronized(this) {
        if (call.pts > pts) return FakeAnswer.Error(400, "PERSISTENT_TIMESTAMP_INVALID")
        val missing = changes.filter { it.first > call.pts }.map { it.second }
        val answer = if (missing.isEmpty()) {
            ChannelDifferenceEmpty(final = true, pts = pts, timeout = POLL_TIMEOUT)
        } else {
            ChannelDifference(final = true, timeout = POLL_TIMEOUT, pts = pts, newMessages = missing, otherUpdates = emptyList(), chats = listOf(forum()), users = users)
        }
        FakeAnswer.Result(answer)
    }

    fun register(server: FakeTelegramServer) {
        server.rpc.on(GetChannelDifference.CONSTRUCTOR_ID) { request ->
            val call = GetChannelDifference.decode(request)
            fake.groups.discussion.difference(call)?.let { return@on it }
            if ((call.channel as? InputChannel)?.channelId != FORUM_ID) return@on FakeAnswer.Error(400, "CHANNEL_INVALID")
            difference(call)
        }
        server.rpc.on(GetForumTopics.CONSTRUCTOR_ID) { request ->
            val call = GetForumTopics.decode(request)
            if (!isForum(call.peer)) return@on FakeAnswer.Error(400, "CHANNEL_FORUM_MISSING")
            topicCalls += call
            val all = ordered()
            val start = if (call.offsetTopic == 0) 0 else all.indexOfFirst { it.id == call.offsetTopic } + 1
            FakeAnswer.Result(answer(all.drop(start).take(call.limit)))
        }
        server.rpc.on(GetForumTopicsByID.CONSTRUCTOR_ID) { request ->
            val call = GetForumTopicsByID.decode(request)
            if (!isForum(call.peer)) return@on FakeAnswer.Error(400, "CHANNEL_FORUM_MISSING")
            FakeAnswer.Result(answer(topics.filter { it.id in call.topics }))
        }
        server.rpc.on(GetReplies.CONSTRUCTOR_ID) { request ->
            val call = GetReplies.decode(request)
            fake.groups.discussion.replies(call)?.let { return@on it }
            if (!isForum(call.peer)) return@on FakeAnswer.Error(400, "MSG_ID_INVALID")
            replyCalls += call
            val all = messages.filter { topicOf(it) == call.msgId }.sortedByDescending(::idOf)
            val first = if (call.offsetId == 0) 0 else all.indexOfFirst { idOf(it) < call.offsetId }.let { if (it < 0) all.size else it }
            val start = first + call.addOffset
            val window = all.subList(start.coerceIn(0, all.size), (start + call.limit).coerceIn(0, all.size)).filter { call.minId == 0 || idOf(it) > call.minId }
            val answer = ChannelMessages(pts = pts, count = all.size, messages = window, topics = emptyList(), chats = listOf(forum()), users = users)
            beforeReplies?.invoke()
            FakeAnswer.Result(answer)
        }
        server.rpc.on(CreateForumTopic.CONSTRUCTOR_ID) { request ->
            val call = CreateForumTopic.decode(request)
            if (!isForum(call.peer)) return@on FakeAnswer.Error(400, "CHANNEL_FORUM_MISSING")
            createCalls += call
            val id = nextId++
            topics += Topic(id, call.title, color = call.iconColor ?: 0x6FB9F0)
            FakeAnswer.Result(stored(created(id, call.title, NOW, call.iconColor ?: 0x6FB9F0, out = true)))
        }
        server.rpc.on(ReadDiscussion.CONSTRUCTOR_ID) { request ->
            val call = ReadDiscussion.decode(request)
            if (!fake.groups.discussion.read(call)) {
                readCalls += call
                read.merge(call.msgId, call.readMaxId, ::maxOf)
            }
            FakeAnswer.Raw(byteArrayOf(0xb5.toByte(), 0x75, 0x72, 0x99.toByte()))
        }
        server.rpc.on(ChannelsGetMessages.CONSTRUCTOR_ID) { request ->
            val call = ChannelsGetMessages.decode(request)
            val ids = call.id.mapNotNull { (it as? InputMessageID)?.id }
            when ((call.channel as? InputChannel)?.channelId) {
                FORUM_ID -> FakeAnswer.Result(Messages(messages.filter { idOf(it) in ids }, emptyList(), listOf(forum()), users))
                FakeTelegram.NEWS_ID -> FakeAnswer.Result(Messages(fake.newsPosts.filter { it.id in ids }, emptyList(), listOf(fake.news()), emptyList()))
                else -> FakeAnswer.Error(400, "CHANNEL_INVALID")
            }
        }
    }

    fun history(peer: TypeInputPeer): Pair<List<Message>, List<Channel>>? = if (isForum(peer)) messages.filterIsInstance<Message>().sortedBy { it.id } to listOf(forum()) else null

    companion object {
        const val FORUM_ID = 9_007L
        const val FORUM_HASH = 17L
        const val RELEASES = 10
        const val HELP = 20
        const val START_PTS = 40

        const val POLL_TIMEOUT = 30

        fun inTopic(topic: Int) = MessageReplyHeader(forumTopic = true, replyToMsgId = topic)

        fun created(id: Int, title: String, date: Int, color: Int = 0x6FB9F0, out: Boolean = false) =
            MessageService(out = out, id = id, peerId = PeerChannel(FORUM_ID), date = date, action = MessageActionTopicCreate(title = title, iconColor = color))

        private fun idOf(message: TypeMessage) = when (message) {
            is Message -> message.id
            is MessageService -> message.id
            else -> 0
        }

        private fun dateOf(message: TypeMessage) = when (message) {
            is Message -> message.date
            is MessageService -> message.date
            else -> 0
        }
    }
}
