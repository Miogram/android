package app.miogram

import app.miogram.FakeDiscussion.Companion.BLOG_ID
import app.miogram.FakeDiscussion.Companion.CHAT_ID
import app.miogram.FakeDiscussion.Companion.QUESTION
import app.miogram.FakeDiscussion.Companion.TOP
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel as KotlinChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class ThreadsRepositoryTest {
    private fun withRepos(block: suspend (FakeTelegram, TestRepositories) -> Unit): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            fake.groups.discussion.inDialogs = true
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                repos.dialogs.dialogs()
                block(fake, repos)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun commentsOpenPageSendReadAndFollow() = withRepos { fake, repos ->
        val discussion = fake.groups.discussion
        val blog = "ch$BLOG_ID"
        val chat = "ch$CHAT_ID"

        val post = repos.messages.history(blog).first { it.id == 3 }
        val replies = assertNotNull(post.replies)
        assertEquals(listOf(2, 1), listOf(replies.count, if (replies.comments) 1 else 0))
        assertEquals(chat, replies.discussionKey)
        assertEquals(listOf(fake.bob.id, fake.alice.id), replies.recent.map { it.peerId })
        assertTrue(replies.unread)
        assertEquals(0, repos.messages.history(blog).first { it.id == 1 }.replies?.count)

        val thread = repos.messages.discussion(blog, 3)
        assertEquals(Triple(chat, TOP, true), Triple(thread.dialogKey, thread.topId, thread.comments))
        assertEquals(listOf("post 3"), thread.messages.map { it.text })
        assertEquals(101 to 1, thread.readInboxMaxId to thread.unread)

        val page = repos.messages.history(chat, topicId = TOP)
        assertEquals(listOf("great post", "agreed"), page.map { it.text })
        assertTrue(page.all { it.threadId == TOP })
        assertEquals(TOP, discussion.replyCalls.last().msgId)

        repos.forum.read(chat, TOP, 102)
        assertEquals(TOP to 102, discussion.readCalls.single().let { it.msgId to it.readMaxId })

        coroutineScope {
            val live = KotlinChannel<ChatEvent>(KotlinChannel.UNLIMITED)
            val listening = launch { repos.updates.events().collect { live.send(it) } }
            delay(300)
            fun reply() = discussion.sendCalls.last().replyTo as InputReplyToMessage
            repos.messages.send(chat, "my comment", topicId = TOP)
            assertEquals(TOP to null, reply().let { it.replyToMsgId to it.topMsgId })
            repos.messages.send(chat, "to alice", replyTo = 101, topicId = TOP)
            assertEquals(101 to TOP, reply().let { it.replyToMsgId to it.topMsgId })
            val sent = withTimeout(10_000) { next<ChatEvent.NewMessage>(live) }
            assertEquals(Triple(chat, "my comment", TOP), Triple(sent.key, sent.message.text, sent.message.threadId))
            val answer = withTimeout(10_000) { next<ChatEvent.NewMessage>(live) }
            assertEquals(Triple(TOP, 101, true), Triple(answer.message.threadId, answer.message.replyTo, answer.message.out))

            discussion.pushComment("late comment")
            assertEquals(TOP to "late comment", withTimeout(10_000) { next<ChatEvent.NewMessage>(live) }.message.let { it.threadId to it.text })
            discussion.pushReadInbox(205)
            val inbox = withTimeout(10_000) { next<ChatEvent.ThreadRead>(live) }
            assertEquals(ChatEvent.ThreadRead(chat, TOP, 205, outbox = false, postKey = blog, postId = 3), inbox)
            discussion.pushReadOutbox(201)
            assertEquals(ChatEvent.ThreadRead(chat, TOP, 201, outbox = true), withTimeout(10_000) { next<ChatEvent.ThreadRead>(live) })
            discussion.pushPostEdit()
            val edited = withTimeout(10_000) { next<ChatEvent.Edited>(live) }
            assertEquals(blog to 5, edited.key to edited.message.replies?.count)
            assertFalse(edited.message.replies?.unread ?: true)
            listening.cancel()
        }

        val question = repos.messages.history(chat).first { it.id == QUESTION }
        val questionReplies = assertNotNull(question.replies)
        assertEquals(1 to false, questionReplies.count to questionReplies.comments)
        assertNull(question.threadId)
        val groupThread = repos.messages.discussion(chat, QUESTION)
        assertEquals(Triple(chat, QUESTION, false), Triple(groupThread.dialogKey, groupThread.topId, groupThread.comments))
        assertEquals(listOf("me"), repos.messages.history(chat, topicId = QUESTION).map { it.text })
    }
}

private suspend inline fun <reified T : ChatEvent> next(events: KotlinChannel<ChatEvent>): T {
    while (true) {
        val event = events.receive()
        if (event is T) return event
    }
}
