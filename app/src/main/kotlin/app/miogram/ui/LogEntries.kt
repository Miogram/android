package app.miogram.ui

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import app.miogram.feature.log.LogViewModel
import app.miogram.nav.Route
import cn.ellerotta.core.ui.components.log.LogScreen

internal fun EntryProviderScope<Route>.logEntries(back: () -> Unit) {
    entry<Route.Log> {
        val model = hiltViewModel<LogViewModel>()
        val lines by model.lines.collectAsStateWithLifecycle()
        LogScreen(lines, back)
    }
}
