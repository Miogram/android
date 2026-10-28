package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.LiveSharer
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.maps.distanceLabel
import cn.ellerotta.core.ui.theme.MioShapes

class ChatPlaces(
    val onOpen: (MessageItem) -> Unit,
    val sharers: List<LiveSharer> = emptyList(),
    val onOpenLive: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onAlert: ((Int) -> Unit)? = null,
    val onShareAlert: ((seconds: Int, radius: Int?) -> Unit)? = null,
    val alertOnOpen: Boolean = false,
)

internal fun liveAlertOffered(sharers: List<LiveSharer>, private: Boolean): Boolean =
    sharers.any { it.self } || private && sharers.any { !it.self }

internal class LastSharers {
    var sharers: List<LiveSharer> = emptyList()
}

@Composable
internal fun liveBarText(sharers: List<LiveSharer>): String {
    val self = sharers.any { it.self }
    val others = sharers.filter { !it.self }
    return when {
        self && others.isEmpty() -> stringResource(R.string.chat_live_bar_you)
        self && others.size == 1 -> stringResource(R.string.chat_live_bar_you_and, others.first().name)
        self -> pluralStringResource(R.plurals.chat_live_bar_you_and_more, others.size, others.size)
        others.size == 1 -> stringResource(R.string.chat_live_bar_one, others.first().name)
        else -> pluralStringResource(R.plurals.chat_live_bar_more, others.size - 1, others.first().name, others.size - 1)
    }
}

@Composable
internal fun LiveBar(sharers: List<LiveSharer>, onOpen: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier, onAlert: (() -> Unit)? = null, private: Boolean = false) {
    if (sharers.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Surface(shape = MioShapes.previewBar, color = colors.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClickLabel = stringResource(R.string.chat_live_bar_open), role = Role.Button, onClick = onOpen)
                .padding(start = 12.dp, end = 4.dp)
                .testTag("liveBar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Icon(painterResource(R.drawable.ic_share_location_filled), null, Modifier.padding(8.dp).size(20.dp))
            }
            val own = sharers.firstOrNull { it.self }
            val radius = own?.live?.radius?.takeIf { it > 0 }
            val within = radius?.let { stringResource(R.string.proximity_within, distanceLabel(it)) }
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    listOfNotNull(stringResource(R.string.chat_place_live), within).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("liveBarTitle"),
                )
                Text(liveBarText(sharers), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("liveBarText"))
            }
            if (onAlert != null && liveAlertOffered(sharers, private)) {
                IconButton(onClick = onAlert, modifier = Modifier.testTag("liveBarAlert"), shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(if (radius != null) R.drawable.ic_notifications_active_filled else R.drawable.ic_notifications), within ?: stringResource(R.string.proximity_notify), Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                }
            }
            if (own != null) {
                IconButton(onClick = onStop, modifier = Modifier.testTag("liveBarStop"), shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.live_stop), Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                }
            }
        }
    }
}
