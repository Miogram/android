package cn.ellerotta.core.ui.components.location

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.TravelMode
import cn.ellerotta.core.ui.components.chat.BubblePosition
import cn.ellerotta.core.ui.components.chat.ChatBackground
import cn.ellerotta.core.ui.components.chat.LiveBar
import cn.ellerotta.core.ui.components.chat.MessageBubble
import cn.ellerotta.core.ui.components.attach.LiveActions
import cn.ellerotta.core.ui.components.attach.LiveDurationContent
import cn.ellerotta.core.ui.components.attach.LocationPickActions
import cn.ellerotta.core.ui.components.attach.LocationShareScreen
import cn.ellerotta.core.ui.components.attach.LocationShareState
import cn.ellerotta.core.ui.components.attach.LocationStatus
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewSheetTheme
import cn.ellerotta.core.ui.components.common.PreviewTheme
import cn.ellerotta.core.ui.components.location.LocationPreviewData as Data
import cn.ellerotta.core.ui.components.maps.LocalGeoMapImages
import cn.ellerotta.core.ui.components.maps.LocalMapImages

@Composable
private fun MapsPreviewTheme(content: @Composable () -> Unit) {
    PreviewTheme { CompositionLocalProvider(LocalMapImages provides Data.maps, LocalGeoMapImages provides Data.geoMaps, content = content) }
}

@Composable
private fun PlaceBubble(message: MessageItem) {
    MessageBubble(message, BubblePosition(first = true, last = true), downloads = emptyMap(), stickers = emptyMap(), onDownload = {}, avatars = Data.avatars, onOpenPlace = {})
}

@MioPreviews
@Composable
internal fun PlaceBubblesPreview() {
    MapsPreviewTheme {
        Box {
            ChatBackground(Modifier.matchParentSize(), null)
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                PlaceBubble(Data.pointMessage)
                PlaceBubble(Data.venueMessage)
                PlaceBubble(Data.liveMessage)
            }
        }
    }
}

@MioPreviews
@Composable
internal fun PlaceBubblesWithoutMapsPreview() {
    PreviewTheme {
        Box {
            ChatBackground(Modifier.matchParentSize(), null)
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                PlaceBubble(Data.venueMessage)
                PlaceBubble(Data.liveMessage)
            }
        }
    }
}

@MioPreviews
@Composable
internal fun LiveBarPreview() {
    PreviewTheme {
        Column(Modifier.padding(8.dp)) {
            LiveBar(Data.alerted, onOpen = {}, onStop = {}, onAlert = {})
            LiveBar(listOf(Data.alice), onOpen = {}, onStop = {}, modifier = Modifier.padding(top = 8.dp), onAlert = {}, private = true)
            LiveBar(listOf(Data.alice), onOpen = {}, onStop = {}, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@MioPreviews
@Composable
internal fun LocationViewPreview() {
    MapsPreviewTheme {
        LocationViewScreen(
            LocationViewState(Data.venue, Data.venue.title, Data.venue.address, me = Data.me, travel = TravelMode.Walking, route = Data.route),
            emptyMap(),
            LocationViewActions({}, {}, {}, {}),
        )
    }
}

@MioPreviews
@Composable
internal fun LocationViewLivePreview() {
    MapsPreviewTheme {
        LocationViewScreen(
            LocationViewState(Data.alice.place, "Alice Moreau", "Karl-Liebknecht-Straße 8, 10178 Berlin", me = Data.me, sharers = Data.alerted, focus = Data.alice.key),
            Data.avatars,
            LocationViewActions({}, {}, {}, {}, onAlert = {}, onShareAlert = { _, _ -> }),
        )
    }
}

private val pickActions = LocationPickActions({ _, _ -> }, {}, {}, {})

@MioPreviews
@Composable
internal fun LocationPickerPreview() {
    MapsPreviewTheme {
        LocationShareScreen(Data.picker, busy = false, onAllow = {}, onRetry = {}, onSendCurrent = {}, live = LiveActions({ _, _ -> }, {}), onBack = {}, pick = pickActions)
    }
}

@MioPreviews
@Composable
internal fun LocationPickerSearchPreview() {
    MapsPreviewTheme {
        LocationShareScreen(Data.searching, busy = false, onAllow = {}, onRetry = {}, onSendCurrent = {}, live = LiveActions({ _, _ -> }, {}), onBack = {}, pick = pickActions)
    }
}

@MioPreviews
@Composable
internal fun LocationPickerNoPermissionPreview() {
    MapsPreviewTheme {
        LocationShareScreen(LocationShareState(LocationStatus.NoPermission, maps = true), busy = false, onAllow = {}, onRetry = {}, onSendCurrent = {}, live = LiveActions({ _, _ -> }, {}), onBack = {}, pick = pickActions)
    }
}

@MioPreviews
@Composable
internal fun LiveDurationPreview() {
    PreviewSheetTheme { LiveDurationContent(onShare = {}, alertName = "Alice Moreau", alertOffered = true) }
}

@MioPreviews
@Composable
internal fun LiveDurationAlertPreview() {
    PreviewSheetTheme { LiveDurationContent(onShare = {}, alertName = "Alice Moreau", alertOffered = true, radius = 500) }
}

@MioPreviews
@Composable
internal fun ProximityStartPreview() {
    PreviewSheetTheme { ProximityContent("Alice Moreau", distance = null, current = null, onSet = {}, onRemove = {}, starting = true) }
}

@MioPreviews
@Composable
internal fun ProximityNewPreview() {
    PreviewSheetTheme { ProximityContent("Alice Moreau", distance = 1240.0, current = null, onSet = {}, onRemove = {}) }
}

@MioPreviews
@Composable
internal fun ProximityUpdatePreview() {
    PreviewSheetTheme { ProximityContent("Bob", distance = 320.0, current = 500, onSet = {}, onRemove = {}) }
}
