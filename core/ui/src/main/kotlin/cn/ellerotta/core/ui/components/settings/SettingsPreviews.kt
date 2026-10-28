package cn.ellerotta.core.ui.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme

@MioPreviews
@Composable
internal fun SettingsPagePreview() {
    PreviewTheme {
        Column {
            SettingsTopBar()
            SettingsContent(
                state = SettingsPreviewData.state,
                me = SettingsPreviewData.me,
                avatars = SettingsPreviewData.avatars,
                actions = SettingsActions(),
                listState = rememberLazyListState(),
                contentPadding = PaddingValues(),
            )
        }
    }
}

@MioPreviews
@Composable
internal fun SettingsPageLoadingPreview() {
    PreviewTheme {
        Column {
            SettingsTopBar()
            SettingsContent(
                state = SettingsPreviewData.loading,
                me = null,
                avatars = emptyMap(),
                actions = SettingsActions(),
                listState = rememberLazyListState(),
                contentPadding = PaddingValues(),
            )
        }
    }
}
