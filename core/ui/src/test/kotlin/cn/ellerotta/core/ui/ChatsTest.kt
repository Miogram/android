package cn.ellerotta.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.FolderTab
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MuteDuration
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Preview
import cn.ellerotta.core.ui.components.chats.ChatAction
import cn.ellerotta.core.ui.components.chats.ChatsScreen
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import cn.ellerotta.core.ui.components.chats.compactCount
import cn.ellerotta.core.ui.components.chats.formatChatDate
import cn.ellerotta.core.ui.components.settings.SettingsActions
import cn.ellerotta.core.ui.theme.MioTheme
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatsTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()
    private val today = (now / 1000).toInt() - 60
    private val alice = DialogItem("u2", "Alice", Preview(text = "see you", sender = null), unread = 3, peerId = 2, date = today, online = true)
    private val group = DialogItem("c5", "Group", Preview(media = MediaKind.Photo, text = "look", sender = ""), peerId = 5, kind = PeerKind.Group, date = today, pinned = true, outgoing = true, read = true)
    private val bot = DialogItem("u4", "Helper", Preview(text = "draft text", draft = true), peerId = 4, kind = PeerKind.Bot, date = today, typing = "")
    private val tabs = listOf(
        FolderTab(ChatFolder.all(""), listOf(group, alice, bot)),
        FolderTab(ChatFolder(7, "Bots", bots = true), listOf(bot)),
    )

    @Test
    fun muteAsksForHowLongAndTheOthersActDirectly() {
        val muted = mutableListOf<Pair<String, MuteDuration>>()
        val actions = mutableListOf<Pair<String, ChatAction>>()
        compose.setContent {
            MioTheme {
                ChatsScreen(
                    tabs = tabs,
                    archived = emptyList(),
                    avatars = emptyMap(),
                    me = null,
                    status = ChatsStatus.Ready,
                    refreshing = false,
                    onOpenChat = {},
                    onOpenArchive = {},
                    onRefresh = {},
                    onAction = { dialog, action -> actions += dialog.key to action },
                    onMute = { dialog, duration -> muted += dialog.key to duration },
                )
            }
        }
        compose.onNodeWithTag("dialog_u2").performTouchInput { longClick() }
        compose.onNodeWithTag("muteForDay").assertDoesNotExist()
        compose.onNodeWithTag("action_Mute").performClick()
        compose.waitForIdle()
        assertEquals(
            listOf("For 1 hour", "For 8 hours", "For 1 day", "For 7 days", "Forever"),
            MuteDuration.entries.map { compose.onNodeWithTag("muteFor${it.name}").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString() },
        )
        assertEquals(emptyList(), muted)
        compose.onNodeWithTag("muteForDay").performClick()
        assertEquals(listOf("u2" to MuteDuration.Day), muted)
        assertEquals(emptyList(), actions)
        compose.onNodeWithTag("action_Mute").assertDoesNotExist()

        compose.onNodeWithTag("dialog_u2").performTouchInput { longClick() }
        compose.onNodeWithTag("action_Archive").performClick()
        assertEquals(listOf("u2" to ChatAction.Archive), actions)
    }

    @Test
    fun rowsFoldersSearchAndMenu() {
        val actions = mutableListOf<Pair<String, ChatAction>>()
        var opened: String? = null
        compose.setContent {
            MioTheme {
                ChatsScreen(
                    tabs = tabs,
                    archived = listOf(DialogItem("u9", "Old", archived = true)),
                    avatars = emptyMap(),
                    me = null,
                    status = ChatsStatus.Ready,
                    refreshing = false,
                    onOpenChat = { opened = it.key },
                    onOpenArchive = {},
                    onRefresh = {},
                    onAction = { dialog, action -> actions += dialog.key to action },
                )
            }
        }
        compose.onNodeWithText("All chats").assertIsDisplayed()
        compose.onNodeWithText("You: Photo, look").assertIsDisplayed()
        compose.onNodeWithText("see you").assertIsDisplayed()
        compose.onNodeWithTag("unread_u2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Alice").performClick()
        assertEquals("u2", opened)

        compose.onNodeWithTag("dialog_u2").performTouchInput { longClick() }
        compose.onNodeWithTag("action_MarkRead").performClick()
        assertEquals(listOf("u2" to ChatAction.MarkRead), actions)

        compose.onNodeWithTag("folder_7").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Bots").assertIsDisplayed()

        compose.onNodeWithTag("search").assertDoesNotExist()
        compose.onNodeWithTag("openSearch").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("search").performTextInput("gro")
        compose.onNodeWithTag("dialog_c5").assertIsDisplayed()
        compose.onNodeWithTag("dialog_u2").assertDoesNotExist()
    }

    @Test
    fun aFolderStillLoadingEndsInAnIndicatorAndAsksForMore() {
        val asked = mutableListOf<Int>()
        val loading = listOf(FolderTab(ChatFolder.all(""), listOf(alice)), FolderTab(ChatFolder(7, "Bots", bots = true), emptyList(), loading = true))
        compose.setContent {
            MioTheme {
                ChatsScreen(
                    tabs = loading, archived = emptyList(), avatars = emptyMap(), me = null, status = ChatsStatus.Ready,
                    refreshing = false, onOpenChat = {}, onOpenArchive = {}, onRefresh = {}, onAction = { _, _ -> },
                    onLoadMore = { asked += it },
                )
            }
        }
        compose.onNodeWithTag("chatsLoading").assertDoesNotExist()
        compose.onNodeWithTag("folder_7").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("chatsLoading").assertIsDisplayed()
        compose.onNodeWithText("No chats here yet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(7), asked.distinct()) }
    }

    @Test
    fun anEmptyFolderTelegramHasNothingMoreForShowsTheEmptyState() {
        val done = listOf(FolderTab(ChatFolder.all(""), listOf(alice)), FolderTab(ChatFolder(7, "Bots", bots = true), emptyList()))
        val asked = mutableListOf<Int>()
        compose.setContent {
            MioTheme {
                ChatsScreen(
                    tabs = done, archived = emptyList(), avatars = emptyMap(), me = null, status = ChatsStatus.Ready,
                    refreshing = false, onOpenChat = {}, onOpenArchive = {}, onRefresh = {}, onAction = { _, _ -> },
                    onLoadMore = { asked += it },
                )
            }
        }
        compose.onNodeWithTag("folder_7").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No chats here yet").assertIsDisplayed()
        compose.onNodeWithTag("chatsLoading").assertDoesNotExist()
        compose.runOnIdle { assertEquals(emptyList(), asked) }
    }

    @Test
    fun aSidewaysDragOnARowPagesTheFolders() {
        show(tabs)
        compose.onNodeWithTag("dialog_u2").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("folder_7").assertIsSelected()
        compose.onNodeWithTag("dialog_u2").assertDoesNotExist()
        compose.onNodeWithTag("dialog_u4").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithTag("folder_0").assertIsSelected()
        compose.onNodeWithTag("dialog_u2").performTouchInput { longClick() }
        compose.onNodeWithTag("action_MarkRead").assertExists()
    }

    @Test
    fun datesAndCounters() {
        val zone = ZoneOffset.UTC
        val nowAt = ZonedDateTime.of(2026, 10, 2, 15, 0, 0, 0, zone)
        fun at(day: Int, hour: Int, month: Int = 10, year: Int = 2026) = ZonedDateTime.of(year, month, day, hour, 30, 0, 0, zone).toEpochSecond().toInt()
        val millis = nowAt.toInstant().toEpochMilli()
        assertEquals("09:30", formatChatDate(at(2, 9), millis, Locale.ENGLISH, true, zone))
        assertEquals("9:30 AM", formatChatDate(at(2, 9), millis, Locale.US, false, zone).replace(' ', ' '))
        assertEquals("Mon", formatChatDate(at(28, 9, month = 9), millis, Locale.ENGLISH, true, zone))
        assertEquals("Aug 1", formatChatDate(at(1, 9, month = 8), millis, Locale.ENGLISH, true, zone))
        assertEquals("1 авг.", formatChatDate(at(1, 9, month = 8), millis, Locale.forLanguageTag("ru"), true, zone))
        assertEquals("", formatChatDate(0, millis, Locale.ENGLISH, true, zone))
        assertEquals("999", compactCount(999, Locale.ENGLISH))
        assertEquals("1.2K", compactCount(1234, Locale.ENGLISH))
        assertEquals("3K", compactCount(3004, Locale.ENGLISH))
        assertEquals("2.5M", compactCount(2_500_000, Locale.ENGLISH))
    }

    @Test
    fun globalResultsFollowTheLocalOnes() {
        val queries = mutableListOf<String>()
        var opened: String? = null
        val all = FolderTab(ChatFolder.all("All"), listOf(DialogItem("u1", "Kotlin club")))
        compose.setContent {
            MioTheme {
                ChatsScreen(
                    tabs = listOf(all), archived = emptyList(), avatars = emptyMap(), me = null, status = ChatsStatus.Ready, refreshing = false,
                    onOpenChat = { opened = it.key }, onOpenArchive = {}, onRefresh = {}, onAction = { _, _ -> },
                    globalResults = listOf(DialogItem("u1", "Kotlin club"), DialogItem("ch5", "Kotlin News", kind = PeerKind.Channel, preview = Preview(text = "@kotlinnews"))),
                    onSearch = { queries += it },
                )
            }
        }
        compose.onNodeWithTag("openSearch").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("search").performTextInput("kot")
        assertEquals("kot", queries.last())
        compose.onNodeWithText("Kotlin club").assertIsDisplayed()
        compose.onNodeWithTag("globalTitle").assertIsDisplayed()
        compose.onNodeWithTag("global_ch5").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("ch5", opened)
        compose.onNodeWithTag("global_u1").assertDoesNotExist()
    }

    @Test
    fun spoilersAndLoginCodesAreCoveredInPreviews() {
        val bob = DialogItem("u3", "Bob", Preview(text = "the end is a twist", hidden = listOf(11..17)), peerId = 3, date = today)
        val photo = DialogItem("u6", "Carol", Preview(media = MediaKind.Photo, text = "guess who\nis here", hidden = listOf(6..8, 10..16)), peerId = 6, date = today)
        val codes = DialogItem("u777000", "Telegram", Preview(text = "Login code: 52814. Do not give this code to anyone"), peerId = 777000, date = today)
        show(listOf(FolderTab(ChatFolder.all(""), listOf(bob, photo, codes))))
        assertEquals(listOf(11..17), transparent("u3"))
        assertEquals("Photo, guess who", previewOf("u6").text)
        assertEquals(listOf(13..15), transparent("u6"))
        assertEquals(listOf(12..16), transparent("u777000"))
    }

    @Test
    fun tabsAndTheToolbarTake48Dp() {
        show(tabs)
        compose.onNodeWithTag("folder_0").assertHeightIsEqualTo(48.dp)
        listOf("openChats", "openContacts", "openSettings").forEach { compose.onNodeWithTag(it).assertHeightIsEqualTo(48.dp) }
        compose.onNodeWithTag("openSaved").assertDoesNotExist()
        compose.onNodeWithTag("openChats").assertIsSelected()
        compose.onNodeWithTag("openSettings").assertIsNotSelected().assertContentDescriptionEquals("Settings")
    }

    @Test
    fun toolbarAndTitleLeadToArchiveSettingsAndProfile() {
        val opened = mutableListOf<String>()
        compose.setContent {
            MioTheme {
                ChatsScreen(
                    tabs = tabs, archived = emptyList(), avatars = emptyMap(), me = null, status = ChatsStatus.Ready, refreshing = false,
                    onOpenChat = {}, onOpenArchive = { opened += "archive" },
                    onRefresh = {}, onAction = { _, _ -> },
                    settingsActions = SettingsActions(onOpenProfile = { opened += "profile" }, onShown = { opened += "settings" }),
                )
            }
        }
        compose.onNodeWithText("Chats").performTouchInput { longClick() }
        compose.onNodeWithTag("openChats").performTouchInput { longClick() }
        val archive = compose.onNodeWithTag("openChats").fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Archived chats" }
        compose.runOnIdle { archive.action() }
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("openOwnProfile").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("openSettings").assertIsSelected()
        compose.onNodeWithTag("openOwnProfile").performClick()
        assertEquals(listOf("archive", "archive", "archive", "settings", "profile"), opened)
    }

    @Test
    fun connectingShowsInTheTitle() {
        compose.setContent {
            MioTheme {
                ChatsScreen(
                    tabs = listOf(FolderTab(ChatFolder.all(""), emptyList())), archived = emptyList(), avatars = emptyMap(), me = null,
                    status = ChatsStatus.Connecting, refreshing = false, onOpenChat = {}, onOpenArchive = {},
                    onRefresh = {}, onAction = { _, _ -> },
                )
            }
        }
        compose.onNodeWithText("Connecting…").assertIsDisplayed()
        compose.onNodeWithTag("placeholders").assertIsDisplayed()
    }

    @Test
    fun waitingForTheNetworkShowsInTheTitle() {
        compose.setContent {
            MioTheme {
                ChatsScreen(
                    tabs = listOf(FolderTab(ChatFolder.all(""), emptyList())), archived = emptyList(), avatars = emptyMap(), me = null,
                    status = ChatsStatus.WaitingForNetwork, refreshing = false, onOpenChat = {}, onOpenArchive = {},
                    onRefresh = {}, onAction = { _, _ -> },
                )
            }
        }
        compose.onNodeWithText("Waiting for network…").assertIsDisplayed()
        compose.onNodeWithTag("placeholders").assertIsDisplayed()
    }

    private fun show(tabs: List<FolderTab>) = compose.setContent {
        MioTheme {
            ChatsScreen(
                tabs = tabs, archived = emptyList(), avatars = emptyMap(), me = null, status = ChatsStatus.Ready,
                refreshing = false, onOpenChat = {}, onOpenArchive = {}, onRefresh = {}, onAction = { _, _ -> },
            )
        }
    }

    private fun previewOf(key: String): AnnotatedString =
        compose.onNodeWithTag("preview_$key", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single()

    private fun transparent(key: String): List<IntRange> =
        previewOf(key).spanStyles.filter { it.item.color == Color.Transparent }.map { it.start until it.end }
}
