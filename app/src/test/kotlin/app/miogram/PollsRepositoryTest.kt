package app.miogram

import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.SignInResult
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class PollsRepositoryTest {
    @Test
    fun pollsVoteRetractFollowAndListVoters(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                val public = fake.polls.addPoll("tabs or spaces?", listOf("tabs", "spaces"), public = true, othersVoted = listOf(1))
                val quiz = fake.polls.addPoll("2 + 2?", listOf("3", "4"), quiz = true, correct = 1, solution = "basic arithmetic")
                val multiple = fake.polls.addPoll("which languages?", listOf("kotlin", "java", "rust"), multiple = true, closeDate = FakeTelegram.NOW + 3_600)
                val saved = repos.dialogs.dialogs().dialogs.first { it.isSaved }.key
                val history = repos.messages.history(saved)

                val publicPoll = assertNotNull(history.single { it.id == public }.poll)
                assertEquals("tabs or spaces?", publicPoll.question.text)
                assertEquals(listOf("0" to "tabs", "1" to "spaces"), publicPoll.answers.map { it.option to it.text.text })
                assertTrue(publicPoll.publicVoters)
                assertEquals(2, publicPoll.results.totalVoters)
                assertEquals(listOf("Alice Example", "Bob"), publicPoll.results.recentVoters.map { it.name })
                assertEquals(listOf(0, 100), publicPoll.percents())
                assertFalse(publicPoll.voted)
                val quizPoll = assertNotNull(history.single { it.id == quiz }.poll)
                assertTrue(quizPoll.quiz)
                assertNull(quizPoll.results.solution)
                val multiplePoll = assertNotNull(history.single { it.id == multiple }.poll)
                assertEquals(true to FakeTelegram.NOW + 3_600, multiplePoll.multipleChoice to multiplePoll.closeDate)

                coroutineScope {
                    val live = Channel<ChatEvent>(Channel.UNLIMITED)
                    val listening = launch { repos.updates.events().collect { live.send(it) } }
                    delay(300)

                    repos.polls.vote(saved, public, listOf("0"))
                    assertEquals(listOf("0"), fake.polls.voteCalls.last().options.map { String(it) })
                    val voted = withTimeout(10_000) { nextPoll(live) }
                    assertEquals(public, voted.id)
                    assertEquals(publicPoll.id, voted.pollId)
                    val afterVote = publicPoll.withResults(voted.results)
                    assertTrue(afterVote.voted)
                    assertEquals(3, afterVote.results.totalVoters)
                    assertEquals(listOf(true, false), afterVote.results.answers.map { it.chosen })

                    repos.polls.vote(saved, public, emptyList())
                    assertTrue(fake.polls.voteCalls.last().options.isEmpty())
                    assertFalse(publicPoll.withResults(withTimeout(10_000) { nextPoll(live) }.results).voted)

                    repos.polls.vote(saved, quiz, listOf("0"))
                    val answered = quizPoll.withResults(withTimeout(10_000) { nextPoll(live) }.results)
                    assertEquals(listOf(true to false, false to true), answered.results.answers.map { it.chosen to it.correct })
                    assertEquals("basic arithmetic", answered.results.solution?.text)

                    repos.polls.vote(saved, multiple, listOf("0", "2"))
                    val both = multiplePoll.withResults(withTimeout(10_000) { nextPoll(live) }.results)
                    assertEquals(listOf(100, 0, 100), both.percents())

                    fake.polls.bobVotes(multiple, 1)
                    val others = withTimeout(10_000) { nextPoll(live) }
                    assertNull(others.key)
                    assertTrue(others.results.min)
                    val merged = both.withResults(others.results)
                    assertEquals(listOf(true, false, true), merged.results.answers.map { it.chosen })
                    assertEquals(2, merged.results.totalVoters)

                    fake.polls.close(multiple)
                    val closed = withTimeout(10_000) { nextPoll(live) }
                    assertTrue(assertNotNull(closed.poll).closed)
                    listening.cancel()
                }

                val first = repos.polls.votes(saved, public, limit = 1)
                assertEquals(2, first.count)
                assertEquals(listOf("Alice Example" to listOf("1")), first.votes.map { it.voter.name to it.options })
                val second = repos.polls.votes(saved, public, offset = assertNotNull(first.next), limit = 1)
                assertEquals(listOf("Bob"), second.votes.map { it.voter.name })
                assertNull(second.next)
                runCatching { repos.polls.votes(saved, quiz) }.let { assertTrue(it.isFailure) }
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}

private suspend fun nextPoll(events: Channel<ChatEvent>): ChatEvent.PollChanged {
    while (true) {
        val event = events.receive()
        if (event is ChatEvent.PollChanged) return event
    }
}
