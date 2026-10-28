package cn.ellerotta.core.ui.components.settings

import androidx.compose.runtime.Composable
import cn.ellerotta.core.model.NotificationSettingsState
import cn.ellerotta.core.model.NotifyException
import cn.ellerotta.core.model.NotifyScope
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.ScopeNotifySettings
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme

private object NotificationPreviewData {
    const val NOW = 1_793_102_400L

    val state = NotificationSettingsState(
        scopes = mapOf(
            NotifyScope.Users to ScopeNotifySettings(),
            NotifyScope.Chats to ScopeNotifySettings(showPreviews = false),
            NotifyScope.Broadcasts to ScopeNotifySettings(Int.MAX_VALUE),
        ),
        exceptions = mapOf(
            NotifyScope.Users to listOf(
                NotifyException("u101", "Anna Petrova", PeerKind.User, muteUntil = Int.MAX_VALUE),
                NotifyException("u102", "Marco Rossi", PeerKind.User, muteUntil = (NOW + 3 * 3_600).toInt()),
                NotifyException("u103", "Lena Hoffmann", PeerKind.User, showPreviews = false),
                NotifyException("u104", "Build bot", PeerKind.Bot, muteUntil = Int.MAX_VALUE),
            ),
            NotifyScope.Broadcasts to listOf(
                NotifyException("ch201", "Kotlin weekly", PeerKind.Channel, muteUntil = 0),
            ),
        ),
    )
}

@MioPreviews
@Composable
internal fun NotificationSettingsPreview() {
    PreviewTheme { NotificationSettingsScreen(NotificationPreviewData.state, busy = false, NotificationPreviewData.NOW, NotificationActions()) }
}

@MioPreviews
@Composable
internal fun NotificationSettingsLoadingPreview() {
    PreviewTheme { NotificationSettingsScreen(NotificationSettingsState(), busy = true, NotificationPreviewData.NOW, NotificationActions()) }
}

@MioPreviews
@Composable
internal fun NotifyExceptionsPreview() {
    PreviewTheme { NotifyExceptionsScreen(NotifyScope.Users, NotificationPreviewData.state, emptyMap(), busy = false, NotificationPreviewData.NOW, NotificationActions()) }
}
