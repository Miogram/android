package cn.ellerotta.core.ui.components.attach

import androidx.compose.runtime.Composable
import cn.ellerotta.core.model.PollDraft
import cn.ellerotta.core.ui.components.attach.AttachPreviewData as Data
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme

@Composable
private fun PollPreview(draft: PollDraft, busy: Boolean = false) {
    PreviewTheme { PollComposerScreen(draft, busy, onChange = {}, onSend = {}, onBack = {}) }
}

@MioPreviews
@Composable
internal fun PollEmptyPreview() = PollPreview(Data.emptyPoll)

@MioPreviews
@Composable
internal fun PollFilledPreview() = PollPreview(Data.poll)

@MioPreviews
@Composable
internal fun PollQuizPreview() = PollPreview(Data.quiz, busy = true)

@MioPreviews
@Composable
internal fun PollFullChannelPreview() = PollPreview(Data.full)

@MioPreviews
@Composable
internal fun LocationFoundPreview() = PreviewTheme {
    LocationShareScreen(Data.found, busy = false, onAllow = {}, onRetry = {}, onSendCurrent = {}, live = LiveActions({ _, _ -> }, {}), onBack = {})
}

@MioPreviews
@Composable
internal fun LocationNoPermissionPreview() = PreviewTheme {
    LocationShareScreen(LocationShareState(LocationStatus.NoPermission), busy = false, onAllow = {}, onRetry = {}, onSendCurrent = {}, live = LiveActions({ _, _ -> }, {}), onBack = {})
}

@MioPreviews
@Composable
internal fun ContactSharePreview() = PreviewTheme {
    ContactShareScreen(Data.contacts, query = "", loaded = true, avatars = emptyMap(), busy = false, onQuery = {}, onSend = {}, onBack = {})
}
