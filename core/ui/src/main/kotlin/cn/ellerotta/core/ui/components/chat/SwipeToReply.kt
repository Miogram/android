package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal val REPLY_THRESHOLD = 72.dp

internal val REPLY_MAX = 100.dp

internal const val REPLY_DAMPING = 0.4f

internal const val REPLY_DIRECTION_RATIO = 1.5f

internal fun replyDistance(raw: Float, threshold: Float, max: Float): Float {
    val travel = raw.coerceIn(0f, max)
    return if (travel > threshold) threshold + (travel - threshold) * REPLY_DAMPING else travel
}

@Composable
internal fun SwipeToReply(onReply: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    if (onReply == null) {
        Box(modifier) { content() }
        return
    }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val reply by rememberUpdatedState(onReply)
    val threshold = with(density) { REPLY_THRESHOLD.toPx() }
    val max = with(density) { REPLY_MAX.toPx() }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) -1f else 1f
    val back = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    var offset by remember { mutableFloatStateOf(0f) }
    Box(modifier) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = BUBBLE_EDGE)
                .size(REPLY_ICON_CIRCLE)
                .graphicsLayer {
                    val progress = (offset * direction / threshold).coerceIn(0f, 1f)
                    alpha = progress
                    scaleX = 0.4f + 0.6f * progress
                    scaleY = 0.4f + 0.6f * progress
                }
                .testTag("replySwipeIcon"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_reply), null, Modifier.size(REPLY_ICON))
            }
        }
        Box(
            Modifier
                .absoluteOffset { IntOffset(offset.roundToInt(), 0) }
                .pointerInput(direction, threshold, max) {
                    coroutineScope {
                        var settling: Job? = null
                        var passed = false
                        fun settle() {
                            settling?.cancel()
                            settling = launch { animate(offset, 0f, animationSpec = back) { value, _ -> offset = value } }
                        }
                        fun drag(by: Float) {
                            val distance = replyDistance(offset * direction + by * direction, threshold, max)
                            offset = distance * direction
                            if (distance >= threshold && !passed) {
                                passed = true
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (distance < threshold * REARM) {
                                passed = false
                            }
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            settling?.cancel()
                            passed = false
                            var over = 0f
                            val start = awaitReplySlop(down.id, direction) { over = it }
                            if (start == null) {
                                settle()
                            } else {
                                drag(over)
                                val completed = horizontalDrag(start.id) { change ->
                                    drag(change.positionChange().x)
                                    change.consume()
                                }
                                if (completed && offset * direction >= threshold) reply()
                                settle()
                            }
                        }
                    }
                },
        ) {
            content()
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitReplySlop(id: PointerId, direction: Float, onStart: (Float) -> Unit): PointerInputChange? {
    var x = 0f
    var y = 0f
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return null
        if (event.changes.any { it.isConsumed } || !change.pressed) return null
        val delta = change.positionChange()
        x += delta.x
        y += delta.y
        val slop = viewConfiguration.touchSlop
        if (abs(x) > slop) {
            if (x * direction > 0f && abs(x) >= abs(y) * REPLY_DIRECTION_RATIO) {
                onStart(x - sign(x) * slop)
                change.consume()
                return change
            }
            if (abs(y) > slop && abs(y) >= abs(x)) return null
        }
    }
}

private const val REARM = 0.9f

private val REPLY_ICON_CIRCLE = 32.dp
private val REPLY_ICON = 18.dp
