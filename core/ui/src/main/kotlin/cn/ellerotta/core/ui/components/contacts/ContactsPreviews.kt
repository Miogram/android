package cn.ellerotta.core.ui.components.contacts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme

@MioPreviews
@Composable
internal fun ContactsPagePreview() {
    var query by remember { mutableStateOf("") }
    PreviewTheme {
        Column {
            ContactsTopBar(query = query, onQueryChange = { query = it }, onAdd = {})
            ContactsContent(
                contacts = ContactsPreviewData.contacts,
                loading = false,
                query = query,
                globalResults = ContactsPreviewData.globalResults,
                avatars = ContactsPreviewData.avatars,
                now = ContactsPreviewData.NOW,
                listState = rememberLazyListState(),
                contentPadding = PaddingValues(),
                onOpen = {},
                onDelete = {},
                onNewGroup = {},
                onNewChannel = {},
            )
        }
    }
}

@MioPreviews
@Composable
internal fun ContactsSearchPreview() {
    PreviewTheme {
        Column {
            ContactsTopBar(query = "Ali", onQueryChange = {}, onAdd = {})
            ContactsContent(
                contacts = ContactsPreviewData.contacts,
                loading = false,
                query = "Ali",
                globalResults = ContactsPreviewData.globalResults,
                avatars = ContactsPreviewData.avatars,
                now = ContactsPreviewData.NOW,
                listState = rememberLazyListState(),
                contentPadding = PaddingValues(),
                onOpen = {},
                onDelete = {},
            )
        }
    }
}

@MioPreviews
@Composable
internal fun ContactsEmptyPreview() {
    PreviewTheme {
        Column {
            ContactsTopBar(query = "", onQueryChange = {}, onAdd = {})
            ContactsContent(
                contacts = emptyList(),
                loading = false,
                query = "",
                globalResults = emptyList(),
                avatars = emptyMap(),
                now = ContactsPreviewData.NOW,
                listState = rememberLazyListState(),
                contentPadding = PaddingValues(),
                onOpen = {},
                onDelete = {},
            )
        }
    }
}

@MioPreviews
@Composable
internal fun ContactsTopBarRevealingPreview() {
    PreviewTheme {
        ContactsTopBar(query = "", onQueryChange = {}, onAdd = {}, searchRevealProgress = 0.5f)
    }
}

@MioPreviews
@Composable
internal fun ContactComponentPreview() {
    PreviewTheme {
        Column {
            ContactsPreviewData.contacts.take(4).forEachIndexed { index, contact ->
                ContactComponent(contact, ContactsPreviewData.avatars, ContactsPreviewData.NOW, onClick = {}, onLongClick = {}, isSelected = index == 3)
            }
        }
    }
}

@MioPreviews
@Composable
internal fun AddContactDialogPreview() {
    PreviewTheme {
        AddContactDialog(notFound = false, onAdd = { _, _, _ -> }, onEdit = {}, onDismiss = {})
    }
}

@MioPreviews
@Composable
internal fun AddContactDialogNotFoundPreview() {
    PreviewTheme {
        AddContactDialog(notFound = true, onAdd = { _, _, _ -> }, onEdit = {}, onDismiss = {})
    }
}

@MioPreviews
@Composable
internal fun AddProfileContactDialogPreview() {
    PreviewTheme {
        AddProfileContactDialog(name = ContactsPreviewData.online.name, onAdd = { _, _, _ -> }, onDismiss = {})
    }
}
