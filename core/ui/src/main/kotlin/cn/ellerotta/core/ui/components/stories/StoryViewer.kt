package cn.ellerotta.core.ui.components.stories

import android.graphics.BitmapFactory
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.PeerStories
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.InlineEmoji
import cn.ellerotta.core.ui.components.chat.MEDIA_SCRIM
import cn.ellerotta.core.ui.components.chat.RichParagraph
import cn.ellerotta.core.ui.components.chat.StillImage
import cn.ellerotta.core.ui.components.chat.TextActions
import cn.ellerotta.core.ui.components.chat.TextPress
import cn.ellerotta.core.ui.components.chats.rememberNow
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.TopBarColors
import cn.ellerotta.core.ui.components.common.TopBarPill
import cn.ellerotta.core.ui.components.common.TopBarScrim
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

internal val StoryBand = Color.Black.copy(alpha = 0.6f)

internal val StoryTrack = Color.White.copy(alpha = 0.25f)

private val BAR_HEIGHT = 2.dp
private val BAR_GAP = 4.dp
private val SCRIM_FADE = 32.dp
private val CAPTION_MAX = 320.dp
private val DISMISS_DISTANCE = 120.dp
private val DISMISS_VELOCITY = 1_000.dp
private const val CAPTION_LINES = 3
private const val HOLD_MILLIS = 200L
private const val DISMISS_FADE = 0.6f
private const val DISMISS_SHRINK = 0.15f
private const val CUBE_DEGREES = 90f
private const val CUBE_CAMERA = 12f
private const val FILL_KEPT = 0.75f
private const val BACKDROP_DIM = 0.45f
private val BACKDROP_BLUR = 24.dp

@Composable
fun StoryViewer(
    peers: List<PeerStories>,
    initialPeer: Int,
    avatars: Map<String, ByteArray>,
    downloads: Map<String, DownloadState>,
    onShown: (peer: PeerStories, story: StoryItem) -> Unit,
    onClose: () -> Unit,
    video: @Composable (media: MediaItem, playing: Boolean, contentScale: ContentScale, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
    onOpenPeer: (String) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    customEmoji: Map<Long, MediaItem> = emptyMap(),
    emojiFiles: Map<String, ByteArray> = emptyMap(),
    footer: @Composable BoxScope.(peer: PeerStories, story: StoryItem, pause: (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onReport: ((peer: PeerStories, story: StoryItem) -> Unit)? = null,
    hold: Boolean = false,
) {
    BackHandler(onBack = onClose)
    if (peers.isEmpty()) {
        Box(modifier.fillMaxSize().background(Color.Black).testTag("storyViewer"), contentAlignment = Alignment.Center) {
            StoryLoading(null)
        }
        return
    }
    val close by rememberUpdatedState(onClose)
    val pager = rememberPagerState(initialPage = initialPeer.coerceIn(0, peers.lastIndex)) { peers.size }
    val scope = rememberCoroutineScope()
    val distance = with(LocalDensity.current) { DISMISS_DISTANCE.toPx() }
    val velocity = with(LocalDensity.current) { DISMISS_VELOCITY.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var dismiss by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val actions = remember(onOpenLink) { TextActions(onOpenLink = onOpenLink) }
    val emoji = remember(customEmoji, emojiFiles) { InlineEmoji(customEmoji, emojiFiles) }
    val now = rememberNow()
    Box(
        modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black, alpha = 1f - (dismiss / size.height).coerceIn(0f, 1f) * DISMISS_FADE) }
            .draggable(
                rememberDraggableState { dismiss = (dismiss + it).coerceAtLeast(0f) },
                Orientation.Vertical,
                onDragStarted = { dragging = true },
                onDragStopped = { speed ->
                    dragging = false
                    if (dismiss > distance || speed > velocity) close() else animate(dismiss, 0f) { value, _ -> dismiss = value }
                },
            )
            .testTag("storyViewer"),
    ) {
        HorizontalPager(
            state = pager,
            key = { peers[it].key },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationY = dismiss
                val scale = 1f - (dismiss / size.height).coerceIn(0f, 1f) * DISMISS_SHRINK
                scaleX = scale
                scaleY = scale
            },
        ) { page ->
            PeerPage(
                peer = peers[page],
                avatars = avatars,
                downloads = downloads,
                now = now,
                settled = page == pager.settledPage,
                held = dragging || pager.isScrollInProgress || hold,
                onNextPeer = { if (page < peers.lastIndex) scope.launch { pager.animateScrollToPage(page + 1) } else close() },
                onPreviousPeer = if (page > 0) { { scope.launch { pager.animateScrollToPage(page - 1) } } } else null,
                onShown = onShown,
                onClose = onClose,
                onOpenPeer = onOpenPeer,
                video = video,
                actions = actions,
                emoji = emoji,
                footer = footer,
                onReport = onReport,
                modifier = Modifier.graphicsLayer {
                    val offset = page - pager.currentPage - pager.currentPageOffsetFraction
                    val position = if (rtl) -offset else offset
                    if (abs(position) < 1f) {
                        cameraDistance = CUBE_CAMERA * density
                        transformOrigin = TransformOrigin(if (position < 0f) 1f else 0f, 0.5f)
                        rotationY = position * CUBE_DEGREES
                    }
                },
            )
        }
    }
}

@Composable
private fun PeerPage(
    peer: PeerStories,
    avatars: Map<String, ByteArray>,
    downloads: Map<String, DownloadState>,
    now: Long,
    settled: Boolean,
    held: Boolean,
    onNextPeer: () -> Unit,
    onPreviousPeer: (() -> Unit)?,
    onShown: (PeerStories, StoryItem) -> Unit,
    onClose: () -> Unit,
    onOpenPeer: (String) -> Unit,
    video: @Composable (MediaItem, Boolean, ContentScale, Modifier) -> Unit,
    actions: TextActions,
    emoji: InlineEmoji,
    footer: @Composable BoxScope.(PeerStories, StoryItem, (Boolean) -> Unit) -> Unit,
    onReport: ((PeerStories, StoryItem) -> Unit)?,
    modifier: Modifier,
) {
    var index by remember { mutableIntStateOf(peer.startIndex) }
    val stories = peer.stories
    if (stories.isEmpty()) {
        Box(modifier.fillMaxSize().testTag("storyPage_${peer.key}"), contentAlignment = Alignment.Center) { StoryLoading(null) }
        return
    }
    val at = index.coerceIn(0, stories.lastIndex)
    val story = stories[at]
    val media = story.media
    val state = media?.let { downloads[it.key] }
    var restarts by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    var expanded by remember(story.id) { mutableStateOf(false) }
    val ready = media != null && (story.isVideo || state is DownloadState.Done)
    val paused = held || pressed || typing || expanded
    val next: () -> Unit = { if (at < stories.lastIndex) index = at + 1 else onNextPeer() }
    val previous: () -> Unit = {
        when {
            at > 0 -> index = at - 1
            onPreviousPeer != null -> onPreviousPeer()
            else -> restarts++
        }
    }
    val progress = remember(story.id, restarts) { Animatable(0f) }
    val advance by rememberUpdatedState(next)
    LaunchedEffect(progress, settled && ready && !paused) {
        if (!settled || !ready || paused) return@LaunchedEffect
        progress.animateTo(1f, tween(((1f - progress.value) * story.durationMillis).toInt(), easing = LinearEasing))
        advance()
    }
    LaunchedEffect(progress, settled) { if (!settled) progress.snapTo(0f) }
    val shown by rememberUpdatedState(onShown)
    val current by rememberUpdatedState(peer)
    LaunchedEffect(settled, story.id, media != null) { if (settled && media != null) shown(current, story) }

    val time = storyTime(story.date, now)
    val description = stringResource(R.string.stories_viewer_page, peer.name, at + 1, stories.size, time)
    val nextLabel = stringResource(R.string.stories_viewer_next)
    val previousLabel = stringResource(R.string.stories_viewer_previous)
    val closeLabel = stringResource(R.string.stories_viewer_close)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val onTap: (Boolean) -> Unit = { back ->
        when {
            expanded -> expanded = false
            back -> previous()
            else -> next()
        }
    }
    val tap by rememberUpdatedState(onTap)
    Box(modifier.fillMaxSize().testTag("storyPage_${peer.key}")) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(rtl) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pressed = true
                        try {
                            var cancelled = false
                            val up = withTimeoutOrNull(HOLD_MILLIS) { waitForUpOrCancellation().also { cancelled = it == null } }
                            if (up != null) {
                                up.consume()
                                val x = down.position.x
                                tap(if (rtl) x > size.width * 2f / 3f else x < size.width / 3f)
                            } else if (!cancelled) {
                                waitForUpOrCancellation()
                            }
                        } finally {
                            pressed = false
                        }
                    }
                }
                .semantics {
                    contentDescription = description
                    customActions = listOf(
                        CustomAccessibilityAction(nextLabel) {
                            next()
                            true
                        },
                        CustomAccessibilityAction(previousLabel) {
                            previous()
                            true
                        },
                        CustomAccessibilityAction(closeLabel) {
                            onClose()
                            true
                        },
                    )
                }
                .testTag("story_${peer.key}_${story.id}"),
            contentAlignment = Alignment.Center,
        ) {
            if (media == null) StoryLoading(null) else StoryMedia(media, state, story.isVideo, settled, settled && !paused, video)
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().background(StoryBand).statusBarsPadding().padding(top = 8.dp)) {
                StoryBars(stories.size, at, { progress.value })
                StoryHeader(peer, peer.avatarKey?.let(avatars::get), time, onOpenPeer, onClose, onReport = onReport?.takeIf { !peer.self }?.let { report -> { report(peer, story) } }, pause = { typing = it })
            }
            TopBarScrim(SCRIM_FADE, color = StoryBand, stops = listOf(0f to 1f, 1f to 0f))
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
            if (story.caption.isNotBlank()) StoryCaption(story, expanded, { expanded = it }, actions, emoji)
            Box(Modifier.fillMaxWidth()) { footer(peer, story) { typing = it } }
        }
    }
}

@Composable
private fun StoryBars(count: Int, current: Int, progress: () -> Float) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(BAR_HEIGHT).testTag("storyBars").clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
    ) {
        repeat(count) { bar ->
            Spacer(
                Modifier.weight(1f).fillMaxHeight().drawBehind {
                    val fill = when {
                        bar < current -> 1f
                        bar == current -> progress()
                        else -> 0f
                    }
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(StoryTrack, cornerRadius = radius)
                    if (fill > 0f) {
                        val width = size.width * fill
                        val start = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                        drawRoundRect(Color.White, topLeft = Offset(start, 0f), size = Size(width, size.height), cornerRadius = radius)
                    }
                },
            )
        }
    }
}

@Composable
private fun StoryHeader(
    peer: PeerStories,
    avatar: ByteArray?,
    time: String,
    onOpenPeer: (String) -> Unit,
    onClose: () -> Unit,
    onReport: (() -> Unit)?,
    pause: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.stories_viewer_open_peer), role = Role.Button) { onOpenPeer(peer.key) }
                .padding(horizontal = 8.dp)
                .testTag("storyPeer"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.clearAndSetSemantics {}) { Avatar(peer.name, avatar, size = 36, seed = peer.peerId) }
            Column(Modifier.weight(1f, fill = false)) {
                Text(peer.name, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (time.isNotEmpty()) Text(time, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.testTag("storyTime"))
            }
        }
        if (onReport != null) {
            var open by remember { mutableStateOf(false) }
            Box(Modifier.padding(end = 8.dp)) {
                TopBarPill(
                    R.drawable.ic_more_vert,
                    stringResource(R.string.stories_viewer_menu),
                    {
                        open = true
                        pause(true)
                    },
                    Modifier.testTag("storyMenu"),
                    colors = TopBarColors.Media,
                )
                DropdownMenu(
                    expanded = open,
                    onDismissRequest = {
                        open = false
                        pause(false)
                    },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.report_action), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_report), null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            open = false
                            pause(false)
                            onReport()
                        },
                        modifier = Modifier.testTag("storyReport"),
                    )
                }
            }
        }
        TopBarPill(R.drawable.ic_close, stringResource(R.string.stories_viewer_close), onClose, Modifier.testTag("storyClose"), colors = TopBarColors.Media)
    }
}

@Composable
private fun StoryCaption(story: StoryItem, expanded: Boolean, onExpand: (Boolean) -> Unit, actions: TextActions, emoji: InlineEmoji) {
    var overflow by remember(story.id) { mutableStateOf(false) }
    var revealed by remember(story.id) { mutableStateOf(false) }
    val press = remember { TextPress() }
    val label = stringResource(if (expanded) R.string.stories_viewer_caption_less else R.string.stories_viewer_caption_more)
    val toggle = if (overflow || expanded) Modifier.clickable(onClickLabel = label) { onExpand(!expanded) } else Modifier
    Column(Modifier.fillMaxWidth()) {
        TopBarScrim(SCRIM_FADE, color = StoryBand, stops = listOf(0f to 0f, 1f to 1f))
        Box(Modifier.fillMaxWidth().background(StoryBand).then(toggle).padding(horizontal = 16.dp, vertical = 8.dp).testTag("storyCaption")) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Box(if (expanded) Modifier.heightIn(max = CAPTION_MAX).verticalScroll(rememberScrollState()) else Modifier) {
                    RichParagraph(
                        story.caption,
                        story.entities,
                        story.id,
                        linkColor = Color.White,
                        revealed = revealed,
                        onReveal = { revealed = true },
                        actions = actions,
                        press = press,
                        emoji = emoji,
                        maxLines = if (expanded) Int.MAX_VALUE else CAPTION_LINES,
                        onOverflow = { if (!expanded) overflow = it },
                        style = MaterialTheme.typography.bodyMedium,
                        tag = "storyCaption_${story.id}",
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryLoading(state: DownloadState.Progress?) {
    val label = stringResource(R.string.stories_viewer_loading)
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = MEDIA_SCRIM), modifier = Modifier.semantics { contentDescription = label }.testTag("storyLoading")) {
        val total = state?.total
        if (state != null && total != null && total > 0) {
            CircularWavyProgressIndicator(progress = { state.downloaded.toFloat() / total }, modifier = Modifier.padding(6.dp).size(48.dp), color = Color.White)
        } else {
            CircularWavyProgressIndicator(Modifier.padding(6.dp).size(48.dp), color = Color.White)
        }
    }
}

@Composable
private fun StoryMedia(
    media: MediaItem,
    state: DownloadState?,
    isVideo: Boolean,
    current: Boolean,
    playing: Boolean,
    video: @Composable (MediaItem, Boolean, ContentScale, Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val scale = storyScale(media.width, media.height, constraints.maxWidth, constraints.maxHeight)
        if (scale == ContentScale.Fit) StoryBackdrop(media)
        if (isVideo) {
            StoryThumb(media, scale)
            if (current) key(media.key) { video(media, playing, scale, Modifier.fillMaxSize()) }
        } else {
            StillImage(media, state, contentScale = scale)
            if (state !is DownloadState.Done) StoryLoading(state as? DownloadState.Progress)
        }
    }
}

@Composable
private fun StoryThumb(media: MediaItem, scale: ContentScale) {
    val thumb = remember(media.key) { media.thumb?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    if (thumb != null) Image(thumb, null, contentScale = scale, modifier = Modifier.fillMaxSize().testTag("storyThumb_${media.key}"))
}

@Composable
private fun StoryBackdrop(media: MediaItem) {
    val thumb = remember(media.key) { media.thumb?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Box(Modifier.fillMaxSize().testTag("storyBackdrop")) {
        if (thumb != null) Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(BACKDROP_BLUR))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = BACKDROP_DIM)))
    }
}

internal fun storyScale(width: Int, height: Int, frameWidth: Int, frameHeight: Int): ContentScale {
    if (width <= 0 || height <= width || frameWidth <= 0 || frameHeight <= 0) return ContentScale.Fit
    val media = width.toFloat() / height
    val frame = frameWidth.toFloat() / frameHeight
    return if (min(media, frame) / max(media, frame) >= FILL_KEPT) ContentScale.Crop else ContentScale.Fit
}

@Composable
private fun storyTime(date: Int, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    if (date <= 0) return ""
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val seconds = (nowMillis / 1000 - date).coerceAtLeast(0)
    val posted = Instant.ofEpochSecond(date.toLong()).atZone(zone)
    val days = ChronoUnit.DAYS.between(posted.toLocalDate(), Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate())
    val time = posted.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale))
    return when {
        seconds < 60 -> stringResource(R.string.stories_viewer_just_now)
        seconds < 3_600 -> pluralStringResource(R.plurals.stories_viewer_minutes, (seconds / 60).toInt(), (seconds / 60).toInt())
        seconds < 86_400 -> pluralStringResource(R.plurals.stories_viewer_hours, (seconds / 3_600).toInt(), (seconds / 3_600).toInt())
        days <= 1L -> stringResource(R.string.stories_viewer_yesterday, time)
        else -> stringResource(R.string.stories_viewer_date, posted.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)), time)
    }
}
