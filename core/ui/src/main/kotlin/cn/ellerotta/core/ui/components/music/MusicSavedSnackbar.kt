package cn.ellerotta.core.ui.components.music

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R
import kotlinx.coroutines.delay

@Composable
fun MusicSavedSnackbar(key: Any, onDismiss: () -> Unit, modifier: Modifier = Modifier, visibleMs: Long = 4_000) {
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(key) {
        delay(visibleMs)
        dismiss()
    }
    Snackbar(modifier = modifier.navigationBarsPadding().padding(16.dp).testTag("musicSaved")) { Text(stringResource(R.string.music_saved_to_profile)) }
}
