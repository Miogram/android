package cn.ellerotta.core.ui.components.folders

import androidx.compose.runtime.Composable
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme

@MioPreviews
@Composable
internal fun FoldersPreview() {
    PreviewTheme { FoldersScreen(FoldersPreviewData.filled, onBack = {}, onOpen = {}, onCreate = {}, onReorder = {}, onAddSuggested = {}) }
}

@MioPreviews
@Composable
internal fun FoldersEmptyPreview() {
    PreviewTheme { FoldersScreen(FoldersPreviewData.empty, onBack = {}, onOpen = {}, onCreate = {}, onReorder = {}, onAddSuggested = {}) }
}

@MioPreviews
@Composable
internal fun FoldersLoadingPreview() {
    PreviewTheme { FoldersScreen(FoldersPreviewData.loadingSuggestions, onBack = {}, onOpen = {}, onCreate = {}, onReorder = {}, onAddSuggested = {}) }
}

@MioPreviews
@Composable
internal fun FoldersLimitPreview() {
    PreviewTheme { FoldersScreen(FoldersPreviewData.atLimit, onBack = {}, onOpen = {}, onCreate = {}, onReorder = {}, onAddSuggested = {}) }
}

@MioPreviews
@Composable
internal fun FolderEditNewPreview() {
    PreviewTheme { FolderEditScreen(FoldersPreviewData.newFolder, FoldersPreviewData.avatars, busy = false, onBack = {}, onChange = {}, onSave = {}, onDelete = {}) }
}

@MioPreviews
@Composable
internal fun FolderEditReadyPreview() {
    PreviewTheme { FolderEditScreen(FoldersPreviewData.newReady, FoldersPreviewData.avatars, busy = false, onBack = {}, onChange = {}, onSave = {}, onDelete = {}) }
}

@MioPreviews
@Composable
internal fun FolderEditPreview() {
    PreviewTheme { FolderEditScreen(FoldersPreviewData.editing, FoldersPreviewData.avatars, busy = false, onBack = {}, onChange = {}, onSave = {}, onDelete = {}) }
}

@MioPreviews
@Composable
internal fun FolderEditSavingPreview() {
    PreviewTheme { FolderEditScreen(FoldersPreviewData.editing, FoldersPreviewData.avatars, busy = true, onBack = {}, onChange = {}, onSave = {}, onDelete = {}) }
}

@MioPreviews
@Composable
internal fun FolderEditSharedPreview() {
    PreviewTheme { FolderEditScreen(FoldersPreviewData.editingShared, FoldersPreviewData.avatars, busy = false, onBack = {}, onChange = {}, onSave = {}, onDelete = {}) }
}

@MioPreviews
@Composable
internal fun FolderEditLimitPreview() {
    PreviewTheme { FolderEditScreen(FoldersPreviewData.newAtLimit, FoldersPreviewData.avatars, busy = false, onBack = {}, onChange = {}, onSave = {}, onDelete = {}) }
}

@MioPreviews
@Composable
internal fun FolderEditLoadingPreview() {
    PreviewTheme { FolderEditScreen(FoldersPreviewData.loading, FoldersPreviewData.avatars, busy = false, onBack = {}, onChange = {}, onSave = {}, onDelete = {}) }
}

@MioPreviews
@Composable
internal fun FolderChatPickerPreview() {
    PreviewTheme {
        FolderChatPicker("Add chats", FoldersPreviewData.chats, picked = listOf("c202", "u101"), avatars = FoldersPreviewData.avatars, limit = 100, onBack = {}, onDone = {})
    }
}

@MioPreviews
@Composable
internal fun FolderChatPickerFullPreview() {
    PreviewTheme {
        FolderChatPicker("Add chats", FoldersPreviewData.chats, picked = listOf("c202", "u101"), avatars = FoldersPreviewData.avatars, limit = 2, onBack = {}, onDone = {})
    }
}
