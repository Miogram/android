package cn.ellerotta.core.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.MediaViewer
import cn.ellerotta.core.ui.components.common.SavedSnackbar
import cn.ellerotta.core.ui.components.settings.SettingsActions
import cn.ellerotta.core.ui.components.settings.SettingsContent
import cn.ellerotta.core.ui.components.settings.SettingsPageState
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadsTest {
    @get:Rule
    val compose = createComposeRule()

    private val file = MediaItem("doc1", "report.pdf", 4_096, 2, isPhoto = false, kind = MediaKind.File, mimeType = "application/pdf")
    private val photo = MediaItem("photo2", "photo_2_w.jpg", 20_000, 2, isPhoto = true, kind = MediaKind.Photo, width = 800, height = 600)

    @Test
    fun filesOfferSavingToDownloads() {
        val saved = mutableListOf<String>()
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Saved", messages = listOf(MessageItem(1, "", out = false, date = 1_700_000_000, media = file)), downloads = emptyMap(), busy = false,
                    onBack = {}, onSend = {}, onDownload = {}, onSaveToDownloads = { saved += it.key },
                )
            }
        }
        compose.onNodeWithTag("message_1").performTouchInput { longClick(Offset(centerX, height - 4f)) }
        compose.onNodeWithTag("actionSaveDownloads").assert(hasText("Save to Downloads"))
        compose.onNodeWithTag("actionSaveDownloads").performClick()
        assertEquals(listOf("doc1"), saved)
    }

    @Test
    fun textsDoNotOfferSavingToDownloads() {
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Saved", messages = listOf(MessageItem(2, "just words", out = false, date = 1_700_000_001)), downloads = emptyMap(), busy = false,
                    onBack = {}, onSend = {}, onDownload = {}, onSaveToDownloads = {},
                )
            }
        }
        compose.onNodeWithTag("message_2").performTouchInput { longClick() }
        compose.onNodeWithTag("actionCopy").assertExists()
        compose.onNodeWithTag("actionSaveDownloads").assertDoesNotExist()
    }

    @Test
    fun theViewerSavesTheShownMediaToDownloadsFromItsMenu() {
        val saved = mutableListOf<String>()
        compose.setContent {
            MioTheme {
                MediaViewer(
                    items = listOf(MessageItem(2, "", out = false, date = 1, media = photo)), initial = 0, downloads = emptyMap(),
                    onBack = {}, onPage = {}, onSave = {}, onShare = {}, onSaveToDownloads = { saved += it.key },
                )
            }
        }
        compose.onNodeWithTag("viewerMenu").performClick()
        compose.onNodeWithTag("viewerSaveDownloads").performClick()
        assertEquals(listOf("photo2"), saved)
    }

    @Test
    fun theSnackbarSaysWhereAndOpens() {
        var opened = 0
        compose.setContent { MioTheme { SavedSnackbar(key = 1, onOpen = { opened++ }, onDismiss = {}) } }
        compose.onNodeWithText("Saved to Download/ktproto").assertIsDisplayed()
        compose.onNodeWithTag("openDownload").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theSettingIsOffByDefaultAndToggles() {
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            MioTheme {
                SettingsContent(
                    SettingsPageState(version = "0.1.0-test (1)", library = "ktproto"), null, emptyMap(),
                    SettingsActions(onAutoSaveDownloads = { changes += it }), rememberLazyListState(), PaddingValues(),
                )
            }
        }
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("autoSaveDownloads"))
        compose.onNodeWithText("Save media to Downloads automatically").assertIsDisplayed()
        compose.onNodeWithTag("autoSaveDownloads").performClick()
        assertEquals(listOf(true), changes)
    }
}
