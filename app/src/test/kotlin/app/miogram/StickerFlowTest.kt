package app.miogram

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.SettingsStore
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.InputDocument
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import cn.ellerotta.ktproto.api.types.InputMediaDocument
import cn.ellerotta.ktproto.api.types.MessageEntityCustomEmoji
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class StickerFlowTest {
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("poc").toFile()

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @BindValue
    @JvmField
    val server = ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test")

    @BindValue
    @JvmField
    val telegram = TelegramClientHolder(dir) { id, hash, logger -> fake.config(id, hash, logger) }

    @After
    fun tearDown() {
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    @Test
    fun panelSendsAndASetSheetInstalls() {
        fake.addDogStickerMessage()
        compose.waitForTag("start")
        compose.onNodeWithTag("start").performClick()
        compose.waitForTag("phone")
        compose.onNodeWithTag("phone").performTextClearance()
        compose.onNodeWithTag("phone").performTextInput("6627777")
        compose.onNodeWithTag("sendCode").performClick()
        compose.waitForTag("confirmPhone")
        compose.onNodeWithTag("confirmPhone").performClick()
        compose.waitForTag("code")
        compose.onNodeWithTag("code").performTextInput("22222")
        compose.waitForTag("openChats")
        compose.openSavedMessages(fake.selfId)

        compose.waitForTag("stickersButton")
        compose.onNodeWithTag("stickersButton").performClick()
        compose.waitForTag("panelTab_Stickers")
        compose.onNodeWithTag("panelTab_Stickers").performClick()
        compose.waitForTag("tab_set${FakeTelegram.CATS_ID}")
        compose.waitForTag("stickerOutline_set${FakeTelegram.CATS_ID}thumb3", unmerged = true)
        compose.waitUntilIdle { fake.setThumbDownloads.isNotEmpty() }
        assertEquals(listOf(FakeTelegram.SetThumbDownload(FakeTelegram.CATS_ID, 3, 2)), fake.setThumbDownloads.toList())
        compose.onNodeWithTag("panelGrid").performScrollToNode(hasTestTag("pick_recent_doc${FakeTelegram.CAT_ID}"))
        compose.waitForTag("pick_recent_doc${FakeTelegram.CAT_ID}")
        compose.waitForTag("stickerPreview_doc${FakeTelegram.CAT_ID}", unmerged = true)
        assertEquals(listOf("${FakeTelegram.CAT_ID}@m"), fake.stickerThumbDownloads.toList())
        compose.onNodeWithTag("pick_recent_doc${FakeTelegram.CAT_ID}").performClick()
        compose.waitUntilIdle { fake.lastSendMedia != null }
        val sent = assertIs<InputDocument>(assertIs<InputMediaDocument>(assertNotNull(fake.lastSendMedia).media).id)
        assertEquals(FakeTelegram.CAT_ID to FakeTelegram.CAT_ID + 1, sent.id to sent.accessHash)
        assertContentEquals(fake.stickerReference, sent.fileReference)

        compose.onNodeWithTag("tab_set${FakeTelegram.CATS_ID}").performClick()
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("panelGrid").performScrollToNode(hasTestTag("pick_set${FakeTelegram.CATS_ID}_doc${FakeTelegram.CAT_TWO_ID}")) }.isSuccess }
        compose.waitForTag("pick_set${FakeTelegram.CATS_ID}_doc${FakeTelegram.CAT_TWO_ID}")
        compose.waitForTag("stickerOutline_doc${FakeTelegram.CAT_TWO_ID}", unmerged = true)
        compose.onNodeWithTag("stickersButton").performClick()

        compose.waitForTag("message_10")
        compose.onNodeWithTag("message_10").performClick()
        compose.waitForTag("addSet")
        compose.onNodeWithTag("addSet").performClick()
        compose.waitUntilIdle { FakeTelegram.DOGS_ID in fake.installedSets }
        compose.waitForTag("removeSet")
    }

    @Test
    fun gifsSendFromThePanel() {
        compose.waitForTag("start")
        compose.onNodeWithTag("start").performClick()
        compose.waitForTag("phone")
        compose.onNodeWithTag("phone").performTextClearance()
        compose.onNodeWithTag("phone").performTextInput("6627777")
        compose.onNodeWithTag("sendCode").performClick()
        compose.waitForTag("confirmPhone")
        compose.onNodeWithTag("confirmPhone").performClick()
        compose.waitForTag("code")
        compose.onNodeWithTag("code").performTextInput("22222")
        compose.waitForTag("openChats")
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("stickersButton")
        compose.onNodeWithTag("stickersButton").performClick()
        compose.waitForTag("panelTab_Gifs")

        compose.onNodeWithTag("panelTab_Gifs").performClick()
        compose.waitForTag("gif_doc${FakeTelegram.SAVED_GIF_ID}")
        compose.onNodeWithTag("gif_doc${FakeTelegram.SAVED_GIF_ID}").performClick()
        compose.waitUntilIdle { (fake.lastSendMedia?.media as? InputMediaDocument)?.let { (it.id as InputDocument).id } == FakeTelegram.SAVED_GIF_ID }
        compose.onNodeWithTag("gifSearch").performTextInput("cats")
        compose.waitForTag("gif_doc${FakeTelegram.FOUND_GIF_ID}")
        compose.onNodeWithTag("gif_doc${FakeTelegram.FOUND_GIF_ID}").performClick()
        compose.waitUntilIdle { FakeTelegram.FOUND_GIF_ID in fake.savedGifs }
        assertEquals(FakeTelegram.FOUND_GIF_ID, ((fake.lastSendMedia?.media as InputMediaDocument).id as InputDocument).id)
    }

    @Test
    fun aGifAnswersTheRepliedMessage() {
        fake.addSavedMessages(1)
        compose.waitForTag("start")
        compose.onNodeWithTag("start").performClick()
        compose.waitForTag("phone")
        compose.onNodeWithTag("phone").performTextClearance()
        compose.onNodeWithTag("phone").performTextInput("6627777")
        compose.onNodeWithTag("sendCode").performClick()
        compose.waitForTag("confirmPhone")
        compose.onNodeWithTag("confirmPhone").performClick()
        compose.waitForTag("code")
        compose.onNodeWithTag("code").performTextInput("22222")
        compose.waitForTag("openChats")
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("message_10")
        compose.onNodeWithTag("message_10").performTouchInput { longClick() }
        compose.waitForTag("actionReply")
        compose.onNodeWithTag("actionReply").performClick()
        compose.waitForTag("composerStrip")
        compose.waitForTag("stickersButton")
        compose.onNodeWithTag("stickersButton").performClick()
        compose.waitForTag("panelTab_Gifs")

        compose.onNodeWithTag("panelTab_Gifs").performClick()
        compose.waitForTag("gif_doc${FakeTelegram.SAVED_GIF_ID}")
        compose.onNodeWithTag("gif_doc${FakeTelegram.SAVED_GIF_ID}").performClick()
        compose.waitUntilIdle { fake.lastSendMedia != null }
        assertEquals(10, assertIs<InputReplyToMessage>(assertNotNull(fake.lastSendMedia).replyTo).replyToMsgId)
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("composerStrip")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun customEmojiComeInOneCallAndDrawInline() {
        fake.addCustomEmojiMessages()
        compose.waitForTag("start")
        compose.onNodeWithTag("start").performClick()
        compose.waitForTag("phone")
        compose.onNodeWithTag("phone").performTextClearance()
        compose.onNodeWithTag("phone").performTextInput("6627777")
        compose.onNodeWithTag("sendCode").performClick()
        compose.waitForTag("confirmPhone")
        compose.onNodeWithTag("confirmPhone").performClick()
        compose.waitForTag("code")
        compose.onNodeWithTag("code").performTextInput("22222")
        compose.waitForTag("openChats")
        compose.openSavedMessages(fake.selfId)

        compose.waitForTag("emoji_${FakeTelegram.EMOJI_HEART}", unmerged = true)
        compose.waitForTag("emoji_${FakeTelegram.EMOJI_STAR}", unmerged = true)
        assertEquals(listOf(listOf(FakeTelegram.EMOJI_STAR, FakeTelegram.EMOJI_HEART, FakeTelegram.EMOJI_MISSING)), fake.customEmojiCalls.toList())
        compose.onNode(hasText(fake.emojiText), useUnmergedTree = true).assertExists()
    }

    @Test
    fun customEmojiInTheFieldComeFromADraftAndFromTheirSet() {
        fake.articles.premium = true
        fake.installedSets += FakeTelegram.EMOJI_SET
        compose.waitForTag("start")
        compose.onNodeWithTag("start").performClick()
        compose.waitForTag("phone")
        compose.onNodeWithTag("phone").performTextClearance()
        compose.onNodeWithTag("phone").performTextInput("6627777")
        compose.onNodeWithTag("sendCode").performClick()
        compose.waitForTag("confirmPhone")
        compose.onNodeWithTag("confirmPhone").performClick()
        compose.waitForTag("code")
        compose.onNodeWithTag("code").performTextInput("22222")
        compose.waitForTag("openChats")
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("messageInput")

        runBlocking { fake.pushDraft(fake.selfId, "hi \u2B50", listOf(MessageEntityCustomEmoji(3, 1, FakeTelegram.EMOJI_STAR))) }
        compose.waitForTag("fieldEmoji_${FakeTelegram.EMOJI_STAR}", unmerged = true)
        assertEquals(listOf(listOf(FakeTelegram.EMOJI_STAR)), fake.customEmojiCalls.toList())

        compose.onNodeWithTag("stickersButton").performClick()
        val emojiPacks = hasTestTag("packBar") and SemanticsMatcher("nine packs") { it.config.getOrElseNullable(SemanticsProperties.CollectionInfo) { null }?.columnCount == 9 }
        compose.waitUntilIdle { compose.onAllNodes(emojiPacks).fetchSemanticsNodes().size == 1 }
        compose.onNode(emojiPacks).performScrollToIndex(8)
        compose.onNode(hasTestTag("tab_set${FakeTelegram.EMOJI_SET}") and hasParent(emojiPacks)).performClick()
        compose.waitForTag("customEmoji_set${FakeTelegram.EMOJI_SET}/c${FakeTelegram.EMOJI_HEART}")
        compose.waitForTag("category_Love")
        val calls = fake.customEmojiCalls.toList()
        compose.onNodeWithTag("customEmoji_set${FakeTelegram.EMOJI_SET}/c${FakeTelegram.EMOJI_HEART}").performClick()
        compose.waitForTag("fieldEmoji_${FakeTelegram.EMOJI_HEART}", unmerged = true)
        assertEquals(calls, fake.customEmojiCalls.toList())
    }

    @Test
    fun trendingSetsAddAndCategoriesSearch() {
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("stickersButton")
        compose.onNodeWithTag("stickersButton").performClick()

        compose.waitForTag("category_Love")
        compose.waitForTag("category_Hello")
        compose.onNodeWithTag("category_Love").performClick()
        compose.waitForTag("emoji_found/\u2764")
        compose.waitForTag("emoji_found/\uD83D\uDE0D")
        assertEquals(0, compose.onAllNodes(hasTestTag("category_Premium")).fetchSemanticsNodes().size)
        compose.onNodeWithTag("category_Love").performClick()
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("emoji_found/\u2764")).fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithTag("panelTab_Stickers").performClick()
        compose.waitForTag("dot_featuredStickers", unmerged = true)
        compose.onNodeWithTag("tab_featuredStickers").performClick()
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("panelGrid").performScrollToNode(hasTestTag("addSet_set${FakeTelegram.DOGS_ID}")) }.isSuccess }
        compose.waitUntilIdle { FakeTelegram.DOGS_ID in fake.readFeatured }
        assertEquals(0, compose.onAllNodes(hasTestTag("addSet_set${FakeTelegram.BIRDS_ID}")).fetchSemanticsNodes().size)
        compose.onNodeWithTag("addSet_set${FakeTelegram.DOGS_ID}").performClick()
        compose.waitUntilIdle { FakeTelegram.DOGS_ID in fake.installedSets }
        compose.waitForTag("tab_set${FakeTelegram.DOGS_ID}")
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("addSet_set${FakeTelegram.DOGS_ID}")).fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithTag("category_Cats").performClick()
        compose.waitForTag("pick_found_doc${FakeTelegram.CAT_TWO_ID}")
        assertEquals(listOf("\uD83D\uDE00 \uD83D\uDE3A" to false), fake.categorySearches.toList())

        compose.onNodeWithTag("panelTab_Gifs").performClick()
        compose.waitForTag("gifGrid")
        compose.waitForTag("category_Love")
        assertEquals(0, compose.onAllNodes(hasTestTag("category_Hello")).fetchSemanticsNodes().size)
        compose.onNodeWithTag("category_Love").performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        compose.waitUntilIdle { fake.bots.inlineCalls.any { it.query == "\u2764" } }
    }

    @Test
    fun aSkinTonePickedInThePanelIsKept() {
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("stickersButton")
        compose.onNodeWithTag("stickersButton").performClick()
        compose.waitForTag("panelGrid")
        compose.onNodeWithTag("panelGrid").performScrollToNode(hasTestTag("emoji_People/👍"))
        compose.onNodeWithTag("emoji_People/👍").performTouchInput { longClick() }
        compose.waitForTag("emojiTones")
        compose.onNodeWithTag("emojiTone_3").performClick()
        compose.waitUntilIdle { compose.onAllNodes(editableText("👍🏽")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("emoji_People/👍").assertTextEquals("👍🏽") }.isSuccess }
        assertEquals(mapOf("👍" to "👍🏽"), SettingsStore(ApplicationProvider.getApplicationContext()).emojiTones.value)
    }
}
