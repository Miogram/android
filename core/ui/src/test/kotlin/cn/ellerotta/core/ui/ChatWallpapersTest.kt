package cn.ellerotta.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.ServiceKind
import cn.ellerotta.core.model.Wallpaper
import cn.ellerotta.core.model.WallpaperAction
import cn.ellerotta.core.model.WallpaperKind
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.ChatWallpaperActions
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
class ChatWallpapersTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()
    private val dialog = DialogItem("u1", "Alice", kind = PeerKind.User)
    private val fill = Wallpaper("wp4", WallpaperKind.Fill, listOf(0x7fa381))
    private val pattern = Wallpaper("wp1", WallpaperKind.Pattern, listOf(0xdbddbb, 0x6ba587), rotation = 0, intensity = 50)

    @Test
    fun serviceMessagesShowTheWallpaperAndOfferTheOtherSides() {
        val messages = listOf(
            MessageItem(1, "", out = false, date = now - 300, senderName = "Alice", service = ServiceKind.Wallpaper, wallpaper = WallpaperAction(pattern)),
            MessageItem(2, "", out = true, date = now - 200, service = ServiceKind.Wallpaper, wallpaper = WallpaperAction(pattern, same = true)),
            MessageItem(3, "", out = true, date = now - 100, service = ServiceKind.Wallpaper, wallpaper = WallpaperAction(fill)),
        )
        val loads = mutableListOf<String>()
        val applied = mutableListOf<Int>()
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen(
                    "Alice", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = dialog,
                    wallpaperActions = ChatWallpaperActions(onLoad = { loads += it.key }, onApply = { applied += it.id }),
                )
            }
        }
        compose.onNodeWithText("Alice set a new wallpaper for this chat").assertIsDisplayed()
        compose.onNodeWithTag("applyWallpaper_1").performClick()
        assertEquals(listOf(1), applied)
        compose.onNodeWithText("You set the same wallpaper for this chat").assertIsDisplayed()
        compose.onNodeWithText("You set a new wallpaper for this chat").assertIsDisplayed()
        compose.onNodeWithTag("applyWallpaper_3").assertDoesNotExist()
        assertEquals(2, compose.onAllNodesWithTag("wallpaperChipPreview", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(listOf("wp1"), loads)
    }

    @Test
    fun withoutActionsAServiceMessageIsALine() {
        val messages = listOf(MessageItem(1, "", out = false, date = now - 300, senderName = "Alice", service = ServiceKind.Wallpaper, wallpaper = WallpaperAction(fill)))
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen("Alice", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = dialog)
            }
        }
        compose.onNodeWithText("Alice set a new wallpaper for this chat").assertIsDisplayed()
        compose.onNodeWithTag("wallpaperChipPreview", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theHeadersMenuOpensTheChatsWallpaper() {
        var opened = 0
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen(
                    "Alice", emptyList(), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = dialog,
                    onDialogAction = {}, wallpaperActions = ChatWallpaperActions(onOpen = { opened++ }),
                )
            }
        }
        compose.onNodeWithTag("avatar").performTouchInput { longClick() }
        compose.onNodeWithText("Wallpaper").assertIsDisplayed()
        compose.onNodeWithTag("menu_wallpaper").performClick()
        assertEquals(1, opened)
        compose.onNodeWithTag("menu_wallpaper").assertDoesNotExist()
    }
}
