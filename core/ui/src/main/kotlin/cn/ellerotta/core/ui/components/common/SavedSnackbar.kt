package cn.ellerotta.core.ui.components.common

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun SavedSnackbar(key: Any, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, visibleMs: Long = 4_000) {
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(key) {
        delay(visibleMs)
        dismiss()
    }
    Snackbar(
        modifier = modifier.navigationBarsPadding().imePadding().padding(16.dp).testTag("savedDownload"),
        action = { TextButton(onClick = onOpen, colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor), modifier = Modifier.testTag("openDownload")) { Text(stringResource(R.string.downloads_open)) } },
    ) { Text(stringResource(R.string.downloads_saved)) }
}

@Composable
fun PremiumSpeedSnackbar(key: Any, upload: Boolean, speedup: Int, onDismiss: () -> Unit, modifier: Modifier = Modifier, visibleMs: Long = 6_000) {
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(key) {
        delay(visibleMs)
        dismiss()
    }
    Snackbar(
        modifier = modifier.navigationBarsPadding().imePadding().padding(16.dp).testTag("premiumSpeed"),
        action = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor)) { Text(stringResource(R.string.premium_speed_ok)) } },
    ) { Text(stringResource(if (upload) R.string.premium_speed_upload else R.string.premium_speed_download, speedup)) }
}
