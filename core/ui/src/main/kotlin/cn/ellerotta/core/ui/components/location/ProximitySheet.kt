package cn.ellerotta.core.ui.components.location

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.LiveSharer
import cn.ellerotta.core.model.ProximityRadii
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.attach.LiveDurationContent
import cn.ellerotta.core.ui.components.maps.distanceLabel
import cn.ellerotta.core.ui.components.maps.distanceUnits
import kotlin.math.roundToInt

@Composable
fun ProximityContent(name: String?, distance: Double?, current: Int?, onSet: (Int) -> Unit, onRemove: () -> Unit, starting: Boolean = false) {
    val steps = ProximityRadii.steps(distanceUnits())
    val set = current?.takeIf { it > 0 }
    var index by rememberSaveable(steps, set) { mutableIntStateOf(ProximityRadii.nearest(steps, set ?: ProximityRadii.DEFAULT)) }
    val radius = steps[index]
    val within = distanceLabel(radius)
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(stringResource(R.string.proximity_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() })
        if (name != null && distance != null) {
            Text(
                stringResource(R.string.proximity_away, name, distanceLabel(distance)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp).testTag("proximityDistance"),
            )
        }
        Text(
            if (name != null) stringResource(R.string.proximity_sentence, name, within) else stringResource(R.string.proximity_sentence_anyone, within),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp).testTag("proximitySentence"),
        )
        Slider(
            value = index.toFloat(),
            onValueChange = { index = it.roundToInt().coerceIn(steps.indices) },
            valueRange = 0f..steps.lastIndex.toFloat(),
            steps = steps.size - 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics { stateDescription = within }.testTag("proximitySlider"),
        )
        if (name != null && distance != null && distance <= radius) {
            Text(
                stringResource(R.string.proximity_already, name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).testTag("proximityAlready"),
            )
        }
        if (starting) {
            Text(
                stringResource(R.string.proximity_share_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp).testTag("proximityShareHint"),
            )
        }
        val label = when {
            starting -> R.string.proximity_share_and_set
            set == null -> R.string.proximity_set
            else -> R.string.proximity_update
        }
        Button(
            onClick = { onSet(radius) },
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp).heightIn(min = 56.dp).testTag("proximitySet"),
            shapes = ButtonDefaults.shapes(),
        ) { Text(stringResource(label)) }
        if (set != null) {
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp).heightIn(min = 48.dp).testTag("proximityRemove"),
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.proximity_remove)) }
        }
    }
}

@Composable
fun ProximitySheet(name: String?, distance: Double?, current: Int?, onAlert: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ProximityContent(
            name, distance, current,
            onSet = {
                onDismiss()
                onAlert(it)
            },
            onRemove = {
                onDismiss()
                onAlert(0)
            },
        )
    }
}

@Composable
fun LiveShareSheet(alertName: String?, distance: Double?, askFirst: Boolean, onShare: (seconds: Int, radius: Int?) -> Unit, onDismiss: () -> Unit) {
    var radius by rememberSaveable { mutableStateOf<Int?>(null) }
    var asking by rememberSaveable { mutableStateOf(askFirst) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (asking) {
            ProximityContent(
                alertName, distance, radius,
                onSet = {
                    radius = it
                    asking = false
                },
                onRemove = {
                    radius = null
                    asking = false
                },
                starting = askFirst,
            )
        } else {
            LiveDurationContent(
                onShare = { seconds ->
                    onDismiss()
                    onShare(seconds, radius)
                },
                alertName = alertName,
                alertOffered = askFirst || alertName != null,
                radius = radius,
                onAlert = { asking = true },
            )
        }
    }
}

@Composable
fun LiveAlertSheet(
    sharers: List<LiveSharer>,
    person: String?,
    me: GeoPlace?,
    onAlert: (Int) -> Unit,
    onShare: ((seconds: Int, radius: Int?) -> Unit)?,
    onDismiss: () -> Unit,
    focus: String? = null,
) {
    val own = sharers.firstOrNull { it.self }
    val target = sharers.firstOrNull { !it.self && it.key == focus } ?: sharers.firstOrNull { !it.self }
    val name = target?.name?.ifBlank { null } ?: person
    val from = own?.place ?: me
    val distance = target?.let { from?.distanceTo(it.place) }
    when {
        own != null -> ProximitySheet(name, distance, own.live.radius, onAlert, onDismiss)
        onShare != null -> LiveShareSheet(name, distance, askFirst = true, onShare, onDismiss)
    }
}
