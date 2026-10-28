package cn.ellerotta.core.ui.components.groups

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.InviteLink
import cn.ellerotta.core.model.InviteLinks
import cn.ellerotta.core.model.NewInviteLink
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.CheckedSwitch
import cn.ellerotta.core.ui.components.common.ConfirmDialog
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.components.profile.InfoCard
import cn.ellerotta.core.ui.components.profile.InfoDivider
import cn.ellerotta.core.ui.components.profile.InfoRow
import cn.ellerotta.core.ui.components.profile.copyToClipboard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun InviteLinksScreen(
    links: InviteLinks?,
    requestsPending: Int,
    busy: Boolean,
    onBack: () -> Unit,
    onCreate: (NewInviteLink) -> Unit,
    onRevoke: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenRequests: () -> Unit,
) {
    val context = LocalContext.current
    var creating by rememberSaveable { mutableStateOf(false) }
    var revoking by remember { mutableStateOf<InviteLink?>(null) }
    val copied = stringResource(R.string.invite_copied)
    fun copy(link: InviteLink) {
        copyToClipboard(context, "link", link.link)
        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
    }
    fun share(link: InviteLink) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link.link)
        context.startActivity(Intent.createChooser(send, null))
    }
    val list = rememberLazyListState()
    val title = stringResource(R.string.invite_links_title)
    Page(title, rememberTopBarState(list), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("inviteLinkList"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "busy") { if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            links?.primary?.let { primary ->
                item(key = "primary") {
                    InfoCard(title = stringResource(R.string.invite_primary)) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Text(
                                primary.link.removePrefix("https://"),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(16.dp).testTag("primaryLink"),
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val pad = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            FilledTonalButton(onClick = { copy(primary) }, contentPadding = pad, modifier = Modifier.weight(1f).testTag("copyPrimary"), shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.invite_copy), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            FilledTonalButton(onClick = { share(primary) }, contentPadding = pad, modifier = Modifier.weight(1f).testTag("sharePrimary"), shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.invite_share), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { revoking = primary }, modifier = Modifier.testTag("revokePrimary")) {
                                Text(stringResource(R.string.invite_revoke), color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(
                            linkDetails(primary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                        )
                    }
                }
            }
            item(key = "actions") {
                InfoCard(Modifier.padding(top = 16.dp)) {
                    InfoRow(
                        R.drawable.ic_person,
                        stringResource(R.string.join_requests_title),
                        if (requestsPending > 0) pluralStringResource(R.plurals.invite_waiting, requestsPending, requestsPending) else null,
                        modifier = Modifier.testTag("joinRequests"),
                        onClick = onOpenRequests,
                    )
                    InfoDivider()
                    InfoRow(R.drawable.ic_add, stringResource(R.string.invite_create), null, modifier = Modifier.testTag("createLink"), onClick = { creating = true })
                }
            }
            val others = links?.others.orEmpty()
            if (others.isNotEmpty()) {
                item(key = "others") {
                    InfoCard(Modifier.padding(top = 16.dp), title = stringResource(R.string.invite_others)) {
                        others.forEachIndexed { index, link ->
                            if (index > 0) InfoDivider()
                            LinkRow(link, onCopy = { copy(link) }, onShare = { share(link) }, onRevoke = { revoking = link })
                        }
                    }
                }
            }
            val revoked = links?.revoked.orEmpty()
            if (revoked.isNotEmpty()) {
                item(key = "revoked") {
                    InfoCard(Modifier.padding(top = 16.dp), title = stringResource(R.string.invite_revoked)) {
                        revoked.forEachIndexed { index, link ->
                            if (index > 0) InfoDivider()
                            InfoRow(
                                R.drawable.ic_link, link.title ?: link.link.removePrefix("https://"), linkDetails(link),
                                modifier = Modifier.testTag("revoked_${link.link}"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                                trailing = {
                                    TextButton(onClick = { onDelete(link.link) }, modifier = Modifier.testTag("delete_${link.link}")) {
                                        Text(stringResource(R.string.invite_delete), color = MaterialTheme.colorScheme.error)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        CreateInviteDialog(
            onCreate = {
                creating = false
                onCreate(it)
            },
            onDismiss = { creating = false },
        )
    }
    revoking?.let { link ->
        ConfirmDialog(
            title = stringResource(R.string.invite_revoke_title),
            text = if (link.primary) stringResource(R.string.invite_revoke_primary_text) else stringResource(R.string.invite_revoke_text, link.title ?: link.link),
            confirm = stringResource(R.string.invite_revoke),
            tag = "confirmRevoke",
            onConfirm = {
                revoking = null
                onRevoke(link.link)
            },
            onDismiss = { revoking = null },
        )
    }
}

@Composable
private fun LinkRow(link: InviteLink, onCopy: () -> Unit, onShare: () -> Unit, onRevoke: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        InfoRow(R.drawable.ic_link, link.title ?: link.link.removePrefix("https://"), linkDetails(link), modifier = Modifier.testTag("link_${link.link}"), onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.invite_copy)) }, onClick = { open = false; onCopy() }, modifier = Modifier.testTag("copyLink"))
            DropdownMenuItem(text = { Text(stringResource(R.string.invite_share)) }, onClick = { open = false; onShare() }, modifier = Modifier.testTag("shareLink"))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.invite_revoke), color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onRevoke() },
                modifier = Modifier.testTag("revokeLink"),
            )
        }
    }
}

@Composable
private fun linkDetails(link: InviteLink): String {
    val locale = LocalConfiguration.current.locales[0]
    val parts = buildList {
        val limit = link.usageLimit
        add(if (limit != null) stringResource(R.string.invite_usage_limit, link.usage, limit) else pluralStringResource(R.plurals.invite_joined, link.usage, link.usage))
        if (link.requested > 0) add(pluralStringResource(R.plurals.invite_waiting, link.requested, link.requested))
        if (link.requestNeeded) add(stringResource(R.string.invite_by_request))
        link.expireDate?.let { expires ->
            if (!link.revoked && expires <= System.currentTimeMillis() / 1000) {
                add(stringResource(R.string.invite_expired))
            } else {
                val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
                add(stringResource(R.string.invite_expires, formatter.format(Instant.ofEpochSecond(expires.toLong()).atZone(ZoneId.systemDefault()))))
            }
        }
    }
    return parts.joinToString(" · ")
}

private val EXPIRIES = listOf(null to R.string.invite_expiry_never, 3_600 to R.string.invite_expiry_hour, 86_400 to R.string.invite_expiry_day, 604_800 to R.string.invite_expiry_week)
private val LIMITS = listOf(1, 10, 100)

@Composable
internal fun CreateInviteDialog(onCreate: (NewInviteLink) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var expiresIn by rememberSaveable { mutableStateOf<Int?>(null) }
    var limit by rememberSaveable { mutableStateOf<Int?>(null) }
    var requestNeeded by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.invite_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(32) },
                    label = { Text(stringResource(R.string.invite_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("inviteName"),
                )
                Text(stringResource(R.string.invite_expiry), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EXPIRIES.forEach { (seconds, label) ->
                        FilterChip(selected = expiresIn == seconds, onClick = { expiresIn = seconds }, label = { Text(stringResource(label)) }, modifier = Modifier.testTag("expiry_${seconds ?: 0}"))
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = requestNeeded, role = Role.Switch, onValueChange = { requestNeeded = it })
                        .testTag("requestNeeded"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.invite_request_needed), modifier = Modifier.weight(1f))
                    CheckedSwitch(checked = requestNeeded, onCheckedChange = null)
                }
                Text(stringResource(R.string.invite_limit), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = limit == null, enabled = !requestNeeded, onClick = { limit = null }, label = { Text(stringResource(R.string.invite_limit_none)) }, modifier = Modifier.testTag("limit_0"))
                    LIMITS.forEach { count ->
                        FilterChip(
                            selected = limit == count,
                            enabled = !requestNeeded,
                            onClick = { limit = count },
                            label = { Text(pluralStringResource(R.plurals.invite_limit_people, count, count)) },
                            modifier = Modifier.testTag("limit_$count"),
                        )
                    }
                }
                if (requestNeeded) Text(stringResource(R.string.invite_limit_with_requests), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(NewInviteLink(title, expiresIn, limit.takeIf { !requestNeeded }, requestNeeded)) },
                modifier = Modifier.testTag("confirmCreate"),
            ) { Text(stringResource(R.string.invite_create_button)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
