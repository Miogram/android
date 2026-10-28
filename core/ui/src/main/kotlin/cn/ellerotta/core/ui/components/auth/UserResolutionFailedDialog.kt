package cn.ellerotta.core.ui.components.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import cn.ellerotta.core.ui.R

@Composable
fun UserResolutionFailedDialog(
    message: String,
    onRetry: () -> Unit,
    onCloseApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag("userResolutionFailed"),
        onDismissRequest = {},
        title = {
            Text(
                text = stringResource(R.string.auth_load_failed_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onRetry, modifier = Modifier.testTag("retryRestore")) {
                Text(stringResource(R.string.auth_try_again))
            }
        },
        dismissButton = {
            TextButton(onClick = onCloseApp, modifier = Modifier.testTag("closeApp")) {
                Text(stringResource(R.string.auth_close_app))
            }
        },
    )
}
