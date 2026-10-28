package cn.ellerotta.core.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ActivityKind
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.ui.components.chat.ActivityStyle
import cn.ellerotta.core.ui.components.chat.StatusLine
import cn.ellerotta.core.ui.components.chat.activityStyle
import cn.ellerotta.core.ui.components.chat.statusLine
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import cn.ellerotta.core.ui.components.common.TopBarTokens
import kotlin.test.assertNull
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.ChatSearchActions
import cn.ellerotta.core.ui.components.chats.ChatAction
import cn.ellerotta.core.ui.theme.MioTheme
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()

    private val dialog = DialogItem("u1", "Ada Lovelace", kind = PeerKind.User, presence = Presence.Recently)

    private val messages = listOf(
        MessageItem(1, "the engine weaves algebraic patterns", out = false, date = now - 600, senderName = "Ada Lovelace"),
        MessageItem(2, "just as the loom weaves flowers and leaves", out = true, date = now - 300),
        MessageItem(3, "see note g", out = false, date = now - 60, senderName = "Ada Lovelace"),
    )

    @Test
    fun headerOpensTheProfileAndTheMenu() {
        var back = 0
        var profiles = 0
        val actions = mutableListOf<ChatAction>()
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen(
                    "Ada Lovelace", messages, emptyMap(), busy = false, onBack = { back++ }, onSend = {}, onDownload = {},
                    onOpenProfile = { profiles++ }, dialog = dialog, onDialogAction = { actions += it },
                )
            }
        }
        compose.onNodeWithTag("chatBackground").assertExists()
        compose.onNodeWithTag("chatStatus", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("last seen recently", useUnmergedTree = true).assertExists()

        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("title").performClick()
        compose.onNodeWithTag("avatar").performClick()
        assertEquals(1, back)
        assertEquals(2, profiles)

        compose.onNodeWithTag("avatar").performTouchInput { longClick() }
        compose.onNodeWithTag("menu_Mute").performClick()
        compose.onNodeWithTag("avatar").performTouchInput { longClick() }
        compose.onNodeWithTag("menu_Archive").performClick()
        assertEquals(listOf(ChatAction.Mute, ChatAction.Archive), actions)
    }

    @Test
    fun theHeaderTakesDragsStartedOnItsEmptyPartsFromTheMessages() {
        val many = (1..60).map { MessageItem(it, "message $it", out = it % 2 == 0, date = now - 3_600 + it) }
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen("Ada Lovelace", many, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = dialog)
            }
        }
        fun scrolled() = compose.onNodeWithTag("messages").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val start = scrolled()
        compose.onNodeWithTag("chatHeader").performTouchInput { swipe(Offset(2f, 2f), Offset(2f, 400f)) }
        compose.waitForIdle()
        assertEquals(start, scrolled())
        compose.onNodeWithTag("messages").performTouchInput { swipe(Offset(centerX, height / 2f), Offset(centerX, height / 2f + 300f)) }
        compose.waitForIdle()
        assertTrue(scrolled() != start, "${scrolled()} $start")
    }

    @Test
    fun aLongPressOnTheTitleSearches() {
        val queries = mutableListOf<String>()
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen(
                    "Ada Lovelace", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = dialog,
                    onDialogAction = {}, searchActions = ChatSearchActions(onQuery = { queries += it }, onMore = {}, onClose = {}),
                )
            }
        }
        compose.onNodeWithTag("searchField").assertDoesNotExist()
        compose.onNodeWithTag("title").performTouchInput { longClick() }
        compose.onNodeWithTag("searchField").assertExists()
        compose.onNodeWithTag("chatMenu").assertDoesNotExist()
        compose.onNodeWithTag("title").assertDoesNotExist()
    }

    @Test
    fun aTitleWithoutASearchHasNoLongPress() {
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen("Ada Lovelace", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = dialog)
            }
        }
        compose.onNodeWithTag("title").performTouchInput { longClick() }
        compose.onNodeWithTag("searchField").assertDoesNotExist()
        compose.onNodeWithTag("chatMenu").assertDoesNotExist()
    }

    private fun show(dialog: DialogItem, connection: ChatsStatus = ChatsStatus.Ready, unreadChats: Int = 0) {
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen("Ada Lovelace", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = dialog, connection = connection, unreadChats = unreadChats)
            }
        }
    }

    @Test
    fun aStrangersNumberTitlesTheChatButNotAContactsBotsOrSupports() {
        fun inTitle(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("title")), useUnmergedTree = true)
        var shown by mutableStateOf(dialog.copy(contact = false, phone = "79991234567"))
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen("Ada Lovelace", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = shown)
            }
        }
        inTitle("+7 999 123-45-67").assertExists()
        inTitle("Ada Lovelace").assertDoesNotExist()
        for (kept in listOf(shown.copy(contact = true), shown.copy(kind = PeerKind.Bot), shown.copy(support = true), shown.copy(phone = null))) {
            shown = kept
            compose.waitForIdle()
            inTitle("Ada Lovelace").assertExists()
            inTitle("+7 999 123-45-67").assertDoesNotExist()
        }
    }

    @Test
    fun aTopicNamesItsGroupUnderTheTitle() {
        var typing by mutableStateOf<String?>(null)
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen(
                    "Releases", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    dialog = DialogItem("ch1", "Kotlin forum", kind = PeerKind.Group, members = 48, typing = typing), subtitle = "Kotlin forum",
                )
            }
        }
        compose.onNode(hasText("Releases") and hasAnyAncestor(hasTestTag("title")), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Kotlin forum", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("48 members", useUnmergedTree = true).assertDoesNotExist()
        typing = "Mark"
        compose.waitForIdle()
        compose.onNodeWithText("Mark is typing", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Kotlin forum", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theBackPillCountsTheUnreadChatsOnABadge() {
        var unread by mutableStateOf(0)
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen("Ada Lovelace", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = dialog, unreadChats = unread)
            }
        }
        compose.onNodeWithTag("backBadge", useUnmergedTree = true).assertDoesNotExist()
        unread = 7
        compose.waitForIdle()
        compose.onNodeWithTag("backBadge", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("7", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("7 unread chats", useUnmergedTree = true).assertExists()
        unread = 1_240
        compose.waitForIdle()
        compose.onNodeWithText("1.2K", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("back").assertHasClickAction()
        unread = 0
        compose.waitForIdle()
        compose.onNodeWithTag("backBadge", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theTitleSitsOnAPillBetweenTheOthers() {
        show(dialog)
        val back = compose.onNodeWithTag("back").getBoundsInRoot()
        val title = compose.onNodeWithTag("title").getBoundsInRoot()
        val avatar = compose.onNodeWithTag("avatar").getBoundsInRoot()
        assertTrue(title.bottom - title.top >= TopBarTokens.PillSize, "$title")
        assertTrue(title.right - title.left >= 150.dp, "$title")
        assertTrue(title.left >= back.right && title.right <= avatar.left, "$back $title $avatar")
        assertEquals(((back.right + avatar.left) / 2).value, ((title.left + title.right) / 2).value, 1f)
    }

    @Test
    fun verifiedReplacesTheOthersAndTheMuteMark() {
        show(dialog.copy(verified = true, premium = true, muted = true, scam = true))
        compose.onNodeWithTag("verified", useUnmergedTree = true).assertExists()
        listOf("premium", "muted", "scam", "emojiStatus").forEach { compose.onNodeWithTag(it, useUnmergedTree = true).assertDoesNotExist() }
    }

    @Test
    fun aScamOrFakeTagComesBeforeTheEmojiStatusAndPremium() {
        show(dialog.copy(scam = true, fake = true, premium = true, emojiStatus = 7))
        compose.onNodeWithTag("scam", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("SCAM", useUnmergedTree = true).assertExists()
        listOf("fake", "premium", "emojiStatus").forEach { compose.onNodeWithTag(it, useUnmergedTree = true).assertDoesNotExist() }
    }

    @Test
    fun anEmojiStatusShowsOnceItsFileIsThereAndPremiumsStarUntilThen() {
        val star = MediaItem("doc7", "emoji", 10, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "\u2B50", animated = true, textColor = true)
        var files by mutableStateOf(emptyMap<String, ByteArray>())
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen(
                    "Ada Lovelace", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    dialog = dialog.copy(premium = true, muted = true, emojiStatus = 7), stickers = files, customEmoji = mapOf(7L to star),
                )
            }
        }
        compose.onNodeWithTag("premium", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("emojiStatus", useUnmergedTree = true).assertDoesNotExist()
        files = mapOf("doc7" to "{}".toByteArray())
        compose.waitForIdle()
        compose.onNodeWithTag("emojiStatus", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("premium", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("muted", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theNetworkComesBeforeWhatPeopleDo() {
        show(dialog.copy(typing = "", online = true), ChatsStatus.Connecting)
        compose.onNodeWithText("Connecting…", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("activity_Dots", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun noNetworkIsWaitedForOverTheConnectionAndWhatPeopleDo() {
        show(dialog.copy(typing = "", online = true), ChatsStatus.WaitingForNetwork)
        compose.onNodeWithText("Waiting for network…", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Connecting…", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("activity_Dots", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theHeaderNamesWhoDoesWhat() {
        var shown by mutableStateOf(dialog.copy(kind = PeerKind.Group, typing = ""))
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen("Ada Lovelace", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = shown)
            }
        }
        compose.onNodeWithText("typing", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("activity_Dots", useUnmergedTree = true).assertExists()
        shown = shown.copy(typing = "Mark", activity = ActivityKind.RecordVoice)
        compose.onNodeWithText("Mark is recording audio", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("activity_Waves", useUnmergedTree = true).assertExists()
        shown = shown.copy(typingMore = listOf("Eve"), activity = ActivityKind.UploadFile)
        compose.onNodeWithText("Mark and Eve are sending a file", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("activity_Bar", useUnmergedTree = true).assertExists()
        shown = shown.copy(typingMore = listOf("Eve", "Joe", "Kim"), activity = ActivityKind.Typing)
        compose.onNodeWithText("Mark and 3 more are typing", useUnmergedTree = true).assertExists()
        shown = shown.copy(typing = null, typingMore = emptyList())
        compose.onNodeWithText("group", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("activity_Dots", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun groupsAndBotsSayHowManyTheyHave() {
        var shown by mutableStateOf(DialogItem("c1", "Hikers", kind = PeerKind.Group, members = 48, membersOnline = 7))
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen("Hikers", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = shown)
            }
        }
        compose.onNodeWithText("48 members, 7 online", useUnmergedTree = true).assertExists()
        shown = shown.copy(membersOnline = 1)
        compose.onNodeWithText("48 members", useUnmergedTree = true).assertExists()
        shown = shown.copy(kind = PeerKind.Channel, membersOnline = 7)
        compose.onNodeWithText("48 subscribers", useUnmergedTree = true).assertExists()
        shown = DialogItem("u9", "Helper", kind = PeerKind.Bot, botUsers = 1_200)
        compose.onNodeWithText("1200 monthly users", useUnmergedTree = true).assertExists()
        shown = shown.copy(botUsers = 0)
        compose.onNodeWithText("bot", useUnmergedTree = true).assertExists()
    }

    @Test
    fun statusPriority() {
        val user = DialogItem("u1", "Ada", presence = Presence.Recently)
        assertEquals(StatusLine.Connection(ChatsStatus.Updating), statusLine(user.copy(typing = ""), ChatsStatus.Updating))
        assertEquals(StatusLine.Activity(ActivityKind.Typing, emptyList()), statusLine(user.copy(typing = "", online = true)))
        assertEquals(StatusLine.Activity(ActivityKind.PlayGame, listOf("Bo", "Cy")), statusLine(user.copy(typing = "Bo", typingMore = listOf("Cy"), activity = ActivityKind.PlayGame)))
        val group = DialogItem("ch1", "Kotlin forum", kind = PeerKind.Group, members = 48)
        assertEquals(StatusLine.Subtitle("Kotlin forum"), statusLine(group, subtitle = "Kotlin forum"))
        assertEquals(StatusLine.Subtitle("Kotlin forum"), statusLine(null, subtitle = "Kotlin forum"))
        assertEquals(StatusLine.Connection(ChatsStatus.Connecting), statusLine(group, ChatsStatus.Connecting, "Kotlin forum"))
        assertEquals(StatusLine.Activity(ActivityKind.Typing, listOf("Bo")), statusLine(group.copy(typing = "Bo"), subtitle = "Kotlin forum"))
        assertEquals(StatusLine.Seen(Presence.Members(48, null, false)), statusLine(group))
        assertEquals(StatusLine.Seen(Presence.Online), statusLine(user.copy(online = true)))
        assertEquals(StatusLine.Seen(Presence.Recently), statusLine(user))
        assertNull(statusLine(user.copy(presence = Presence.Unknown)))
        assertNull(statusLine(DialogItem("u0", "Saved", isSaved = true, kind = PeerKind.Saved, typing = "", online = true)))
        assertEquals(StatusLine.Seen(Presence.Members(48, 7, false)), statusLine(DialogItem("c1", "G", kind = PeerKind.Group, members = 48, membersOnline = 7)))
        assertEquals(StatusLine.Seen(Presence.Members(9, null, true)), statusLine(DialogItem("ch1", "C", kind = PeerKind.Channel, members = 9, membersOnline = 3)))
        assertEquals(StatusLine.Kind(PeerKind.Group), statusLine(DialogItem("c1", "G", kind = PeerKind.Group)))
        assertEquals(StatusLine.BotUsers(5), statusLine(DialogItem("u2", "B", kind = PeerKind.Bot, botUsers = 5)))
        assertNull(statusLine(null))
    }

    @Test
    fun everyActivityHasAStyle() {
        assertEquals(ActivityKind.entries.toSet(), ActivityKind.entries.filter { activityStyle(it) in ActivityStyle.entries }.toSet())
        assertEquals(ActivityStyle.Waves, activityStyle(ActivityKind.RecordVoice))
        assertEquals(ActivityStyle.Bar, activityStyle(ActivityKind.UploadVideo))
    }

    @Test
    fun lightScreenshot() = screenshot(dark = false)

    @Test
    fun darkScreenshot() = screenshot(dark = true)

    private fun screenshot(dark: Boolean) {
        compose.setContent {
            MioTheme(darkTheme = dark, useDynamicColor = false) {
                ChatScreen(
                    "Ada Lovelace", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = dialog,
                    onAttachMedia = {}, onAttachFile = {}, onVoiceStart = {},
                )
            }
        }
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue(image.width > 0 && image.height > 0)
        System.getenv("MIOGRAM_SCREENSHOTS")?.let { dir ->
            File(dir).mkdirs()
            File(dir, if (dark) "chat_dark.png" else "chat_light.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
