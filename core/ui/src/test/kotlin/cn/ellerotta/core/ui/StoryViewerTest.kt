package cn.ellerotta.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.PeerStories
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.ui.components.stories.StoryViewer
import cn.ellerotta.core.ui.components.stories.storyScale
import cn.ellerotta.core.ui.theme.MioTheme
import java.io.File
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp")
class StoryViewerTest {
    @get:Rule
    val compose = createComposeRule()

    private val file = File.createTempFile("story", ".jpg").apply { deleteOnExit() }

    private val scales = mutableMapOf<String, ContentScale>()
    private val players = mutableSetOf<String>()

    private fun photo(id: Int) = MediaItem("story$id", "story$id.jpg", 1_000, 2, isPhoto = true, kind = MediaKind.Photo, width = 720, height = 1280)

    private fun story(id: Int, media: MediaItem? = photo(id), caption: String = "", entities: List<TextEntity> = emptyList()) =
        StoryItem(id, date = 1_790_000_000, expireDate = 1_790_086_400, media = media, caption = caption, entities = entities)

    private val alice = PeerStories("u1", "Alice", peerId = 1, stories = listOf(story(1), story(2), story(3)), maxReadId = 1)
    private val bob = PeerStories("u2", "Bob", peerId = 2, stories = listOf(story(11), story(12)))

    private fun done(vararg peers: PeerStories): Map<String, DownloadState> =
        peers.flatMap { it.stories }.mapNotNull { it.media }.associate { it.key to DownloadState.Done(file) }

    private class Events {
        val shown = mutableListOf<Pair<String, Int>>()
        val links = mutableListOf<String>()
        var closed = 0
    }

    private fun show(peers: List<PeerStories>, downloads: Map<String, DownloadState> = emptyMap()): Events {
        val events = Events()
        compose.setContent {
            MioTheme {
                StoryViewer(
                    peers = peers,
                    initialPeer = 0,
                    avatars = emptyMap(),
                    downloads = downloads,
                    onShown = { peer, story -> events.shown += peer.key to story.id },
                    onClose = { events.closed++ },
                    video = { media, playing, scale, modifier ->
                        scales[media.key] = scale
                        DisposableEffect(media.key) {
                            players += media.key
                            onDispose { players -= media.key }
                        }
                        Box(modifier.testTag("video_${media.key}_$playing"))
                    },
                    onOpenLink = { events.links += it },
                )
            }
        }
        return events
    }

    private fun showing(peer: String, story: Int) = compose.onNodeWithTag("story_${peer}_$story").assertExists()

    private fun tap(fraction: Float) = compose.onNodeWithTag("storyViewer").performTouchInput { click(Offset(width * fraction, centerY)) }

    private fun AnnotatedString.styleAt(index: Int): SpanStyle =
        spanStyles.filter { index >= it.start && index < it.end }.fold(SpanStyle()) { merged, range -> merged.merge(range.item) }

    @Test
    fun startsAtTheFirstUnreadStoryAndReportsIt() {
        val events = show(listOf(alice, bob))
        compose.waitForIdle()
        showing("u1", 2)
            .assert(hasContentDescription("Alice, story 2 of 3", substring = true))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        val bars = compose.onAllNodesWithTag("storyBars", useUnmergedTree = true).assertCountEquals(2)
        bars[0].assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        bars[1].assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        compose.onAllNodesWithTag("storyLoading").onFirst().assertExists()
        assertEquals(listOf("u1" to 2), events.shown)
    }

    @Test
    fun aPhotoStillDownloadingDoesNotMoveOn() {
        compose.mainClock.autoAdvance = false
        show(listOf(alice))
        compose.mainClock.advanceTimeBy(10_000)
        showing("u1", 2)
    }

    @Test
    fun aDownloadedPhotoMovesOnAfterSixSeconds() {
        compose.mainClock.autoAdvance = false
        val events = show(listOf(alice), done(alice))
        compose.mainClock.advanceTimeBy(1_000)
        showing("u1", 2)
        compose.mainClock.advanceTimeBy(5_500)
        showing("u1", 3)
        assertEquals(listOf("u1" to 2, "u1" to 3), events.shown)
        compose.mainClock.advanceTimeBy(6_500)
        assertEquals(1, events.closed)
    }

    @Test
    fun tapsMoveBetweenStoriesAndPeers() {
        val events = show(listOf(alice, bob))
        tap(0.8f)
        showing("u1", 3)
        tap(0.1f)
        showing("u1", 2)
        tap(0.1f)
        showing("u1", 1)
        tap(0.1f)
        showing("u1", 1)
        repeat(3) { tap(0.8f) }
        compose.waitForIdle()
        showing("u2", 11)
        assertEquals(listOf("u1" to 2, "u1" to 3, "u1" to 2, "u1" to 1, "u1" to 2, "u1" to 3, "u2" to 11), events.shown)
        assertEquals(0, events.closed)
    }

    @Test
    fun aTapAtTheLastStoryOfTheLastPeerCloses() {
        val events = show(listOf(bob))
        tap(0.8f)
        showing("u2", 12)
        assertEquals(0, events.closed)
        tap(0.8f)
        compose.waitForIdle()
        assertEquals(1, events.closed)
    }

    @Test
    fun holdingPausesTheStory() {
        compose.mainClock.autoAdvance = false
        show(listOf(alice), done(alice))
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("storyViewer").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(10_000)
        showing("u1", 2)
        compose.onNodeWithTag("storyViewer").performTouchInput { up() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        showing("u1", 2)
        compose.mainClock.advanceTimeBy(5_000)
        showing("u1", 3)
    }

    @Test
    fun videosPlayUntilHeld() {
        compose.mainClock.autoAdvance = false
        val clip = MediaItem("clip", "clip.mp4", 5_000_000, 2, isPhoto = false, kind = MediaKind.Video, width = 720, height = 1280, duration = 10)
        show(listOf(PeerStories("u3", "Carol", peerId = 3, stories = listOf(story(21, clip)))))
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("video_clip_true").assertExists()
        compose.onNodeWithTag("storyViewer").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("video_clip_false").assertExists()
        compose.onNodeWithTag("storyViewer").performTouchInput { up() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("video_clip_true").assertExists()
    }

    @Test
    fun mediaKeepsItsShape() {
        assertEquals(ContentScale.Crop, storyScale(720, 1280, 1080, 2410))
        assertEquals(ContentScale.Crop, storyScale(1080, 2410, 1080, 2410))
        assertEquals(ContentScale.Fit, storyScale(960, 1280, 1080, 2410))
        assertEquals(ContentScale.Fit, storyScale(1080, 1080, 1080, 2410))
        assertEquals(ContentScale.Fit, storyScale(1280, 720, 1080, 2410))
        assertEquals(ContentScale.Fit, storyScale(0, 0, 1080, 2410))
        val portrait = MediaItem("tall", "tall.mp4", 5_000_000, 2, isPhoto = false, kind = MediaKind.Video, width = 720, height = 1280, duration = 10)
        val landscape = MediaItem("wide", "wide.mp4", 5_000_000, 2, isPhoto = false, kind = MediaKind.Video, width = 1280, height = 720, duration = 10)
        compose.mainClock.autoAdvance = false
        show(listOf(PeerStories("u3", "Carol", peerId = 3, stories = listOf(story(21, portrait), story(22, landscape)))))
        compose.mainClock.advanceTimeBy(100)
        showing("u3", 21)
        assertEquals(ContentScale.Crop, scales["tall"])
        compose.onNodeWithTag("storyBackdrop").assertDoesNotExist()
        tap(0.8f)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        showing("u3", 22)
        assertEquals(ContentScale.Fit, scales["wide"])
        compose.onNodeWithTag("storyBackdrop").assertExists()
    }

    @Test
    fun swipingSidewaysShowsTheNextPeer() {
        val events = show(listOf(alice, bob))
        compose.onNodeWithTag("storyViewer").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        showing("u2", 11)
        assertEquals(listOf("u1" to 2, "u2" to 11), events.shown)
    }

    @Test
    fun onlyTheCurrentPageHasAVideo() {
        compose.mainClock.autoAdvance = false
        fun clip(key: String) = MediaItem(key, "$key.mp4", 5_000_000, 2, isPhoto = false, kind = MediaKind.Video, width = 720, height = 1280, duration = 10)
        val carol = PeerStories("u3", "Carol", peerId = 3, stories = listOf(story(21, clip("c"))))
        val dave = PeerStories("u4", "Dave", peerId = 4, stories = listOf(story(31, clip("d"))))
        show(listOf(carol, dave))
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("storyPage_u4").assertExists()
        assertEquals(setOf("c"), players)
        compose.onNodeWithTag("video_c_true").assertExists()
        compose.onNodeWithTag("storyViewer").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        showing("u4", 31)
        assertEquals(setOf("d"), players)
    }

    @Test
    fun swipingDownCloses() {
        val events = show(listOf(alice, bob))
        compose.onNodeWithTag("storyViewer").performTouchInput { swipeDown(startY = centerY, endY = bottom) }
        compose.waitForIdle()
        assertEquals(1, events.closed)
        showing("u1", 2)
    }

    @Test
    fun captionsShowEntitiesAndOpenLinks() {
        val text = "bold and docs"
        val url = "https://core.telegram.org/api/stories"
        val entities = listOf(TextEntity(0, 4, EntityType.Bold), TextEntity(text.indexOf("docs"), 4, EntityType.TextUrl(url)))
        val events = show(listOf(PeerStories("u3", "Carol", peerId = 3, stories = listOf(story(21, caption = text, entities = entities)))))
        val caption = compose.onNodeWithTag("storyCaption_21", useUnmergedTree = true)
        val shown = caption.fetchSemanticsNode().config[SemanticsProperties.Text].first()
        assertEquals(text, shown.text)
        assertEquals(FontWeight.Bold, shown.styleAt(0).fontWeight)
        assertEquals(null, shown.styleAt(text.indexOf("and")).fontWeight)
        caption.performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == url }
        compose.waitForIdle()
        assertEquals(listOf(url), events.links)
    }
}
