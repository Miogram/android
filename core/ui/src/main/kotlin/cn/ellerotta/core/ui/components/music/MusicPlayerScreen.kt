package cn.ellerotta.core.ui.components.music

import android.graphics.BitmapFactory
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import cn.ellerotta.core.model.SLEEP_MINUTES
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.speedText
import cn.ellerotta.core.ui.components.chat.voiceTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
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
    var showing by remember { mutableStateOf(false) }
    val target = open && state != null
    LaunchedEffect(target) {
        if (target) {
            showing = true
            progress.animateTo(1f, motion.slowSpatialSpec())
        } else {
            progress.animateTo(0f, motion.defaultSpatialSpec())
            showing = false
        }
    }
    SideEffect { anchor?.taken = showing }
    if (!showing) return
    MaterialTheme(colorScheme = rememberArtworkScheme(shown.cover)) {
        PlayerPage(shown, actions, progress, anchor, target, onDismiss)
    }
}

@Composable
private fun PlayerPage(
    state: MusicState,
    actions: MusicActions,
    progress: Animatable<Float, AnimationVector1D>,
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
    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var coverBounds by remember { mutableStateOf<Rect?>(null) }
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
            .onGloballyPositioned { root = it }
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
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(MaterialTheme.colorScheme.surface),
        ) {
            MusicPlayerContent(
                state,
                actions,
                onCollapse = onDismiss,
                cover = { modifier ->
                    Spacer(
                        modifier.onGloballyPositioned { placed ->
                            val page = root ?: return@onGloballyPositioned
                            if (page.isAttached && placed.isAttached) coverBounds = page.localBoundingBoxOf(placed)
                        },
                    )
                },
            )
        }
        val end = coverBounds
        if (end != null) {
            val start = remember(anchor?.bounds, root) {
                val from = anchor?.bounds
                val page = root
                if (from == null || page == null || !page.isAttached) null else from.translate(-page.positionInWindow())
            }
            PlayerCover(
                state,
                actions,
                swipe = open,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val p = progress.value
                        val rect = if (start == null) end else lerp(start, end, p)
                        val placeable = measurable.measure(Constraints.fixed(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)))
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(rect.left.roundToInt(), rect.top.roundToInt()) }
                    }
                    .graphicsLayer { if (start == null) alpha = progress.value.coerceIn(0f, 1f) },
            )
        }
    }
}

@Composable
internal fun MusicPlayerContent(
    state: MusicState,
    actions: MusicActions,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    cover: @Composable (Modifier) -> Unit = { PlayerCover(state, actions, swipe = true, modifier = it) },
) {
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    val track = state.track
    var lyricsOpen by rememberSaveable(track.slot) { mutableStateOf(false) }
    val lyrics = state.lyrics
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .testTag("musicSheet"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("sheetCollapse")) {
                Icon(painterResource(R.drawable.ic_keyboard_arrow_down), stringResource(R.string.music_collapse))
            }
            val sleep = state.sleep
            Text(
                when {
                    sleep == null -> stringResource(R.string.music_position, state.index + 1, state.queue.size)
                    sleep.endOfTrack -> stringResource(R.string.music_sleep_at_end)
                    else -> stringResource(R.string.music_sleep_left, voiceTime(sleep.leftMs))
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).testTag("sheetStatus"),
            )
            SleepButton(sleep, actions)
            QueueButton(onClick = { queueOpen = true })
            MoreButton(state, actions)
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            if (lyricsOpen && lyrics != null) {
                Text(
                    lyrics,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(PAGE_CORNER))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                        .testTag("sheetLyrics"),
                )
            } else {
                cover(Modifier.widthIn(max = COVER_MAX).aspectRatio(1f))
            }
        }
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
                    onClick = actions.onSaveToProfile,
                    shapes = IconButtonShapes(shape = RoundedCornerShape(PROFILE_CORNER), pressedShape = RoundedCornerShape(PROFILE_PRESSED_CORNER)),
                    modifier = Modifier.padding(start = 8.dp).testTag("sheetSaveToProfile"),
                ) {
                    Icon(painterResource(R.drawable.ic_favorite), stringResource(R.string.music_save_to_profile))
                }
            }
            }
        }
        Spacer(Modifier.height(12.dp))
        WavySeekBar(state, actions.onSeek)
        TimeRow(state)
        Spacer(Modifier.height(8.dp))
        Transport(state, actions)
        Spacer(Modifier.height(20.dp))
        ModeGroup(state, actions, lyricsOpen, onLyrics = { lyricsOpen = !lyricsOpen })
        Spacer(Modifier.height(16.dp))
    }
    if (queueOpen) MusicQueueSheet(state, actions, onDismiss = { queueOpen = false })
}

@Composable
private fun PlayerCover(state: MusicState, actions: MusicActions, swipe: Boolean, modifier: Modifier = Modifier) {
    val voice = state.track.isVoice
    val bitmap = remember(state.cover) { state.cover?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    val motion = MaterialTheme.motionScheme
    val paused by animateFloatAsState(if (state.playing) 0f else 1f, motion.slowSpatialSpec(), label = "coverPause")
    val morph = remember { Morph(MaterialShapes.Square, MaterialShapes.Cookie12Sided) }
    val skip = remember { SkipSwipe() }
    val container = MaterialTheme.colorScheme.tertiaryContainer
    Box(
        modifier
            .skipSwipe(skip, actions, enabled = swipe)
            .graphicsLayer {
                val p = paused.coerceIn(0f, 1f)
                val scale = 1f - PAUSED_SHRINK * paused
                scaleX = scale
                scaleY = scale
                translationX = skip.offset
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
                animate(swipe.offset, if (forward) -side else side, animationSpec = motion.fastEffectsSpec()) { value, _ -> swipe.offset = value }
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

@Composable
internal fun WavySeekBar(state: MusicState, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val length = state.lengthMs
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (length > 0) (state.positionMs.toFloat() / length).coerceIn(0f, 1f) else 0f
    val slider = remember { SliderState(fraction) }
    slider.value = fraction
    val interactions = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val colors = SliderDefaults.colors(thumbColor = scheme.primary, activeTrackColor = scheme.primary, inactiveTrackColor = scheme.secondaryContainer)
    val label = stringResource(R.string.viewer_seek)
    val waving = state.playing && dragging == null
    Slider(
        slider,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { onSeek((it * length).toLong()) }
            dragging = null
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
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label }.testTag("sheetSeek"),
    )
}

@Composable
private fun TimeRow(state: MusicState) {
    var left by rememberSaveable { mutableStateOf(false) }
    val length = state.lengthMs
    val style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(voiceTime(state.positionMs), style = style, color = color, modifier = Modifier.testTag("sheetPosition"))
        Spacer(Modifier.weight(1f))
        Text(
            if (left) stringResource(R.string.music_remaining, voiceTime((length - state.positionMs).coerceAtLeast(0))) else voiceTime(length),
            style = style,
            color = color,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(if (left) R.string.music_show_length else R.string.music_show_left), role = Role.Button) { left = !left }
                .minimumInteractiveComponentSize()
                .padding(horizontal = 8.dp)
                .testTag("sheetLength"),
        )
    }
}

@Composable
private fun Transport(state: MusicState, actions: MusicActions) {
    val previous = remember { MutableInteractionSource() }
    val play = remember { MutableInteractionSource() }
    val next = remember { MutableInteractionSource() }
    val pressedPrevious by previous.collectIsPressedAsState()
    val pressedPlay by play.collectIsPressedAsState()
    val pressedNext by next.collectIsPressedAsState()
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val any = pressedPrevious || pressedPlay || pressedNext
    @Composable
    fun weight(pressed: Boolean, rest: Float): Float = animateFloatAsState(
        when {
            pressed -> rest * PRESSED_WEIGHT
            any -> rest * MAKE_WAY_WEIGHT
            else -> rest
        },
        spec,
        label = "transportWeight",
    ).value
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(TRANSPORT_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(TRANSPORT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(
            onClick = actions.onPrevious,
            corner = if (pressedPrevious) PRESSED_CORNER else HALF,
            container = scheme.secondaryContainer,
            content = scheme.onSecondaryContainer,
            interactionSource = previous,
            modifier = Modifier.weight(weight(pressedPrevious, SIDE_WEIGHT)).testTag("sheetPrevious"),
        ) {
            Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.music_previous), Modifier.size(TRANSPORT_ICON))
        }
        TransportButton(
            onClick = actions.onToggle,
            corner = when {
                pressedPlay -> PRESSED_CORNER
                state.playing -> PLAYING_CORNER
                else -> HALF
            },
            container = scheme.primary,
            content = scheme.onPrimary,
            interactionSource = play,
            toggled = state.playing,
            modifier = Modifier.weight(weight(pressedPlay, PLAY_WEIGHT)).testTag("sheetToggle"),
        ) {
            PlayIcon(state.playing, Modifier.size(PLAY_ICON))
        }
        TransportButton(
            onClick = actions.onNext,
            corner = if (pressedNext) PRESSED_CORNER else HALF,
            container = scheme.secondaryContainer,
            content = scheme.onSecondaryContainer,
            interactionSource = next,
            modifier = Modifier.weight(weight(pressedNext, SIDE_WEIGHT)).testTag("sheetNext"),
        ) {
            Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.music_next), Modifier.size(TRANSPORT_ICON))
        }
    }
}

@Composable
private fun TransportButton(
    onClick: () -> Unit,
    corner: Dp,
    container: Color,
    content: Color,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    toggled: Boolean? = null,
    icon: @Composable () -> Unit,
) {
    val radius by animateDpAsState(corner, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "transportCorner")
    val shape = RoundedCornerShape(radius.coerceAtMost(TRANSPORT_HEIGHT / 2))
    if (toggled != null) {
        Surface(
            checked = toggled,
            onCheckedChange = { onClick() },
            shape = shape,
            color = container,
            contentColor = content,
            interactionSource = interactionSource,
            modifier = modifier.fillMaxHeight(),
        ) { Box(contentAlignment = Alignment.Center) { icon() } }
    } else {
        Surface(
            onClick = onClick,
            shape = shape,
            color = container,
            contentColor = content,
            interactionSource = interactionSource,
            modifier = modifier.fillMaxHeight(),
        ) { Box(contentAlignment = Alignment.Center) { icon() } }
    }
}

@Composable
private fun ModeGroup(state: MusicState, actions: MusicActions, lyricsOpen: Boolean, onLyrics: () -> Unit) {
    val track = state.track
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = MODE_ROW_PADDING, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!track.isVoice) {
            OrderButton(state.order, actions, Modifier.weight(1f))
            ModeButton(
                checked = state.repeat != MusicRepeat.Off,
                onClick = actions.onRepeat,
                description = stringResource(
                    when (state.repeat) {
                        MusicRepeat.Off -> R.string.music_repeat
                        MusicRepeat.All -> R.string.music_repeat_all
                        MusicRepeat.One -> R.string.music_repeat_one
                    },
                ),
                modifier = Modifier.weight(1f).testTag("sheetRepeat"),
            ) { Icon(painterResource(if (state.repeat == MusicRepeat.One) R.drawable.ic_repeat_one else R.drawable.ic_repeat), null) }
        }
        if (track.hasSpeed) {
            ModeButton(
                checked = state.speed != 1f,
                onClick = actions.onSpeed,
                description = stringResource(R.string.music_speed_change, speedText(state.speed)),
                modifier = Modifier.weight(1f).testTag("sheetSpeed"),
            ) { Text(stringResource(R.string.music_speed, speedText(state.speed)), style = MaterialTheme.typography.labelLarge) }
        }
        if (state.lyrics != null) {
            ModeButton(
                checked = lyricsOpen,
                onClick = onLyrics,
                description = stringResource(R.string.music_lyrics),
                modifier = Modifier.weight(1f).testTag("sheetLyricsToggle"),
            ) { Icon(painterResource(R.drawable.ic_lyrics), null) }
        }
    }
}

@Composable
private fun QueueButton(onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonShapes(
            shape = RoundedCornerShape(topStart = CornerSize(QUEUE_CORNER), topEnd = CornerSize(50), bottomEnd = CornerSize(50), bottomStart = CornerSize(QUEUE_CORNER)),
            pressedShape = RoundedCornerShape(QUEUE_CORNER),
        ),
        modifier = Modifier.size(QUEUE_WIDTH, QUEUE_HEIGHT).testTag("sheetQueue"),
    ) {
        Icon(painterResource(R.drawable.ic_queue_music), stringResource(R.string.music_queue))
    }
}

@Composable
private fun SleepButton(sleep: MusicSleep?, actions: MusicActions) {
    var menu by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconToggleButton(
            checked = sleep != null,
            onCheckedChange = { menu = true },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier.testTag("sheetSleep"),
        ) {
            Icon(painterResource(R.drawable.ic_bedtime), stringResource(R.string.music_sleep))
        }
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
private fun MoreButton(state: MusicState, actions: MusicActions) {
    if (state.track.saved) return
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("sheetMore")) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.music_entry_menu))
        }
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
private fun ModeButton(
    checked: Boolean,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    val radius by animateDpAsState(if (checked) MODE_HEIGHT / 2 else MODE_OFF_CORNER, motion.defaultSpatialSpec(), label = "modeCorner")
    val color by animateColorAsState(if (checked) scheme.primary else scheme.surfaceContainerHighest, motion.defaultEffectsSpec(), label = "modeColor")
    val tint by animateColorAsState(if (checked) scheme.onPrimary else scheme.onSurfaceVariant, motion.defaultEffectsSpec(), label = "modeTint")
    Surface(
        checked = checked,
        onCheckedChange = { onClick() },
        shape = RoundedCornerShape(radius),
        color = color,
        contentColor = tint,
        interactionSource = interactionSource,
        modifier = modifier.height(MODE_HEIGHT).semantics { contentDescription = description },
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

@Composable
internal fun modeColors() = ToggleButtonDefaults.colors(
    containerColor = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    checkedContainerColor = MaterialTheme.colorScheme.primary,
    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun OrderButton(order: MusicOrder, actions: MusicActions, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    var held by remember { mutableStateOf(false) }
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
    Box(modifier) {
        ModeButton(
            checked = order != MusicOrder.Chat,
            onClick = { if (held) held = false else actions.onShuffle() },
            description = stringResource(if (order == MusicOrder.Reverse) R.string.music_order_reverse else R.string.music_shuffle),
            interactionSource = interactions,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    onLongClick(chooseLabel) {
                        menu = true
                        true
                    }
                }
                .testTag("sheetShuffle"),
        ) { Icon(painterResource(if (order == MusicOrder.Reverse) R.drawable.ic_swap_vert else R.drawable.ic_shuffle), null) }
        OrderMenu(menu, order, onDismiss = { menu = false }) {
            menu = false
            actions.onOrder(it)
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

private val WAVING = WavyProgressIndicatorDefaults.indicatorAmplitude
private val FLAT: (Float) -> Float = { 0f }
private val THUMB = DpSize(4.dp, 32.dp)
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
private const val MARQUEE_ROUNDS = 3
private val TRANSPORT_HEIGHT = 80.dp
private val TRANSPORT_GAP = 8.dp
private val TRANSPORT_ICON = 32.dp
private val PLAY_ICON = 36.dp
private val HALF = 40.dp
private val PLAYING_CORNER = 26.dp
private val PRESSED_CORNER = 16.dp
private const val SIDE_WEIGHT = 1f
private const val PLAY_WEIGHT = 1.4f
private const val PRESSED_WEIGHT = 1.15f
private const val MAKE_WAY_WEIGHT = 0.8f
private val MODE_HEIGHT = 48.dp
private val MODE_OFF_CORNER = 8.dp
private val MODE_ROW_PADDING = 12.dp
private val QUEUE_WIDTH = 52.dp
private val QUEUE_HEIGHT = 40.dp
private val QUEUE_CORNER = 6.dp
private val PROFILE_CORNER = 12.dp
private val PROFILE_PRESSED_CORNER = 8.dp
