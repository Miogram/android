package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ActivityKind
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import cn.ellerotta.core.ui.components.profile.presenceText
import kotlin.math.PI
import kotlin.math.sin

internal enum class StatusGroup { Connection, Activity, Subtitle, Online, LastSeen, Members, BotUsers, Kind }

internal sealed interface StatusLine {
    val group: StatusGroup

    data class Connection(val status: ChatsStatus) : StatusLine {
        override val group get() = StatusGroup.Connection
    }

    data class Activity(val kind: ActivityKind, val names: List<String>) : StatusLine {
        override val group get() = StatusGroup.Activity
    }

    data class Seen(val presence: Presence) : StatusLine {
        override val group
            get() = when (presence) {
                Presence.Online -> StatusGroup.Online
                is Presence.Members -> StatusGroup.Members
                else -> StatusGroup.LastSeen
            }
    }

    data class BotUsers(val count: Int) : StatusLine {
        override val group get() = StatusGroup.BotUsers
    }

    data class Subtitle(val text: String) : StatusLine {
        override val group get() = StatusGroup.Subtitle
    }

    data class Kind(val kind: PeerKind) : StatusLine {
        override val group get() = StatusGroup.Kind
    }
}

internal fun statusLine(dialog: DialogItem?, connection: ChatsStatus = ChatsStatus.Ready, subtitle: String? = null): StatusLine? {
    if (connection != ChatsStatus.Ready) return StatusLine.Connection(connection)
    val typing = dialog?.typing
    if (dialog != null && typing != null && !dialog.isSaved) return StatusLine.Activity(dialog.activity, if (typing.isEmpty() && dialog.typingMore.isEmpty()) emptyList() else listOf(typing) + dialog.typingMore)
    if (subtitle != null) return StatusLine.Subtitle(subtitle)
    dialog ?: return null
    return when (dialog.kind) {
        PeerKind.Saved -> null
        PeerKind.User -> {
            val presence = dialog.presence
            when {
                dialog.online -> StatusLine.Seen(Presence.Online)
                presence != null && presence != Presence.Unknown -> StatusLine.Seen(presence)
                else -> null
            }
        }
        PeerKind.Bot -> if (dialog.botUsers > 0) StatusLine.BotUsers(dialog.botUsers) else StatusLine.Kind(PeerKind.Bot)
        PeerKind.Group, PeerKind.Channel -> if (dialog.members > 0) {
            val channel = dialog.kind == PeerKind.Channel
            StatusLine.Seen(Presence.Members(dialog.members, if (channel) null else dialog.membersOnline, channel))
        } else {
            StatusLine.Kind(dialog.kind)
        }
    }
}

@Composable
internal fun ChatStatus(line: StatusLine?, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    val spatial = motion.fastSpatialSpec<IntOffset>()
    val effects = motion.fastEffectsSpec<Float>()
    val shift = with(LocalDensity.current) { STATUS_SHIFT.roundToPx() }
    AnimatedContent(
        line,
        modifier = modifier,
        transitionSpec = { (slideInVertically(spatial) { -shift } + fadeIn(effects)) togetherWith (slideOutVertically(spatial) { shift } + fadeOut(effects)) },
        contentKey = { it?.group },
        label = "status",
    ) { shown ->
        val accent = shown is StatusLine.Activity || (shown as? StatusLine.Seen)?.presence == Presence.Online
        val color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        when (shown) {
            null -> Unit
            is StatusLine.Activity -> Row(Modifier.testTag("chatStatus"), verticalAlignment = Alignment.CenterVertically) {
                ActivityIndicator(shown.kind, color)
                Spacer(Modifier.width(4.dp))
                Text(activityText(shown.kind, shown.names), style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            else -> Text(
                statusText(shown),
                style = MaterialTheme.typography.bodySmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("chatStatus"),
            )
        }
    }
}

@Composable
private fun statusText(line: StatusLine): String = when (line) {
    is StatusLine.Connection -> stringResource(
        when (line.status) {
            ChatsStatus.WaitingForNetwork -> R.string.chats_waiting_network
            ChatsStatus.Connecting -> R.string.chats_connecting
            else -> R.string.chats_updating
        },
    )
    is StatusLine.Subtitle -> line.text
    is StatusLine.Seen -> presenceText(line.presence, remember(line.presence) { System.currentTimeMillis() })
    is StatusLine.BotUsers -> pluralStringResource(R.plurals.chat_bot_users, line.count, line.count)
    is StatusLine.Kind -> stringResource(
        when (line.kind) {
            PeerKind.Bot -> R.string.presence_bot
            PeerKind.Channel -> R.string.chat_channel
            else -> R.string.chat_group
        },
    )
    is StatusLine.Activity -> activityText(line.kind, line.names)
}

@Composable
internal fun activityText(kind: ActivityKind, names: List<String>): String {
    val known = names.filter { it.isNotEmpty() }
    val several = names.size > 1
    val verb = stringResource(activityVerb(kind, several))
    return when {
        known.isEmpty() -> verb
        names.size == 1 -> stringResource(R.string.chat_activity_one, known[0], verb)
        names.size == 2 && known.size == 2 -> stringResource(R.string.chat_activity_two, known[0], known[1], verb)
        else -> pluralStringResource(R.plurals.chat_activity_more, names.size - 1, known[0], names.size - 1, verb)
    }
}

private fun activityVerb(kind: ActivityKind, several: Boolean): Int = when (kind) {
    ActivityKind.Typing -> if (several) R.string.chat_activity_typing_many else R.string.chat_activity_typing
    ActivityKind.RecordVoice -> if (several) R.string.chat_activity_record_voice_many else R.string.chat_activity_record_voice
    ActivityKind.UploadPhoto -> if (several) R.string.chat_activity_upload_photo_many else R.string.chat_activity_upload_photo
    ActivityKind.UploadVideo -> if (several) R.string.chat_activity_upload_video_many else R.string.chat_activity_upload_video
    ActivityKind.UploadVoice -> if (several) R.string.chat_activity_upload_voice_many else R.string.chat_activity_upload_voice
    ActivityKind.UploadFile -> if (several) R.string.chat_activity_upload_file_many else R.string.chat_activity_upload_file
    ActivityKind.RecordRound -> if (several) R.string.chat_activity_record_round_many else R.string.chat_activity_record_round
    ActivityKind.UploadRound -> if (several) R.string.chat_activity_upload_round_many else R.string.chat_activity_upload_round
    ActivityKind.ChooseSticker -> if (several) R.string.chat_activity_choose_sticker_many else R.string.chat_activity_choose_sticker
    ActivityKind.PlayGame -> if (several) R.string.chat_activity_play_game_many else R.string.chat_activity_play_game
}

internal enum class ActivityStyle { Dots, Waves, Bar }

internal fun activityStyle(kind: ActivityKind): ActivityStyle = when (kind) {
    ActivityKind.RecordVoice -> ActivityStyle.Waves
    ActivityKind.UploadPhoto, ActivityKind.UploadVideo, ActivityKind.UploadVoice, ActivityKind.UploadFile, ActivityKind.UploadRound -> ActivityStyle.Bar
    ActivityKind.Typing, ActivityKind.RecordRound, ActivityKind.ChooseSticker, ActivityKind.PlayGame -> ActivityStyle.Dots
}

@Composable
internal fun ActivityIndicator(kind: ActivityKind, color: Color, modifier: Modifier = Modifier) {
    val style = activityStyle(kind)
    val loop = rememberInfiniteTransition(label = "activity")
    val time by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (style == ActivityStyle.Bar) BAR_MILLIS else LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
    )
    Canvas(modifier.size(24.dp, 16.dp).testTag("activity_${style.name}")) {
        val centre = size.height / 2
        when (style) {
            ActivityStyle.Dots -> repeat(3) { dot ->
                val phase = (time - dot * 0.2f).mod(1f)
                val swell = sin(phase * PI.toFloat())
                drawCircle(color.copy(alpha = 0.75f + 0.25f * swell), (3f + 1.5f * swell).dp.toPx() / 2, Offset(size.width / 2 + (dot - 1) * 5.5.dp.toPx(), centre))
            }
            ActivityStyle.Waves -> {
                val lit = (time * 4).toInt()
                repeat(3) { arc ->
                    val radius = (4 + 5 * arc).dp.toPx()
                    val left = 3.dp.toPx()
                    drawArc(
                        color.copy(alpha = if (arc < lit) 1f else 0.3f),
                        startAngle = -18f,
                        sweepAngle = 36f,
                        useCenter = false,
                        topLeft = Offset(left - radius, centre - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            ActivityStyle.Bar -> {
                val width = 13.dp.toPx()
                val height = 4.dp.toPx()
                val left = (size.width - width) / 2
                val top = centre - height / 2
                val corner = CornerRadius(1.25.dp.toPx())
                drawRoundRect(color.copy(alpha = 0.3f), Offset(left, top), Size(width, height), corner)
                drawRoundRect(color, Offset(left, top), Size(width * BarEasing.transform(time), height), corner)
            }
        }
    }
}

private val BarEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val STATUS_SHIFT = 4.dp
private const val LOOP_MILLIS = 700
private const val BAR_MILLIS = 1750
