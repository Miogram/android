package cn.ellerotta.core.ui.components.safety

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import cn.ellerotta.core.model.BlockedList
import cn.ellerotta.core.model.PeerBar
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme

@MioPreviews
@Composable
internal fun BlockedUsersScreenPreview() {
    PreviewTheme {
        BlockedUsersScreen(SafetyPreviewData.blocked, SafetyPreviewData.avatars, busy = false, onBack = {}, onAdd = {}, onUnblock = {}, onOpenProfile = {}, onMore = {})
    }
}

@MioPreviews
@Composable
internal fun BlockedUsersEmptyPreview() {
    PreviewTheme {
        BlockedUsersScreen(BlockedList(loaded = true), emptyMap(), busy = false, onBack = {}, onAdd = {}, onUnblock = {}, onOpenProfile = {}, onMore = {})
    }
}

@MioPreviews
@Composable
internal fun ReportMenuPreview() {
    PreviewTheme { ReportContent(SafetyPreviewData.menu, ReportActions({}, {}, {})) }
}

@MioPreviews
@Composable
internal fun ReportPeerReasonsPreview() {
    PreviewTheme { ReportContent(SafetyPreviewData.peerReasons, ReportActions({}, {}, {})) }
}

@MioPreviews
@Composable
internal fun ReportCommentPreview() {
    PreviewTheme { ReportContent(SafetyPreviewData.comment, ReportActions({}, {}, {})) }
}

@MioPreviews
@Composable
internal fun ReportOptionalCommentPreview() {
    PreviewTheme { ReportContent(SafetyPreviewData.optionalComment, ReportActions({}, {}, {})) }
}

@MioPreviews
@Composable
internal fun ReportLoadingPreview() {
    PreviewTheme { ReportContent(SafetyPreviewData.loading, ReportActions({}, {}, {})) }
}

@MioPreviews
@Composable
internal fun ReportDonePreview() {
    PreviewTheme { ReportContent(SafetyPreviewData.done, ReportActions({}, {}, {})) }
}

@MioPreviews
@Composable
internal fun PeerActionBarPreview() {
    PreviewTheme {
        Column {
            PeerActionBar(SafetyPreviewData.stranger, PeerBarActions({}, {}, {}, {}))
            PeerActionBar(PeerBar(reportSpam = true), PeerBarActions({}, {}, {}, {}))
        }
    }
}

@MioPreviews
@Composable
internal fun BlockedBannerPreview() {
    PreviewTheme { BlockedBanner(onUnblock = {}) }
}
