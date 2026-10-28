package app.miogram.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import app.miogram.feature.folders.FolderEditViewModel
import app.miogram.feature.folders.FoldersViewModel
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.ui.components.folders.FolderEditScreen
import cn.ellerotta.core.ui.components.folders.FoldersScreen

internal fun EntryProviderScope<Route>.foldersEntries(navigator: Navigator, back: () -> Unit) {
    entry<Route.Folders> {
        val model = hiltViewModel<FoldersViewModel>()
        val state by model.state.collectAsStateWithLifecycle()
        Work(model) {
            FoldersScreen(
                state = state,
                onBack = back,
                onOpen = { navigator.go(Route.FolderEdit(it.id)) },
                onCreate = { navigator.go(Route.FolderEdit(ChatFolder.NEW)) },
                onReorder = model::reorder,
                onAddSuggested = model::add,
            )
        }
    }
    entry<Route.FolderEdit> { route ->
        val model = hiltViewModel<FolderEditViewModel, FolderEditViewModel.Factory>(key = "folder_" + route.folderId) { it.create(route.folderId) }
        val state by model.state.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        LaunchedEffect(model) { model.done.collect { back() } }
        Work(model) { busy ->
            FolderEditScreen(
                state = state,
                avatars = avatars,
                busy = busy,
                onBack = back,
                onChange = model::change,
                onSave = model::save,
                onDelete = model::delete,
                onLoadMoreChats = model::loadMoreChats,
            )
        }
    }
}
