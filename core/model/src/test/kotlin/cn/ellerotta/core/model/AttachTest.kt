package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttachTest {
    private fun poll(vararg texts: String): PollDraft {
        var draft = PollDraft().withQuestion("q")
        texts.forEachIndexed { index, text ->
            if (index >= draft.options.size) draft = draft.addOption()
            draft = draft.withOption(draft.options[index].id, text)
        }
        return draft
    }

    @Test
    fun aPollNeedsAQuestionAndTwoFilledOptions() {
        assertFalse(PollDraft().canSend)
        assertFalse(poll("a", "").canSend)
        assertFalse(poll("a", "b").withQuestion(" ").canSend)
        assertTrue(poll("a", "b").canSend)
        assertTrue(poll("a", "", "b").canSend)
    }

    @Test
    fun optionsStayBetweenTwoAndTen() {
        var draft = PollDraft()
        repeat(20) { draft = draft.addOption() }
        assertEquals(PollLimits.MAX_OPTIONS, draft.options.size)
        assertFalse(draft.canAddOption)
        assertEquals(10, draft.options.map { it.id }.toSet().size)
        repeat(20) { draft = draft.removeOption(draft.options.first().id) }
        assertEquals(PollLimits.MIN_OPTIONS, draft.options.size)
        assertFalse(draft.canRemoveOption)
    }

    @Test
    fun optionsMoveAndKeepTheirTexts() {
        val draft = poll("a", "b", "c")
        val ids = draft.options.map { it.id }
        assertEquals(listOf("b", "a", "c"), draft.move(ids[1], 0).options.map { it.text })
        assertEquals(listOf("b", "c", "a"), draft.move(ids[0], 2).options.map { it.text })
        assertEquals(draft, draft.move(ids[0], -1))
        assertEquals(draft, draft.move(ids[0], 3))
        assertEquals(draft, draft.move(ids[0], 0))
    }

    @Test
    fun aQuizNeedsItsRightAnswerAndTakesOneAnswerOnly() {
        val quiz = poll("a", "b").withQuiz(true)
        assertFalse(quiz.canSend)
        val ids = quiz.options.map { it.id }
        assertTrue(quiz.withCorrect(ids[1]).canSend)
        assertEquals(1, quiz.withCorrect(ids[1]).correctIndex)
        assertFalse(poll("a", "b", "").withQuiz(true).let { it.withCorrect(it.options[2].id) }.canSend)
        assertNull(quiz.withCorrect(ids[1]).addOption().removeOption(ids[1]).correct)
        assertFalse(quiz.withMultiple(true).quiz)
        assertFalse(poll("a", "b").withMultiple(true).withQuiz(true).multiple)
        val plain = quiz.withCorrect(ids[0]).withExplanation("why").withQuiz(false)
        assertNull(plain.correctIndex)
        assertEquals("", plain.explanation)
    }

    @Test
    fun theLimitsCutWhatIsTyped() {
        assertEquals(PollLimits.QUESTION, PollDraft().withQuestion("x".repeat(400)).question.length)
        assertEquals(PollLimits.OPTION, PollDraft().withOption(0, "x".repeat(400)).options[0].text.length)
        assertEquals(PollLimits.EXPLANATION, PollDraft().withExplanation("x".repeat(400)).explanation.length)
    }

    @Test
    fun onlyGroupsChooseWhoSeesTheVotes() {
        assertTrue(PollDraft(kind = PeerKind.Group, anonymous = false).publicVoters)
        assertFalse(PollDraft(kind = PeerKind.Group).publicVoters)
        assertFalse(PollDraft(kind = PeerKind.Channel, anonymous = false).publicVoters)
        assertFalse(PollDraft(kind = PeerKind.User, anonymous = false).publicVoters)
        assertTrue(PollDraft(kind = PeerKind.Group).canChooseAnonymity)
        assertFalse(PollDraft(kind = PeerKind.Channel).canChooseAnonymity)
    }

    @Test
    fun aNameSplitsAtItsLastSpace() {
        assertEquals("Ada" to "", splitName(" Ada "))
        assertEquals("Ada" to "Lovelace", splitName("Ada  Lovelace"))
        assertEquals("Anna Maria" to "Ivanova", splitName("Anna Maria Ivanova"))
    }

    @Test
    fun aContactCardNeedsANumberAndAName() {
        assertTrue(ContactCard("Ada", phone = "+44 20 7946 0000").canSend)
        assertFalse(ContactCard("", phone = "+44").canSend)
        assertFalse(ContactCard("Ada", phone = "n/a").canSend)
        assertEquals("Ada Lovelace", ContactCard("Ada", "Lovelace", "+1").name)
        assertEquals("Ada", ContactCard("Ada", "", "+1").name)
    }

    @Test
    fun aTelegramContactBecomesACardWithAPlusedNumber() {
        val card = ContactItem("u7", 7, "Ada Lovelace", phone = "447911123456").card()!!
        assertEquals(ContactCard("Ada", "Lovelace", "+447911123456", 7), card)
        assertNull(ContactItem("u8", 8, "No Number").card())
    }

    @Test
    fun coordinatesAreWrittenTheSameInEveryLocale() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ru"))
            assertEquals("48.85660, 2.35220", GeoPlace(48.8566, 2.3522).coordinates)
            assertEquals("geo:48.856600,2.352200?q=48.856600,2.352200", GeoPlace(48.8566, 2.3522).geoUri)
            assertEquals("geo:48.856600,2.352200?q=48.856600,2.352200(Caf%C3%A9%20de%20Flore)", GeoPlace(48.8566, 2.3522, title = "Café de Flore").geoUri)
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun distancesFollowTheGround() {
        assertEquals(111_195.0, distanceMeters(0.0, 0.0, 1.0, 0.0), 100.0)
        assertEquals(0.0, GeoPlace(10.0, 20.0).distanceTo(GeoPlace(10.0, 20.0)), 0.001)
        assertEquals(344_000.0, distanceMeters(48.8566, 2.3522, 51.5074, -0.1278), 2_000.0)
    }

    @Test
    fun aLiveLocationEndsWhenItsPeriodIsOverOrItIsStopped() {
        assertFalse(LiveInfo(period = 900, expires = 1_000).isOver(999))
        assertTrue(LiveInfo(period = 900, expires = 1_000).isOver(1_000))
        assertTrue(LiveInfo(period = 900, expires = 1_000, stopped = true).isOver(10))
        assertFalse(LiveInfo(period = LivePeriods.FOREVER).isOver(Int.MAX_VALUE))
        assertEquals(LivePeriods.FOREVER, LivePeriods.periodOf(0))
        assertEquals(900, LivePeriods.periodOf(900))
    }
}
