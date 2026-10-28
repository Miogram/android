package cn.ellerotta.core.ui.components.profile

import android.graphics.Bitmap
import android.util.LruCache
import android.graphics.BitmapFactory
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import kotlin.math.hypot
import kotlin.random.Random
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import cn.ellerotta.core.lottie.LottieAnimation
import cn.ellerotta.core.lottie.LottieData
import cn.ellerotta.core.lottie.lottieAvailable
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.ProfileDetails
import cn.ellerotta.core.model.ProfileGifts
import cn.ellerotta.core.model.SavedGift
import cn.ellerotta.core.model.StickerFormat
import cn.ellerotta.core.model.StoryRing
import cn.ellerotta.core.model.stickerFormat
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.StickerView
import cn.ellerotta.core.ui.components.chat.tinted
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.StoryRingBox
import cn.ellerotta.core.model.bigPhotoKey
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val profileAccents = listOf(
    Color(0xFFFF885E),
    Color(0xFFFFB85C),
    Color(0xFF8C86FF),
    Color(0xFF7ED67E),
    Color(0xFF3FD8D8),
    Color(0xFF5EC4FD),
    Color(0xFFE0A2F3),
)

internal fun profileAccent(colorIndex: Int): Color = profileAccents[colorIndex.mod(profileAccents.size)]

private val AvatarTop = 33.dp
private val AvatarSize = 105.dp

val ProfileHeaderHeight = 210.dp

internal fun Map<String, ByteArray>.photoOf(avatarKey: String?): ByteArray? = avatarKey?.let { this[bigPhotoKey(it)] ?: this[it] }

@Immutable
data class ProfileDecoration(
    val emojiStatus: MediaItem? = null,
    val pattern: MediaItem? = null,
    val gifts: List<SavedGift> = emptyList(),
    val files: Map<String, ByteArray> = emptyMap(),
)

fun profileDecoration(profile: ProfileDetails?, customEmoji: Map<Long, MediaItem>, gifts: ProfileGifts, files: Map<String, ByteArray>): ProfileDecoration = ProfileDecoration(
    emojiStatus = profile?.emojiStatusId?.let(customEmoji::get),
    pattern = profile?.patternEmojiId?.let(customEmoji::get),
    gifts = gifts.page?.gifts.orEmpty().filter { gift -> gift.pinned && gift.unique.let { it != null && it.id != profile?.collectibleGiftId } }.take(MAX_FLOATING_GIFTS),
    files = files,
)

private const val MAX_FLOATING_GIFTS = 12

@Composable
fun rememberHeaderCollapse(list: LazyListState): Float {
    val height = with(LocalDensity.current) { ProfileHeaderHeight.toPx() }
    val collapse by remember(list, height) {
        derivedStateOf { if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / (height * 0.7f)).coerceIn(0f, 1f) }
    }
    return collapse
}

@Composable
fun ProfileHeader(
    name: String,
    subtitle: String,
    avatar: ByteArray?,
    seed: Long,
    colorIndex: Int,
    verified: Boolean,
    premium: Boolean,
    collapse: Float,
    modifier: Modifier = Modifier,
    decoration: ProfileDecoration = ProfileDecoration(),
    onLoadSticker: (MediaItem) -> Unit = {},
    onAvatarClick: (() -> Unit)? = null,
    onGiftClick: ((SavedGift) -> Unit)? = null,
    storyRing: StoryRing = StoryRing.None,
    onOpenStories: (() -> Unit)? = null,
) {
    val accent = profileAccent(colorIndex)
    Box(modifier.fillMaxWidth().statusBarsPadding().height(ProfileHeaderHeight)) {
        decoration.pattern?.let { pattern ->
            val image = rememberPatternImage(pattern, decoration.files[pattern.key])
            ProfilePattern(image, accent, collapse, Modifier.fillMaxWidth().height(ProfileHeaderHeight).testTag("profilePattern").patternState(image))
        }
        Column(
            Modifier.fillMaxWidth().graphicsLayer {
                val scale = lerp(1f, 0.55f, collapse)
                scaleX = scale
                scaleY = scale
                alpha = 1f - collapse
                translationY = collapse * 60.dp.toPx()
                transformOrigin = TransformOrigin(0.5f, (size.height + AvatarTop.toPx()) / 2 / size.height)
            }.padding(top = AvatarTop),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(AvatarSize), contentAlignment = Alignment.Center) {
                FloatingGifts(decoration.gifts, decoration.files, maxWidth, collapse, accent, onLoadSticker, onGiftClick)
                val photoLabel = stringResource(R.string.profile_open_photo)
                val storiesLabel = stringResource(R.string.stories_ring_open)
                val openStories = onOpenStories?.takeIf { storyRing != StoryRing.None }
                val click = openStories ?: onAvatarClick
                Box(
                    Modifier
                        .size(AvatarSize)
                        .clip(CircleShape)
                        .then(if (click != null) Modifier.clickable(onClickLabel = if (openStories != null) storiesLabel else photoLabel, onClick = click) else Modifier)
                        .testTag("profileAvatar"),
                ) {
                    StoryRingBox(storyRing, AvatarSize.value.toInt()) { photo -> Avatar(name, avatar, size = photo, seed = seed) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false).testTag("profileName"),
                )
                if (verified) Icon(painterResource(R.drawable.ic_verified), stringResource(R.string.chats_verified), Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                val status = decoration.emojiStatus
                if (status != null) {
                    val label = stringResource(R.string.profile_emoji_status)
                    StickerView(
                        status,
                        decoration.files[status.key],
                        Modifier.then(if (status.textColor) Modifier.tinted(accent) else Modifier).semantics { contentDescription = label }.testTag("emojiStatus"),
                        size = 28.dp,
                    )
                } else if (premium) {
                    Icon(painterResource(R.drawable.ic_star), stringResource(R.string.chats_premium), Modifier.size(24.dp), tint = accent)
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("profileStatus"),
            )
        }
    }
}

private class PatternSpot(val x: Float, val y: Float, val size: Float, val ring: Int)

private val patternSpots: List<PatternSpot> = run {
    val random = Random(PATTERN_SEED)
    (0 until PATTERN_RINGS).flatMap { ring ->
        val radius = AvatarSize.value / 2 + 22f + 28f * ring
        val turn = if (ring % 2 == 1) PI / 6 else 0.0
        List(6) { i ->
            val angle = -PI / 2 + turn + i * PI / 3
            PatternSpot((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat(), 26f * (0.7f + 0.3f * random.nextFloat()), ring)
        }
    }
}

private const val PATTERN_RINGS = 5
private const val PATTERN_SEED = 229

private const val PATTERN_REACH = 230f

@Composable
private fun ProfilePattern(image: ImageBitmap?, accent: Color, collapse: Float, modifier: Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width / 2, (AvatarTop + AvatarSize / 2).toPx())
        val glow = 150.dp.toPx()
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.24f * (1f - collapse)), accent.copy(alpha = 0f)), center, glow), glow, center)
        if (image == null) return@Canvas
        val tint = ColorFilter.tint(accent)
        val progress = (collapse * 1.6f).coerceAtMost(1f)
        patternSpots.forEach { spot ->
            val start = spot.ring * 0.1f
            val t = ((progress - start) / (1f - (PATTERN_RINGS - 1) * 0.1f)).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
            val distance = hypot(spot.x, spot.y)
            val pull = lerp(1f, 20f / distance, t)
            val alpha = 0.6f * (1f - distance / PATTERN_REACH).coerceAtLeast(0f) * (1f - t)
            if (alpha <= 0f) return@forEach
            val side = spot.size.dp.toPx()
            val x = center.x + (spot.x * pull).dp.toPx() - side / 2
            val y = center.y + (spot.y * pull).dp.toPx() - side / 2
            drawImage(image, dstOffset = IntOffset(x.roundToInt(), y.roundToInt()), dstSize = IntSize(side.roundToInt(), side.roundToInt()), alpha = alpha, colorFilter = tint)
        }
    }
}

@Composable
internal fun rememberPatternImage(media: MediaItem, bytes: ByteArray?): ImageBitmap? {
    val image by produceState(patternStills.get(media.key), bytes, media.thumb) {
        if (value != null && value === patternStills.get(media.key)) return@produceState
        value = withContext(Dispatchers.Default) { bytes?.let(::stillOf)?.also { patternStills.put(media.key, it) } ?: media.thumb?.let(::decodeStill) }
    }
    return image
}

private val patternStills = LruCache<String, ImageBitmap>(64)

internal val PatternDrawn = SemanticsPropertyKey<Boolean>("PatternDrawn")
private var SemanticsPropertyReceiver.patternDrawn by PatternDrawn

internal fun Modifier.patternState(image: ImageBitmap?): Modifier = semantics { patternDrawn = image != null }

private fun stillOf(bytes: ByteArray): ImageBitmap? = when (stickerFormat(bytes)) {
    StickerFormat.Lottie -> lottieStill(bytes)
    StickerFormat.Image -> decodeStill(bytes) ?: lottieStill(bytes)
    StickerFormat.Video -> null
}

private fun decodeStill(bytes: ByteArray): ImageBitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

private fun lottieStill(bytes: ByteArray): ImageBitmap? {
    if (!lottieAvailable) return null
    val json = runCatching { if (LottieData.isGzip(bytes)) LottieData.fromTgs(bytes) else bytes }.getOrNull() ?: return null
    return LottieAnimation.load(json)?.use { animation ->
        if (!animation.render(0f, PATTERN_PIXELS, PATTERN_PIXELS)) return@use null
        Bitmap.createBitmap(PATTERN_PIXELS, PATTERN_PIXELS, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(animation.pixels.rewind()) }.asImageBitmap()
    }
}

private const val PATTERN_PIXELS = 96

private const val GIFT_SIZE = 32f

internal class GiftSpot(val x: Float, val y: Float, val scale: Float, val bobPeriod: Float, val bobHeight: Float, val wigglePeriod: Float, val wiggle: Float)

private class Keepout(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun hits(x: Float, y: Float, half: Float) = x + half > left && x - half < right && y + half > top && y - half < bottom
}

internal fun giftSpots(count: Int, width: Float, random: Random): List<GiftSpot> {
    val radius = AvatarSize.value / 2
    val centreY = AvatarTop.value + radius
    val half = width / 2
    val keepouts = listOf(
        Keepout(-half, -centreY, -half + 16f + 48f + 8f, -centreY + 16f + 48f + 4f),
        Keepout(half - 16f - 76f - 8f, -centreY, half, -centreY + 16f + 48f + 4f),
        Keepout(-130f, radius + 6f, 130f, radius + 6f + 76f),
    )
    val placed = ArrayList<GiftSpot>()
    for (i in 0 until count) {
        val near = i < 4
        val side = if (i % 2 == 0) 1f else -1f
        val gap = if (placed.size > 5) 29f else 42f
        for (attempt in 0 until 80) {
            val distance = radius * if (near) 1.4f + 0.8f * random.nextFloat() else 2.5f + 1.1f * random.nextFloat()
            val angle = (random.nextFloat() - 0.5f) * PI.toFloat() * 0.9f
            val x = side * cos(angle) * distance
            val y = sin(angle) * distance
            val scale = if (near) 0.75f + 0.4f * random.nextFloat() else lerp(1.15f, 0.7f, ((distance / radius) - 2.5f) / 1.1f)
            val halfSize = GIFT_SIZE * scale / 2
            val inside = abs(x) + halfSize <= half - 5f && y - halfSize >= -centreY + 4f && y + halfSize <= ProfileHeaderHeight.value - centreY
            if (!inside || keepouts.any { it.hits(x, y, halfSize) }) continue
            if (placed.any { hypot(it.x - x, it.y - y) < gap }) continue
            placed += GiftSpot(x, y, scale, 3.5f + random.nextFloat(), 1f + random.nextFloat(), 2f + random.nextFloat(), 0.025f + 0.025f * random.nextFloat())
            break
        }
    }
    return placed
}

internal class Sparkle(val angle: Float, val distance: Float, val size: Float, val period: Float, val phase: Float)

internal fun giftSparkles(count: Int, random: Random): List<Sparkle> = List(count) { i ->
    val angle = 2 * PI.toFloat() / count * (i + 0.2f + 0.6f * random.nextFloat())
    val size = 2f + 1.5f * random.nextFloat()
    val distance = GIFT_SIZE / 2 + size / 2 + (SPARKLE_ROOM - 1.5f * size) * random.nextFloat()
    Sparkle(angle, distance, size, 1.6f + random.nextFloat(), random.nextFloat())
}

private const val SPARKLE_ROOM = 8f

private const val SPARKLES_PER_GIFT = 3

private fun DrawScope.drawSparkles(sparkles: List<Sparkle>, time: Float, color: Color) {
    sparkles.forEach { sparkle ->
        val glow = sin(2 * PI.toFloat() * (time / sparkle.period + sparkle.phase))
        if (glow <= 0f) return@forEach
        val arm = sparkle.size.dp.toPx() * glow
        val waist = arm * 0.22f
        val x = center.x + cos(sparkle.angle) * sparkle.distance.dp.toPx()
        val y = center.y + sin(sparkle.angle) * sparkle.distance.dp.toPx()
        val star = Path().apply {
            moveTo(x, y - arm)
            lineTo(x + waist, y - waist)
            lineTo(x + arm, y)
            lineTo(x + waist, y + waist)
            lineTo(x, y + arm)
            lineTo(x - waist, y + waist)
            lineTo(x - arm, y)
            lineTo(x - waist, y - waist)
            close()
        }
        drawPath(star, color.copy(alpha = glow))
    }
}

@Composable
private fun FloatingGifts(gifts: List<SavedGift>, files: Map<String, ByteArray>, width: Dp, collapse: Float, accent: Color, onLoadSticker: (MediaItem) -> Unit, onGiftClick: ((SavedGift) -> Unit)?) {
    if (gifts.isEmpty()) return
    val spots = remember(gifts.size, width) { giftSpots(gifts.size, width.value, Random(System.nanoTime())) }
    val sparkles = remember(gifts.size) { Random(System.nanoTime()).let { random -> List(gifts.size) { giftSparkles(SPARKLES_PER_GIFT, random) } } }
    val time by produceState(0f) {
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) withInfiniteAnimationFrameMillis { value = (it - start) / 1000f }
    }
    val gone = (collapse * 1.33f).coerceAtMost(1f)
    gifts.zip(spots).forEachIndexed { index, (gift, spot) ->
        Box(
            Modifier
                .offset((spot.x * (1f - gone)).dp, (spot.y * (1f - gone)).dp)
                .graphicsLayer {
                    val local = (time - index * 1.5f).coerceAtLeast(0f)
                    translationY = sin(2 * PI.toFloat() * local / spot.bobPeriod) * spot.bobHeight.dp.toPx()
                    rotationZ = Math.toDegrees((sin(2 * PI * local / spot.wigglePeriod) * spot.wiggle).toDouble()).toFloat() + gone * 32.4f
                    val scale = spot.scale * (1f - gone)
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - gone
                }
                .size((GIFT_SIZE + 2 * SPARKLE_ROOM).dp)
                .drawWithContent {
                    drawContent()
                    drawSparkles(sparkles[index], (time - index * 1.5f).coerceAtLeast(0f), accent)
                }
                .then(if (onGiftClick != null) Modifier.clickable { onGiftClick(gift) } else Modifier)
                .testTag("pinnedGift_${gift.key}"),
            contentAlignment = Alignment.Center,
        ) {
            GiftSticker(gift.sticker, files, onLoadSticker, GIFT_SIZE.dp)
        }
    }
}
