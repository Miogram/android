package app.miogram

import androidx.test.core.app.ApplicationProvider
import app.miogram.FakeTelegram.Companion.NOW
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.TelegramClientHolder
import cn.ellerotta.core.database.ChatDatabase
import cn.ellerotta.core.model.PeerBar
import cn.ellerotta.core.model.PeerReportReason
import cn.ellerotta.core.model.ReportOption
import cn.ellerotta.core.model.ReportStep
import cn.ellerotta.core.model.ReportTarget
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.InputReportReasonOther
import cn.ellerotta.ktproto.api.types.InputReportReasonSpam
import cn.ellerotta.ktproto.api.types.PeerSettings
import cn.ellerotta.ktproto.session.RpcException
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SafetyRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun <T> withRepos(block: suspend (TestRepositories, FakeTelegram) -> T): T = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        try {
            FakeTelegram().use { fake ->
                val telegram = TelegramClientHolder(dir, database = ChatDatabase.inMemory(context)) { id, hash, logger -> fake.config(id, hash, logger) }
                val repos = TestRepositories(dir, fake, telegram)
                withTimeout(90_000) {
                    telegram.connect(12345, "hash")
                    fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                    repos.dialogs.dialogs()
                    try {
                        block(repos, fake)
                    } finally {
                        telegram.close()
                    }
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theBlockListPagesAndBlockingChangesIt() = withRepos { repos, fake ->
        val safety = repos.safety
        fake.safety.blocked[fake.bob.id] = NOW - 10
        fake.safety.blocked[fake.carol.id] = NOW - 20
        fake.safety.blocked[fake.contacts.erin.id] = NOW - 30

        val first = safety.blocked(0, limit = 3)
        assertEquals(listOf("u502", "u503", "u506"), first.users.map { it.key })
        assertEquals("Carol Writer", first.users[1].name)
        assertEquals("carol_writer", first.users[1].username)
        assertEquals(NOW - 20, first.users[1].date)
        assertEquals(4, first.total)
        assertTrue(first.more)

        val all = first.plus(safety.blocked(first.read, limit = 3))
        assertEquals(listOf(0, 3), fake.safety.getBlockedCalls.map { it.offset })
        assertEquals(listOf("u502", "u503", "u506", "u507"), all.users.map { it.key })
        assertFalse(all.more)
        assertEquals(4, safety.blockedCount())

        safety.unblock("u502")
        val unblocked = fake.safety.unblocks.single().id as InputPeerUser
        assertEquals(502L to 10L, unblocked.userId to unblocked.accessHash)
        assertEquals(3, safety.blockedCount())
        assertFalse(safety.isBlocked("u502"))

        safety.block("u502")
        assertEquals(502L, (fake.safety.blocks.single().id as InputPeerUser).userId)
        assertTrue(safety.isBlocked("u502"))
        assertTrue(repos.profiles.profile("u502").blocked)
        assertFalse(safety.isBlocked("ch1"))
        val changes = Channel<Unit>(Channel.UNLIMITED)
        coroutineScope {
            val listening = launch { safety.changes.collect { changes.send(Unit) } }
            delay(200)
            safety.unblock("u502")
            withTimeout(10_000) { changes.receive() }
            listening.cancel()
        }
        assertFalse(repos.profiles.profile("u502").blocked)
    }

    @Test
    fun anotherDevicesBlockIsAnEventAndTheProfileFollowsIt() = withRepos { repos, fake ->
        val bob = "u${fake.bob.id}"
        assertFalse(repos.profiles.profile(bob).blocked)
        coroutineScope {
            val live = Channel<ChatEvent>(Channel.UNLIMITED)
            val listening = launch { repos.updates.events().collect { live.send(it) } }
            delay(300)
            fake.safety.pushBlocked(fake.bob.id, true)
            assertEquals(ChatEvent.Blocked(bob, true), withTimeout(10_000) { nextOf<ChatEvent.Blocked>(live) })
            assertTrue(repos.profiles.stored(bob)?.blocked == true)
            fake.safety.pushBlocked(fake.bob.id, false)
            assertEquals(ChatEvent.Blocked(bob, false), withTimeout(10_000) { nextOf<ChatEvent.Blocked>(live) })
            assertTrue(repos.profiles.stored(bob)?.blocked == false)

            fake.safety.pushBar(fake.bob.id, PeerSettings(reportSpam = true, addContact = true))
            assertEquals(ChatEvent.PeerBarChanged(bob, PeerBar(reportSpam = true, addContact = true)), withTimeout(10_000) { nextOf<ChatEvent.PeerBarChanged>(live) })
            listening.cancel()
        }
    }

    private suspend inline fun <reified E : ChatEvent> nextOf(live: Channel<ChatEvent>): E {
        while (true) {
            val event = live.receive()
            if (event is E) return event
        }
    }

    @Test
    fun theMessageMenuAsksForChoicesThenACommentThenIsReported() = withRepos { repos, fake ->
        val safety = repos.safety
        val target = ReportTarget.Messages("u${fake.alice.id}", listOf(7, 8))

        val menu = assertIs<ReportStep.Choose>(safety.report(target, null))
        assertEquals("What is wrong?", menu.title)
        assertEquals(listOf("Spam", "Violence", "Something else"), menu.options.map { it.text })
        assertContentEquals(ByteArray(0), fake.safety.reports.single().option)
        assertEquals(listOf(7, 8), fake.safety.reports.single().id)

        assertEquals(ReportStep.Done, safety.report(target, menu.options[0].id))
        assertContentEquals(byteArrayOf(1), fake.safety.reports.last().option)

        val kinds = assertIs<ReportStep.Choose>(safety.report(target, menu.options[1].id))
        assertEquals(listOf("Graphic", "Threats"), kinds.options.map { it.text })
        assertEquals(ReportStep.Done, safety.report(target, kinds.options[1].id))
        assertContentEquals(byteArrayOf(2, 2), fake.safety.reports.last().option)

        val comment = assertIs<ReportStep.Comment>(safety.report(target, menu.options[2].id))
        assertFalse(comment.optional)
        assertEquals(ReportStep.Done, safety.report(target, comment.option, "  rude and loud  "))
        assertContentEquals(byteArrayOf(3), fake.safety.reports.last().option)
        assertEquals("rude and loud", fake.safety.reports.last().message)

        val error = assertFailsWith<RpcException> { safety.report(ReportTarget.Messages("u${fake.alice.id}", emptyList()), null) }
        assertEquals("MESSAGE_REQUIRED", error.errorMessage)
    }

    @Test
    fun aStoryIsReportedThroughTheSameMenu() = withRepos { repos, fake ->
        val target = ReportTarget.Story("u${fake.alice.id}", 12)
        val menu = assertIs<ReportStep.Choose>(repos.safety.report(target, null))
        assertEquals(ReportStep.Done, repos.safety.report(target, menu.options[0].id))
        val sent = fake.safety.storyReports
        assertEquals(listOf(listOf(12), listOf(12)), sent.map { it.id })
        assertEquals(fake.alice.id, (sent[0].peer as InputPeerUser).userId)
        assertContentEquals(byteArrayOf(1), sent[1].option)
        assertTrue(fake.safety.reports.isEmpty())
    }

    @Test
    fun aWholePeerIsReportedWithTheReasonsAccountReportPeerTakes() = withRepos { repos, fake ->
        val target = ReportTarget.Peer("u${fake.alice.id}")
        val reasons = assertIs<ReportStep.Choose>(repos.safety.report(target, null))
        assertNull(reasons.title)
        assertEquals(PeerReportReason.entries.map { ReportOption(it.name, reason = it) }, reasons.options)
        assertTrue(fake.safety.peerReports.isEmpty() && fake.safety.reports.isEmpty())

        assertEquals(ReportStep.Done, repos.safety.report(target, PeerReportReason.Spam.name))
        assertEquals(InputReportReasonSpam, fake.safety.peerReports.single().reason)

        val comment = assertIs<ReportStep.Comment>(repos.safety.report(target, PeerReportReason.Other.name))
        assertFalse(comment.optional)
        assertEquals(1, fake.safety.peerReports.size)
        assertEquals(ReportStep.Done, repos.safety.report(target, comment.option, "pretends to be my bank"))
        assertEquals(InputReportReasonOther, fake.safety.peerReports.last().reason)
        assertEquals("pretends to be my bank", fake.safety.peerReports.last().message)
        assertEquals(fake.alice.id, (fake.safety.peerReports.last().peer as InputPeerUser).userId)
    }

    @Test
    fun theActionBarOfAStrangerIsAskedDismissedAndReported() = withRepos { repos, fake ->
        val bob = "u${fake.bob.id}"
        assertEquals(PeerBar(), repos.safety.bar(bob))

        fake.safety.bars[fake.bob.id] = PeerSettings(reportSpam = true, addContact = true, blockContact = true, shareContact = true)
        val bar = repos.safety.bar(bob)
        assertEquals(PeerBar(reportSpam = true, addContact = true, block = true), bar)
        assertTrue(bar.shown)

        repos.safety.hideBar(bob)
        assertEquals(fake.bob.id, (fake.safety.hiddenBars.single().peer as InputPeerUser).userId)
        assertFalse(repos.safety.bar(bob).shown)

        fake.safety.bars[fake.bob.id] = PeerSettings(reportSpam = true)
        repos.safety.reportSpam(bob)
        assertEquals(fake.bob.id, (fake.safety.spamReports.single().peer as InputPeerUser).userId)
        assertFalse(repos.safety.bar(bob).shown)
    }
}
