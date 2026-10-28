package app.miogram

import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.PollDraft
import cn.ellerotta.ktproto.api.types.InputMediaPoll
import cn.ellerotta.ktproto.api.types.InputPollAnswer
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.ContactCard
import cn.ellerotta.core.model.card
import cn.ellerotta.ktproto.api.types.InputGeoPoint
import cn.ellerotta.ktproto.api.types.InputMediaContact
import cn.ellerotta.ktproto.api.types.InputMediaGeoPoint
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class AttachRepositoryTest {
    private fun withChat(block: suspend (repos: TestRepositories, fake: FakeTelegram, saved: String) -> Unit): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                fake.addSavedMessages(1)
                block(repos, fake, repos.dialogs.dialogs().dialogs.first { it.isSaved }.key)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun aPollGoesOutWithItsOptionsAndShowsInTheChat(): Unit = withChat { repos, fake, saved ->
        val reply = repos.messages.history(saved).first().id
        val draft = PollDraft(kind = PeerKind.Group)
            .withQuestion("  Lunch?  ")
            .withOption(0, "Soup")
            .withOption(1, "")
            .addOption().withOption(2, "Noodles")
            .withAnonymous(false)
            .withMultiple(true)
        repos.messages.awaitOutgoing(repos.attach.sendPoll(saved, draft, replyTo = reply))

        val call = assertNotNull(fake.attach.sent.single())
        assertEquals(reply, assertIs<InputReplyToMessage>(call.replyTo).replyToMsgId)
        val media = assertIs<InputMediaPoll>(call.media)
        assertEquals("Lunch?", media.poll.question.text)
        assertEquals(listOf("Soup", "Noodles"), media.poll.answers.map { assertIs<InputPollAnswer>(it).text.text })
        assertTrue(media.poll.publicVoters)
        assertTrue(media.poll.multipleChoice)
        assertFalse(media.poll.quiz)
        assertNull(media.correctAnswers)
        assertNull(media.solution)

        val poll = assertNotNull(repos.messages.history(saved).last().poll)
        assertEquals("Lunch?", poll.question.text)
        assertEquals(listOf("Soup", "Noodles"), poll.answers.map { it.text.text })
    }

    @Test
    fun aQuizCarriesItsRightAnswerByPositionAndItsExplanation(): Unit = withChat { repos, fake, saved ->
        val draft = PollDraft()
            .withQuestion("2 + 2?")
            .withOption(0, "3")
            .withOption(1, "")
            .addOption().withOption(2, "4")
            .addOption().withOption(3, "5")
            .withQuiz(true)
            .withCorrect(2)
            .withExplanation(" basic arithmetic ")
        repos.messages.awaitOutgoing(repos.attach.sendPoll(saved, draft))

        val media = assertIs<InputMediaPoll>(fake.attach.sent.single().media)
        assertTrue(media.poll.quiz)
        assertEquals(listOf(1), media.correctAnswers)
        assertEquals("basic arithmetic", media.solution)
        assertEquals(emptyList(), media.solutionEntities)
        assertFalse(media.poll.publicVoters)
    }

    @Test
    fun aBroadcastChannelsPollAndAPrivateChatsPollStayAnonymous(): Unit = withChat { repos, fake, saved ->
        listOf(PeerKind.Channel, PeerKind.User, PeerKind.Saved).forEach { kind ->
            repos.messages.awaitOutgoing(repos.attach.sendPoll(saved, PollDraft(kind = kind, anonymous = false).withQuestion("q").withOption(0, "a").withOption(1, "b")))
            assertFalse(assertIs<InputMediaPoll>(fake.attach.sent.last().media).poll.publicVoters, "$kind")
        }
    }

    @Test
    fun aPlaceGoesOutAsAGeoPoint(): Unit = withChat { repos, fake, saved ->
        repos.messages.awaitOutgoing(repos.attach.sendPlace(saved, GeoPlace(48.8566, 2.3522), topicId = null))
        val media = assertIs<InputMediaGeoPoint>(fake.attach.sent.single().media)
        val point = assertIs<InputGeoPoint>(media.geoPoint)
        assertEquals(48.8566 to 2.3522, point.lat to point.long)
        assertEquals(GeoPlace(48.8566, 2.3522, accessHash = FakeAttach.GEO_HASH), repos.messages.history(saved).last().place)
    }

    @Test
    fun aContactGoesOutWithItsNameSplitAndItsNumber(): Unit = withChat { repos, fake, saved ->
        val card = assertNotNull(ContactItem(key = "u7", userId = 7, name = "Ada King Lovelace", phone = "4479460000").card())
        repos.messages.awaitOutgoing(repos.attach.sendContact(saved, card))
        val media = assertIs<InputMediaContact>(fake.attach.sent.single().media)
        assertEquals(listOf("Ada King", "Lovelace", "+4479460000"), listOf(media.firstName, media.lastName, media.phoneNumber))
        assertEquals(ContactCard("Ada King", "Lovelace", "+4479460000"), repos.messages.history(saved).last().contact)
    }
}
