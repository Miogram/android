package app.miogram.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miogram.feature.media.MusicViewModel
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.ErrorSnackbar
import cn.ellerotta.core.ui.components.music.MiniPlayer
import cn.ellerotta.core.ui.components.music.MusicActions
import cn.ellerotta.core.ui.components.music.MusicPlayerOverlay
import cn.ellerotta.core.ui.components.music.MusicSavedSnackbar

@Composable
internal fun musicModel(): MusicViewModel = hiltViewModel(LocalActivity.current as ComponentActivity)

@Composable
private fun rememberMusicActions(model: MusicViewModel): MusicActions = remember(model) {
    MusicActions(
        onToggle = model::toggle,
        onNext = model::next,
        onPrevious = model::previous,
        onSeek = model::seekTo,
        onShuffle = model::toggleShuffle,
        onRepeat = model::cycleRepeat,
        onPlayAt = model::playAt,
        onClose = model::close,
        onSpeed = model::cycleSpeed,
        onOrder = model::setOrder,
        onMove = model::move,
        onPlayNext = model::playNext,
        onRemove = model::remove,
        onSleep = model::sleep,
        onSleepAtEnd = model::sleepAtEnd,
        onGoToMessage = model::goToMessage,
        onSaveToProfile = model::saveToProfile,
    )
}

@Composable
internal fun MusicBar(keepLast: Boolean = false) {
    val model = musicModel()
    val music by model.music.collectAsStateWithLifecycle()
    val actions = rememberMusicActions(model)
    val last = remember { LastMusic() }
    music?.let { last.state = it }
    (music ?: last.state.takeIf { keepLast })?.let { MiniPlayer(it, onOpen = model::openSheet, actions = actions) }
}

private class LastMusic {
    var state: MusicState? = null
}

@Composable
internal fun MusicHost(model: MusicViewModel, navigator: Navigator) {
    val music by model.music.collectAsStateWithLifecycle()
    val open by model.sheetOpen.collectAsStateWithLifecycle()
    val saved by model.savedToProfile.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val actions = rememberMusicActions(model)
    val savedTitle = stringResource(R.string.saved_messages)
    LaunchedEffect(model) {
        model.openMessage.collect { message ->
            if ((navigator.current as? Route.Chat)?.dialogKey == message.dialogKey) navigator.back()
            navigator.go(Route.Chat(message.dialogKey, if (message.saved) savedTitle else message.title, messageId = message.messageId))
        }
    }
    Box(Modifier.fillMaxSize()) {
        MusicPlayerOverlay(music, open, actions, onDismiss = model::closeSheet)
        saved?.let { MusicSavedSnackbar(it, model::dismissSaved, Modifier.align(Alignment.BottomCenter)) }
        error?.let { ErrorSnackbar(it, model::dismissError, Modifier.align(Alignment.BottomCenter)) }
    }
}
