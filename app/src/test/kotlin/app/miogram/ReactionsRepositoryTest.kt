package app.miogram

import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.ChatReactions
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.ReactionCount
import cn.ellerotta.ktproto.api.types.ReactionEmoji
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class ReactionsRepositoryTest {
    private val like: Reaction = Reaction.Emoji(FakeTelegram.THUMBS_UP)
    private val heart: Reaction = Reaction.Emoji(FakeTelegram.HEART)
    private val fire: Reaction = Reaction.Emoji(FakeTelegram.FIRE)

    @Test
    fun listsSendsAndFollowsReactions(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val profile = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile
                fake.selfId = profile.id
                val id = fake.addReactedMessage()
                val saved = repos.dialogs.dialogs().dialogs.first { it.isSaved }.key

                val unicorn: Reaction = Reaction.Emoji("\uD83E\uDD84")
                assertEquals(listOf(like, heart, fire, unicorn), repos.reactions.catalog().map { it.reaction })
                assertEquals(listOf(unicorn), repos.reactions.catalog().filter { it.premium }.map { it.reaction })
                assertEquals(listOf(0, FakeTelegram.REACTIONS_HASH), fake.availableReactionsHashes)
                assertEquals(listOf(fire), repos.reactions.top())
                assertEquals(listOf(fire), repos.reactions.top())
                assertEquals(ChatReactions(custom = true), repos.reactions.chatReactions(saved))
                val news = repos.dialogs.search("kotl").single()
                assertEquals(ChatReactions(all = false, some = listOf(like, fire)), repos.reactions.chatReactions(news.key))

                val message = repos.messages.history(saved).single { it.id == id }
                assertEquals(listOf(like, Reaction.Paid, Reaction.CustomEmoji(FakeTelegram.EMOJI_HEART)), message.reactions.map { it.reaction })
                assertEquals(listOf(2, 5, 1), message.reactions.map { it.count })
                assertEquals(listOf("Alice Example", "Bob"), message.reactions.first().recent.map { it.name })
                assertNotNull(message.reactions.first().recent.first().avatarKey)
                assertTrue(message.reactions.first().showsReactors)

                val live = Channel<ChatEvent>(Channel.UNLIMITED)
                val listening = launch { repos.updates.events().collect { live.send(it) } }
                delay(300)
                repos.reactions.react(saved, id, listOf(fire), addToRecent = true)
                val call = assertNotNull(fake.lastSendReaction)
                assertEquals(id, call.msgId)
                assertTrue(call.addToRecent)
                assertEquals(listOf(ReactionEmoji(FakeTelegram.FIRE)), call.reaction)
                val sent = withTimeout(10_000) { nextReactions(live) }
                assertEquals(saved, sent.key)
                assertEquals(id, sent.id)
                assertEquals(ReactionCount(fire, 1, chosen = true), sent.reactions.single { it.reaction == fire }.copy(order = null))

                repos.reactions.react(saved, id, emptyList())
                assertNull(fake.lastSendReaction?.reaction)
                assertTrue(withTimeout(10_000) { nextReactions(live) }.reactions.none { it.chosen })
                listening.cancel()
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun whoReactedAndUnreadReactions(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val profile = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile
                fake.selfId = profile.id
                fake.unreadReactions[fake.alice.id] = listOf(7, 3, 5)
                val id = fake.addReactedMessage()
                val dialogs = repos.dialogs.dialogs().dialogs
                val saved = dialogs.first { it.isSaved }.key
                val alice = dialogs.first { it.peerId == fake.alice.id }

                assertEquals(3, alice.reactions)
                assertTrue(repos.messages.history(saved).single { it.id == id }.reactorsListed)

                val first = repos.reactions.reactors(saved, id)
                assertEquals(2, first.count)
                assertEquals(listOf("Alice Example" to like), first.list.map { it.reactor.name to it.reaction })
                assertEquals("u${fake.alice.id}", first.list.single().key)
                assertNotNull(first.list.single().reactor.avatarKey)
                val second = repos.reactions.reactors(saved, id, first.next)
                assertEquals(listOf("Bob"), second.list.map { it.reactor.name })
                assertNull(second.next)
                assertEquals(listOf(id to null, id to FakeTelegram.REACTORS_NEXT), fake.reactorsCalls.toList())

                assertEquals(listOf(3, 5, 7), repos.reactions.unread(alice.key))
                assertEquals(listOf(5, 7), repos.reactions.unread(alice.key, after = 3))
                repos.reactions.readAll(alice.key)
                assertEquals(listOf(fake.alice.id), fake.readReactionsCalls.toList())
                assertEquals(emptyList(), repos.reactions.unread(alice.key))
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}

private suspend fun nextReactions(events: Channel<ChatEvent>): ChatEvent.Reactions {
    while (true) {
        val event = events.receive()
        if (event is ChatEvent.Reactions) return event
    }
}
