package cn.ellerotta.core.ui.components.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.UiError
import cn.ellerotta.core.ui.R

@Composable
fun Page(
    title: String,
    state: TopBarState,
    onBack: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floating: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalTopBarState provides state) {
                content(PaddingValues(top = topBarHeight(), bottom = bottom + 16.dp))
            }
            TopBar(title, state, onBack, onTitleClick = onTitleClick, actions = actions)
            Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)) { floating() }
        }
    }
}

@Composable
fun ErrorSnackbar(error: UiError, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Snackbar(
        modifier = modifier.navigationBarsPadding().imePadding().padding(16.dp).testTag("error"),
        action = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor)) { Text(stringResource(R.string.common_ok)) } },
    ) { Text(errorText(error)) }
}
