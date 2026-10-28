package cn.ellerotta.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cn.ellerotta.core.model.ChatReactionState
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.ReactionCount
import cn.ellerotta.core.model.Reactor
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.compactCount
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReactionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()
    private val like: Reaction = Reaction.Emoji("\uD83D\uDC4D")
    private val fire: Reaction = Reaction.Emoji("\uD83D\uDD25")
    private val heart: Reaction = Reaction.Emoji("\u2764\uFE0F")

    private fun description(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)

    @Test
    fun chipsShowCountsReactorsAndTheOwnChoice() {
        val reacted = mutableListOf<Triple<Int, Reaction, Boolean>>()
        val message = MessageItem(
            5, "nice", out = false, date = now,
            reactions = listOf(
                ReactionCount(like, 2, recent = listOf(Reactor(501, "Alice"), Reactor(502, "Bob"))),
                ReactionCount(fire, 1_200, chosen = true),
                ReactionCount(Reaction.Paid, 5),
            ),
        )
        compose.setContent {
            MioTheme {
                ChatScreen(
                    "Saved", listOf(message), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    reactions = ChatReactionState(listOf(like, fire, heart), default = heart),
                    onReact = { id, reaction, picked, _ -> reacted += Triple(id, reaction, picked) },
                )
            }
        }
        compose.onNodeWithTag("reaction_5_\uD83D\uDC4D").assertIsNotSelected()
        compose.onNodeWithTag("reaction_5_\uD83D\uDD25").assertIsSelected()
        assertEquals(listOf("\uD83D\uDC4D, 2 reactions"), description("reaction_5_\uD83D\uDC4D"))
        assertEquals(listOf("\uD83D\uDD25, 1200 reactions"), description("reaction_5_\uD83D\uDD25"))
        assertEquals(listOf("Stars, 5 reactions"), description("reaction_5_paid"))
        assertEquals("1.2K", compactCount(1_200))
        assertEquals("999", compactCount(999))
        assertEquals("3M", compactCount(3_000_000))

        compose.onNodeWithTag("reaction_5_\uD83D\uDC4D").performClick()
        compose.onNodeWithTag("reaction_5_paid").assertIsDisplayed().performClick()
        assertEquals(listOf(Triple(5, like, false)), reacted)

        compose.onNodeWithTag("message_5").performTouchInput { doubleClick(Offset(centerX, 12f)) }
        compose.waitForIdle()
        assertEquals(Triple(5, heart, false), reacted.last())

        compose.onNodeWithTag("message_5").performTouchInput { longClick(Offset(centerX, 12f)) }
        compose.onNodeWithTag("reactionPicker").assertExists()
        compose.onNodeWithTag("react_\uD83D\uDD25").assertIsSelected()
        compose.onNodeWithTag("react_\u2764\uFE0F").performClick()
        assertEquals(Triple(5, heart, true), reacted.last())
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("reactionPicker")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aLongPressOnAChipOpensTheMenuWithoutToggling() {
        val reacted = mutableListOf<Reaction>()
        val message = MessageItem(5, "nice", out = false, date = now, reactions = listOf(ReactionCount(like, 2)))
        compose.setContent {
            MioTheme {
                ChatScreen(
                    "Saved", listOf(message), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    reactions = ChatReactionState(listOf(like, heart), default = heart),
                    onReact = { _, reaction, _, _ -> reacted += reaction },
                )
            }
        }
        compose.onNodeWithTag("reaction_5_\uD83D\uDC4D").performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(emptyList(), reacted)
        compose.onNodeWithTag("reactionPicker").assertExists()
    }
}
