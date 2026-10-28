package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Preview
import cn.ellerotta.core.model.ProximityAlert
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.maps.distanceLabel
import cn.ellerotta.core.ui.components.chats.previewText

@Composable
fun CenterChip(text: String, modifier: Modifier = Modifier) {
    var lines by remember { mutableIntStateOf(1) }
    val themed = LocalThemedBackground.current
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        GlassPill(
            if (lines > 1) MaterialTheme.shapes.large else CircleShape,
            Modifier.widthIn(max = CHIP_WIDTH),
            backdrop = if (themed) null else LocalChatBackdrop.current,
            fill = if (themed) colors.secondaryContainer else Color.Black.copy(alpha = PILL_ALPHA),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (themed) colors.onSecondaryContainer else Color.White,
                textAlign = TextAlign.Center,
                onTextLayout = { lines = it.lineCount },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

private val CHIP_WIDTH = 300.dp

@Composable
fun serviceText(message: MessageItem, kind: PeerKind): String {
    message.proximity?.let { return proximityText(it) }
    return previewText(Preview(sender = message.senderName ?: if (message.out) "" else null, service = message.service, added = message.added), kind).text
}

@Composable
internal fun proximityText(alert: ProximityAlert): String {
    val distance = distanceLabel(alert.distance)
    return when {
        alert.to.isEmpty() -> stringResource(R.string.chat_proximity_you, alert.from, distance)
        alert.from.isEmpty() -> stringResource(R.string.chat_proximity_from_you, alert.to, distance)
        else -> stringResource(R.string.chat_proximity, alert.from, alert.to, distance)
    }
}
