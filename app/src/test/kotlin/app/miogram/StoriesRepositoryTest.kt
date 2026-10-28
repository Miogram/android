package app.miogram

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.MediaFileInfo
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.StoriesRepository
import app.miogram.data.telegram.StoryUpload
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.data.telegram.VideoStream
import cn.ellerotta.core.database.ChatDatabase
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.StoryAudience
import cn.ellerotta.core.model.StoryRing
import cn.ellerotta.core.model.StoryViews
import cn.ellerotta.ktproto.api.LAYER
import cn.ellerotta.ktproto.api.types.DocumentAttributeVideo
import cn.ellerotta.ktproto.api.types.InputMediaUploadedDocument
import cn.ellerotta.ktproto.api.types.InputMediaUploadedPhoto
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowAll
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowCloseFriends
import cn.ellerotta.ktproto.api.types.InputReplyToStory
import cn.ellerotta.ktproto.api.types.ReactionEmoji
import cn.ellerotta.ktproto.api.types.ReactionEmpty
import cn.ellerotta.ktproto.client.UploadSource
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoriesRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun listsStoreActionsUpdatesAndReferences(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val database = ChatDatabase.inMemory(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        FakeTelegram().use { fake ->
            val telegram = TelegramClientHolder(dir, database = database) { id, hash, logger -> fake.config(id, hash, logger) }
            val repos = TestRepositories(dir, fake, telegram)
            withTimeout(60_000) {
                telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                val stories = repos.stories
                val calls = fake.stories.allCalls
                val self = "u${fake.selfId}"
                val alice = "u${fake.alice.id}"
                val carol = "u${fake.carol.id}"
                val bob = "u${fake.bob.id}"
                stories.stored()
                assertEquals(emptyList(), stories.stories.value)

                stories.load()
                assertEquals(listOf(null to false, "main-1-1" to true), calls.map { it.state to it.next })
                assertFalse(calls.any { it.hidden })
                assertEquals(listOf(self, alice), stories.stories.value.map { it.key })
                val own = assertNotNull(stories.self.value)
                assertTrue(own.self)
                assertEquals(StoryViews(views = 2, reactions = 1, recent = listOf("55"), hasViewers = true), own.stories.single().views)
                assertEquals("own story", own.stories.single().caption)
                val aliceStories = stories.stories.value[1]
                assertEquals("Alice Example", aliceStories.name)
                assertEquals("55", aliceStories.avatarKey)
                assertEquals(listOf(1, 2), aliceStories.stories.map { it.id })
                assertNull(aliceStories.stories[0].media)
                assertNotNull(aliceStories.stories[1].media)
                assertTrue(aliceStories.stories[1].closeFriends)
                assertEquals(1, aliceStories.startIndex)
                assertEquals(mapOf(self to StoryRing.Read, alice to StoryRing.CloseFriends), stories.rings.value)

                stories.load()
                assertEquals("main-1-2" to false, calls.last().let { it.state to it.next })
                assertEquals(listOf(self, alice), stories.stories.value.map { it.key })

                stories.load(hidden = true)
                assertTrue(calls.last().hidden)
                assertNull(calls.last().state)
                val carolStories = stories.hidden.value.single()
                assertEquals(carol, carolStories.key)
                assertTrue(carolStories.hidden)
                assertTrue(carolStories.stories.single().isVideo)
                assertEquals(5_000L, carolStories.stories.single().durationMillis)
                assertEquals(StoryRing.Unread, stories.rings.value[carol])

                val carolVideo = assertNotNull(carolStories.stories.single().media)
                repos.media.prefetch(carolVideo)
                assertEquals(1, fake.stories.downloads.count { it == FakeStories.CAROL_VIDEO })
                assertContentEquals(fake.stories.videoBytes.copyOf(1_000), readStream(repos.media.stream(carolVideo), 1_000))
                assertEquals(1, fake.stories.downloads.count { it == FakeStories.CAROL_VIDEO })
                repos.media.clearStreams()
                assertContentEquals(fake.stories.videoBytes.copyOf(1_000), readStream(repos.media.stream(carolVideo), 1_000))
                assertEquals(2, fake.stories.downloads.count { it == FakeStories.CAROL_VIDEO })

                stories.fill(alice, listOf(1))
                assertEquals(listOf(1), fake.stories.byIdCalls.last())
                assertNotNull(stories.stories.value.first { it.key == alice }.stories[0].media)

                val restarted = StoriesRepository(telegram, repos.updates, scope)
                restarted.stored()
                assertEquals(listOf(self, alice), restarted.stories.value.map { it.key })
                assertEquals(listOf(carol), restarted.hidden.value.map { it.key })
                assertNotNull(restarted.stories.value.first { it.key == alice }.stories[0].media)
                assertEquals(stories.rings.value, restarted.rings.value)
                restarted.load()
                assertEquals("main-1-2" to false, calls.last().let { it.state to it.next })
                assertEquals(listOf(self, alice), restarted.stories.value.map { it.key })

                stories.read(alice, 2)
                assertEquals(listOf(fake.alice.id to 2), fake.stories.reads)
                assertEquals(StoryRing.Read, stories.rings.value[alice])
                assertEquals(2, assertNotNull(database.chats().storiesOf(alice, LAYER)).maxRead)

                stories.view(alice, (1..450).toList())
                assertEquals(listOf(200, 200, 50), fake.stories.views.map { it.second.size })
                assertEquals(fake.alice.id, fake.stories.views.first().first)

                stories.react(alice, 2, Reaction.Emoji(FakeTelegram.HEART))
                val reacted = fake.stories.reactions.last()
                assertEquals(2, reacted.storyId)
                assertEquals(FakeTelegram.HEART, assertIs<ReactionEmoji>(reacted.reaction).emoticon)
                assertEquals(Reaction.Emoji(FakeTelegram.HEART), stories.stories.value.first { it.key == alice }.stories[1].reaction)
                stories.react(alice, 2, null)
                assertEquals(ReactionEmpty, fake.stories.reactions.last().reaction)
                withTimeout(10_000) { stories.stories.first { list -> list.first { it.key == alice }.stories[1].reaction == null } }

                stories.reply(alice, 2, "nice")
                val reply = fake.stories.replies.single()
                assertEquals("nice", reply.message)
                assertEquals(fake.alice.id, assertIs<InputPeerUser>(reply.peer).userId)
                val replyTo = assertIs<InputReplyToStory>(reply.replyTo)
                assertEquals(2, replyTo.storyId)
                assertEquals(fake.alice.id, assertIs<InputPeerUser>(replyTo.peer).userId)

                val first = stories.viewers(1)
                assertEquals(listOf("Alice Example"), first.viewers.map { it.name })
                assertEquals(Reaction.Emoji(FakeTelegram.HEART), first.viewers.single().reaction)
                assertEquals(Triple(2, 2, "1"), Triple(first.views, first.reactions, first.nextOffset))
                val second = stories.viewers(1, offset = "1")
                assertEquals(listOf(fake.bob.id), second.viewers.map { it.peerId })
                assertEquals(Reaction.CustomEmoji(FakeTelegram.EMOJI_STAR), second.viewers.single().reaction)
                assertNull(second.nextOffset)
                assertEquals(InputPeerSelf, fake.stories.viewerCalls.first().peer)
                assertEquals(listOf("", "1"), fake.stories.viewerCalls.map { it.offset })

                assertTrue(stories.refreshCanPost())
                assertTrue(stories.canPost.value)
                fake.stories.canSendError = "PREMIUM_ACCOUNT_REQUIRED"
                assertFalse(stories.refreshCanPost())
                assertFalse(stories.canPost.value)
                fake.stories.canSendError = null
                assertTrue(stories.refreshCanPost())

                val photoProgress = stories.post(StoryUpload("story.jpg", "image/jpeg", UploadSource.of(fake.stories.photoBytes)), "hello", StoryAudience.CloseFriends).toList()
                assertTrue(photoProgress.isNotEmpty())
                val photoPost = fake.stories.sent.last()
                assertIs<InputMediaUploadedPhoto>(photoPost.media)
                assertEquals(InputPeerSelf, photoPost.peer)
                assertEquals(listOf(InputPrivacyValueAllowCloseFriends), photoPost.privacyRules)
                assertEquals("hello", photoPost.caption)
                assertNull(photoPost.period)
                assertEquals(listOf("own story", "hello"), assertNotNull(stories.self.value).stories.map { it.caption })
                fake.stories.canSendError = "STORIES_TOO_MUCH"
                stories.post(StoryUpload("clip.mp4", "video/mp4", UploadSource.of(fake.stories.videoBytes), MediaFileInfo(width = 720, height = 1280, duration = 3.5)), "", StoryAudience.Everybody).toList()
                assertFalse(stories.canPost.value)
                fake.stories.canSendError = null
                val videoPost = fake.stories.sent.last()
                val video = assertIs<InputMediaUploadedDocument>(videoPost.media)
                assertEquals("video/mp4", video.mimeType)
                val attribute = assertIs<DocumentAttributeVideo>(video.attributes.single())
                assertTrue(attribute.supportsStreaming)
                assertEquals(Triple(3.5, 720, 1280), Triple(attribute.duration, attribute.w, attribute.h))
                assertEquals(listOf(InputPrivacyValueAllowAll), videoPost.privacyRules)
                assertNull(videoPost.caption)
                assertTrue(assertNotNull(stories.self.value).stories.last().isVideo)
                assertEquals(3, assertNotNull(stories.self.value).stories.size)

                stories.setHidden(alice, true)
                assertEquals(fake.alice.id to true, fake.stories.toggles.last())
                assertEquals(listOf(self), stories.stories.value.map { it.key })
                assertEquals(setOf(alice, carol), stories.hidden.value.map { it.key }.toSet())
                assertTrue(assertNotNull(database.chats().storiesOf(alice, LAYER)).hidden)
                stories.setHidden(alice, false)
                assertEquals(listOf(self, alice), stories.stories.value.map { it.key })

                fake.stories.pushStory(FakeStories.BOB, 1)
                withTimeout(10_000) { stories.rings.first { it[bob] == StoryRing.Unread } }
                assertEquals("Bob", stories.stories.value.first { it.key == bob }.name)
                eventually { database.chats().storiesOf(bob, LAYER) != null }
                fake.stories.pushStory(FakeStories.CAROL, 2)
                withTimeout(10_000) { stories.hidden.first { list -> list.firstOrNull { it.key == carol }?.stories?.size == 2 } }
                fake.stories.pushRead(FakeStories.BOB, 1)
                withTimeout(10_000) { stories.rings.first { it[bob] == StoryRing.Read } }
                eventually { database.chats().storiesOf(bob, LAYER)?.maxRead == 1 }
                fake.stories.pushDeleted(FakeStories.BOB, 1)
                withTimeout(10_000) { stories.rings.first { bob !in it } }
                assertFalse(stories.stories.value.any { it.key == bob })
                eventually { database.chats().storiesOf(bob, LAYER) == null }

                val photo = assertNotNull(stories.stories.value.first { it.key == alice }.stories[1].media)
                fake.stories.reference = byteArrayOf(2)
                assertIs<DownloadState.Done>(repos.media.download(photo).last())
                assertEquals(listOf(2), fake.stories.byIdCalls.last())
                assertEquals(FakeStories.ALICE_PHOTO, fake.stories.downloads.last())
                assertTrue(telegram.log.value.any { "file reference of ${photo.key} refreshed" in it.text })
            }
            telegram.close()
        }
        scope.cancel()
        database.close()
        dir.deleteRecursively()
    }

    @Test
    fun aPeerOpenedOutsideTheListsFollowsReadsAndReactions(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val database = ChatDatabase.inMemory(context)
        FakeTelegram().use { fake ->
            val telegram = TelegramClientHolder(dir, database = database) { id, hash, logger -> fake.config(id, hash, logger) }
            val repos = TestRepositories(dir, fake, telegram)
            withTimeout(60_000) {
                telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                val stories = repos.stories
                val bob = "u${fake.bob.id}"
                repos.dialogs.dialogs()
                fake.stories.addUnlisted(FakeStories.BOB, listOf(1, 2))
                stories.load()
                stories.load(hidden = true)

                val opened = assertNotNull(stories.peer(bob))
                assertEquals(listOf(1, 2), opened.stories.map { it.id })
                assertFalse((stories.stories.value + stories.hidden.value).any { it.key == bob })
                assertEquals(opened, stories.outside.value[bob])
                assertEquals(StoryRing.Unread, stories.rings.value[bob])

                stories.read(bob, 1)
                assertEquals(1, stories.outside.value[bob]?.maxReadId)
                fake.stories.pushRead(FakeStories.BOB, 2)
                withTimeout(10_000) { stories.outside.first { it[bob]?.maxReadId == 2 } }
                assertEquals(StoryRing.Read, stories.rings.value[bob])
                stories.react(bob, 2, Reaction.Emoji(FakeTelegram.HEART))
                assertEquals(Reaction.Emoji(FakeTelegram.HEART), stories.outside.value[bob]?.stories?.last()?.reaction)

                stories.load()
                assertNotNull(stories.outside.value[bob])
                assertFalse(stories.stories.value.any { it.key == bob })
                fake.stories.pushDeleted(FakeStories.BOB, 1)
                fake.stories.pushDeleted(FakeStories.BOB, 2)
                withTimeout(10_000) { stories.outside.first { bob !in it } }
                assertNull(stories.rings.value[bob])
            }
            telegram.close()
        }
        database.close()
        dir.deleteRecursively()
    }

    @Test
    fun ownStoriesAreEditedKeptOnTheProfileAndDeleted(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val database = ChatDatabase.inMemory(context)
        FakeTelegram().use { fake ->
            val telegram = TelegramClientHolder(dir, database = database) { id, hash, logger -> fake.config(id, hash, logger) }
            val repos = TestRepositories(dir, fake, telegram)
            withTimeout(60_000) {
                telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                val stories = repos.stories
                val self = "u${fake.selfId}"
                stories.load()
                val own = assertNotNull(stories.self.value).stories.single()
                assertEquals(StoryAudience.Contacts, own.audience)
                assertFalse(own.pinned)

                stories.pin(listOf(1), true)
                assertEquals(listOf(1) to true, fake.stories.pins.last().let { it.id to it.pinned })
                assertEquals(InputPeerSelf, fake.stories.pins.last().peer)
                assertTrue(assertNotNull(stories.self.value).stories.single().pinned)
                val restarted = StoriesRepository(telegram, repos.updates, CoroutineScope(SupervisorJob() + Dispatchers.Default))
                restarted.stored()
                assertTrue(assertNotNull(restarted.self.value).stories.single().pinned)
                stories.pin(listOf(1), false)
                assertFalse(assertNotNull(stories.self.value).stories.single().pinned)

                stories.edit(1, caption = "changed").toList()
                val captionEdit = fake.stories.edits.last()
                assertEquals(Triple("changed", null, null), Triple(captionEdit.caption, captionEdit.privacyRules, captionEdit.media))
                assertEquals("changed", assertNotNull(stories.self.value).stories.single().caption)
                stories.edit(1, audience = StoryAudience.CloseFriends).toList()
                assertEquals(listOf(InputPrivacyValueAllowCloseFriends), fake.stories.edits.last().privacyRules)
                assertNull(fake.stories.edits.last().caption)
                assertEquals(StoryAudience.CloseFriends, assertNotNull(stories.self.value).stories.single().audience)
                val progress = stories.edit(1, media = StoryUpload("new.jpg", "image/jpeg", UploadSource.of(fake.stories.videoBytes))).toList()
                assertTrue(progress.isNotEmpty())
                assertIs<InputMediaUploadedPhoto>(fake.stories.edits.last().media)
                val photo = assertNotNull(assertNotNull(stories.self.value).stories.single().media)
                assertEquals(fake.stories.videoBytes.size.toLong(), photo.size)
                eventually { database.chats().storiesOf(self, LAYER)?.let { "changed" in String(it.tl, Charsets.UTF_8) } == true }

                fake.stories.canSendError = "STORIES_TOO_MUCH"
                assertFalse(stories.refreshCanPost())
                fake.stories.canSendError = null
                stories.delete(listOf(1))
                assertTrue(stories.canPost.value)
                assertEquals(listOf(listOf(1)), fake.stories.deletes)
                assertNull(stories.self.value)
                assertFalse(stories.stories.value.any { it.key == self })
                assertNull(database.chats().storiesOf(self, LAYER))
            }
            telegram.close()
        }
        database.close()
        dir.deleteRecursively()
    }
}

private suspend fun eventually(check: suspend () -> Boolean) {
    withTimeout(10_000) {
        while (!check()) delay(20)
    }
}

@OptIn(UnstableApi::class)
private suspend fun readStream(stream: VideoStream, count: Int): ByteArray = withContext(Dispatchers.IO) {
    val source = stream.factory.createDataSource()
    try {
        source.open(DataSpec(stream.uri))
        val out = ByteArray(count)
        var at = 0
        while (at < count) {
            val read = source.read(out, at, count - at)
            if (read < 0) break
            at += read
        }
        out.copyOf(at)
    } finally {
        source.close()
    }
}
