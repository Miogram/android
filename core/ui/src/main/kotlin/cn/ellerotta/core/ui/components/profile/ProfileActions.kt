package cn.ellerotta.core.ui.components.profile

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MuteDuration
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.theme.TEXT_CONTRAST
import cn.ellerotta.core.ui.theme.fadedOver
import kotlin.math.roundToInt
import androidx.core.graphics.scale

private val SquareGap = 12.dp
private val SquareSize = 64.dp

@Composable
internal fun ProfileActionRow(
    muted: Boolean,
    canMute: Boolean,
    link: String?,
    onMessage: () -> Unit,
    onMute: (MuteDuration) -> Unit,
    onUnmute: () -> Unit,
    onSearch: (() -> Unit)?,
    modifier: Modifier = Modifier,
    contact: Boolean? = null,
    onAddContact: () -> Unit = {},
    onDeleteContact: () -> Unit = {},
    blocked: Boolean? = null,
    onBlock: () -> Unit = {},
    onUnblock: () -> Unit = {},
    onReport: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var muteMenu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(SquareGap, Alignment.CenterHorizontally)) {
        val square = Modifier.size(SquareSize)
        ActionSquare(R.drawable.ic_chat, stringResource(R.string.profile_message), square.testTag("message"), onClick = onMessage)
        Box(square) {
            ActionSquare(
                if (muted) R.drawable.ic_volume_off else R.drawable.ic_notifications,
                stringResource(if (muted) R.string.action_unmute else R.string.action_mute),
                Modifier.fillMaxWidth().testTag("toggleMute"),
                enabled = canMute,
                onClick = { if (muted) onUnmute() else muteMenu = true },
            )
            DropdownMenu(expanded = muteMenu, onDismissRequest = { muteMenu = false }, modifier = Modifier.testTag("muteMenu")) {
                MuteDuration.entries.forEach { duration ->
                    DropdownMenuItem(
                        text = { Text(stringResource(duration.label)) },
                        onClick = {
                            muteMenu = false
                            onMute(duration)
                        },
                        modifier = Modifier.testTag("muteFor${duration.name}"),
                    )
                }
            }
        }
        ActionSquare(R.drawable.ic_search, stringResource(R.string.chat_search), square.testTag("search"), enabled = onSearch != null, onClick = { onSearch?.invoke() })
        Box(square) {
            ActionSquare(R.drawable.ic_more_horiz, stringResource(R.string.chat_menu), Modifier.fillMaxWidth().testTag("more"), enabled = link != null || contact != null || blocked != null || onReport != null, onClick = { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.testTag("profileMenu")) {
                if (link != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_share)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_share), null) },
                        onClick = {
                            menu = false
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                            context.startActivity(Intent.createChooser(send, null))
                        },
                        modifier = Modifier.testTag("shareLink"),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_copy_link)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_link), null) },
                        onClick = {
                            menu = false
                            copyToClipboard(context, "link", link)
                        },
                        modifier = Modifier.testTag("copyLink"),
                    )
                }
                when (contact) {
                    false -> DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_add_contact)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_person_add), null) },
                        onClick = {
                            menu = false
                            onAddContact()
                        },
                        modifier = Modifier.testTag("addToContacts"),
                    )
                    true -> DropdownMenuItem(
                        text = { Text(stringResource(R.string.contacts_delete)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null) },
                        onClick = {
                            menu = false
                            onDeleteContact()
                        },
                        modifier = Modifier.testTag("deleteContact"),
                    )
                    null -> Unit
                }
                when (blocked) {
                    false -> DropdownMenuItem(
                        text = { Text(stringResource(R.string.block_action), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_block), null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menu = false
                            onBlock()
                        },
                        modifier = Modifier.testTag("blockUser"),
                    )
                    true -> DropdownMenuItem(
                        text = { Text(stringResource(R.string.blocked_unblock)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_front_hand), null) },
                        onClick = {
                            menu = false
                            onUnblock()
                        },
                        modifier = Modifier.testTag("unblockUser"),
                    )
                    null -> Unit
                }
                if (onReport != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.report_action), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_report), null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menu = false
                            onReport()
                        },
                        modifier = Modifier.testTag("reportPeer"),
                    )
                }
            }
        }
    }
}

internal val MuteDuration.label: Int
    @StringRes get() = when (this) {
        MuteDuration.Hour -> R.string.mute_for_hour
        MuteDuration.EightHours -> R.string.mute_for_eight_hours
        MuteDuration.Day -> R.string.mute_for_day
        MuteDuration.Week -> R.string.mute_for_week
        MuteDuration.Forever -> R.string.mute_forever
    }

@Composable
private fun ActionSquare(@DrawableRes icon: Int, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "press")
    val corner by animateDpAsState(if (pressed) 28.dp else 20.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "corner")
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = interaction,
        modifier = modifier.aspectRatio(1f).alpha(if (enabled) 1f else 0.38f).graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.semantics { contentDescription = label },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

internal val ProfileCardShape = RoundedCornerShape(28.dp)

@Composable
internal fun Modifier.profileCardOutline(): Modifier = border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), ProfileCardShape)

@Composable
internal fun ProfileMusicCard(
    music: MediaItem,
    cover: ByteArray?,
    playing: Boolean,
    pattern: ImageBitmap?,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val image = remember(cover) { cover?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    val tone = remember(image) { image?.let(::coverTone) }
    val base = tone ?: MaterialTheme.colorScheme.surfaceContainerLow
    val deep = tone?.let { darken(it, 0.62f) } ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val text = if (tone != null) Color.White else MaterialTheme.colorScheme.onSurface
    val secondary = if (tone != null) musicCardSecondary(tone) else MaterialTheme.colorScheme.onSurfaceVariant
    val title = music.title?.takeIf { it.isNotBlank() } ?: music.fileName.substringBeforeLast('.').ifEmpty { stringResource(R.string.music_unknown_title) }
    val performer = music.performer?.takeIf { it.isNotBlank() } ?: stringResource(R.string.music_unknown_performer)
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(88.dp)
            .clip(ProfileCardShape)
            .background(base)
            .profileCardOutline()
            .combinedClickable(
                onClickLabel = stringResource(if (playing) R.string.music_pause else R.string.music_play),
                onLongClickLabel = onLongClick?.let { stringResource(R.string.profile_songs_order) },
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .testTag("profileMusic"),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val edge = Offset(size.width, size.height / 2)
            drawRect(Brush.radialGradient(listOf(deep, deep.copy(alpha = 0f)), edge, size.width * 0.55f))
            if (pattern == null) return@Canvas
            val tint = ColorFilter.tint(if (tone != null) Color.White else accent)
            musicPatternSpots.forEach { (fx, fy, side) ->
                val px = side.dp.toPx()
                drawImage(pattern, IntOffset((size.width * fx - px / 2).roundToInt(), (size.height * fy - px / 2).roundToInt()), IntSize(px.roundToInt(), px.roundToInt()), alpha = 0.16f, colorFilter = tint)
            }
        }
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                if (image != null) {
                    Image(image.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(painterResource(R.drawable.ic_music_note), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (playing) {
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_pause), null, tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("musicTitle"))
                Text(performer, style = MaterialTheme.typography.bodyMedium, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val musicPatternSpots = listOf(
    Triple(0.62f, 0.3f, 16f),
    Triple(0.7f, 0.74f, 20f),
    Triple(0.79f, 0.22f, 22f),
    Triple(0.86f, 0.68f, 16f),
    Triple(0.94f, 0.36f, 18f),
)

@SuppressLint("UseKtx")
internal fun coverTone(bitmap: Bitmap): Color {
    val small = bitmap.scale(TONE_SIDE, TONE_SIDE)
    val pixels = IntArray(TONE_SIDE * TONE_SIDE).also { small.getPixels(it, 0, TONE_SIDE, 0, 0, TONE_SIDE, TONE_SIDE) }
    val weights = FloatArray(HUE_BUCKETS)
    val sums = Array(HUE_BUCKETS) { FloatArray(3) }
    val all = FloatArray(3)
    val hsv = FloatArray(3)
    pixels.forEach { argb ->
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        all[0] += r
        all[1] += g
        all[2] += b
        android.graphics.Color.colorToHSV(argb, hsv)
        if (hsv[1] < 0.25f || hsv[2] < 0.15f || hsv[2] > 0.97f) return@forEach
        val bucket = (hsv[0] / 360f * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
        weights[bucket] += hsv[1]
        sums[bucket][0] += r * hsv[1]
        sums[bucket][1] += g * hsv[1]
        sums[bucket][2] += b * hsv[1]
    }
    val best = weights.indices.maxBy { weights[it] }
    val color = if (weights[best] >= pixels.size * 0.05f) {
        Color(sums[best][0] / weights[best], sums[best][1] / weights[best], sums[best][2] / weights[best])
    } else {
        Color(all[0] / pixels.size, all[1] / pixels.size, all[2] / pixels.size)
    }
    return legibleTone(color)
}

internal fun legibleTone(color: Color): Color {
    var tone = color
    while (contrastWithWhite(tone) < TEXT_CONTRAST) tone = darken(tone, 0.9f)
    return tone
}

internal fun musicCardSecondary(tone: Color): Color = Color.White.fadedOver(tone, 0.7f, TEXT_CONTRAST)

private const val TONE_SIDE = 24
private const val HUE_BUCKETS = 12

internal fun contrastWithWhite(color: Color): Float = 1.05f / (color.luminance() + 0.05f)

private fun darken(color: Color, factor: Float) = Color(color.red * factor, color.green * factor, color.blue * factor, color.alpha)
