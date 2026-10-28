package cn.ellerotta.core.ui.components.links

import androidx.compose.runtime.Composable
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewSheetTheme

@MioPreviews
@Composable
internal fun InviteSheetPreview() {
    PreviewSheetTheme {
        InviteContent(invite = LinksPreviewData.groupInvite, onJoin = {})
    }
}

@MioPreviews
@Composable
internal fun InviteSheetRequestPreview() {
    PreviewSheetTheme {
        InviteContent(invite = LinksPreviewData.channelRequest, onJoin = {})
    }
}

@MioPreviews
@Composable
internal fun UnsupportedLinkSheetPreview() {
    PreviewSheetTheme {
        UnsupportedLinkContent(url = LinksPreviewData.UNSUPPORTED_URL, onCopy = {})
    }
}

@MioPreviews
@Composable
internal fun LoginCodeSheetPreview() {
    PreviewSheetTheme {
        LoginCodeContent(code = LinksPreviewData.LOGIN_CODE, onCopy = {})
    }
}
