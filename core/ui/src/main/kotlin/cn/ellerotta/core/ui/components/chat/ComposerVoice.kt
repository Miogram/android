package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.VoiceRecording
import cn.ellerotta.core.ui.R

@Composable
internal fun RecordButton(
    recording: VoiceRecording?,
    onStart: () -> Unit,
    onStop: (Boolean) -> Unit,
    onLock: () -> Unit,
    onTap: () -> Unit,
    onDrag: (Float) -> Unit,
) {
    val start by rememberUpdatedState(onStart)
    val stop by rememberUpdatedState(onStop)
    val lock by rememberUpdatedState(onLock)
    val tap by rememberUpdatedState(onTap)
    val drag by rememberUpdatedState(onDrag)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val lockDistance = with(density) { LOCK_RISE.toPx() }
    val cancelDistance = with(density) { CANCEL_DRAG.toPx() }
    var held by remember { mutableStateOf(false) }
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var rise by remember { mutableFloatStateOf(0f) }
    var slide by remember { mutableFloatStateOf(0f) }
    val active = recording != null
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val effects = motion.fastEffectsSpec<Float>()
    val spatial = motion.fastSpatialSpec<Float>()
    val corner by animateDpAsState(if (active) RECORDING_CORNER else IDLE_CORNER, motion.fastSpatialSpec(), label = "recordCorner")
    val scale by animateFloatAsState(if (active) 1.06f else 1f, spatial, label = "recordScale")
    val container by animateColorAsState(if (active) colors.errorContainer else colors.surfaceContainerLow, motion.defaultEffectsSpec(), label = "recordColor")
    val content by animateColorAsState(if (active) colors.onErrorContainer else colors.onSurfaceVariant, motion.defaultEffectsSpec(), label = "recordContent")
    val level by animateFloatAsState(recording?.level ?: 0f, effects, label = "level")
    val halo = colors.error.copy(alpha = 0.2f)
    Box(Modifier.size(ACTION_SIZE), contentAlignment = Alignment.Center) {
        LockHint(held, (rise / lockDistance).coerceIn(0f, 1f), Modifier.align(Alignment.TopCenter))
        Surface(
            shape = RoundedCornerShape(corner),
            color = container,
            contentColor = content,
            modifier = Modifier
                .size(ACTION_SIZE)
                .onGloballyPositioned { placed[0] = it }
                .pointerInput(lockDistance, cancelDistance) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun onScreen(local: Offset) = (placed[0]?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero) + local
                        val pressedAt = onScreen(down.position)
                        var lifted = false
                        val waiting = withTimeoutOrNull(HOLD_MS) { lifted = waitForUpOrCancellation() != null }
                        if (waiting != null) {
                            if (lifted) tap()
                            return@awaitEachGesture
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        start()
                        held = true
                        var outcome = RecordOutcome.Cancel
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) {
                                    outcome = if (change.uptimeMillis - down.uptimeMillis < MIN_HOLD_MS) RecordOutcome.Cancel else RecordOutcome.Send
                                    break
                                }
                                val now = onScreen(change.position)
                                rise = (pressedAt.y - now.y).coerceAtLeast(0f)
                                slide = (now.x - pressedAt.x).coerceAtMost(0f)
                                drag(slide)
                                if (rise >= lockDistance) {
                                    outcome = RecordOutcome.Lock
                                    break
                                }
                                if (-slide >= cancelDistance) break
                            }
                        } finally {
                            held = false
                            rise = 0f
                            slide = 0f
                            drag(0f)
                            when (outcome) {
                                RecordOutcome.Lock -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    lock()
                                }
                                RecordOutcome.Send -> stop(true)
                                RecordOutcome.Cancel -> stop(false)
                            }
                        }
                    }
                }
                .graphicsLayer {
                    translationX = slide
                    scaleX = scale
                    scaleY = scale
                }
                .drawBehind { if (active) drawCircle(halo, radius = size.minDimension / 2 * (1f + 0.5f * level)) }
                .semantics { role = Role.Button }
                .testTag("mic"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    active,
                    transitionSpec = { (fadeIn(effects) + scaleIn(spatial, initialScale = 0.8f)) togetherWith (fadeOut(effects) + scaleOut(spatial, targetScale = 0.8f)) },
                    label = "recordIcon",
                ) { recordingNow ->
                    Icon(
                        painterResource(if (recordingNow) R.drawable.ic_lock_open else R.drawable.ic_mic),
                        stringResource(if (recordingNow) R.string.chat_voice_recording else R.string.chat_record_voice),
                    )
                }
            }
        }
    }
}

private enum class RecordOutcome { Send, Cancel, Lock }

@Composable
private fun LockHint(visible: Boolean, progress: Float, modifier: Modifier = Modifier) {
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val travel = with(LocalDensity.current) { LOCK_HINT_TRAVEL.toPx() }
    AnimatedVisibility(
        visible,
        modifier = modifier.offset(y = LOCK_HINT_OFFSET).graphicsLayer { translationY = -travel * progress },
        enter = fadeIn(effects) + scaleIn(spatial, initialScale = 0.92f),
        exit = fadeOut(effects) + scaleOut(spatial, targetScale = 0.92f),
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
            Box(Modifier.heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp).testTag("lockHint"), contentAlignment = Alignment.Center) {
                Icon(painterResource(if (progress >= 1f) R.drawable.ic_lock else R.drawable.ic_north), stringResource(R.string.chat_voice_lock_hint), Modifier.size(24.dp))
            }
        }
    }
}

@Composable
internal fun RecordingShell(recording: VoiceRecording, locked: Boolean, drag: Float, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (locked) 1f else 0.985f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "shellScale")
    val cancelDistance = with(LocalDensity.current) { CANCEL_DRAG.toPx() }
    val content = if (locked) colors.onSecondaryContainer else colors.onSurface
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerHigh,
        modifier = modifier.height(ACTION_SIZE).graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = if (locked) colors.secondaryContainer else colors.surfaceContainerLow,
            contentColor = content,
            modifier = Modifier.fillMaxWidth().padding(4.dp).height(FIELD_HEIGHT),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("recording"), verticalAlignment = Alignment.CenterVertically) {
                if (locked) {
                    IconButton(onClick = onDiscard, modifier = Modifier.testTag("discardVoice"), shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.chat_voice_discard), tint = content)
                    }
                    Text(
                        stringResource(R.string.chat_voice_recording),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    Text(voiceTime(recording.elapsedMs), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp).testTag("voiceTimer"))
                } else {
                    BouncingDot()
                    Text(voiceTime(recording.elapsedMs), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp).testTag("voiceTimer"))
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.graphicsLayer {
                            translationX = drag / 2
                            alpha = 1f - (-drag / cancelDistance).coerceIn(0f, 1f)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_chevron_left), null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                        Text(stringResource(R.string.chat_voice_cancel), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BouncingDot() {
    val transition = rememberInfiniteTransition(label = "dot")
    val scale by transition.animateFloat(
        1f,
        1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = DOT_CYCLE_MS
                1f at 0
                1.32f at 220
                0.9f at 420
                1.08f at 650
                1f at DOT_CYCLE_MS
            },
        ),
        label = "dotScale",
    )
    val alpha by transition.animateFloat(
        1f,
        1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = DOT_CYCLE_MS
                1f at 0
                0.72f at 220
                1f at 650
                1f at DOT_CYCLE_MS
            },
        ),
        label = "dotAlpha",
    )
    Box(
        Modifier
            .size(10.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .background(MaterialTheme.colorScheme.error, CircleShape),
    )
}

@Composable
internal fun VoiceHint() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
            Text(
                stringResource(R.string.chat_voice_hint),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).testTag("voiceHint"),
            )
        }
    }
}

internal val ACTION_SIZE = 56.dp

internal val FIELD_HEIGHT = 48.dp

internal val CANCEL_DRAG = 120.dp

private val LOCK_RISE = 72.dp

private val LOCK_HINT_OFFSET = (-64).dp
private val LOCK_HINT_TRAVEL = 24.dp
private val IDLE_CORNER = 28.dp
private val RECORDING_CORNER = 16.dp
private const val DOT_CYCLE_MS = 1_200

internal const val HOLD_MS = 120L

internal const val MIN_HOLD_MS = 1_000L
