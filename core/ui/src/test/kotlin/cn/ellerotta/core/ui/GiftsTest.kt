package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import cn.ellerotta.core.model.GiftCollection
import cn.ellerotta.core.model.CatalogueGift
import cn.ellerotta.core.model.GiftAction
import cn.ellerotta.core.model.GiftAttribute
import cn.ellerotta.core.model.GiftBackdrop
import cn.ellerotta.core.model.GiftPage
import cn.ellerotta.core.model.GiftRarity
import cn.ellerotta.core.model.GiftRarityTier
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.ProfileDetails
import cn.ellerotta.core.model.ProfileGifts
import cn.ellerotta.core.model.SavedGift
import cn.ellerotta.core.model.ServiceKind
import cn.ellerotta.core.model.UniqueGift
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.profile.GiftCallbacks
import cn.ellerotta.core.ui.components.profile.PeerProfileScreen
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GiftsTest {
    @get:Rule
    val compose = createComposeRule()

    private val teddy = SavedGift("giftm70", 61, null, stars = 50, date = 1_790_000_000, fromName = "Alice Example", message = "happy birthday", manageKey = "giftm70", convertStars = 40)
    private val pepe = SavedGift(
        "giftm71", 62, null, stars = 0, date = 1_790_000_000, fromName = "Bob",
        unique = UniqueGift(
            "Plush Pepe", "plushpepe-123", 123,
            model = GiftAttribute("Gold", null, GiftRarity.Permille(15)),
            backdrop = GiftBackdrop("Midnight", 0x334455, 0x112233, 0x556677, 0xffffff, GiftRarity.Tier(GiftRarityTier.Rare)),
            symbol = GiftAttribute("Star", null, GiftRarity.Permille(8)),
            issued = 500, total = 1_000,
        ),
    )
    private val hidden = SavedGift("giftm72", 61, null, stars = 50, date = 1_790_000_000, nameHidden = true, saved = false, manageKey = "giftm72", convertStars = 40)
    private val me = ProfileDetails("u1", 1, "Test User", PeerKind.User, isSelf = true)

    private fun scrollToOnGifts(tag: String) {
        compose.onNodeWithTag("peerProfile").performScrollToKey("pages")
        compose.onNodeWithTag("profilePage_Gifts").performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun collectionsPickWhichGiftsShow() {
        val picked = mutableListOf<Int?>()
        var gifts by mutableStateOf(ProfileGifts(GiftPage(2, listOf(teddy, pepe), null), collections = listOf(GiftCollection(7, "Bears", null, 1))))
        compose.setContent {
            MioTheme {
                PeerProfileScreen(
                    me.copy(isSelf = false, name = "Alice"), "Alice", emptyMap(), busy = false, onBack = {}, onMessage = {}, onMute = {}, onUnmute = {},
                    gifts = gifts, giftCallbacks = GiftCallbacks({}, {}, onCollection = { picked += it }),
                )
            }
        }
        compose.onNodeWithTag("peerProfile").performScrollToKey("pages")
        compose.onNodeWithTag("giftCollection_all").assertIsSelected()
        compose.onNodeWithTag("giftCollection_7").assertIsNotSelected().assertTextContains("Bears")
        compose.onNodeWithTag("giftCollection_7").performClick()
        assertEquals(listOf<Int?>(7), picked)
        gifts = gifts.copy(collection = 7)
        compose.waitForIdle()
        compose.onNodeWithTag("giftCollection_7").assertIsSelected()
        compose.onNodeWithTag("gift_giftm71").assertDoesNotExist()
        gifts = gifts.copy(collectionPages = mapOf(7 to GiftPage(1, listOf(teddy), null)))
        compose.waitForIdle()
        compose.onNodeWithTag("gift_giftm70").assertIsDisplayed()
        compose.onNodeWithTag("gift_giftm71").assertDoesNotExist()
        compose.onNodeWithTag("giftCollection_all").performClick()
        assertEquals(listOf<Int?>(7, null), picked)
    }

    @Test
    fun peerGiftsOpenTheirDetails() {
        compose.setContent {
            MioTheme {
                PeerProfileScreen(
                    me.copy(isSelf = false, name = "Alice"), "Alice", emptyMap(), busy = false, onBack = {}, onMessage = {}, onMute = {}, onUnmute = {},
                    gifts = ProfileGifts(GiftPage(2, listOf(teddy, pepe), null)), giftCallbacks = GiftCallbacks({}, {}),
                )
            }
        }
        scrollToOnGifts("gift_giftm71")
        compose.onNodeWithText("#123").assertIsDisplayed()
        compose.onNodeWithTag("gift_giftm71").performClick()
        compose.onNodeWithTag("giftTitle").assertTextEquals("Plush Pepe #123")
        compose.onNodeWithText("Gold · 1.5%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Midnight · rare").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Star · 0.8%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("500 of 1000 issued").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("giftShow").assertDoesNotExist()
    }

    @Test
    fun ownGiftsHideShowAndTheCatalogueIsReadOnly() {
        val toggles = mutableListOf<Pair<String, Boolean>>()
        var more = 0
        var gifts by mutableStateOf(ProfileGifts(GiftPage(3, listOf(teddy, pepe), "2")))
        compose.setContent {
            MioTheme {
                PeerProfileScreen(
                    me, "", emptyMap(), busy = false, onBack = {}, onMessage = {}, onMute = {}, onUnmute = {}, self = true,
                    gifts = gifts,
                    giftCallbacks = GiftCallbacks({}, { more++ }, onToggleSaved = { gift, saved -> toggles += gift.key to saved }, onOpenCatalogue = {}),
                )
            }
        }
        scrollToOnGifts("giftsMore")
        compose.onNodeWithTag("giftsMore").performClick()
        assertEquals(1, more)
        gifts = ProfileGifts(GiftPage(3, listOf(teddy, pepe, hidden), null))
        compose.waitForIdle()
        scrollToOnGifts("gift_giftm72")
        compose.onNodeWithTag("gift_giftm72").performClick()
        compose.onNodeWithText("Hidden sender").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("giftConvert").performScrollTo().assertTextEquals("Can be converted to 40 stars")
        compose.onNodeWithTag("giftShow").performScrollTo().performClick()
        assertEquals(listOf("giftm72" to true), toggles)
        compose.onNodeWithTag("giftSheet").assertIsDisplayed()
    }

    @Test
    fun catalogueShowsPricesAndThatPaymentsAreLeft() {
        var catalogues = 0
        var gifts by mutableStateOf(ProfileGifts(GiftPage(1, listOf(teddy), null)))
        compose.setContent {
            MioTheme {
                PeerProfileScreen(
                    me, "", emptyMap(), busy = false, onBack = {}, onMessage = {}, onMute = {}, onUnmute = {}, self = true,
                    gifts = gifts,
                    giftCallbacks = GiftCallbacks({}, {}, onToggleSaved = { _, _ -> }, onOpenCatalogue = { catalogues++ }),
                )
            }
        }
        scrollToOnGifts("giftCatalogue")
        compose.onNodeWithTag("giftCatalogue").performClick()
        assertEquals(1, catalogues)
        gifts = gifts.copy(catalogue = listOf(CatalogueGift(61, null, 50, 40, limited = true, remains = 120, total = 1_000), CatalogueGift(63, null, 2_500, 2_000, limited = true, soldOut = true)))
        compose.waitForIdle()
        compose.onNodeWithTag("giftPaymentsLeft").assertIsDisplayed()
        compose.onNode(hasText("50 stars") and hasAnyAncestor(hasTestTag("catalogue_61"))).assertIsDisplayed()
        compose.onNodeWithText("120 of 1000 left").assertIsDisplayed()
        compose.onNodeWithText("Sold out").assertIsDisplayed()
    }

    @Test
    fun giftServiceMessagesAreCenteredCards() {
        val messages = listOf(
            MessageItem(1, "", out = false, date = 1_790_000_000, senderName = "Alice", service = ServiceKind.Gift, gift = GiftAction(null, 50, message = "for you")),
            MessageItem(2, "", out = true, date = 1_790_000_001, service = ServiceKind.Gift, gift = GiftAction(null, 0, unique = pepe.unique)),
        )
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Alice", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {})
            }
        }
        compose.onNodeWithTag("giftChip_1").assertIsDisplayed()
        compose.onNodeWithText("Alice sent a gift").assertIsDisplayed()
        compose.onNodeWithText("50 stars").assertIsDisplayed()
        compose.onNodeWithText("for you").assertIsDisplayed()
        compose.onNodeWithText("You sent a gift").assertIsDisplayed()
        compose.onNodeWithText("Plush Pepe #123").assertIsDisplayed()
    }
}
