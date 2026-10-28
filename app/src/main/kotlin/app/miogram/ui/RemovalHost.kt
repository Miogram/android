package app.miogram.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miogram.feature.chats.RemovalsViewModel
import cn.ellerotta.core.ui.components.chats.RemovalSnackbar
import cn.ellerotta.core.ui.components.common.ErrorSnackbar

@Composable
internal fun RemovalHost(model: RemovalsViewModel) {
    val pending by model.pending.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        pending?.let { RemovalSnackbar(it, model::undo, Modifier.align(Alignment.BottomCenter)) }
        if (pending == null) error?.let { ErrorSnackbar(it, model::dismissError, Modifier.align(Alignment.BottomCenter)) }
    }
}
