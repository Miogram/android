package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme

@Composable
private fun SheetFrame(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box(Modifier.padding(top = 24.dp, bottom = 16.dp)) { content() }
    }
}

@MioPreviews
@Composable
internal fun AttachmentSheetPreview() {
    PreviewTheme { SheetFrame { AttachmentContent(SendingPreviewData.draft, SendingPreviewData.actions, whenOnline = true) } }
}

@MioPreviews
@Composable
internal fun AttachmentSheetFilesPreview() {
    PreviewTheme { SheetFrame { AttachmentContent(SendingPreviewData.draft.copy(asFile = true), SendingPreviewData.actions, whenOnline = false) } }
}

@MioPreviews
@Composable
internal fun SendMenuPreview() {
    PreviewTheme {
        Box(Modifier.padding(16.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 3.dp) {
                Column(Modifier.padding(vertical = 8.dp).width(IntrinsicSize.Max)) {
                    SendMenuItems(whenOnline = true, onDismiss = {}, onSchedule = {}, onSend = {})
                }
            }
        }
    }
}

@MioPreviews
@Composable
internal fun ScheduleDialogPreview() {
    PreviewTheme { ScheduleDialog(onConfirm = {}, onDismiss = {}, initial = SendingPreviewData.FIRST.toLong()) }
}

@MioPreviews
@Composable
internal fun ScheduledScreenPreview() {
    PreviewTheme { ScheduledScreen(stringResource(R.string.chat_scheduled_title), SendingPreviewData.scheduled, loaded = true, SendingPreviewData.scheduledActions, onBack = {}) }
}

@MioPreviews
@Composable
internal fun ScheduledScreenEmptyPreview() {
    PreviewTheme { ScheduledScreen(stringResource(R.string.chat_scheduled_title), emptyList(), loaded = true, SendingPreviewData.scheduledActions, onBack = {}) }
}

@MioPreviews
@Composable
internal fun ScheduledBarPreview() {
    PreviewTheme { ScheduledBar(3) {} }
}
