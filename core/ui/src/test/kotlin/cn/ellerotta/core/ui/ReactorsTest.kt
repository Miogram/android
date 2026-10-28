package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cn.ellerotta.core.model.ChatReactionState
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.PeerReaction
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.ReactionCount
import cn.ellerotta.core.model.Reactor
import cn.ellerotta.core.model.ReactorsState
import cn.ellerotta.core.ui.components.chat.ChatReactorActions
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReactorsTest {
    @get:Rule
    val compose = createComposeRule()

    private val like = Reaction.Emoji("\uD83D\uDC4D")
    private val ada = Reactor(1, "Ada Lovelace")
    private val charles = Reactor(2, "Charles Babbage")
    private val now = (System.currentTimeMillis() / 1000).toInt()
    private val calls = mutableListOf<String>()
    private val actions = ChatReactorActions(
        onShow = { calls += "show $it" },
        onMore = { calls += "more" },
        onClose = { calls += "close" },
        onOpenPeer = { calls += "open $it" },
        onNextUnread = { calls += "next" },
        onReadAll = { calls += "readAll" },
    )

    @Test
    fun theMenuListsWhoReactedAndTheButtonStepsThroughUnreadOnes() {
        var reactors by mutableStateOf<ReactorsState?>(null)
        var dialog by mutableStateOf(DialogItem("c1", "Engines", kind = PeerKind.Group, reactions = 2))
        val messages = listOf(
            MessageItem(1, "the analytical engine", out = true, date = now - 60, reactions = listOf(ReactionCount(like, 2, recent = listOf(ada, charles))), reactorsListed = true),
            MessageItem(2, "a channel post", out = false, date = now - 30, senderName = "Ada Lovelace", reactions = listOf(ReactionCount(like, 1))),
        )
        compose.setContent {
            MioTheme {
                ChatScreen(
                    "Engines", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = dialog,
                    reactions = ChatReactionState(listOf(like), default = like),
                    onReact = { _, _, _, _ -> },
                    reactors = reactors,
                    reactorActions = actions,
                )
            }
        }
        compose.onNodeWithTag("message_1").performTouchInput { longClick() }
        compose.onNodeWithTag("showReactors").assertIsDisplayed()
        compose.onNodeWithText("2 reactions").assertIsDisplayed()
        compose.onNodeWithTag("showReactors").performClick()
        assertEquals("show 1", calls.last())
        compose.onNodeWithTag("messageMenu").assertDoesNotExist()
        reactors = ReactorsState(1, total = 2, list = listOf(PeerReaction(ada, "u1", like, now), PeerReaction(charles, "u2", like, now)), count = 2)
        compose.waitForIdle()
        compose.onNodeWithTag("reactorsSheet").assertIsDisplayed()
        compose.onNodeWithTag("reactor_u2").assertIsDisplayed()
        compose.onNodeWithTag("reactor_u1").performClick()
        assertEquals("open u1", calls.last())
        reactors = null
        compose.waitForIdle()

        compose.onNodeWithContentDescription("2 unread reactions, show the next").assertIsDisplayed()
        compose.onNodeWithTag("unreadReactions").performClick()
        assertEquals("next", calls.last())
        compose.onNodeWithTag("unreadReactions").performTouchInput { longClick() }
        assertEquals("readAll", calls.last())
        dialog = dialog.copy(reactions = 0)
        compose.waitForIdle()
        compose.onNodeWithTag("unreadReactions").assertDoesNotExist()

        compose.onNodeWithTag("message_2").performTouchInput { longClick() }
        compose.onNodeWithTag("messageMenu").assertExists()
        compose.onNodeWithTag("showReactors").assertDoesNotExist()
    }
}
