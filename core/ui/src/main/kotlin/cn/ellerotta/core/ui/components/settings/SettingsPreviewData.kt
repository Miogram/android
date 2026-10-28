package cn.ellerotta.core.ui.components.settings

import cn.ellerotta.core.model.Appearance
import cn.ellerotta.core.model.ProfileInfo
import cn.ellerotta.core.model.ThemeMode
import cn.ellerotta.core.ui.components.chats.ChatsPreviewData

internal object SettingsPreviewData {
    val me: ProfileInfo get() = ChatsPreviewData.me
    val avatars: Map<String, ByteArray> get() = ChatsPreviewData.avatars

    val state = SettingsPageState(
        sessions = 3,
        appearance = Appearance(mode = ThemeMode.System, dynamicColor = false),
        autoSaveDownloads = true,
        version = "0.1.0 (1)",
        library = "ktproto 0.2.0 · layer 229",
    )

    val loading = SettingsPageState(appearance = Appearance(mode = ThemeMode.Dark, dynamicColor = true), version = "0.1.0 (1)", library = "ktproto 0.2.0")
}
