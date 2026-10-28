package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.lottie.Lottie
import cn.ellerotta.core.lottie.LottieData
import cn.ellerotta.core.lottie.lottieAvailable
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.ReactionArt
import cn.ellerotta.core.model.ReactionCount
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

fun reactionTag(reaction: Reaction): String = when (reaction) {
    is Reaction.Emoji -> reaction.emoji
    is Reaction.CustomEmoji -> "c${reaction.documentId}"
    Reaction.Paid -> "paid"
}

internal val LocalReactionArt = staticCompositionLocalOf<Map<Reaction, ReactionArt>> { emptyMap() }

internal val LocalReactionEffects = staticCompositionLocalOf<ReactionEffects?> { null }

private val starColor = Color(0xFFF5A623)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReactionChips(
    message: MessageItem,
    emoji: InlineEmoji,
    avatars: Map<String, ByteArray>,
    onToggle: ((Reaction) -> Unit)?,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
    end: Boolean = false,
) {
    val shown = remember(message.id) { message.reactions.mapTo(HashSet()) { it.reaction } }
    FlowRow(modifier.testTag("reactions_${message.id}"), horizontalArrangement = Arrangement.spacedBy(CHIP_GAP, if (end) Alignment.End else Alignment.Start)) {
        message.reactions.forEach { count ->
            key(count.reaction) {
                val click = onToggle?.takeIf { count.reaction != Reaction.Paid }
                val fresh = remember { shown.add(count.reaction) }
                ReactionChip(message.id, count, emoji, avatars, fresh, click?.let { { it(count.reaction) } }, onLongPress, Modifier.testTag("reaction_${message.id}_${reactionTag(count.reaction)}"))
            }
        }
    }
}

@Composable
private fun ReactionChip(messageId: Int, count: ReactionCount, emoji: InlineEmoji, avatars: Map<String, ByteArray>, fresh: Boolean, onClick: (() -> Unit)?, onLongPress: (() -> Unit)?, modifier: Modifier) {
    val colors = bubbleColors()
    val motion = MaterialTheme.motionScheme
    val paid = count.reaction == Reaction.Paid
    val background by animateColorAsState(
        when {
            paid && count.chosen -> starColor
            paid -> starColor.copy(alpha = 0.22f).compositeOver(colors.container)
            count.chosen -> colors.accent
            else -> colors.accent.copy(alpha = 0.12f).compositeOver(colors.container)
        },
        motion.fastEffectsSpec(),
        label = "chip",
    )
    val content by animateColorAsState(
        when {
            paid && count.chosen -> Color.Black
            count.chosen -> colors.onAccent
            else -> colors.content
        },
        motion.fastEffectsSpec(),
        label = "chipContent",
    )
    val corner by animateDpAsState(if (count.chosen) CHIP_HEIGHT / 2 else CHIP_CORNER, motion.fastSpatialSpec(), label = "chipCorner")
    val shape = RoundedCornerShape(corner)
    val label = reactionLabel(count.reaction, emoji)
    val description = pluralStringResource(R.plurals.chat_reaction_count, count.count, label, count.count)
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val dip by animateFloatAsState(if (pressed) PRESSED_CHIP_SCALE else 1f, motion.fastSpatialSpec(), label = "chipPress")
    val click = if (onClick != null) Modifier.combinedClickable(interactionSource = press, indication = ripple(), onLongClick = onLongPress, onClick = onClick) else Modifier
    val grow = remember { Animatable(if (fresh) 0.4f else 1f) }
    val growSpec = motion.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) { if (grow.value < 1f) grow.animateTo(1f, growSpec) }
    val effects = LocalReactionEffects.current
    val target = effects?.current?.takeIf { it.messageId == messageId && it.reaction == count.reaction }
    Row(
        modifier
            .graphicsLayer {
                scaleX = grow.value * dip
                scaleY = grow.value * dip
                alpha = grow.value.coerceIn(0f, 1f)
            }
            .minimumInteractiveComponentSize()
            .clip(shape)
            .drawBehind { drawRect(background) }
            .then(click)
            .semantics(mergeDescendants = true) {}
            .clearAndSetSemantics {
                contentDescription = description
                selected = count.chosen
                if (onClick != null) role = Role.Button
            }
            .heightIn(min = CHIP_HEIGHT)
            .padding(start = 8.dp, end = if (count.showsReactors) 4.dp else 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .then(if (target != null && effects != null) Modifier.onGloballyPositioned { effects.target = it.boundsInRoot() } else Modifier)
                .graphicsLayer { alpha = if (target != null && effects?.landed == false) 0f else 1f },
        ) {
            ReactionGlyph(count.reaction, emoji, ICON_SIZE)
        }
        if (count.showsReactors) {
            val ring = background
            Row(Modifier.padding(start = 4.dp)) {
                count.recent.take(count.count).forEachIndexed { index, reactor ->
                    Box(Modifier.offset(x = -AVATAR_OVERLAP * index).border(1.5.dp, ring, CircleShape).padding(1.5.dp)) {
                        Avatar(reactor.name, reactor.avatarKey?.let(avatars::get), size = REACTOR_SIZE, seed = reactor.peerId)
                    }
                }
            }
        } else {
            RollingCount(count.count, content, Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun RollingCount(count: Int, color: Color, modifier: Modifier = Modifier) {
    val text = compactCount(count)
    val previous = remember { CountMemory(count) }
    val up = count >= previous.value
    previous.value = count
    val motion = MaterialTheme.motionScheme
    Row(modifier) {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                AnimatedContent(
                    char,
                    transitionSpec = { rolled(up, motion) },
                    label = "count",
                ) { shown ->
                    Text(shown.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
                }
            }
        }
    }
}

private class CountMemory(var value: Int)

private fun rolled(up: Boolean, motion: MotionScheme): ContentTransform {
    val sign = if (up) 1 else -1
    val spec = motion.fastSpatialSpec<IntOffset>()
    return ContentTransform(
        slideInVertically(spec) { sign * it } + fadeIn(motion.fastEffectsSpec()),
        slideOutVertically(spec) { -sign * it } + fadeOut(motion.fastEffectsSpec()),
        sizeTransform = SizeTransform(clip = true),
    )
}

internal fun compactCount(count: Int): String = when {
    count < 1_000 -> "$count"
    count < 1_000_000 -> "%.1fK".format(java.util.Locale.ROOT, count / 1_000.0).replace(".0K", "K")
    else -> "%.1fM".format(java.util.Locale.ROOT, count / 1_000_000.0).replace(".0M", "M")
}

@Composable
internal fun reactionLabel(reaction: Reaction, emoji: InlineEmoji): String = when (reaction) {
    is Reaction.Emoji -> reaction.emoji
    is Reaction.CustomEmoji -> emoji.documents[reaction.documentId]?.emoji ?: stringResource(R.string.chat_reaction_custom)
    Reaction.Paid -> stringResource(R.string.chat_reaction_paid)
}

@Composable
internal fun ReactionGlyph(reaction: Reaction, emoji: InlineEmoji, size: Dp) {
    val fontSize = with(LocalDensity.current) { (size * 0.8f).toSp() }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        when (reaction) {
            is Reaction.Emoji -> {
                val still = LocalReactionArt.current[reaction]?.still
                val bytes = still?.let { emoji.files[it.key] }
                if (still != null && bytes != null) StickerView(still, bytes, size = size) else Text(reaction.emoji, fontSize = fontSize)
            }
            is Reaction.CustomEmoji -> {
                val document = emoji.documents[reaction.documentId]
                val bytes = document?.let { emoji.files[it.key] }
                if (document != null && bytes != null) StickerView(document, bytes, size = size) else Text(document?.emoji ?: "\u2026", fontSize = fontSize)
            }
            Reaction.Paid -> Text("\u2B50", fontSize = fontSize)
        }
    }
}

internal data class ReactionEffect(val messageId: Int, val reaction: Reaction, val from: Rect?, val big: Boolean)

@Stable
internal class ReactionEffects {
    var current by mutableStateOf<ReactionEffect?>(null)
        private set
    var target by mutableStateOf<Rect?>(null)
    var landed by mutableStateOf(true)
    var started by mutableLongStateOf(0L)
        private set

    fun play(effect: ReactionEffect) {
        target = null
        landed = effect.from == null
        current = effect
        started++
    }

    fun done(effect: ReactionEffect) {
        if (current == effect) {
            current = null
            target = null
            landed = true
        }
    }
}

@Composable
internal fun ReactionEffectLayer(effects: ReactionEffects, emoji: InlineEmoji, arts: Map<Reaction, ReactionArt>, modifier: Modifier = Modifier) {
    val effect = effects.current ?: return
    val art = arts[effect.reaction]
    CompositionLocalProvider(LocalReactionArt provides arts) { EffectContent(effects, effect, art, emoji, modifier) }
}

@Composable
private fun EffectContent(effects: ReactionEffects, effect: ReactionEffect, art: ReactionArt?, emoji: InlineEmoji, modifier: Modifier) {
    key(effects.started) { EffectPhases(effects, effect, art, emoji, modifier) }
}

@Composable
private fun EffectPhases(effects: ReactionEffects, effect: ReactionEffect, art: ReactionArt?, emoji: InlineEmoji, modifier: Modifier) {
    val fly = remember { Animatable(0f) }
    val flySpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val target = effects.target
    LaunchedEffect(Unit) {
        delay(TARGET_WAIT_MS)
        if (effects.target == null) effects.done(effect)
    }
    LaunchedEffect(target != null) {
        if (target != null && !effects.landed) {
            fly.animateTo(1f, flySpec)
            effects.landed = true
        }
    }
    if (target != null) {
        Box(modifier) {
            val from = effect.from
            if (!effects.landed && from != null) {
                val center = lerp(from.center, target.center, fly.value)
                val side = from.width + (target.width * LANDING_SCALE - from.width) * fly.value
                Glyph(effect.reaction, emoji, center, side)
            } else {
                Landed(effects, effect, art, emoji, target)
            }
        }
    }
}

@Composable
private fun Landed(effects: ReactionEffects, effect: ReactionEffect, art: ReactionArt?, emoji: InlineEmoji, target: Rect) {
    val iconSide = target.width
    val main = if (effect.big) art?.activate else art?.center
    val around = if (effect.big) art?.effect else art?.around
    val mainBytes = main?.let { emoji.files[it.key] }
    val aroundBytes = around?.let { emoji.files[it.key] }
    if (!lottieAvailable || (mainBytes == null && aroundBytes == null)) {
        Bounce(effect, emoji, target.center, iconSide) { effects.done(effect) }
    } else {
        val bigSide = with(LocalDensity.current) { BIG_SIDE.toPx() }
        val mainSide = if (effect.big) bigSide else iconSide * LANDING_SCALE
        val aroundSide = if (effect.big) mainSide * 2 else iconSide * AROUND_SCALE
        val clock = remember { EffectClock() }
        if (aroundBytes != null) OnceLottie(aroundBytes, target.center, aroundSide, clock)
        if (mainBytes != null) OnceLottie(mainBytes, target.center, mainSide, clock) else Glyph(effect.reaction, emoji, target.center, mainSide)
        LaunchedEffect(Unit) {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < EFFECT_MAX_MS) {
                delay(FRAME_POLL_MS)
                if (clock.last > 0 && System.currentTimeMillis() - clock.last > FRAME_GAP_MS) break
            }
            effects.done(effect)
        }
    }
}

private class EffectClock {
    var last = 0L
}

@Composable
private fun OnceLottie(bytes: ByteArray, center: Offset, side: Float, clock: EffectClock) {
    val json = remember(bytes) { runCatching { if (LottieData.isGzip(bytes)) LottieData.fromTgs(bytes) else bytes }.getOrNull() } ?: return
    val dp = with(LocalDensity.current) { side.toDp() }
    Lottie(
        json,
        Modifier.offset { IntOffset((center.x - side / 2).roundToInt(), (center.y - side / 2).roundToInt()) }.size(dp),
        repeat = false,
        onFrame = { clock.last = System.currentTimeMillis() },
    )
}

@Composable
private fun Glyph(reaction: Reaction, emoji: InlineEmoji, center: Offset, side: Float) {
    val dp = with(LocalDensity.current) { side.toDp() }
    Box(Modifier.offset { IntOffset((center.x - side / 2).roundToInt(), (center.y - side / 2).roundToInt()) }) {
        ReactionGlyph(reaction, emoji, dp)
    }
}

@Composable
private fun Bounce(effect: ReactionEffect, emoji: InlineEmoji, center: Offset, side: Float, onDone: () -> Unit) {
    val scale = remember { Animatable(LANDING_SCALE) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spec)
        onDone()
    }
    Glyph(effect.reaction, emoji, center, side * scale.value)
}

@Composable
internal fun ReactionPicture(media: MediaItem?, files: Map<String, ByteArray>, size: Dp, fallback: @Composable () -> Unit) {
    val bytes = media?.let { files[it.key] }
    if (media != null && bytes != null) StickerView(media, bytes, size = size) else fallback()
}

private val CHIP_HEIGHT = 32.dp
private val CHIP_CORNER = 8.dp
private val CHIP_GAP = 8.dp

private const val PRESSED_CHIP_SCALE = 0.9f
private val ICON_SIZE = 20.dp
private const val REACTOR_SIZE = 21
private val AVATAR_OVERLAP = 12.dp
private val BIG_SIDE = 120.dp
private const val LANDING_SCALE = 2f
private const val AROUND_SCALE = 6f
private const val TARGET_WAIT_MS = 1_000L
private const val EFFECT_MAX_MS = 3_000L
private const val FRAME_POLL_MS = 100L
private const val FRAME_GAP_MS = 250L
