package cn.ellerotta.core.ui.components.groups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.appbar.SearchBar
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.components.profile.presenceText
import cn.ellerotta.core.ui.components.profile.rememberProfileNow
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut

data class MemberPickerState(
    val contacts: List<ContactItem> = emptyList(),
    val selected: List<ContactItem> = emptyList(),
    val query: String = "",
    val loaded: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberPickerScreen(
    title: String,
    state: MemberPickerState,
    avatars: Map<String, ByteArray>,
    confirmLabel: String,
    busy: Boolean,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onToggle: (ContactItem) -> Unit,
    onConfirm: () -> Unit,
    skipLabel: String? = null,
    onSkip: () -> Unit = {},
    now: Long = rememberProfileNow(),
) {
    val list = rememberLazyListState()
    val picked = state.selected.map { it.key }.toSet()
    Page(
        title,
        rememberTopBarState(list),
        onBack = onBack,
        floating = {
            AnimatedVisibility(state.selected.isNotEmpty(), enter = MaterialTheme.motionScheme.expandFadeIn(), exit = MaterialTheme.motionScheme.shrinkFadeOut()) {
                MediumExtendedFloatingActionButton(
                    onClick = onConfirm,
                    icon = { Icon(painterResource(R.drawable.ic_check), null) },
                    text = { Text(confirmLabel) },
                    modifier = Modifier.testTag("pickerConfirm"),
                    expanded = true,
                )
            }
            AnimatedVisibility(state.selected.isEmpty() && skipLabel != null, enter = MaterialTheme.motionScheme.expandFadeIn(), exit = MaterialTheme.motionScheme.shrinkFadeOut()) {
                MediumExtendedFloatingActionButton(
                    onClick = onSkip,
                    icon = { Icon(painterResource(R.drawable.ic_forward), null) },
                    text = { Text(skipLabel.orEmpty()) },
                    modifier = Modifier.testTag("pickerSkip"),
                    expanded = true,
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("pickerList"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "busy") { if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            item(key = "search") {
                SearchBar(
                    value = state.query,
                    onValueChange = onQuery,
                    placeholder = stringResource(R.string.contacts_search),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fieldTag = "pickerSearch",
                )
            }
            if (state.selected.isNotEmpty()) {
                item(key = "chips") {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("pickerChips"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.selected.forEach { person ->
                            InputChip(
                                selected = true,
                                onClick = { onToggle(person) },
                                label = { Text(person.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp)) },
                                avatar = { Avatar(person.name, person.avatarKey?.let { avatars[it] }, size = 24, seed = person.userId) },
                                trailingIcon = { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.picker_remove, person.name), Modifier.size(18.dp)) },
                                modifier = Modifier.testTag("chip_${person.key}"),
                            )
                        }
                    }
                }
            }
            if (state.loaded && state.contacts.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(if (state.query.isBlank()) R.string.contacts_empty else R.string.contacts_nothing_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp).testTag("pickerEmpty"),
                    )
                }
            }
            items(state.contacts, key = { it.key }) { person ->
                PickRow(person, person.key in picked, avatars, now, onToggle = { onToggle(person) }, modifier = Modifier.animateItem())
            }
            item(key = "end") { Spacer(Modifier.heightIn(min = 72.dp)) }
        }
    }
}

@Composable
private fun PickRow(person: ContactItem, picked: Boolean, avatars: Map<String, ByteArray>, now: Long, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = picked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("pick_${person.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = picked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Avatar(person.name, person.avatarKey?.let { avatars[it] }, seed = person.userId)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val seen = presenceText(person.presence, now)
            if (seen.isNotEmpty()) Text(seen, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
