package cn.ellerotta.core.ui.components.music

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import cn.ellerotta.core.model.MusicOrder
import cn.ellerotta.core.model.MusicRepeat
import cn.ellerotta.core.model.MusicSleep
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.MusicTrack
import cn.ellerotta.core.model.SLEEP_MINUTES
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.speedText
import cn.ellerotta.core.ui.components.chat.voiceTime
import cn.ellerotta.core.ui.components.common.TopBarPill
import cn.ellerotta.core.ui.components.common.TopBarTokens
import cn.ellerotta.core.ui.components.common.rememberDecodedPhoto
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Stable
class MusicCoverAnchor {
    var bounds: Rect? by mutableStateOf(null)

    var taken: Boolean by mutableStateOf(false)
}

val LocalMusicCoverAnchor = staticCompositionLocalOf<MusicCoverAnchor?> { null }

internal fun Modifier.coverAnchor(anchor: MusicCoverAnchor?): Modifier =
    if (anchor == null) this else onGloballyPositioned { anchor.bounds = it.boundsInWindow() }.graphicsLayer { alpha = if (anchor.taken) 0f else 1f }

@Composable
fun MusicPlayerOverlay(state: MusicState?, open: Boolean, actions: MusicActions, onDismiss: () -> Unit) {
    val last = remember { arrayOfNulls<MusicState>(1) }
    state?.let { last[0] = it }
    val shown = last[0] ?: return
    val anchor = LocalMusicCoverAnchor.current
    val motion = MaterialTheme.motionScheme
    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    var showing by remember { mutableStateOf(false) }
    val target = open && state != null
    LaunchedEffect(target) {
        if (target) showing = true
        coroutineScope {
            launch { fade.animateTo(if (target) 1f else 0f, if (target) motion.slowEffectsSpec() else motion.defaultEffectsSpec()) }
            launch { progress.animateTo(if (target) 1f else 0f, if (target) motion.slowSpatialSpec() else motion.defaultSpatialSpec()) }
        }
        if (!target) showing = false
    }
    SideEffect { anchor?.taken = showing }
    rememberCoverSeed(shown.cover)
    if (!showing) return
    MaterialTheme(colorScheme = rememberArtworkScheme(shown.cover)) {
        PlayerPage(shown, actions, progress, fade, anchor, target, onDismiss)
    }
}

@Stable
private class CoverPlace {
    var bounds: Rect? by mutableStateOf(null)
    var page: LayoutCoordinates? = null
}

@Composable
private fun PlayerPage(
    state: MusicState,
    actions: MusicActions,
    progress: Animatable<Float, AnimationVector1D>,
    fade: Animatable<Float, AnimationVector1D>,
    anchor: MusicCoverAnchor?,
    open: Boolean,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    val density = LocalDensity.current
    var drag by remember { mutableFloatStateOf(0f) }
    val back = remember { Animatable(0f) }
    var height by remember { mutableIntStateOf(0) }
    val place = remember { CoverPlace() }
    val placed by remember { derivedStateOf { place.bounds != null } }
    val coverShown = remember { arrayOf<() -> Float>({ 1f }) }
    PredictiveBackHandler(enabled = open) { events ->
        try {
            events.collect { back.snapTo(it.progress) }
            onDismiss()
        } catch (e: CancellationException) {
            scope.launch { back.animateTo(0f, motion.fastSpatialSpec()) }
            throw e
        }
    }
    LaunchedEffect(open) {
        if (open) return@LaunchedEffect
        launch { animate(drag, 0f, animationSpec = motion.defaultSpatialSpec()) { value, _ -> drag = value } }
        back.animateTo(0f, motion.defaultSpatialSpec())
    }
    val fling = with(density) { FLING_SPEED.toPx() }
    val corner = with(density) { PAGE_CORNER.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height }
            .onGloballyPositioned { place.page = it }
            .graphicsLayer {
                val b = back.value
                scaleX = 1f - BACK_SCALE * b
                scaleY = scaleX
                val rounding = max(b, (drag / (height * DISMISS_SHARE).coerceAtLeast(1f)).coerceIn(0f, 1f))
                shape = RoundedCornerShape(corner * rounding)
                clip = rounding > 0f
            }
            .draggable(
                rememberDraggableState { delta -> drag = (drag + delta).coerceAtLeast(0f) },
                Orientation.Vertical,
                enabled = open,
                onDragStopped = { velocity ->
                    if (drag > height * DISMISS_SHARE || velocity > fling) {
                        onDismiss()
                    } else {
                        animate(drag, 0f, animationSpec = motion.fastSpatialSpec()) { value, _ -> drag = value }
                    }
                },
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, ((1f - progress.value) * height * SLIDE_SHARE + drag).roundToInt()) }
                .graphicsLayer { alpha = fade.value }
                .background(MaterialTheme.colorScheme.surface),
        ) {
            MusicPlayerContent(
                state,
                actions,
                onCollapse = onDismiss,
                cover = { modifier, shown ->
                    coverShown[0] = shown
                    Spacer(
                        modifier.onGloballyPositioned { placed ->
                            val page = place.page ?: return@onGloballyPositioned
                            if (page.isAttached && placed.isAttached) place.bounds = page.localBoundingBoxOf(placed)
                        },
                    )
                    DisposableEffect(Unit) { onDispose { place.bounds = null } }
                },
            )
        }
        if (placed) {
            PlayerCover(
                state.track,
                state.cover,
                state.playing,
                actions,
                swipe = open,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val end = place.bounds ?: Rect.Zero
                        val start = startOf(anchor, place.page)
                        val rect = if (start == null) end else lerp(start, end, progress.value)
                        val placeable = measurable.measure(Constraints.fixed(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)))
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(rect.left.roundToInt(), rect.top.roundToInt()) }
                    }
                    .graphicsLayer { alpha = (if (anchor?.bounds == null) fade.value else 1f) * coverShown[0]() },
            )
        }
    }
}

private fun startOf(anchor: MusicCoverAnchor?, page: LayoutCoordinates?): Rect? {
    val from = anchor?.bounds ?: return null
    if (page == null || !page.isAttached) return null
    return from.translate(-page.positionInWindow())
}

@Composable
internal fun MusicPlayerContent(
    state: MusicState,
    actions: MusicActions,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    showLyrics: Boolean = false,
    cover: @Composable (modifier: Modifier, shown: () -> Float) -> Unit = { modifier, _ ->
        PlayerCover(state.track, state.cover, state.playing, actions, swipe = true, modifier = modifier)
    },
) {
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    val track = state.track
    var lyricsOpen by rememberSaveable(track.slot) { mutableStateOf(showLyrics) }
    val lyrics = state.lyrics
    val lastLyrics = remember { arrayOfNulls<String>(1) }
    lyrics?.let { lastLyrics[0] = it }
    val motion = MaterialTheme.motionScheme
    val words = lyricsOpen && lyrics != null
    val fade = animateFloatAsState(if (words) 1f else 0f, motion.defaultEffectsSpec(), label = "lyricsFade")
    val rise = animateFloatAsState(if (words) 1f else 0f, motion.defaultSpatialSpec(), label = "lyricsRise")
    val coverIn by remember { derivedStateOf { fade.value < 1f } }
    val wordsIn by remember { derivedStateOf { fade.value > 0f } }
    val seek = rememberSeekHold(state)
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("musicSheet"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlayerBar(state.index + 1, state.queue.size, state.sleep, track.saved, onCollapse, onQueue = { queueOpen = true }, actions)
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = PAGE_MARGIN, vertical = COVER_GAP), contentAlignment = Alignment.Center) {
            if (coverIn) {
                cover(
                    Modifier.widthIn(max = COVER_MAX).aspectRatio(1f).graphicsLayer {
                        alpha = 1f - fade.value
                        val scale = 1f - LYRICS_COVER_SHRINK * rise.value
                        scaleX = scale
                        scaleY = scale
                    },
                ) { 1f - fade.value }
            }
            val text = lastLyrics[0]
            if (wordsIn && text != null) {
                Lyrics(
                    text,
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = fade.value
                        translationY = (1f - rise.value) * LYRICS_RISE.toPx()
                    },
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
            TitleRow(track, actions.onSaveToProfile)
            SeekBlock(state, seek, actions.onSeek)
            Spacer(Modifier.height(CONTROLS_GAP))
            Transport(state.playing, actions)
            Spacer(Modifier.height(CONTROLS_GAP))
            Modes(track, state.order, state.repeat, state.speed, lyrics != null, lyricsOpen, state.sleep, actions, onLyrics = { lyricsOpen = !lyricsOpen })
            Spacer(Modifier.height(BOTTOM_GAP))
        }
    }
    if (queueOpen) MusicQueueSheet(state, actions, onDismiss = { queueOpen = false })
}

@Composable
private fun PlayerBar(position: Int, count: Int, sleep: MusicSleep?, saved: Boolean, onCollapse: () -> Unit, onQueue: () -> Unit, actions: MusicActions) {
    Box(Modifier.fillMaxWidth().padding(start = TopBarTokens.EdgeInset, end = TopBarTokens.EdgeInset, top = TopBarTokens.EdgeInset)) {
        TopBarPill(R.drawable.ic_keyboard_arrow_down, stringResource(R.string.music_collapse), onCollapse, Modifier.align(Alignment.CenterStart).testTag("sheetCollapse"))
        Text(
            when {
                sleep == null -> stringResource(R.string.music_position, position, count)
                sleep.endOfTrack -> stringResource(R.string.music_sleep_at_end)
                else -> stringResource(R.string.music_sleep_left, voiceTime(sleep.leftMs))
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = BAR_TITLE_INSET).testTag("sheetStatus"),
        )
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(TopBarTokens.PillGap)) {
            TopBarPill(R.drawable.ic_queue_music, stringResource(R.string.music_queue), onQueue, Modifier.testTag("sheetQueue"))
            if (!saved) MoreButton(actions)
        }
    }
}

@Composable
private fun MoreButton(actions: MusicActions) {
    var menu by remember { mutableStateOf(false) }
    Box {
        TopBarPill(R.drawable.ic_more_vert, stringResource(R.string.music_entry_menu), { menu = true }, Modifier.testTag("sheetMore"))
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_go_to_message)) },
                onClick = {
                    menu = false
                    actions.onGoToMessage()
                },
                shape = MenuDefaults.itemShape(0, 1).shape,
                leadingIcon = { Icon(painterResource(R.drawable.ic_chat), null) },
                modifier = Modifier.testTag("sheetGoToMessage"),
            )
        }
    }
}

@Composable
private fun TitleRow(track: MusicTrack, onSaveToProfile: () -> Unit) {
    key(track.slot) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.heading(),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = MARQUEE_ROUNDS).testTag("sheetTitle"),
                )
                Text(
                    track.subheading(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = MARQUEE_ROUNDS).testTag("sheetPerformer"),
                )
            }
            if (!track.isVoice) {
                FilledTonalIconButton(
                    onClick = onSaveToProfile,
                    shapes = IconButtonShapes(shape = IconButtonDefaults.smallSquareShape, pressedShape = IconButtonDefaults.smallPressedShape),
                    modifier = Modifier.padding(start = 8.dp).testTag("sheetSaveToProfile"),
                ) {
                    Icon(painterResource(R.drawable.ic_favorite), stringResource(R.string.music_save_to_profile))
                }
            }
        }
    }
}

@Composable
private fun PlayerCover(track: MusicTrack, cover: ByteArray?, playing: Boolean, actions: MusicActions, swipe: Boolean, modifier: Modifier = Modifier) {
    val voice = track.isVoice
    val bitmap = rememberDecodedPhoto(cover)
    val motion = MaterialTheme.motionScheme
    val paused by animateFloatAsState(if (playing) 0f else 1f, motion.slowSpatialSpec(), label = "coverPause")
    val morph = remember { Morph(MaterialShapes.Square, MaterialShapes.Cookie12Sided) }
    val skip = remember { SkipSwipe() }
    val container = MaterialTheme.colorScheme.tertiaryContainer
    val label = stringResource(if (voice) R.string.music_photo else R.string.music_cover, track.heading())
    Box(
        modifier
            .skipSwipe(skip, actions, enabled = swipe)
            .semantics { contentDescription = label }
            .graphicsLayer {
                val p = paused.coerceIn(0f, 1f)
                val scale = 1f - PAUSED_SHRINK * paused
                scaleX = scale
                scaleY = scale
                translationX = skip.offset
                rotationZ = skip.offset / SWIPE_TILT.toPx()
                alpha = 1f - skip.share * SWIPE_FADE
                shape = if (voice) CircleShape else MorphShape(morph, p)
                clip = true
            }
            .background(container)
            .testTag("sheetCover"),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                painterResource(if (voice) R.drawable.ic_mic else R.drawable.ic_music_note),
                null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(COVER_ICON),
            )
        }
    }
}

@Stable
internal class SkipSwipe {
    var offset by mutableFloatStateOf(0f)
    var width by mutableIntStateOf(0)

    val share: Float get() = (abs(offset) / width.coerceAtLeast(1)).coerceIn(0f, 1f)
}

@Composable
internal fun Modifier.skipSwipe(swipe: SkipSwipe, actions: MusicActions, enabled: Boolean = true): Modifier {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val motion = MaterialTheme.motionScheme
    val threshold = with(density) { SWIPE_THRESHOLD.toPx() }
    val fling = with(density) { FLING_SPEED.toPx() }
    val nextLabel = stringResource(R.string.music_next)
    val previousLabel = stringResource(R.string.music_previous)
    return onSizeChanged { swipe.width = it.width }
        .draggable(
            rememberDraggableState { delta ->
                val before = abs(swipe.offset) >= threshold
                swipe.offset += delta
                if (!before && abs(swipe.offset) >= threshold) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            },
            Orientation.Horizontal,
            enabled = enabled,
            onDragStopped = { velocity ->
                val forward = swipe.offset <= -threshold || velocity < -fling
                val backward = swipe.offset >= threshold || velocity > fling
                if (!forward && !backward) {
                    animate(swipe.offset, 0f, animationSpec = motion.fastSpatialSpec()) { value, _ -> swipe.offset = value }
                    return@draggable
                }
                val side = swipe.width.toFloat()
                animate(swipe.offset, if (forward) -side else side, initialVelocity = velocity, animationSpec = motion.fastSpatialSpec()) { value, _ -> swipe.offset = value }
                if (forward) actions.onNext() else actions.onPrevious()
                swipe.offset = if (forward) side * SWIPE_ENTRY else -side * SWIPE_ENTRY
                scope.launch { animate(swipe.offset, 0f, animationSpec = motion.defaultSpatialSpec()) { value, _ -> swipe.offset = value } }
            },
        )
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction(nextLabel) { actions.onNext(); true },
                CustomAccessibilityAction(previousLabel) { actions.onPrevious(); true },
            )
        }
}

private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is MorphShape && other.morph === morph && other.progress == progress

    override fun hashCode(): Int = 31 * morph.hashCode() + progress.hashCode()
}

@Stable
internal class SeekHold {
    var dragging by mutableStateOf<Float?>(null)
    var sought by mutableStateOf<Long?>(null)

    fun shownMs(state: MusicState): Long = dragging?.let { (it * state.lengthMs).toLong() } ?: sought ?: state.positionMs
}

@Composable
internal fun rememberSeekHold(state: MusicState): SeekHold {
    val seek = remember(state.track.slot) { SeekHold() }
    val sought = seek.sought
    LaunchedEffect(seek, sought) {
        if (sought == null) return@LaunchedEffect
        delay(SEEK_HOLD_MS)
        seek.sought = null
    }
    if (sought != null && abs(state.positionMs - sought) <= SEEK_REACHED_MS) {
        SideEffect { if (seek.sought == sought) seek.sought = null }
    }
    return seek
}

@Composable
private fun SeekBlock(state: MusicState, seek: SeekHold, onSeek: (Long) -> Unit) {
    val length = state.lengthMs
    val position = seek.shownMs(state)
    val style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val dragged by animateColorAsState(
        if (seek.dragging != null) MaterialTheme.colorScheme.primary else color,
        MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "seekTime",
    )
    Layout(
        content = {
            WavySeekBar(state, seek, onSeek, stateText = stringResource(R.string.music_seek_state, voiceTime(position), voiceTime(length)))
            Text(voiceTime(position), style = style, color = dragged, modifier = Modifier.testTag("sheetPosition"))
            Text(voiceTime(length), style = style, color = color, modifier = Modifier.testTag("sheetLength"))
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val bar = measurables[0].measure(constraints.copy(minHeight = 0))
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gone = measurables[1].measure(loose)
        val total = measurables[2].measure(loose)
        val top = bar.height / 2 + (maxOf(THUMB.height, WavyProgressIndicatorDefaults.LinearContainerHeight) / 2 + TIME_GAP).roundToPx()
        val height = max(bar.height, top + max(gone.height, total.height))
        layout(constraints.maxWidth, height) {
            bar.place(0, 0)
            gone.place(0, top)
            total.place(constraints.maxWidth - total.width, top)
        }
    }
}

@Composable
internal fun WavySeekBar(state: MusicState, seek: SeekHold, onSeek: (Long) -> Unit, stateText: String, modifier: Modifier = Modifier) {
    val length = state.lengthMs
    val fraction = if (length > 0) (seek.shownMs(state).toFloat() / length).coerceIn(0f, 1f) else 0f
    val slider = remember { SliderState(fraction) }
    slider.value = fraction
    val interactions = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val colors = SliderDefaults.colors(thumbColor = scheme.primary, activeTrackColor = scheme.primary, inactiveTrackColor = scheme.secondaryContainer)
    val label = stringResource(R.string.viewer_seek)
    val waving = state.playing && seek.dragging == null
    Slider(
        slider,
        onValueChange = { seek.dragging = it },
        onValueChangeFinished = {
            seek.dragging?.let {
                val to = (it * length).toLong()
                seek.sought = to
                onSeek(to)
            }
            seek.dragging = null
        },
        enabled = length > 0,
        colors = colors,
        interactionSource = interactions,
        thumb = { SliderDefaults.Thumb(interactions, colors = colors, enabled = length > 0, thumbSize = THUMB) },
        track = { current ->
            LinearWavyProgressIndicator(
                progress = { current.value },
                color = scheme.primary,
                trackColor = scheme.secondaryContainer,
                amplitude = if (waving) WAVING else FLAT,
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = label
                stateDescription = stateText
            }
            .testTag("sheetSeek"),
    )
}

@Composable
private fun Transport(playing: Boolean, actions: MusicActions) {
    val previous = remember { MutableInteractionSource() }
    val play = remember { MutableInteractionSource() }
    val next = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val skipShapes = IconButtonShapes(shape = IconButtonDefaults.largeRoundShape, pressedShape = IconButtonDefaults.largePressedShape)
    val playShapes = IconToggleButtonShapes(
        shape = IconButtonDefaults.largeRoundShape,
        pressedShape = IconButtonDefaults.largePressedShape,
        checkedShape = IconButtonDefaults.largeSelectedRoundShape,
    )
    val playColors = IconButtonDefaults.filledIconToggleButtonColors(
        containerColor = scheme.primary,
        contentColor = scheme.onPrimary,
        checkedContainerColor = scheme.primary,
        checkedContentColor = scheme.onPrimary,
    )
    ButtonGroup(overflowIndicator = {}, horizontalArrangement = Arrangement.spacedBy(TRANSPORT_GAP), modifier = Modifier.fillMaxWidth()) {
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        actions.onPrevious()
                    },
                    shapes = skipShapes,
                    interactionSource = previous,
                    modifier = Modifier.weight(1f).height(TRANSPORT_HEIGHT).animateWidth(previous).testTag("sheetPrevious"),
                ) {
                    Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.music_previous), Modifier.size(IconButtonDefaults.largeIconSize))
                }
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                FilledIconToggleButton(
                    checked = playing,
                    onCheckedChange = {
                        haptics.performHapticFeedback(if (playing) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                        actions.onToggle()
                    },
                    shapes = playShapes,
                    colors = playColors,
                    interactionSource = play,
                    modifier = Modifier.weight(PLAY_WEIGHT).height(TRANSPORT_HEIGHT).animateWidth(play).testTag("sheetToggle"),
                ) {
                    PlayIcon(playing, Modifier.size(IconButtonDefaults.largeIconSize))
                }
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        actions.onNext()
                    },
                    shapes = skipShapes,
                    interactionSource = next,
                    modifier = Modifier.weight(1f).height(TRANSPORT_HEIGHT).animateWidth(next).testTag("sheetNext"),
                ) {
                    Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.music_next), Modifier.size(IconButtonDefaults.largeIconSize))
                }
            },
            menuContent = {},
        )
    }
}

@Composable
private fun Modes(
    track: MusicTrack,
    order: MusicOrder,
    repeat: MusicRepeat,
    speed: Float,
    hasLyrics: Boolean,
    lyricsOpen: Boolean,
    sleep: MusicSleep?,
    actions: MusicActions,
    onLyrics: () -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ButtonGroup(overflowIndicator = {}, horizontalArrangement = Arrangement.spacedBy(MODE_GAP)) {
            if (!track.isVoice) {
                customItem(buttonGroupContent = { OrderButton(order, actions) }, menuContent = {})
                customItem(
                    buttonGroupContent = {
                        val interactions = remember { MutableInteractionSource() }
                        ModeToggle(
                            checked = repeat != MusicRepeat.Off,
                            onClick = actions.onRepeat,
                            icon = if (repeat == MusicRepeat.One) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                            description = stringResource(
                                when (repeat) {
                                    MusicRepeat.Off -> R.string.music_repeat
                                    MusicRepeat.All -> R.string.music_repeat_all
                                    MusicRepeat.One -> R.string.music_repeat_one
                                },
                            ),
                            interactionSource = interactions,
                            modifier = Modifier.animateWidth(interactions).testTag("sheetRepeat"),
                        )
                    },
                    menuContent = {},
                )
            }
            if (track.hasSpeed) {
                customItem(
                    buttonGroupContent = {
                        val interactions = remember { MutableInteractionSource() }
                        val description = stringResource(R.string.music_speed_change, speedText(speed))
                        FilledTonalToggleButton(
                            checked = speed != 1f,
                            onCheckedChange = { actions.onSpeed() },
                            interactionSource = interactions,
                            modifier = Modifier
                                .animateWidth(interactions)
                                .semantics { contentDescription = description }
                                .testTag("sheetSpeed"),
                        ) {
                            Text(stringResource(R.string.music_speed, speedText(speed)), maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
                        }
                    },
                    menuContent = {},
                )
            }
            if (hasLyrics) {
                customItem(
                    buttonGroupContent = {
                        val interactions = remember { MutableInteractionSource() }
                        ModeToggle(
                            checked = lyricsOpen,
                            onClick = onLyrics,
                            icon = R.drawable.ic_lyrics,
                            description = stringResource(R.string.music_lyrics),
                            interactionSource = interactions,
                            modifier = Modifier.animateWidth(interactions).testTag("sheetLyricsToggle"),
                        )
                    },
                    menuContent = {},
                )
            }
            customItem(buttonGroupContent = { SleepButton(sleep, actions) }, menuContent = {})
        }
    }
}

@Composable
private fun ModeToggle(
    checked: Boolean,
    onClick: () -> Unit,
    icon: Int,
    description: String,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        shapes = IconButtonDefaults.toggleableShapes(),
        interactionSource = interactionSource,
        modifier = modifier.size(IconButtonDefaults.smallContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)).semantics { contentDescription = description },
    ) {
        Icon(painterResource(icon), null)
    }
}

@Composable
private fun ButtonGroupScope.OrderButton(order: MusicOrder, actions: MusicActions) {
    var menu by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    LaunchedEffect(interactions) {
        interactions.interactions.collectLatest { interaction ->
            if (interaction !is PressInteraction.Press) return@collectLatest
            held = false
            delay(timeout)
            held = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            menu = true
        }
    }
    val chooseLabel = stringResource(R.string.music_order_choose)
    Box(Modifier.animateWidth(interactions)) {
        ModeToggle(
            checked = order != MusicOrder.Chat,
            onClick = { if (held) held = false else actions.onShuffle() },
            icon = if (order == MusicOrder.Reverse) R.drawable.ic_swap_vert else R.drawable.ic_shuffle,
            description = stringResource(if (order == MusicOrder.Reverse) R.string.music_order_reverse else R.string.music_shuffle),
            interactionSource = interactions,
            modifier = Modifier
                .semantics {
                    onLongClick(chooseLabel) {
                        menu = true
                        true
                    }
                }
                .testTag("sheetShuffle"),
        )
        OrderMenu(menu, order, onDismiss = { menu = false }) {
            menu = false
            actions.onOrder(it)
        }
    }
}

@Composable
private fun ButtonGroupScope.SleepButton(sleep: MusicSleep?, actions: MusicActions) {
    var menu by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    Box(Modifier.animateWidth(interactions)) {
        ModeToggle(
            checked = sleep != null,
            onClick = { menu = true },
            icon = R.drawable.ic_bedtime,
            description = stringResource(R.string.music_sleep),
            interactionSource = interactions,
            modifier = Modifier.testTag("sheetSleep"),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val choices = SLEEP_MINUTES.size + 2
            SelectableDropdownMenuItem(
                selected = sleep == null,
                onClick = {
                    menu = false
                    actions.onSleep(null)
                },
                text = { Text(stringResource(R.string.music_sleep_off), style = if (sleep == null) MaterialTheme.typography.labelLargeEmphasized else LocalTextStyle.current) },
                shapes = MenuDefaults.itemShape(0, choices),
                selectedLeadingIcon = { Icon(painterResource(R.drawable.ic_check), null) },
                modifier = Modifier.testTag("sleepOff"),
            )
            SLEEP_MINUTES.forEachIndexed { index, minutes ->
                SelectableDropdownMenuItem(
                    selected = false,
                    onClick = {
                        menu = false
                        actions.onSleep(minutes)
                    },
                    text = { Text(pluralStringResource(R.plurals.music_sleep_minutes, minutes, minutes)) },
                    shapes = MenuDefaults.itemShape(index + 1, choices),
                    modifier = Modifier.testTag("sleep_$minutes"),
                )
            }
            SelectableDropdownMenuItem(
                selected = sleep?.endOfTrack == true,
                onClick = {
                    menu = false
                    actions.onSleepAtEnd()
                },
                text = { Text(stringResource(R.string.music_sleep_end), style = if (sleep?.endOfTrack == true) MaterialTheme.typography.labelLargeEmphasized else LocalTextStyle.current) },
                shapes = MenuDefaults.itemShape(choices - 1, choices),
                selectedLeadingIcon = { Icon(painterResource(R.drawable.ic_check), null) },
                modifier = Modifier.testTag("sleepAtEnd"),
            )
        }
    }
}

@Composable
internal fun OrderMenu(expanded: Boolean, order: MusicOrder, onDismiss: () -> Unit, onChoose: (MusicOrder) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.testTag("orderMenu")) {
        MusicOrder.entries.forEachIndexed { index, each ->
            SelectableDropdownMenuItem(
                selected = each == order,
                onClick = { onChoose(each) },
                text = { Text(orderName(each), style = if (each == order) MaterialTheme.typography.labelLargeEmphasized else LocalTextStyle.current) },
                shapes = MenuDefaults.itemShape(index, MusicOrder.entries.size),
                leadingIcon = { Icon(painterResource(orderIcon(each)), null) },
                selectedLeadingIcon = { Icon(painterResource(R.drawable.ic_check), null) },
                modifier = Modifier.testTag("order_${each.name}"),
            )
        }
    }
}

@Composable
internal fun orderName(order: MusicOrder): String = stringResource(
    when (order) {
        MusicOrder.Chat -> R.string.music_order_chat
        MusicOrder.Reverse -> R.string.music_order_reverse
        MusicOrder.Shuffle -> R.string.music_shuffle
    },
)

internal fun orderIcon(order: MusicOrder): Int = when (order) {
    MusicOrder.Chat -> R.drawable.ic_south
    MusicOrder.Reverse -> R.drawable.ic_swap_vert
    MusicOrder.Shuffle -> R.drawable.ic_shuffle
}

@Composable
private fun Lyrics(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(PAGE_CORNER)).background(MaterialTheme.colorScheme.surfaceContainer)) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxSize()
                .fadingEdges(LYRICS_FADE)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag("sheetLyrics"),
        )
    }
}

private fun Modifier.fadingEdges(edge: Dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val share = (edge.toPx() / size.height).coerceIn(0f, 0.5f)
            drawRect(
                Brush.verticalGradient(0f to Color.Transparent, share to Color.Black, 1f - share to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }

private val WAVING = WavyProgressIndicatorDefaults.indicatorAmplitude
private val FLAT: (Float) -> Float = { 0f }
private val THUMB = DpSize(4.dp, 24.dp)
private val TIME_GAP = 4.dp
private val PAGE_MARGIN = 24.dp
private val COVER_GAP = 16.dp
private val CONTROLS_GAP = 16.dp
private val BOTTOM_GAP = 24.dp
private val BAR_TITLE_INSET = TopBarTokens.PillSize * 2 + TopBarTokens.PillGap * 2
private val COVER_MAX = 480.dp
private val COVER_ICON = 72.dp
private val PAGE_CORNER = 28.dp
private val SWIPE_THRESHOLD = 72.dp
private val FLING_SPEED = 1200.dp
private const val SLIDE_SHARE = 0.25f
private const val DISMISS_SHARE = 0.25f
private const val BACK_SCALE = 0.1f
private const val PAUSED_SHRINK = 0.1f
private const val SWIPE_FADE = 0.4f
private const val SWIPE_ENTRY = 0.35f
private val SWIPE_TILT = 40.dp
private const val LYRICS_COVER_SHRINK = 0.08f
private val LYRICS_RISE = 24.dp
private val LYRICS_FADE = 24.dp
private const val SEEK_HOLD_MS = 2_000L
private const val SEEK_REACHED_MS = 1_000L
private const val MARQUEE_ROUNDS = 3
private val TRANSPORT_HEIGHT = 96.dp
private const val PLAY_WEIGHT = 128f / 96f
private val TRANSPORT_GAP = 8.dp
private val MODE_GAP = 12.dp
