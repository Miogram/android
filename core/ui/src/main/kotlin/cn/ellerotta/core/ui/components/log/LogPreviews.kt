package cn.ellerotta.core.ui.components.log

import androidx.compose.runtime.Composable
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme

@MioPreviews
@Composable
internal fun LogScreenPreview() {
    PreviewTheme {
        LogScreen(lines = LogPreviewData.lines, onBack = {})
    }
}

@MioPreviews
@Composable
internal fun LogScreenEmptyPreview() {
    PreviewTheme {
        LogScreen(lines = emptyList(), onBack = {})
    }
}
