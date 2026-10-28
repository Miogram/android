package cn.ellerotta.core.ui.components.profile

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.LinkPreview
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.MusicTrack
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.SharedList
import cn.ellerotta.core.model.SharedMedia
import cn.ellerotta.core.model.SharedTab
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme
import cn.ellerotta.core.ui.components.common.previewPhoto
import cn.ellerotta.core.ui.components.profile.ProfilePreviewData as Data

private object SharedPreviewData {
    const val NOW = 1_790_000_000
    private const val DAY = 86_400

    private fun photo(n: Int, wide: Boolean = n % 3 == 0) = MediaItem(
        key = "photo$n", fileName = "photo$n.jpg", size = 120_000, dcId = 2, isPhoto = true, kind = MediaKind.Photo,
        thumb = previewPhoto(40, 40, start = 0xFF000000.toInt() or (0x2A3F7A + n * 0x0B1409), end = 0xFF000000.toInt() or (0xE86A9A - n * 0x050A03)),
        width = if (wide) 1600 else 900, height = 900,
    )

    private fun video(n: Int) = MediaItem(
        key = "video$n", fileName = "clip$n.mp4", size = 3_000_000, dcId = 2, isPhoto = false, kind = MediaKind.Video, duration = 12 + n * 7,
        thumb = previewPhoto(40, 40, start = 0xFF1B5E20.toInt(), end = 0xFFFFC107.toInt()), width = 1280, height = 720,
    )

    private fun message(id: Int, date: Int, media: MediaItem? = null, text: String = "", entities: List<TextEntity> = emptyList(), link: LinkPreview? = null, out: Boolean = false, name: String? = null) =
        MessageItem(id, text, out, date, media = media, entities = entities, link = link, senderName = name)

    val media: List<MessageItem> = buildList {
        var id = 120
        repeat(7) { n -> add(message(id--, NOW - n * 2 * DAY, if (n % 4 == 3) video(n) else photo(n))) }
        repeat(4) { n -> add(message(id--, NOW - 40 * DAY - n * DAY, photo(10 + n))) }
        repeat(3) { n -> add(message(id--, NOW - 400 * DAY - n * DAY, if (n == 1) video(20 + n) else photo(20 + n))) }
    }

    val files: List<MessageItem> = listOf(
        message(90, NOW - DAY, MediaItem("doc1", "Quarterly report.pdf", 2_400_000, 2, isPhoto = false, kind = MediaKind.File)),
        message(89, NOW - 3 * DAY, MediaItem("doc2", "holiday-photos-final-final-v2-for-printing.zip", 85_000_000, 2, isPhoto = false, kind = MediaKind.File)),
        message(88, NOW - 12 * DAY, MediaItem("doc3", "notes.txt", 1_200, 2, isPhoto = false, kind = MediaKind.File)),
        message(87, NOW - 30 * DAY, MediaItem("doc4", "scan.jpg", 4_100_000, 2, isPhoto = true, kind = MediaKind.File)),
    )

    private const val LINK = "https://core.telegram.org/api/files"
    private const val BARE = "https://example.org/some/rather/long/path/that/goes/on?and=on&on=and-on"

    val links: List<MessageItem> = listOf(
        message(80, NOW - DAY, text = "read $LINK first", entities = listOf(TextEntity(5, LINK.length, EntityType.Url)), link = LinkPreview(LINK, "Telegram APIs", "Uploading and Downloading Files", "How to transfer large data batches correctly.", photo(1))),
        message(79, NOW - 5 * DAY, text = "this too $BARE", entities = listOf(TextEntity(9, BARE.length, EntityType.Url))),
        message(78, NOW - 9 * DAY, text = "two: $LINK and $BARE", entities = listOf(TextEntity(5, LINK.length, EntityType.Url), TextEntity(14 + LINK.length, BARE.length, EntityType.Url)), link = LinkPreview(LINK, "Telegram APIs", "Files")),
    )

    fun song(n: Int, title: String) = MediaItem("song$n", "$title.mp3", 4_000_000, 2, isPhoto = false, kind = MediaKind.Music, title = title, performer = if (n == 3) null else "The Looms", duration = 150 + n * 37)

    val music: List<MessageItem> = listOf(
        message(70, NOW - DAY, song(1, "Analytical Engine")),
        message(69, NOW - 4 * DAY, song(2, "Difference Engine, a very long title that wraps")),
        message(68, NOW - 20 * DAY, song(3, "Untitled demo")),
    )

    private fun voice(n: Int) = MediaItem("voice$n", "voice$n.ogg", 20_000, 2, isPhoto = false, kind = MediaKind.Voice, duration = 12 + n * 9, waveform = List(40) { (it * 7 + n * 5) % 32 })

    private fun round(n: Int) = MediaItem("round$n", "round$n.mp4", 90_000, 2, isPhoto = false, kind = MediaKind.Round, duration = 7 + n, thumb = previewPhoto(40, 40, start = 0xFF6A1B9A.toInt(), end = 0xFFFF7043.toInt()), width = 240, height = 240)

    val voices: List<MessageItem> = listOf(
        message(60, NOW - 2 * DAY, voice(1), name = "Maria"),
        message(59, NOW - 3 * DAY, round(1), name = "Maria"),
        message(58, NOW - 8 * DAY, voice(3), out = true),
        message(57, NOW - 9 * DAY, voice(4), name = "Maria"),
    )

    private fun gif(n: Int) = MediaItem("gif$n", "loop$n.mp4", 90_000, 2, isPhoto = false, kind = MediaKind.Gif, duration = 2, thumb = previewPhoto(40, 40, start = 0xFFE91E63.toInt() + n * 0x0A0A0A, end = 0xFF3F51B5.toInt()), width = 320, height = 240)

    val gifs: List<MessageItem> = List(5) { n -> message(50 - n, NOW - n * 3 * DAY, gif(n)) }

    val groups = listOf(
        DialogItem("ch4004", "Kotlin devs", kind = PeerKind.Group, peerId = 4004, members = 12_400),
        DialogItem("c3003", "Book club", kind = PeerKind.Group, peerId = 3003, members = 18),
        DialogItem("ch4005", "Weekly news", kind = PeerKind.Channel, peerId = 4005, members = 52_000),
    )

    val counts = mapOf(SharedTab.Media to 14, SharedTab.Files to 4, SharedTab.Links to 3, SharedTab.Music to 3, SharedTab.Voice to 4, SharedTab.Gifs to 5, SharedTab.Groups to 3)

    fun shared(tab: SharedTab, loading: Boolean = false, failed: Boolean = false): SharedMedia {
        val items = when (tab) {
            SharedTab.Media -> media
            SharedTab.Files -> files
            SharedTab.Links -> links
            SharedTab.Music -> music
            SharedTab.Voice -> voices
            SharedTab.Gifs -> gifs
            SharedTab.Groups -> emptyList()
        }
        return SharedMedia(
            counts = counts,
            chosen = tab,
            lists = mapOf(tab to SharedList(items = if (loading) emptyList() else items, complete = !loading && !failed, loading = loading, failed = failed)),
            common = if (tab == SharedTab.Groups) groups else emptyList(),
            commonComplete = true,
        )
    }

    fun playing(message: MessageItem, buffering: Boolean = false) =
        MusicState(listOf(MusicTrack("u2002", message.id, message.media!!)), index = 0, playing = true, buffering = buffering, positionMs = 42_000, durationMs = (message.media!!.duration * 1000).toLong())

    val actions = SharedMediaActions({}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, { _, _, _, _ -> }, { _, _ -> })
}

@Composable
private fun SharedHost(shared: SharedMedia, music: MusicState? = null) {
    val tabs = shared.tabs.map { ProfileTab.Shared(it) }
    val pager = rememberPagerState(initialPage = tabs.indexOfFirst { it.tab == shared.tab }.coerceAtLeast(0)) { tabs.size }
    val list = rememberLazyListState()
    val scroll = remember(list) { ProfileScroll(list) { null } }
    PreviewTheme {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            ProfilePages(tabs, pager, maxHeight, scroll, onTabClick = {}) { page ->
                val tab = (page as ProfileTab.Shared).tab
                SharedPage(tab, shared, rememberLazyListState(), PaddingValues(top = 4.dp, bottom = 24.dp), "u2002", SharedPreviewData.actions, emptyMap(), emptyMap(), music, emptyMap(), onMenu = {})
            }
        }
    }
}

@MioPreviews
@Composable
internal fun SharedMediaGridPreview() = SharedHost(SharedPreviewData.shared(SharedTab.Media))

@MioPreviews
@Composable
internal fun SharedFilesPreview() = SharedHost(SharedPreviewData.shared(SharedTab.Files))

@MioPreviews
@Composable
internal fun SharedLinksPreview() = SharedHost(SharedPreviewData.shared(SharedTab.Links))

@MioPreviews
@Composable
internal fun SharedMusicPreview() = SharedHost(SharedPreviewData.shared(SharedTab.Music), music = SharedPreviewData.playing(SharedPreviewData.music[1]))

@MioPreviews
@Composable
internal fun SharedMusicBufferingPreview() = SharedHost(SharedPreviewData.shared(SharedTab.Music), music = SharedPreviewData.playing(SharedPreviewData.music[0], buffering = true))

@MioPreviews
@Composable
internal fun SharedVoicePreview() = SharedHost(SharedPreviewData.shared(SharedTab.Voice), music = SharedPreviewData.playing(SharedPreviewData.voices[0]))

@MioPreviews
@Composable
internal fun SharedGifsPreview() = SharedHost(SharedPreviewData.shared(SharedTab.Gifs))

@MioPreviews
@Composable
internal fun SharedGroupsPreview() = SharedHost(SharedPreviewData.shared(SharedTab.Groups))

@MioPreviews
@Composable
internal fun SharedLoadingPreview() = SharedHost(SharedPreviewData.shared(SharedTab.Files, loading = true))

@MioPreviews
@Composable
internal fun SharedFailedPreview() = SharedHost(SharedPreviewData.shared(SharedTab.Files, failed = true))

@MioPreviews
@Composable
internal fun SharedMenuPreview() {
    val state = remember { SharedMenuState().also { it.menu = SharedPreviewData.files[0] } }
    PreviewTheme {
        SharedMessageMenus(state, SharedPreviewData.actions, emptyList(), emptyMap(), canDelete = { true }, revokeLabel = { null })
    }
}

@MioPreviews
@Composable
internal fun ProfileTabsPreview() {
    PreviewTheme {
        PeerProfileScreen(
            profile = Data.userProfile,
            title = Data.userProfile.name,
            avatars = Data.avatars,
            busy = false,
            onBack = {},
            onMessage = {},
            onMute = {},
            onUnmute = {},
            gifts = Data.gifts,
            giftCallbacks = Data.giftCallbacks,
            shared = SharedPreviewData.shared(SharedTab.Media),
            sharedActions = SharedPreviewData.actions,
            now = Data.NOW_MILLIS,
        )
    }
}
