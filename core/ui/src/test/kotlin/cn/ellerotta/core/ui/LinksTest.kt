package cn.ellerotta.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.text.LinkAnnotation
import cn.ellerotta.core.model.ChatInvitePreview
import cn.ellerotta.core.model.LinkPreview
import cn.ellerotta.core.model.Membership
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.links.InviteSheet
import cn.ellerotta.core.ui.components.links.UnsupportedLinkSheet
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
class LinksTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun linksInTextsAndPreviewsReachTheCallback() {
        val text = "ask @carol_writer or see example.com"
        val messages = listOf(
            MessageItem(1, text, out = false, date = 1_790_000_000, entities = listOf(TextEntity(4, 13, EntityType.Mention), TextEntity(25, 11, EntityType.Url))),
            MessageItem(2, "", out = true, date = 1_790_000_000, link = LinkPreview("https://core.telegram.org/api/links", siteName = "Telegram APIs", title = "Deep links")),
        )
        val opened = mutableListOf<String>()
        compose.setContent { MioTheme { ChatScreen("Saved", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, onOpenLink = { opened += it }) } }
        compose.onNode(hasText(text), useUnmergedTree = true).performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "example.com" }
        compose.onNode(hasText(text), useUnmergedTree = true).performFirstLinkClick()
        compose.onNodeWithTag("link_https://core.telegram.org/api/links").performClick()
        compose.waitForIdle()
        assertEquals(listOf("example.com", "https://t.me/carol_writer", "https://core.telegram.org/api/links"), opened)
    }

    @Test
    fun aChatFromALinkShowsJoinOrStartInsteadOfTheComposer() {
        var joined = 0
        compose.setContent { MioTheme { ChatScreen("Kotlin News", emptyList(), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, initialText = "draft", membership = Membership.CanJoin, onJoin = { joined++ }) } }
        compose.onNodeWithTag("joinChat").performClick()
        assertEquals(1, joined)
        assertTrue(compose.onAllNodes(hasTestTag("messageInput")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun inviteSheetShowsTheChatAndJoins() {
        var joins = 0
        val invite = ChatInvitePreview("hash", "Book club", about = "we read about kotlin", participants = 42, requestNeeded = true)
        compose.setContent { MioTheme { InviteSheet(invite, onJoin = { joins++ }, onDismiss = {}) } }
        compose.onNodeWithText("Book club").assertIsDisplayed()
        compose.onNodeWithText("42 members").assertIsDisplayed()
        compose.onNodeWithText("we read about kotlin").assertIsDisplayed()
        compose.onNodeWithText("An admin has to approve your request").assertIsDisplayed()
        compose.onNodeWithText("Request to join").performClick()
        assertEquals(1, joins)
    }

    @Test
    fun unsupportedLinkCanBeCopied() {
        var copies = 0
        compose.setContent { MioTheme { UnsupportedLinkSheet("tg://proxy?server=a&port=1&secret=b", onCopy = { copies++ }, onDismiss = {}) } }
        compose.onNodeWithText("Link not supported").assertIsDisplayed()
        compose.onNodeWithText("tg://proxy?server=a&port=1&secret=b").assertIsDisplayed()
        compose.onNodeWithTag("copyLink").performClick()
        assertEquals(1, copies)
    }
}
