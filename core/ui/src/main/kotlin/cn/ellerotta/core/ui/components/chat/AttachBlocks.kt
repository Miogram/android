package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.min
import cn.ellerotta.core.model.GeoMaps
import cn.ellerotta.core.ui.components.maps.AvatarMarker
import cn.ellerotta.core.ui.components.maps.GeoMapView
import cn.ellerotta.core.ui.components.maps.LocalGeoMapImages
import cn.ellerotta.core.ui.components.maps.MapPin
import cn.ellerotta.core.ui.components.maps.updatedLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ContactCard
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar

@Composable
internal fun PlaceBlock(place: GeoPlace, messageId: Int, accent: Color, now: Long, enabled: Boolean, avatars: Map<String, ByteArray> = emptyMap(), onOpen: (() -> Unit)? = null) {
    val uri = LocalUriHandler.current
    val live = place.live
    val over = live != null && live.isOver(now.toInt())
    val title = when {
        over -> stringResource(R.string.chat_place_live_ended)
        live != null -> stringResource(R.string.chat_place_live)
        else -> place.title ?: stringResource(R.string.chat_place)
    }
    val updated = live?.updated
    val subtitle = when {
        live != null && !over && updated != null -> updatedLabel((now - updated).coerceAtLeast(0))
        else -> place.address ?: place.coordinates
    }
    val open: () -> Unit = onOpen ?: { runCatching { uri.openUri(place.geoUri) } }
    if (LocalGeoMapImages.current == null || live?.stopped == true) {
        AttachCard(
            onClick = open,
            enabled = enabled,
            label = stringResource(R.string.chat_place_open),
            tag = "place_$messageId",
            leading = {
                Surface(shape = MaterialTheme.shapes.large, color = accent, contentColor = MaterialTheme.colorScheme.surface) {
                    Icon(painterResource(if (live != null) R.drawable.ic_share_location else R.drawable.ic_location_on), null, Modifier.padding(8.dp).size(24.dp))
                }
            },
            title = title,
            subtitle = subtitle,
        )
        return
    }
    BoxWithConstraints(Modifier.padding(vertical = 4.dp)) {
        val width = min(maxWidth, PLACE_MAP_WIDTH)
        Column(
            Modifier
                .width(width)
                .clip(MaterialTheme.shapes.large)
                .clickable(enabled = enabled, onClickLabel = stringResource(R.string.chat_place_open), role = Role.Button, onClick = open)
                .testTag("place_$messageId"),
        ) {
            GeoMapView(
                place,
                zoom = GeoMaps.zoomOf(place),
                modifier = Modifier.fillMaxWidth().height(width * PLACE_MAP_RATIO).clip(MaterialTheme.shapes.large).testTag("placeMap_$messageId"),
            ) {
                if (live != null && !over) {
                    val name = live.senderName.orEmpty()
                    AvatarMarker(name, live.senderAvatar?.let { avatars[it] }, live.heading, Modifier.align(Alignment.Center))
                } else {
                    MapPin(Modifier.align(Alignment.Center).tipOnCentre())
                }
            }
            Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal fun Modifier.tipOnCentre(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { placeable.place(0, -placeable.height / 2) }
}

private val PLACE_MAP_WIDTH = 264.dp
private const val PLACE_MAP_RATIO = 0.6f

@Composable
internal fun ContactBlock(card: ContactCard, messageId: Int, enabled: Boolean) {
    val uri = LocalUriHandler.current
    val phone = if (card.phone.startsWith("+")) card.phone else "+${card.phone}"
    AttachCard(
        onClick = { runCatching { uri.openUri("tel:$phone") } },
        enabled = enabled,
        label = stringResource(R.string.chat_contact_call, card.name),
        tag = "contact_$messageId",
        leading = { Avatar(card.name, null, size = 40, seed = card.userId) },
        title = card.name,
        subtitle = phone,
    )
}

@Composable
private fun AttachCard(onClick: () -> Unit, enabled: Boolean, label: String, tag: String, leading: @Composable () -> Unit, title: String, subtitle: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
        modifier = Modifier.widthIn(min = 200.dp).heightIn(min = 56.dp).padding(vertical = 4.dp).testTag(tag),
    ) {
        Row(
            Modifier.clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading()
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
