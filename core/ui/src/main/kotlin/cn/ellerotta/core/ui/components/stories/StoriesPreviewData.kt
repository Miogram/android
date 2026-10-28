package cn.ellerotta.core.ui.components.stories

import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.StoryAudience
import cn.ellerotta.core.model.PeerStories
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.core.model.StoryViewersPage
import cn.ellerotta.core.model.StoryViews
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.ui.components.common.previewPhoto
import java.io.File
import cn.ellerotta.core.model.StoryViewer as Viewer

internal object StoriesPreviewData {
    private val now: Int get() = (System.currentTimeMillis() / 1000).toInt()

    val avatars: Map<String, ByteArray> by lazy {
        mapOf(
            "a1" to previewPhoto(start = 0xFF5B8DEF.toInt(), end = 0xFF44C2A8.toInt()),
            "a2" to previewPhoto(start = 0xFFE86A9A.toInt(), end = 0xFFF4B04F.toInt()),
            "a3" to previewPhoto(start = 0xFF7C5BEF.toInt(), end = 0xFFE86A9A.toInt()),
            "self" to previewPhoto(start = 0xFF44C2A8.toInt(), end = 0xFF2E6BD9.toInt()),
        )
    }

    private fun photo(id: Int, start: Int, end: Int) = MediaItem(
        key = "story$id",
        fileName = "story$id.jpg",
        size = 120_000,
        dcId = 2,
        isPhoto = true,
        kind = MediaKind.Photo,
        thumb = previewPhoto(width = 45, height = 80, start = start, end = end),
        width = 720,
        height = 1280,
    )

    private fun video(id: Int) = MediaItem(
        key = "story$id",
        fileName = "story$id.mp4",
        size = 2_400_000,
        dcId = 2,
        isPhoto = false,
        kind = MediaKind.Video,
        thumb = previewPhoto(width = 45, height = 80, start = 0xFF2E6BD9.toInt(), end = 0xFF101820.toInt()),
        width = 720,
        height = 1280,
        duration = 12,
        mimeType = "video/mp4",
    )

    private const val CAPTION = "Sunrise over the bay, worth the early start. More photos on the blog"

    val others: PeerStories by lazy {
        PeerStories(
            key = "u1",
            name = "Alice Moreau",
            avatarKey = "a1",
            peerId = 1,
            stories = listOf(
                StoryItem(
                    id = 1,
                    date = now - 3 * 3_600,
                    expireDate = now + 21 * 3_600,
                    media = photo(1, 0xFFF4B04F.toInt(), 0xFFE86A9A.toInt()),
                    caption = CAPTION,
                    entities = listOf(
                        TextEntity(0, 7, EntityType.Bold),
                        TextEntity(CAPTION.indexOf("worth"), 21, EntityType.Italic),
                        TextEntity(CAPTION.indexOf("blog"), 4, EntityType.TextUrl("https://example.com/blog")),
                    ),
                ),
                StoryItem(id = 2, date = now - 20 * 60, expireDate = now + 23 * 3_600, media = video(2)),
            ),
        )
    }

    val own: PeerStories by lazy {
        PeerStories(
            key = "self",
            name = "Robin Park",
            avatarKey = "self",
            peerId = 10,
            self = true,
            stories = listOf(
                StoryItem(
                    id = 5,
                    date = now - 50 * 60,
                    expireDate = now + 23 * 3_600,
                    media = photo(5, 0xFF44C2A8.toInt(), 0xFF5B8DEF.toInt()),
                    caption = "First day at the new studio",
                    out = true,
                    audience = StoryAudience.Contacts,
                    views = StoryViews(views = 128, reactions = 24, recent = listOf("a1", "a2", "a3"), hasViewers = true),
                ),
            ),
        )
    }

    val liked: StoryItem by lazy { others.stories.first().copy(reaction = StoryHeart) }

    val downloads: Map<String, DownloadState> by lazy {
        (others.stories + own.stories).mapNotNull { it.media }.filter { it.isPhoto }.associate { it.key to DownloadState.Done(File("preview/${it.fileName}")) }
    }

    val downloading: Map<String, DownloadState> by lazy {
        mapOf(others.stories.first().media!!.key to DownloadState.Progress(downloaded = 48_000, total = 120_000))
    }

    val viewers: StoryViewersPage by lazy {
        StoryViewersPage(
            viewers = listOf(
                Viewer("u1", "Alice Moreau", avatarKey = "a1", peerId = 1, date = now - 5 * 60, reaction = StoryHeart),
                Viewer("u2", "Bruno Silva", avatarKey = "a2", peerId = 2, date = now - 25 * 60, reaction = Reaction.Emoji("\uD83D\uDD25")),
                Viewer("u3", "Chen Wei", avatarKey = "a3", peerId = 3, date = now - 40 * 60),
                Viewer("u4", "Dana Novak", peerId = 4, date = now - 26 * 3_600, reaction = Reaction.Emoji("\uD83D\uDC4F")),
                Viewer("u5", "Emil Larsen", peerId = 5, date = now - 27 * 3_600),
            ),
            views = 128,
            reactions = 24,
            nextOffset = null,
        )
    }

    val noViewers = StoryViewersPage(viewers = emptyList(), views = 0, reactions = 0, nextOffset = null)

    val composerPhoto: ByteArray by lazy { previewPhoto(width = 90, height = 160, start = 0xFFF4B04F.toInt(), end = 0xFF7C5BEF.toInt()) }

    const val UPLOAD_PROGRESS = 0.42f
}
