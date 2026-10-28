package cn.ellerotta.core.ui.components.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.PeerBar
import cn.ellerotta.core.ui.R

class PeerBarActions(val onReportSpam: () -> Unit, val onAddContact: () -> Unit, val onBlock: () -> Unit, val onDismiss: () -> Unit)

@Composable
fun PeerActionBar(bar: PeerBar, actions: PeerBarActions, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("peerBar"),
    ) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(stringResource(R.string.peer_bar_text), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (bar.addContact) BarButton(R.string.peer_bar_add_contact, "peerBarAdd", MaterialTheme.colorScheme.primary, actions.onAddContact)
                    if (bar.block) BarButton(R.string.peer_bar_block, "peerBarBlock", MaterialTheme.colorScheme.error, actions.onBlock)
                    if (bar.reportSpam) BarButton(R.string.peer_bar_report_spam, "peerBarSpam", MaterialTheme.colorScheme.error, actions.onReportSpam)
                }
            }
            IconButton(onClick = actions.onDismiss, modifier = Modifier.size(48.dp).testTag("peerBarDismiss"), shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.peer_bar_dismiss), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BarButton(label: Int, tag: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp).testTag(tag)) {
        Text(stringResource(label), color = color)
    }
}
