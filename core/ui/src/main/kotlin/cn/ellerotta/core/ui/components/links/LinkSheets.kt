package cn.ellerotta.core.ui.components.links

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatInvitePreview
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar

@Composable
fun InviteSheet(invite: ChatInvitePreview, onJoin: () -> Unit, onDismiss: () -> Unit) {
    LinkSheet("inviteSheet", onDismiss) { InviteContent(invite, onJoin) }
}

@Composable
internal fun InviteContent(invite: ChatInvitePreview, onJoin: () -> Unit) {
    LinkColumn {
        Avatar(invite.title, invite.photo, size = 80, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(invite.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        val members = if (invite.channel) R.plurals.links_subscribers else R.plurals.links_members
        Text(
            pluralStringResource(members, invite.participants, invite.participants),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("inviteMembers"),
        )
        invite.about?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        if (invite.requestNeeded) {
            Text(stringResource(R.string.links_request_needed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        val label = when {
            invite.requestNeeded -> R.string.links_request_join
            invite.channel -> R.string.links_join_channel
            else -> R.string.links_join_group
        }
        Button(onClick = onJoin, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().testTag("joinInvite")) { Text(stringResource(label)) }
    }
}

@Composable
fun UnsupportedLinkSheet(url: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    LinkSheet("unsupportedLink", onDismiss) { UnsupportedLinkContent(url, onCopy) }
}

@Composable
internal fun UnsupportedLinkContent(url: String, onCopy: () -> Unit) {
    LinkColumn {
        Text(stringResource(R.string.links_unsupported_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.links_unsupported_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(url, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, modifier = Modifier.testTag("linkText")) }
        FilledTonalButton(onClick = onCopy, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().testTag("copyLink")) { Text(stringResource(R.string.links_copy)) }
    }
}

@Composable
fun LoginCodeSheet(code: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    LinkSheet("loginCodeSheet", onDismiss) { LoginCodeContent(code, onCopy) }
}

@Composable
internal fun LoginCodeContent(code: String, onCopy: () -> Unit) {
    LinkColumn {
        Text(stringResource(R.string.links_login_code_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        SelectionContainer {
            Text(code, style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag("loginCode"))
        }
        Text(stringResource(R.string.links_login_code_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onCopy, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().testTag("copyCode")) { Text(stringResource(R.string.links_copy_code)) }
    }
}

@Composable
private fun LinkSheet(tag: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        modifier = Modifier.testTag(tag),
        content = { content() },
    )
}

@Composable
private fun LinkColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
