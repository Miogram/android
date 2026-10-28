package cn.ellerotta.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.SharedList
import cn.ellerotta.core.model.SharedMedia
import cn.ellerotta.core.model.SharedTab
import cn.ellerotta.core.ui.components.profile.SharedMediaActions
import cn.ellerotta.core.model.GiftCollection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import cn.ellerotta.core.model.GiftBackdrop
import cn.ellerotta.core.model.GiftPage
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.ProfileDetails
import cn.ellerotta.core.model.ProfileGifts
import cn.ellerotta.core.model.SavedGift
import cn.ellerotta.core.model.UniqueGift
import cn.ellerotta.core.model.ChannelPost
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.PersonalChannel
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.ui.components.profile.GiftCallbacks
import cn.ellerotta.core.ui.components.profile.PeerProfileScreen
import cn.ellerotta.core.ui.components.profile.giftSparkles
import cn.ellerotta.core.ui.components.profile.giftSpots
import cn.ellerotta.core.ui.components.profile.profileDecoration
import cn.ellerotta.core.ui.components.profile.PatternDrawn
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import cn.ellerotta.core.ui.theme.MioTheme
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PeerProfileTest {
    @get:Rule
    val compose = createComposeRule()

    private val profile = ProfileDetails(
        key = "u7", id = 3034249, name = "Ada Lovelace", kind = PeerKind.User, presence = Presence.Recently,
        avatarKey = "a7", phone = "79991234567", usernames = listOf("ada"), about = "first programmer", colorIndex = 5,
        emojiStatusId = 11, patternEmojiId = 12,
        music = MediaItem("doc20", "song.mp3", 10, 2, isPhoto = false, kind = MediaKind.Music, title = "Analytical Engine", performer = "The Looms", thumb = png(0xFF3FA65A.toInt())),
        channel = PersonalChannel(
            "ch77", "Engine Notes", avatarKey = "a8", verified = true, subscribers = 1245,
            post = ChannelPost(41, 1_790_000_000, "Notes on the engine: the mill and the store\nsecond line, third line", listOf(TextEntity(0, 5, EntityType.Bold), TextEntity(21, 8, EntityType.Italic))),
        ),
        dcId = 2,
    )

    private val status = MediaItem("doc11", "emoji", 10, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "\u2B50")
    private val pattern = MediaItem("doc12", "emoji", 10, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "\u2693", textColor = true)
    private val giftSticker = MediaItem("doc13", "gift", 10, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "\uD83E\uDDF8")
    private val gifts = ProfileGifts(
        GiftPage(
            2,
            listOf(
                SavedGift("giftm1", 62, giftSticker, 0, 0, title = "Plush Pepe", unique = UniqueGift("Plush Pepe", "pepe", 123, backdrop = GiftBackdrop("Mint", 0x7ED67E, 0x3FA65A, 0x2A9FA8, 0xFFFFFF, null), id = 77), pinned = true),
                SavedGift("giftm2", 61, giftSticker, 50, 0, title = "Teddy", pinned = true),
            ),
            null,
        ),
    )
    private val files = mapOf("a7" to png(0xFF8C86FF.toInt()), "a8" to png(0xFF202020.toInt()), "doc11" to png(0xFFFFB85C.toInt()), "doc12" to png(0xFF000000.toInt()), "doc13" to png(0xFFE0455C.toInt()))

    @Composable
    private fun Screen(
        onOpenPhoto: () -> Unit = {},
        onOpenChannel: (PersonalChannel) -> Unit = {},
        profile: ProfileDetails = this.profile,
        gifts: ProfileGifts = this.gifts,
        shared: SharedMedia = SharedMedia(),
        giftCallbacks: GiftCallbacks = GiftCallbacks({}, {}),
        onTab: (SharedTab) -> Unit = {},
    ) {
        PeerProfileScreen(
            profile, "Ada Lovelace", mapOf("a7" to files.getValue("a7"), "a8" to files.getValue("a8")), busy = false, onBack = {}, onMessage = {}, onMute = {}, onUnmute = {},
            gifts = gifts, stickers = files, giftCallbacks = giftCallbacks,
            customEmoji = mapOf(11L to status, 12L to pattern), onOpenPhoto = onOpenPhoto, onSearch = {}, onOpenChannel = onOpenChannel,
            shared = shared, sharedActions = SharedMediaActions(onTab, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, { _, _, _, _ -> }, { _, _ -> }),
        )
    }

    private val manyGifts = ProfileGifts(GiftPage(12, List(12) { SavedGift("gift$it", 60L + it, giftSticker, 50, 0, title = "Teddy") }, null))

    private fun photo(n: Int) = MessageItem(100 - n, "", false, 1_790_000_000 - n * 86_400, media = MediaItem("photo$n", "p.jpg", 10, 2, isPhoto = true, kind = MediaKind.Photo, thumb = png(0xFF3F7AE0.toInt() + n * 0x0B1409)))

    private val photos = SharedMedia(
        counts = mapOf(SharedTab.Media to 9, SharedTab.Files to 0, SharedTab.Links to 2),
        lists = mapOf(SharedTab.Media to SharedList(List(9, ::photo), complete = true)),
    )

    @Test
    fun nothingCoversTheCardsOrTheGifts() {
        var card = 0
        var surface = 0
        compose.setContent {
            MioTheme(darkTheme = false, useDynamicColor = false) {
                card = MaterialTheme.colorScheme.surfaceContainer.toArgb()
                surface = MaterialTheme.colorScheme.surface.toArgb()
                Screen(gifts = manyGifts, shared = photos)
            }
        }
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasTestTag("datacenter"))
        assertDrawn("datacenter", surface, card) { DpOffset((it.left + it.right) / 2, it.bottom - 4.dp) }
        assertDrawn("datacenter", surface, surface, page = true) { DpOffset(8.dp, it.bottom - 4.dp) }
        compose.onNodeWithTag("peerProfile").performScrollToKey("pages")
        assertEquals(72f, compose.onNodeWithTag("profileTabs").getUnclippedBoundsInRoot().top.value, 0.5f)
        assertDrawn("gift_gift0", surface) { DpOffset((it.left + it.right) / 2, it.bottom - 6.dp) }
        compose.onNodeWithTag("profilePage_Gifts").performScrollToNode(hasTestTag("gift_gift11"))
        val last = compose.onNodeWithTag("gift_gift11").getUnclippedBoundsInRoot()
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue(last.bottom <= screen.bottom, "the last gift ends at ${last.bottom}, the screen at ${screen.bottom}")
        assertDrawn("gift_gift11", surface) { DpOffset((it.left + it.right) / 2, it.bottom - 6.dp) }
    }

    private fun assertDrawn(tag: String, surface: Int, expected: Int? = null, page: Boolean = false, spot: (DpRect) -> DpOffset) {
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val point = spot(bounds)
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val x = with(compose.density) { point.x.roundToPx() }
        val y = with(compose.density) { point.y.roundToPx() }
        assertTrue(y in 0 until image.height, "$tag ends at ${bounds.bottom}, off the screen")
        val pixel = image.getPixel(x, y)
        if (!page) assertTrue(pixel != surface, "$tag is covered at $point")
        expected?.let { assertEquals(Integer.toHexString(it), Integer.toHexString(pixel), "$tag at $point") }
    }

    @Test
    fun tabsShowWhatHoldsSomethingTheGiftsFirst() {
        var gifts by mutableStateOf(manyGifts)
        var callbacks by mutableStateOf(GiftCallbacks({}, {}))
        compose.setContent { MioTheme { Screen(gifts = gifts, shared = photos, giftCallbacks = callbacks) } }
        compose.onNodeWithTag("peerProfile").performScrollToKey("pages")
        assertEquals(listOf("Gifts", "Media", "Links"), tabOrder())
        compose.onNodeWithTag("profileTab_Gifts").assertIsSelected()
        compose.onNodeWithTag("profilePage_Gifts").assertIsDisplayed()
        gifts = ProfileGifts(GiftPage(0, emptyList(), null))
        compose.waitForIdle()
        assertEquals(listOf("Media", "Links"), tabOrder())
        compose.onNodeWithTag("profileTab_Media").assertIsSelected()
        callbacks = GiftCallbacks({}, {}, onToggleSaved = { _, _ -> }, onOpenCatalogue = {})
        compose.waitForIdle()
        assertEquals(listOf("Media", "Links", "Gifts"), tabOrder())
        compose.onNodeWithTag("profileTab_Media").assertIsSelected()
    }

    private fun tabOrder(): List<String> =
        compose.onAllNodes(SemanticsMatcher("profile tab") { node -> node.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith("profileTab_") == true })
            .fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.left }
            .map { it.config[SemanticsProperties.TestTag].removePrefix("profileTab_") }

    @Test
    fun aSwipeAndATapChangeTheTab() {
        val tabs = mutableListOf<SharedTab>()
        compose.setContent { MioTheme { Screen(gifts = manyGifts, shared = photos, onTab = { tabs += it }) } }
        compose.onNodeWithTag("peerProfile").performScrollToKey("pages")
        compose.onNodeWithTag("profileTab_Gifts").assertIsSelected()
        compose.onNodeWithTag("profilePages").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("profileTab_Media").assertIsSelected()
        compose.onNodeWithTag("profileTab_Gifts").assertIsNotSelected()
        compose.onNodeWithTag("profilePage_Media").assertIsDisplayed()
        compose.onNodeWithTag("profileTab_Links").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("profileTab_Links").assertIsSelected()
        compose.onNodeWithTag("profilePage_Links").assertIsDisplayed()
        assertEquals(listOf(SharedTab.Media, SharedTab.Links), tabs)
        compose.onNodeWithTag("profilePages").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithTag("profileTab_Media").assertIsSelected()
    }

    @Test
    fun theProfileAndAPageScrollAsOne() {
        val lots = ProfileGifts(GiftPage(30, List(30) { SavedGift("gift$it", 60L + it, giftSticker, 50, 0, title = "Teddy") }, null))
        compose.setContent { MioTheme { Screen(gifts = lots, shared = photos) } }
        compose.onNodeWithTag("peerProfile").performScrollToKey("pages")
        compose.onRoot().performTouchInput { swipeDown(startY = centerY, endY = centerY + 300.dp.toPx(), durationMillis = 1500) }
        compose.waitForIdle()
        val before = rowTop()
        assertTrue(before > 200.dp, "the row is at $before")
        compose.onRoot().performTouchInput { swipeUp(startY = bottom - 40f, endY = bottom - 40f - 120.dp.toPx(), durationMillis = 1500) }
        compose.waitForIdle()
        val after = rowTop()
        assertTrue(after < before - 80.dp, "the row went from $before to $after")
        compose.onNodeWithTag("gift_gift0").assertIsDisplayed()
        compose.onRoot().performTouchInput { swipeUp(startY = (after - 20.dp).toPx(), endY = (after - 170.dp).toPx(), durationMillis = 60) }
        compose.waitForIdle()
        assertEquals(72f, rowTop().value, 0.5f)
        compose.onNodeWithTag("gift_gift0").assertIsNotDisplayed()
    }

    private fun rowTop() = compose.onNodeWithTag("profileTabs").getUnclippedBoundsInRoot().top

    @Test
    fun detailsShowThePersonalChannelAndTheDatacenter() {
        val opened = mutableListOf<String>()
        compose.setContent { MioTheme { Screen(onOpenChannel = { opened += it.key }) } }
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasTestTag("personalChannel"))
        compose.onNodeWithTag("channelSubscribers", useUnmergedTree = true).assertTextEquals("1,245 subscribers")
        compose.onNodeWithTag("channelTitle", useUnmergedTree = true).assertTextEquals("Engine Notes")
        compose.onNodeWithTag("channelPost", useUnmergedTree = true).assertTextContains("the mill", substring = true)
        compose.onNodeWithTag("personalChannel").performClick()
        assertEquals(listOf("ch77"), opened)
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasTestTag("datacenter"))
        compose.onNodeWithTag("datacenter").assertTextContains("DC 2")
        compose.onNodeWithText("@ada").assertExists()
    }

    @Test
    fun aShortChannelTitleLeavesThePostDateAtTheEnd() {
        val short = profile.copy(channel = profile.channel?.copy(title = "Ada", verified = false))
        compose.setContent { MioTheme { Screen(profile = short) } }
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasTestTag("personalChannel"))
        val card = compose.onNodeWithTag("personalChannel").getUnclippedBoundsInRoot()
        val date = compose.onNodeWithTag("channelPostDate", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue((card.right - date.right).value <= 36.5f, "the date ends at ${date.right}, the card at ${card.right}")
    }

    @Test
    fun headerShowsStatusPatternAndPinnedGifts() {
        var photos = 0
        compose.setContent { MioTheme { Screen(onOpenPhoto = { photos++ }) } }
        compose.onNodeWithTag("profileName").assertIsDisplayed()
        compose.onNodeWithTag("emojiStatus", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("profilePattern").assertExists()
        compose.onNodeWithTag("pinnedGift_giftm1").assertIsDisplayed()
        compose.onNodeWithTag("pinnedGift_giftm2").assertDoesNotExist()
        compose.onNodeWithTag("search").assertIsDisplayed()
        compose.onNodeWithTag("musicTitle", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("profileAvatar").performClick()
        assertEquals(1, photos)
        compose.onNodeWithTag("pinnedGift_giftm1").performClick()
        compose.onNodeWithTag("giftTitle").assertIsDisplayed()
    }

    @Test
    fun aGiftWornAsTheStatusDoesNotFloat() {
        assertEquals(listOf("giftm1"), profileDecoration(profile, emptyMap(), gifts, files).gifts.map { it.key })
        val wearing = profile.copy(collectibleGiftId = 77)
        assertEquals(emptyList(), profileDecoration(wearing, emptyMap(), gifts, files).gifts)
        compose.setContent { MioTheme { Screen(profile = wearing) } }
        compose.onNodeWithTag("profileName").assertIsDisplayed()
        compose.onNodeWithTag("pinnedGift_giftm1").assertDoesNotExist()
    }

    @Test
    fun sparklesStayAroundTheGiftInsideItsBox() {
        repeat(20) { seed ->
            val sparkles = giftSparkles(3, Random(seed))
            sparkles.forEachIndexed { i, sparkle ->
                assertTrue(sparkle.distance - sparkle.size / 2 >= 16f - 0.001f, "$seed: ${sparkle.distance} ${sparkle.size}")
                assertTrue(sparkle.distance + sparkle.size <= 24f + 0.001f, "$seed: ${sparkle.distance} ${sparkle.size}")
                assertTrue(sparkle.angle > 2 * PI.toFloat() / 3 * i && sparkle.angle < 2 * PI.toFloat() / 3 * (i + 1))
            }
        }
    }

    @Test
    fun floatingGiftsKeepApartAndInsideTheHeader() {
        repeat(20) { seed ->
            val spots = giftSpots(12, 412f, Random(seed))
            assertTrue(spots.size >= 6, "$seed: ${spots.size}")
            spots.forEachIndexed { i, a ->
                assertTrue(abs(a.x) + 16f * a.scale <= 206f - 5f)
                spots.drop(i + 1).forEach { b -> assertTrue(hypot(a.x - b.x, a.y - b.y) >= 29f) }
            }
            spots.take(4).forEach { assertTrue(hypot(it.x, it.y) <= 52.5f * 2.2f + 0.01f) }
        }
    }

    @Test
    fun lightScreenshot() = screenshot(dark = false)

    @Test
    fun darkScreenshot() = screenshot(dark = true)

    private fun screenshot(dark: Boolean) {
        val shown = ProfileGifts(GiftPage(14, gifts.page!!.gifts + manyGifts.page!!.gifts, null), collections = listOf(GiftCollection(7, "Bears", giftSticker, 12), GiftCollection(8, "Pepe", null, 1)))
        compose.setContent { MioTheme(darkTheme = dark, useDynamicColor = false) { Screen(gifts = shown, shared = photos) } }
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            val marked = compose.onAllNodes(SemanticsMatcher.keyIsDefined(PatternDrawn), useUnmergedTree = true).fetchSemanticsNodes()
            marked.size == 2 && marked.all { it.config[PatternDrawn] }
        }
        val theme = if (dark) "dark" else "light"
        keep("profile_$theme")
        compose.onNodeWithTag("peerProfile").performScrollToKey("pages")
        keep("profile_gifts_$theme")
        compose.onNodeWithTag("profilePages").performTouchInput { swipeLeft() }
        keep("profile_media_$theme")
    }

    private fun keep(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue(image.width > 0 && image.height > 0)
        System.getenv("MIOGRAM_SCREENSHOTS")?.let { dir ->
            File(dir).mkdirs()
            File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawCircle(32f, 32f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
