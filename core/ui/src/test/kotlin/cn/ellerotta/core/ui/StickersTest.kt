package cn.ellerotta.core.ui

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatReactionState
import cn.ellerotta.core.model.ChatStickerState
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.GifPanel
import cn.ellerotta.core.model.GifResults
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PickedEmoji
import cn.ellerotta.core.model.StickerPanel
import cn.ellerotta.core.model.StickerSearch
import cn.ellerotta.core.model.StickerSetDetail
import cn.ellerotta.core.model.StickerSetInfo
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.ChatStickerActions
import cn.ellerotta.core.ui.components.chat.EntitiesTransformation
import cn.ellerotta.core.ui.components.chat.RichColors
import cn.ellerotta.core.ui.components.chat.StickerView
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StickersTest {
    @get:Rule
    val compose = createComposeRule()

    private fun sticker(id: Long, emoji: String) = MediaItem("doc$id", "sticker", 100, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = emoji, animated = true)

    private val cat = sticker(811, "😀")
    private val catTwo = sticker(812, "😺")
    private val dog = sticker(821, "🐶")
    private val cats = StickerSetInfo("set801", "Cats", "cats", 2, installed = true)
    private val dogs = StickerSetInfo("set802", "Dogs", "dogs", 1, installed = false)

    private val calls = mutableListOf<String>()
    private val actions = ChatStickerActions(
        onOpenPanel = { calls += "panel" },
        onOpenTab = { calls += "tab $it" },
        onLoad = {},
        onSend = { calls += "send ${it.key}" },
        onDraft = { calls += "draft $it" },
        onShowSet = { calls += "show ${it.key}" },
        onInstall = { key, install -> calls += "install $key $install" },
        onDismissSet = { calls += "dismiss" },
        onPickEmoji = { calls += "pick ${it.emoji}" },
        onSearchStickers = { calls += "stickers $it" },
        onOpenGifs = { calls += "gifs" },
        onSearchGifs = { calls += "searchGifs $it" },
        onSendGif = { calls += "sendGif ${it.key}" },
        onChooseTone = { base, toned -> calls += "tone $base $toned" },
        onFave = { sticker, fave -> calls += "fave ${sticker.key} $fave" },
        onRemoveGif = { calls += "removeGif ${it.key}" },
        onReorderSets = { keys, emoji -> calls += "order ${keys.joinToString(",")} $emoji" },
    )

    private fun gif(id: Long) = MediaItem("doc$id", "gif.mp4", 100, 2, isPhoto = false, kind = MediaKind.Gif, width = 240, height = 180)

    private fun input(): String = compose.onNodeWithTag("messageInput").fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test
    fun panelTabsGridAndSending() {
        var state by mutableStateOf(ChatStickerState())
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Saved", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, stickerState = state, stickerActions = actions)
            }
        }
        compose.onNodeWithTag("stickersButton").performClick()
        compose.onNodeWithTag("stickerPanel").assertIsDisplayed()
        assertTrue("panel" in calls)
        compose.onNodeWithTag("panelBackspace").assertIsDisplayed()
        compose.onNodeWithTag("panelTab_Stickers").performClick()
        state = ChatStickerState(panel = StickerPanel(recent = listOf(cat), faved = listOf(catTwo), sets = listOf(cats)))
        compose.waitForIdle()
        compose.onNodeWithTag("pick_faved_doc812").assertIsDisplayed()
        compose.onNodeWithTag("panelGrid").performScrollToNode(hasTestTag("pick_recent_doc811"))
        compose.onNodeWithTag("pick_recent_doc811").performClick()
        assertEquals("send doc811", calls.last())
        compose.onNodeWithTag("tab_set801").performClick()
        compose.waitForIdle()
        assertTrue("tab set801" in calls)
        state = state.copy(setStickers = mapOf("set801" to listOf(cat, catTwo)))
        compose.waitForIdle()
        compose.onNodeWithTag("panelGrid").performScrollToNode(hasTestTag("pick_set801_doc812"))
        compose.onNodeWithTag("pick_set801_doc812").performClick()
        assertEquals("send doc812", calls.last())
    }

    @Test
    fun emojiGoWhereTheCursorIsAndBackspaceDeletesThem() {
        val state = ChatStickerState(recentEmoji = listOf(PickedEmoji("🙂")))
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Saved", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, stickerState = state, stickerActions = actions)
            }
        }
        compose.onNodeWithTag("messageInput").performTextInput("hi ")
        compose.onNodeWithTag("stickersButton").performClick()
        compose.onNodeWithTag("emoji_recent/🙂").performClick()
        compose.onNodeWithTag("panelGrid").performScrollToNode(hasTestTag("emoji_People/😀"))
        compose.onNodeWithTag("emoji_People/😀").performClick()
        assertEquals("hi 🙂😀", input())
        assertEquals(listOf("pick 🙂", "pick 😀"), calls.filter { it.startsWith("pick") })
        compose.onNodeWithTag("panelBackspace").performClick()
        assertEquals("hi 🙂", input())
        compose.onNodeWithTag("panelBackspace").performClick()
        compose.onNodeWithTag("panelBackspace").performClick()
        assertEquals("hi", input())
    }

    @Test
    fun aLongPressPicksASkinToneThatStays() {
        var state by mutableStateOf(ChatStickerState())
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Saved", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, stickerState = state, stickerActions = actions)
            }
        }
        compose.onNodeWithTag("messageInput").performTextInput("hi ")
        compose.onNodeWithTag("stickersButton").performClick()
        compose.onNodeWithTag("panelGrid").performScrollToNode(hasTestTag("emoji_People/👍"))

        compose.onNodeWithTag("emoji_People/👍").performTouchInput { longClick() }
        compose.onNodeWithTag("emojiTones").assertIsDisplayed()
        compose.onNodeWithTag("emojiTone_0").assertTextEquals("👍")
        compose.onNodeWithTag("emojiTone_4").performClick()
        assertEquals("hi 👍🏾", input())
        assertEquals(listOf("tone 👍 👍🏾", "pick 👍🏾"), calls.filter { it.startsWith("tone") || it.startsWith("pick") })
        compose.onNodeWithTag("emojiTones").assertDoesNotExist()

        state = state.copy(emojiTones = mapOf("👍" to "👍🏾"), recentEmoji = listOf(PickedEmoji("👍🏾")))
        compose.waitForIdle()
        compose.onNodeWithTag("emoji_People/👍").assertTextEquals("👍🏾").performClick()
        assertEquals("hi 👍🏾👍🏾", input())
        compose.onNodeWithTag("panelGrid").performScrollToNode(hasTestTag("emoji_recent/👍🏾"))
        compose.onNodeWithTag("emoji_recent/👍🏾").performTouchInput { longClick() }
        compose.onNodeWithTag("emojiTone_0").performClick()
        assertEquals("tone 👍 👍", calls.filter { it.startsWith("tone") }.last())
    }

    @Test
    fun aCustomEmojiInTheFieldIsDrawnOverItsAltEmoji() {
        val star = MediaItem("doc901", "emoji", 100, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "⭐", documentId = 901)
        var files by mutableStateOf(emptyMap<String, ByteArray>())
        val state = ChatStickerState(recentEmoji = listOf(PickedEmoji("⭐", 901)))
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Saved", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    stickerState = state, stickerActions = actions, customEmoji = mapOf(901L to star), stickers = files, reactions = ChatReactionState(premium = true),
                )
            }
        }
        compose.onNodeWithTag("messageInput").performTextInput("hi ")
        compose.onNodeWithTag("stickersButton").performClick()
        compose.onNodeWithTag("customEmoji_recent/c901").performClick()
        assertEquals("hi ⭐", input())
        compose.onNodeWithTag("fieldEmoji_901").assertDoesNotExist()
        files = mapOf("doc901" to byteArrayOf(1, 2, 3))
        compose.waitForIdle()
        compose.onNodeWithTag("fieldEmoji_901").assertIsDisplayed()
        val field = compose.onNodeWithTag("messageInput").fetchSemanticsNode().boundsInRoot
        val picture = compose.onNodeWithTag("fieldEmoji_901").fetchSemanticsNode().boundsInRoot
        assertTrue(field.contains(picture.center), "$picture outside $field")
        compose.onNodeWithTag("panelBackspace").performClick()
        assertEquals("hi ", input())
        compose.onNodeWithTag("fieldEmoji_901").assertDoesNotExist()
    }

    @Test
    fun onlyCustomEmojiWithPicturesHideTheirAltEmoji() {
        val text = "hi ⭐ ⭐"
        val entities = listOf(TextEntity(3, 1, EntityType.CustomEmoji(901)), TextEntity(5, 1, EntityType.CustomEmoji(902)))
        val colors = RichColors(Color.Blue, Color.Gray, Color.Unspecified, Color.Unspecified)
        val styled = EntitiesTransformation(entities, colors, drawn = setOf(901L)).filter(AnnotatedString(text)).text
        assertEquals(text, styled.text)
        assertEquals(listOf(3 to 4), styled.spanStyles.filter { it.item.color == Color.Transparent }.map { it.start to it.end })
    }

    @Test
    fun stickerSearchShowsWhatItFound() {
        var state by mutableStateOf(ChatStickerState(panel = StickerPanel(sets = listOf(cats))))
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Saved", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, stickerState = state, stickerActions = actions)
            }
        }
        compose.onNodeWithTag("stickersButton").performClick()
        compose.onNodeWithTag("panelTab_Stickers").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("stickerSearch").performTextInput("cat")
        assertEquals("stickers cat", calls.last())
        state = state.copy(stickerSearch = StickerSearch("cat", listOf(catTwo)))
        compose.waitForIdle()
        compose.onNodeWithTag("pick_found_doc812").performClick()
        assertEquals("send doc812", calls.last())
        state = state.copy(stickerSearch = StickerSearch("cat", emptyList()))
        compose.waitForIdle()
        compose.onNodeWithText("No stickers found").assertIsDisplayed()
    }

    @Test
    fun gifsSavedTrendingSearchAndSending() {
        var state by mutableStateOf(ChatStickerState())
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Saved", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, stickerState = state, stickerActions = actions)
            }
        }
        compose.onNodeWithTag("stickersButton").performClick()
        compose.onNodeWithTag("panelTab_Gifs").performClick()
        compose.waitForIdle()
        assertTrue("gifs" in calls)
        state = state.copy(gifs = GifPanel(saved = listOf(gif(861)), trending = GifResults("", listOf(gif(862)), nextOffset = "2")))
        compose.waitForIdle()
        compose.onNodeWithTag("gif_doc861").performClick()
        assertEquals("sendGif doc861", calls.last())
        compose.onNodeWithTag("tab_trending").performClick()
        compose.onNodeWithTag("gif_doc862").assertIsDisplayed()
        compose.onNodeWithTag("gifSearch").performTextInput("dogs")
        assertEquals("searchGifs dogs", calls.last())
        state = state.copy(gifs = state.gifs.copy(search = GifResults("dogs", emptyList())))
        compose.waitForIdle()
        compose.onNodeWithText("No GIFs here").assertIsDisplayed()
    }

    @Test
    fun aLongPressPreviewsStickersAndGifs() {
        val state = ChatStickerState(panel = StickerPanel(faved = listOf(catTwo), sets = listOf(cats)), gifs = GifPanel(saved = listOf(gif(861))), suggestions = listOf(dog))
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Saved", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, stickerState = state, stickerActions = actions)
            }
        }
        compose.onNodeWithTag("messageInput").performTextInput("🐶")
        compose.onNodeWithTag("suggest_doc821").performTouchInput { longClick() }
        compose.onNodeWithTag("mediaPreview").assertIsDisplayed()
        compose.onNodeWithTag("previewSet").assertDoesNotExist()
        compose.onNodeWithTag("previewSend").performClick()
        assertEquals("send doc821", calls.last())
        compose.onNodeWithTag("mediaPreview").assertDoesNotExist()
        compose.onNodeWithTag("stickersButton").performClick()
        compose.onNodeWithTag("panelTab_Stickers").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("pick_faved_doc812").performTouchInput { longClick() }
        compose.onNodeWithTag("mediaPreview").assertIsDisplayed()
        compose.onAllNodes(hasText("😺") and hasAnyAncestor(hasTestTag("mediaPreview"))).onFirst().assertIsDisplayed()
        assertEquals("send doc821", calls.last { it.startsWith("send") })
        compose.onNodeWithTag("previewSet").performClick()
        assertEquals("show doc812", calls.last())
        compose.onNodeWithTag("pick_faved_doc812").performTouchInput { longClick() }
        compose.onNodeWithTag("previewSend").performClick()
        assertEquals("send doc812", calls.last())
        compose.onNodeWithTag("panelTab_Gifs").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("gif_doc861").performTouchInput { longClick() }
        compose.onNodeWithTag("mediaPreview").assertIsDisplayed()
        compose.onNodeWithTag("previewSet").assertDoesNotExist()
        compose.onNodeWithTag("previewSend").performClick()
        assertEquals("sendGif doc861", calls.last())
    }

    @Test
    fun favesSavedGifsAndTheOrderOfSets() {
        var state by mutableStateOf(ChatStickerState(panel = StickerPanel(recent = listOf(cat), faved = listOf(catTwo), sets = listOf(cats, dogs.copy(installed = true))), gifs = GifPanel(saved = listOf(gif(861)), trending = GifResults("", listOf(gif(862))))))
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Saved", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, stickerState = state, stickerActions = actions)
            }
        }
        compose.onNodeWithTag("stickersButton").performClick()
        compose.onNodeWithTag("panelTab_Stickers").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("pick_faved_doc812").performTouchInput { longClick() }
        compose.onNodeWithText("Remove from favorites").performClick()
        assertEquals("fave doc812 false", calls.last())
        compose.onNodeWithTag("mediaPreview").assertDoesNotExist()
        compose.onNodeWithTag("panelGrid").performScrollToNode(hasTestTag("pick_recent_doc811"))
        compose.onNodeWithTag("pick_recent_doc811").performTouchInput { longClick() }
        compose.onNodeWithText("Add to favorites").performClick()
        assertEquals("fave doc811 true", calls.last())
        compose.onNodeWithTag("packBar").performScrollToNode(hasTestTag("reorderSets"))
        compose.onNodeWithTag("reorderSets").performClick()
        compose.onNodeWithTag("setOrderSheet").assertIsDisplayed()
        compose.onNodeWithTag("moveUp_set801").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move Dogs up").performClick()
        assertEquals("order set802,set801 false", calls.last())
        compose.onNodeWithTag("setOrderSheet").performTouchInput { swipeDown() }
        compose.waitForIdle()
        state = state.copy(panel = state.panel?.copy(sets = listOf(cats)))
        compose.onNodeWithTag("panelTab_Gifs").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("gif_doc861").performTouchInput { longClick() }
        compose.onNodeWithTag("previewRemove").performClick()
        assertEquals("removeGif doc861", calls.last())
        compose.onNodeWithTag("tab_trending").performClick()
        compose.onNodeWithTag("gif_doc862").performTouchInput { longClick() }
        compose.onNodeWithTag("previewRemove").assertDoesNotExist()
        compose.onNodeWithTag("previewFave").assertDoesNotExist()
    }

    @Test
    fun aSingleEmojiSuggestsStickers() {
        var state by mutableStateOf(ChatStickerState())
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Saved", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, stickerState = state, stickerActions = actions)
            }
        }
        compose.onNodeWithTag("messageInput").performTextInput("🐶")
        compose.waitForIdle()
        assertEquals("draft 🐶", calls.last())
        state = state.copy(suggestions = listOf(dog))
        compose.waitForIdle()
        compose.onNodeWithTag("suggest_doc821").performClick()
        assertEquals("send doc821", calls.last())
    }

    @Test
    fun aTappedStickerOpensItsSetWhichAdds() {
        var state by mutableStateOf(ChatStickerState())
        val messages = listOf(MessageItem(1, "", out = false, date = 1_700_000_000, media = dog))
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Saved", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, stickerState = state, stickerActions = actions)
            }
        }
        compose.onNodeWithTag("message_1").performClick()
        assertEquals("show doc821", calls.last())
        state = state.copy(openSet = StickerSetDetail(dogs, listOf(dog)))
        compose.waitForIdle()
        compose.onNodeWithText("Dogs").assertIsDisplayed()
        compose.onNodeWithText("1 sticker").assertIsDisplayed()
        compose.onNodeWithTag("addSet").performClick()
        assertEquals("install set802 true", calls.last())
        state = state.copy(openSet = StickerSetDetail(dogs.copy(installed = true), listOf(dog)))
        compose.waitForIdle()
        compose.onNodeWithTag("removeSet").performClick()
        assertEquals("install set802 false", calls.last())
    }

    @Test
    fun aVideoStickerWithoutADecoderShowsItsEmoji() {
        val webm = javaClass.getResourceAsStream("/webm/alpha.webm")!!.use { it.readBytes() }
        val broken = byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte(), 0x80.toByte())
        val cat = sticker(831, "😼")
        compose.setContent {
            MioTheme {
                Column {
                    StickerView(dog, webm, size = 64.dp)
                    StickerView(cat, broken, size = 64.dp)
                }
            }
        }
        repeat(20) {
            Thread.sleep(25)
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
        }
        compose.onNodeWithText("🐶").assertIsDisplayed()
        compose.onNodeWithText("😼").assertIsDisplayed()
    }
}
