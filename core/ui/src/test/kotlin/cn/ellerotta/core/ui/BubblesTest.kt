package cn.ellerotta.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.ReactionCount
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.ui.components.chat.dayLabel
import java.time.LocalDate
import java.util.Locale
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.font.FontWeight
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.REPLY_MAX
import cn.ellerotta.core.ui.components.chat.REPLY_THRESHOLD
import cn.ellerotta.core.ui.components.chat.bubbleMaxWidth
import cn.ellerotta.core.ui.components.chat.bubbleWidth
import cn.ellerotta.core.ui.components.chat.replyDistance
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp")
class BubblesTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()

    private fun show(
        messages: List<MessageItem>,
        onRetry: ((Int) -> Unit)? = null,
        onDownload: (MediaItem) -> Unit = {},
        onDelete: ((Int, Boolean) -> Unit)? = null,
        dialog: DialogItem? = null,
    ) {
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Ada", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {},
                    onDownload = onDownload, onRetry = onRetry, onDelete = onDelete, dialog = dialog,
                )
            }
        }
    }

    @Test
    fun aChannelsOwnPostSitsOnTheIncomingSideWithItsReactionsUnderIt() {
        val like = Reaction.Emoji("\uD83D\uDC4D")
        val post = MessageItem(1, "a post of the channel", out = true, date = now, read = true, reactions = listOf(ReactionCount(like, 3, chosen = true)))
        show(listOf(post), onDelete = { _, _ -> }, dialog = DialogItem("ch5", "News", kind = PeerKind.Channel))
        val bubble = compose.onNodeWithTag("message_1").getBoundsInRoot()
        val chips = compose.onNodeWithTag("reactions_1").getBoundsInRoot()
        assertEquals(12.dp, bubble.left)
        assertTrue(compose.onAllNodesWithContentDescription("Read", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(chips.top >= bubble.bottom, "the reactions are under the bubble")
        assertEquals(bubble.left, chips.left)
        assertTrue(compose.onAllNodes(hasTestTag("reaction_1_\uD83D\uDC4D") and hasAnyAncestor(hasTestTag("message_1"))).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("message_1").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("actionDelete").assertExists()
    }

    @Test
    fun ownReactionsEndUnderTheBubblesEnd() {
        val like = Reaction.Emoji("\uD83D\uDC4D")
        show(listOf(MessageItem(1, "mine", out = true, date = now, reactions = listOf(ReactionCount(like, 1)))))
        val bubble = compose.onNodeWithTag("message_1").getBoundsInRoot()
        val chips = compose.onNodeWithTag("reactions_1").getBoundsInRoot()
        assertTrue(chips.top >= bubble.bottom)
        assertEquals(bubble.right, chips.right)
    }

    @Test
    fun theSenderSitsOverTheFirstBubbleOfARunWithTheirTag() {
        val messages = listOf(
            MessageItem(1, "first", out = false, date = now - 10, senderName = "Bob", senderId = 7, senderRank = "editor"),
            MessageItem(2, "second", out = false, date = now, senderName = "Bob", senderId = 7, senderRank = "editor"),
        )
        show(messages, dialog = DialogItem("c5", "Team", kind = PeerKind.Group))
        val name = compose.onNodeWithTag("sender_1").getBoundsInRoot()
        val bubble = compose.onNodeWithTag("message_1").getBoundsInRoot()
        assertTrue(name.bottom <= bubble.top, "the name is over the bubble")
        assertEquals(bubble.left, name.left)
        compose.onNodeWithTag("senderRank_1", useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodes(hasText("Bob") and hasAnyAncestor(hasTestTag("message_1")), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasTestTag("sender_2")).fetchSemanticsNodes().isEmpty())
        val next = compose.onNodeWithTag("message_2").getBoundsInRoot()
        assertEquals(2.dp, next.top - bubble.bottom)
    }

    @Test
    fun bubblesTakeAShareOfTheScreen() {
        assertEquals(412.dp * 0.79f, bubbleMaxWidth(412.dp, avatar = false))
        assertEquals(560.dp, bubbleMaxWidth(1_200.dp, avatar = false))
        assertEquals(512.dp, bubbleMaxWidth(1_200.dp, avatar = true))
        assertEquals(72.dp, bubbleMaxWidth(80.dp, avatar = true))
        compose.setContent {
            Box(Modifier.width(412.dp).padding(horizontal = 12.dp)) {
                Box(Modifier.bubbleWidth(24.dp, avatar = false).fillMaxWidth().height(10.dp).testTag("bubble"))
            }
        }
        compose.onNodeWithTag("bubble").assertWidthIsEqualTo(412.dp * 0.79f)
    }

    @Test
    fun aShortTextTakesTheTimeOnItsLine() {
        show(listOf(MessageItem(2, "a long line of words ".repeat(12).trim() + " end", out = false, date = now), MessageItem(1, "hi", out = false, date = now - 10)))
        val text = compose.onNodeWithTag("messageText_1", useUnmergedTree = true).getBoundsInRoot()
        val time = compose.onNodeWithTag("time_1", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(time.top < text.bottom, "the time sits on the text's line")
        val longText = compose.onNodeWithTag("messageText_2", useUnmergedTree = true).getBoundsInRoot()
        val longTime = compose.onNodeWithTag("time_2", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(longTime.top >= longText.bottom || longTime.left > longText.left, "the time is after the last line")
    }

    @Test
    fun aFailedSendIsMarkedAndSentAgainWithATap() {
        val retried = mutableListOf<Int>()
        val deleted = mutableListOf<Int>()
        val failed = MessageItem(Int.MAX_VALUE, "lost", out = true, date = now, pending = true, failed = true)
        show(listOf(failed), onRetry = { retried += it }, onDelete = { id, _ -> deleted += id })
        compose.onNodeWithTag("failed_${Int.MAX_VALUE}", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("message_${Int.MAX_VALUE}").performClick()
        assertEquals(listOf(Int.MAX_VALUE), retried)
        compose.onNodeWithTag("message_${Int.MAX_VALUE}").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("actionCopy").assertExists()
        compose.onNodeWithTag("actionDelete").assertExists()
        assertTrue(compose.onAllNodes(hasTestTag("actionReply")).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasTestTag("actionEdit")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aSwipeOrTalkbackAnswersAMessage() {
        assertEquals(50f, replyDistance(50f, 72f, 100f))
        assertEquals(72f + 28f * 0.4f, replyDistance(150f, 72f, 100f))
        assertEquals(0f, replyDistance(-20f, 72f, 100f))
        assertTrue(REPLY_THRESHOLD < REPLY_MAX)
        show(listOf(MessageItem(2, "second", out = false, date = now, senderName = "Ada"), MessageItem(1, "first", out = false, date = now - 10, senderName = "Ada")))
        compose.onNodeWithTag("message_1").performTouchInput { swipeLeft(startX = centerX, endX = centerX - 150.dp.toPx()) }
        compose.waitForIdle()
        compose.onNodeWithTag("composerStrip").assertExists()
        compose.onNodeWithTag("closeStrip").performClick()
        compose.waitForIdle()
        val actions = compose.onNodeWithTag("message_2").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        val reply = actions.single { it.label == "Reply" }
        compose.runOnIdle { reply.action() }
        compose.waitForIdle()
        compose.onNodeWithTag("composerStrip").assertExists()
    }

    @Test
    fun roundVideosAreCirclesThatLoadOnATap() {
        val asked = mutableListOf<String>()
        val round = MediaItem("r1", "round.mp4", 1000, 2, isPhoto = false, kind = MediaKind.Round, duration = 9, width = 384, height = 384)
        show(listOf(MessageItem(1, "", out = false, date = now, media = round)), onDownload = { asked += it.key })
        compose.onNodeWithTag("round_r1", useUnmergedTree = true).assertWidthIsEqualTo(240.dp).assertHeightIsEqualTo(240.dp)
        compose.onNodeWithTag("message_1").performClick()
        compose.waitForIdle()
        assertTrue("r1" in asked, "a tap downloads the video to play it")
    }

    @Test
    fun anIncomingVoiceMessageNotListenedToHasADot() {
        val voice = MediaItem("v1", "voice.ogg", 1000, 2, isPhoto = false, kind = MediaKind.Voice, duration = 3)
        show(
            listOf(
                MessageItem(2, "", out = true, date = now, media = voice.copy(key = "v2"), mediaUnread = true),
                MessageItem(1, "", out = false, date = now - 10, media = voice, mediaUnread = true),
            ),
        )
        compose.onNodeWithTag("voiceUnread_v1", useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodes(hasTestTag("voiceUnread_v2"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("voiceToggle_v1", useUnmergedTree = true).assertWidthIsEqualTo(48.dp)
    }

    @Test
    fun aFewEmojiAloneShowLargeWithoutABubble() {
        show(listOf(MessageItem(2, "hi 😀", out = false, date = now), MessageItem(1, "😀😀", out = false, date = now - 10)))
        compose.onNodeWithTag("jumbo_1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("metaPill_1", useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodes(hasTestTag("jumbo_2"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aReplyQuoteBarIsAsTallAsTheQuote() {
        val original = MessageItem(1, "the question", out = false, date = now - 60, senderName = "Ada")
        show(listOf(MessageItem(2, "the answer", out = true, date = now, replyTo = 1), original))
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("message_2"))
        val quote = compose.onNodeWithTag("replyQuote", useUnmergedTree = true).getBoundsInRoot()
        compose.onNodeWithTag("replyBar", useUnmergedTree = true).assertHeightIsEqualTo(quote.bottom - quote.top)
    }

    @Test
    fun aQuoteKeepsItsFormattingFollowsTheBubbleAndTakesEveryTap() {
        val opened = mutableListOf<String>()
        val words = "the question with a link to example.org and " + "many more words to fill the line ".repeat(8).trim()
        val link = words.indexOf("example.org")
        val original = MessageItem(
            1, words, out = false, date = now - 60, senderName = "Ada",
            entities = listOf(TextEntity(0, 3, EntityType.Bold), TextEntity(link, 11, EntityType.Url)),
        )
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Ada", messages = listOf(original, MessageItem(2, "the answer", out = true, date = now, replyTo = 1)),
                    downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, onOpenLink = { opened += it },
                )
            }
        }
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("message_2"))
        val text = compose.onNodeWithTag("replyText", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertTrue(text.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 0 })
        assertTrue(compose.onNodeWithTag("replyQuote", useUnmergedTree = true).getBoundsInRoot().let { it.right - it.left } > 260.dp)
        compose.onNodeWithTag("replyText", useUnmergedTree = true).performClick()
        assertTrue(opened.isEmpty())
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("highlight_1")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun mediaUnderASpoilerShowsOnATap() {
        val opened = mutableListOf<Int>()
        val video = MediaItem("video9", "video.mp4", 1000, 2, isPhoto = false, kind = MediaKind.Video, width = 800, height = 600, duration = 5, spoiler = true)
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Ada", messages = listOf(MessageItem(1, "", out = false, date = now, media = video)),
                    downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, onOpenMedia = { opened += it.id },
                )
            }
        }
        compose.onNodeWithTag("spoiler_video9", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("photo_video9").performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("spoiler_video9"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(opened.isEmpty())
        compose.onNodeWithTag("photo_video9").performClick()
        assertEquals(listOf(1), opened)
    }

    @Test
    fun daySeparatorsNameTheWeekdayAndTheYearOnlyWhenOther() {
        val today = LocalDate.of(2026, 10, 26)
        assertEquals("Thursday", dayLabel(today.minusDays(4), today, Locale.US))
        assertEquals("Четверг", dayLabel(today.minusDays(4), today, Locale.forLanguageTag("ru")))
        assertEquals("October 3", dayLabel(LocalDate.of(2026, 10, 3), today, Locale.US))
        assertEquals("3 октября", dayLabel(LocalDate.of(2026, 10, 3), today, Locale.forLanguageTag("ru")))
        assertEquals("December 24, 2025", dayLabel(LocalDate.of(2025, 12, 24), today, Locale.US))
    }
}
