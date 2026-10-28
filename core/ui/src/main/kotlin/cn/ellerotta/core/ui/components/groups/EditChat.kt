package cn.ellerotta.core.ui.components.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.CHAT_ABOUT_MAX
import cn.ellerotta.core.model.CHAT_TITLE_MAX
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState

@Composable
fun EditChatScreen(
    channel: Boolean,
    name: String,
    about: String,
    photo: ByteArray?,
    changed: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onAbout: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onSave: () -> Unit,
) {
    val list = rememberLazyListState()
    val title = stringResource(if (channel) R.string.edit_channel_title else R.string.edit_group_title)
    Page(title, rememberTopBarState(list), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("editChat"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "busy") { if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            item(key = "photo") { ChatPhotoButton(photo, name, onPickPhoto, onRemovePhoto.takeIf { photo != null }, Modifier.padding(top = 8.dp)) }
            item(key = "fields") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { onName(it.take(CHAT_TITLE_MAX)) },
                        label = { Text(stringResource(if (channel) R.string.channel_name_hint else R.string.group_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("editName"),
                    )
                    OutlinedTextField(
                        value = about,
                        onValueChange = { onAbout(it.take(CHAT_ABOUT_MAX)) },
                        label = { Text(stringResource(R.string.profile_description)) },
                        supportingText = { Text(stringResource(R.string.chat_about_counter, about.length, CHAT_ABOUT_MAX)) },
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().testTag("editAbout"),
                    )
                }
            }
            item(key = "save") {
                Button(
                    onClick = onSave,
                    enabled = changed && name.isNotBlank() && !busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp).testTag("saveChat"),
                ) { Text(stringResource(R.string.edit_chat_save)) }
            }
        }
    }
}
