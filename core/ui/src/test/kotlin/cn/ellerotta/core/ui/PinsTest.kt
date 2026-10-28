package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import android.graphics.Bitmap
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import java.io.ByteArrayOutputStream
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.PinnedMessages
import cn.ellerotta.core.ui.components.chat.ChatPinActions
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PinsTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()
    private val messages = (1..3).map { MessageItem(it, "message $it", out = false, date = now - 100 + it) }
    private val tapped = mutableListOf<Int>()
    private val pinnedCalls = mutableListOf<Triple<Int, Boolean, Boolean>>()
    private val unpinned = mutableListOf<Int>()
    private var unpinnedAll = 0
    private var hidden = 0
    private var pinnedState by mutableStateOf(PinnedMessages())

    private fun show(pinned: PinnedMessages, kind: PeerKind = PeerKind.User, canPin: Boolean = true, chat: List<MessageItem> = messages, stickers: Map<String, ByteArray> = emptyMap()) {
        pinnedState = pinned
        val actions = ChatPinActions(
            canPin = canPin,
            onTapped = { pinnedState.shown?.id.also { id -> id?.let(tapped::add) } },
            onPin = { id, silent, forBoth -> pinnedCalls += Triple(id, silent, forBoth) },
            onUnpin = {
                unpinned += it
                pinnedState = pinnedState.without(listOf(it))
            },
            onUnpinAll = { unpinnedAll++ },
            onHide = { hidden++ },
            onShow = { pinnedState = pinnedState.showing(it) },
        )
        val dialog = DialogItem(if (kind == PeerKind.User) "u7" else "ch7", "Alice", kind = kind)
        compose.setContent {
            MioTheme { ChatScreen(title = "Alice", messages = chat, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = dialog, pinned = pinnedState, pins = actions, stickers = stickers) }
        }
    }

    @Test
    fun theOlderOfTwoIsThePreviousOneAndTheListUnpinsAll() {
        show(PinnedMessages(listOf(messages[2], messages[0]), current = 1))
        compose.onNodeWithText("Previous pinned message", useUnmergedTree = true).assertExists()
        compose.onNode(hasTestTag("pinnedText") and hasText("message 1"), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("pinnedStripe", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("pinnedBar").performClick()
        assertEquals(listOf(1), tapped)
        compose.onNodeWithTag("closePinned").assertDoesNotExist()
        compose.onNodeWithTag("pinnedList").performClick()
        compose.onNodeWithText("2 pinned messages").assertExists()
        compose.onNodeWithTag("pinnedListAction").performClick()
        compose.onNodeWithText("Unpin all 2 messages?").assertExists()
        compose.onNodeWithTag("confirmUnpinAll").performClick()
        assertEquals(1, unpinnedAll)
        compose.onNodeWithTag("pinnedListScreen").assertDoesNotExist()
    }

    @Test
    fun aMiddleOneIsNumberedAndALongPressOpensTheList() {
        show(PinnedMessages(listOf(messages[2], messages[1], messages[0]), current = 1))
        compose.onNodeWithTag("pinnedTitle", useUnmergedTree = true).assertContentDescriptionEquals("Pinned message #2")
        compose.onNodeWithTag("pinnedBar").performTouchInput { longClick() }
        compose.onNodeWithText("3 pinned messages").assertExists()
        compose.onNodeWithTag("pinnedListAction").assertExists()
        compose.onNodeWithTag("pinnedListBack").performClick()
        compose.onNodeWithTag("pinnedListScreen").assertDoesNotExist()
    }

    @Test
    fun aMessageOpenedFromTheListMovesTheBarToIt() {
        show(PinnedMessages(listOf(messages[2], messages[1], messages[0])))
        compose.onNodeWithTag("pinnedList").performClick()
        compose.onNode(hasTestTag("message_1") and hasAnyAncestor(hasTestTag("pinnedListScreen"))).performClick()
        compose.onNodeWithTag("pinnedListScreen").assertDoesNotExist()
        compose.onNode(hasTestTag("pinnedText") and hasText("message 1"), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("pinnedTitle", useUnmergedTree = true).assertContentDescriptionEquals("Pinned message #1")
        compose.onNodeWithTag("pinnedBar").performClick()
        assertEquals(listOf(1), tapped)
    }

    @Test
    fun aPinnedStickerShowsItselfInTheBar() {
        val sticker = MediaItem(key = "st5", fileName = "st5.webp", size = 20_000, dcId = 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "👍", width = 512, height = 512, mimeType = "image/webp")
        val pinned = MessageItem(4, "", out = false, date = now, media = sticker)
        val png = ByteArrayOutputStream().also { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        show(PinnedMessages(listOf(pinned)), chat = messages + pinned, stickers = mapOf("st5" to png))
        compose.onNode(hasTestTag("pinnedThumb") and hasAnyAncestor(hasTestTag("pinnedBar")), useUnmergedTree = true).assertExists()
        compose.onNode(hasTestTag("sticker_st5") and hasAnyAncestor(hasTestTag("pinnedBar")), useUnmergedTree = true).assertExists()
        compose.onNode(hasTestTag("pinnedText") and hasText("👍 Sticker"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun aLongPressInTheListOpensTheMessageMenu() {
        show(PinnedMessages(listOf(messages[2], messages[1], messages[0])))
        compose.onNodeWithTag("pinnedList").performClick()
        fun inList(tag: String) = hasTestTag(tag) and hasAnyAncestor(hasTestTag("pinnedListScreen"))
        compose.onNode(inList("message_2")).performTouchInput { longClick() }
        compose.onNodeWithTag("messageMenu").assertExists()
        compose.onNodeWithTag("actionGoTo").assertExists()
        compose.onNodeWithTag("actionCopy").assertExists()
        compose.onNodeWithTag("actionUnpin").performClick()
        assertEquals(listOf(2), unpinned)
        compose.onNodeWithText("2 pinned messages").assertExists()
        compose.onNode(inList("message_2")).assertDoesNotExist()
        compose.onNode(inList("message_1")).performTouchInput { longClick() }
        compose.onNodeWithTag("actionGoTo").performClick()
        compose.onNodeWithTag("pinnedListScreen").assertDoesNotExist()
        compose.onNode(hasTestTag("pinnedText") and hasText("message 1"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun theActionsSheetPinsForBothOrOneSideAndUnpins() {
        show(PinnedMessages(listOf(messages[0])))
        compose.onNodeWithText("Pinned message", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("message_2").performTouchInput { longClick() }
        compose.onNodeWithTag("actionPin").performClick()
        compose.onNodeWithText("Also pin for Alice").assertExists()
        compose.onNodeWithTag("pinForBoth").performClick()
        compose.onNodeWithTag("confirmPin").performClick()
        assertEquals(listOf(Triple(2, false, false)), pinnedCalls)
        compose.onNodeWithTag("message_1").performTouchInput { longClick() }
        compose.onNodeWithTag("actionUnpin").performClick()
        assertEquals(listOf(1), unpinned)
    }

    @Test
    fun theCloseButtonUnpinsASinglePinAfterAsking() {
        show(PinnedMessages(listOf(messages[1])))
        compose.onNodeWithTag("pinnedList").assertDoesNotExist()
        compose.onNodeWithTag("closePinned").performClick()
        compose.onNodeWithText("Unpin this message?").assertExists()
        compose.onNodeWithTag("confirmUnpin").performClick()
        assertEquals(listOf(2), unpinned)
    }

    @Test
    fun aGroupAsksWhetherToNotifyAndWithoutTheRightNothingIsOffered() {
        show(PinnedMessages(), kind = PeerKind.Group)
        compose.onNodeWithTag("pinnedBar").assertDoesNotExist()
        compose.onNodeWithTag("message_3").performTouchInput { longClick() }
        compose.onNodeWithTag("actionPin").performClick()
        compose.onNodeWithTag("pinNotify").performClick()
        compose.onNodeWithTag("confirmPin").performClick()
        assertEquals(listOf(Triple(3, true, true)), pinnedCalls)
    }

    @Test
    fun withoutTheRightThereIsNoPinAndTheCloseButtonHides() {
        show(PinnedMessages(listOf(messages[0])), kind = PeerKind.Channel, canPin = false)
        compose.onNodeWithTag("pinnedBar").assertExists()
        compose.onNodeWithTag("closePinned").performClick()
        assertEquals(1, hidden)
        assertEquals(emptyList(), unpinned)
        compose.onNodeWithTag("message_1").performTouchInput { longClick() }
        compose.onNodeWithTag("actionCopy").assertExists()
        compose.onNodeWithTag("actionUnpin").assertDoesNotExist()
    }
}
