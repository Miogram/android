package cn.ellerotta.core.ui.components.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.Appearance
import cn.ellerotta.core.model.AttachMenuBot
import cn.ellerotta.core.model.ProfileInfo
import cn.ellerotta.core.model.ThemeMode
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.CheckedSwitch
import cn.ellerotta.core.ui.components.common.ConnectedChoice
import cn.ellerotta.core.ui.components.profile.InfoRow
import cn.ellerotta.core.ui.components.profile.ProfileCard
import cn.ellerotta.core.ui.components.profile.copyToClipboard
import cn.ellerotta.core.ui.components.profile.formatPhone

data class SettingsPageState(
    val sessions: Int? = null,
    val appearance: Appearance = Appearance(),
    val autoSaveDownloads: Boolean = false,
    val version: String = "",
    val library: String = "",
    val apps: List<AttachMenuBot> = emptyList(),
)

class SettingsActions(
    val onOpenProfile: () -> Unit = {},
    val onEditProfile: () -> Unit = {},
    val onOpenNotifications: () -> Unit = {},
    val onOpenDataStorage: () -> Unit = {},
    val onOpenPrivacy: () -> Unit = {},
    val onOpenSessions: () -> Unit = {},
    val onOpenFolders: () -> Unit = {},
    val onAppearance: (Appearance) -> Unit = {},
    val onOpenWallpapers: () -> Unit = {},
    val onAutoSaveDownloads: (Boolean) -> Unit = {},
    val onOpenLanguage: () -> Unit = {},
    val onOpenLog: () -> Unit = {},
    val onLogOut: () -> Unit = {},
    val onShown: () -> Unit = {},
    val onOpenApp: (AttachMenuBot) -> Unit = {},
    val onRemoveApp: (AttachMenuBot) -> Unit = {},
)

@Composable
fun SettingsTopBar(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.profile_settings),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

@Composable
fun SettingsContent(
    state: SettingsPageState,
    me: ProfileInfo?,
    avatars: Map<String, ByteArray>,
    actions: SettingsActions,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var confirmLogOut by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<AttachMenuBot?>(null) }
    val appearance = state.appearance

    LazyColumn(modifier = modifier.fillMaxSize().testTag("settings"), state = listState, contentPadding = contentPadding) {
        item(key = "me") {
            ProfileCard(me, avatars, actions.onOpenProfile, actions.onEditProfile)
        }

        item(key = "account") {
            SettingsCard(TopShape, gap = GroupGap) {
                SettingsRow(
                    R.drawable.ic_lock,
                    stringResource(R.string.profile_privacy),
                    stringResource(R.string.settings_privacy_hint),
                    actions.onOpenPrivacy,
                    "openPrivacySettings")
            }
            SettingsCard(MiddleShape) {
                SettingsRow(
                    R.drawable.ic_folder,
                    stringResource(R.string.folders_title),
                    stringResource(R.string.settings_folders_hint),
                    actions.onOpenFolders,
                    "openFolders")
            }
            SettingsCard(BottomShape) {
                SettingsRow(R.drawable.ic_devices,
                    stringResource(R.string.profile_devices),
                    state.sessions?.let { pluralStringResource(R.plurals.profile_sessions, it, it) },
                    actions.onOpenSessions,
                    "openSessions")
            }
        }

        item(key = "application") {
            SettingsCard(TopShape, gap = GroupGap) {
                SettingsRow(
                    R.drawable.ic_notifications,
                    stringResource(R.string.settings_notifications),
                    stringResource(R.string.settings_notifications_hint),
                    actions.onOpenNotifications,
                    "openNotifications"
                )
            }
            SettingsCard(MiddleShape) {
                SettingsRow(
                    R.drawable.ic_database,
                    stringResource(R.string.settings_data),
                    stringResource(R.string.settings_data_hint),
                    actions.onOpenDataStorage,
                    "openDataStorage"
                )
            }
            SettingsCard(MiddleShape) {
                InfoRow(
                    icon = R.drawable.ic_dark_mode,
                    value = stringResource(R.string.profile_theme)
                )
                ConnectedChoice(
                    options = ThemeMode.entries,
                    chosen = appearance.mode,
                    onChoose = { actions.onAppearance(appearance.copy(mode = it)) },
                    label = { mode ->
                        stringResource(
                            when (mode) {
                                ThemeMode.System -> R.string.profile_theme_system
                                ThemeMode.Light -> R.string.profile_theme_light
                                ThemeMode.Dark -> R.string.profile_theme_dark
                            },
                        )
                    },
                    tag = { "theme_${it.name}" },
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 72.dp, end = 16.dp, bottom = 12.dp),
                )
            }
            SettingsCard(MiddleShape) {
                SwitchRow(
                    R.drawable.ic_palette,
                    stringResource(R.string.profile_dynamic_color),
                    stringResource(R.string.profile_dynamic_color_hint),
                    appearance.dynamicColor,
                    "dynamicColor"
                ) {
                    actions.onAppearance(appearance.copy(dynamicColor = it))
                }
            }
            SettingsCard(MiddleShape) {
                SettingsRow(
                    R.drawable.ic_image,
                    stringResource(R.string.profile_wallpaper),
                    stringResource(R.string.profile_wallpaper_hint),
                    actions.onOpenWallpapers,
                    "openWallpapers"
                )
            }
            SettingsCard(MiddleShape) {
                SwitchRow(
                    R.drawable.ic_download,
                    stringResource(R.string.profile_auto_save_downloads),
                    stringResource(R.string.profile_auto_save_downloads_hint),
                    state.autoSaveDownloads,
                    "autoSaveDownloads",
                    actions.onAutoSaveDownloads
                )
            }
            SettingsCard(BottomShape) {
                SettingsRow(
                    R.drawable.ic_language,
                    stringResource(R.string.profile_language),
                    locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) },
                    actions.onOpenLanguage,
                    "openLanguage"
                )
            }
        }
        items(state.apps, key = { "app_${it.key}" }) { app ->
            SettingsCard(MiddleShape) {
                SettingsRow(
                    R.drawable.ic_public, app.shortName, null, { actions.onOpenApp(app) }, "sideApp_${app.key}",
                    onLongClick = if (app.inactive) null else ({ removing = app }),
                )
            }
        }
        item(key = "experimental") {
            SettingsCard(TopShape, gap = GroupGap) {
                SettingsRow(R.drawable.ic_article,
                    stringResource(R.string.chats_log),
                    null,
                    actions.onOpenLog,
                    "openLog")
            }
            SettingsCard(MiddleShape) {
                InfoLine(state.version,
                    stringResource(R.string.profile_version),
                    onCopy = {
                        copyToClipboard(context, "version", state.version)
                    }
                )
            }
            SettingsCard(BottomShape) {
                InfoLine(state.library,
                    stringResource(R.string.profile_library),
                    Modifier.testTag("library"),
                    onCopy = {
                        copyToClipboard(context, "library", state.library)
                    }
                )
            }
        }

        item(key = "logout") {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FilledTonalButton(
                    onClick = { confirmLogOut = true },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("logOut"),
                ) {
                    Icon(painterResource(R.drawable.ic_logout), null)
                    Text(stringResource(R.string.profile_log_out), Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (confirmLogOut) {
        AlertDialog(
            onDismissRequest = { confirmLogOut = false },
            icon = { Icon(painterResource(R.drawable.ic_logout), null) },
            title = { Text(stringResource(R.string.profile_log_out_title)) },
            text = { Text(stringResource(R.string.profile_log_out_text)) },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                    confirmLogOut = false
                    actions.onLogOut()
                }, modifier = Modifier.testTag("confirmLogOut")) { Text(stringResource(R.string.profile_log_out), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmLogOut = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    removing?.let { app ->
        AlertDialog(
            onDismissRequest = { removing = null },
            text = { Text(stringResource(R.string.settings_app_remove_title, app.shortName)) },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                    removing = null
                    actions.onRemoveApp(app)
                }, modifier = Modifier.testTag("confirmRemoveApp")) { Text(stringResource(R.string.settings_app_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { removing = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun ProfileCard(me: ProfileInfo?, avatars: Map<String, ByteArray>, onOpen: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    SettingsCard(RoundedShape, first = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = stringResource(R.string.settings_my_profile), onClick = onOpen)
                    .testTag("openOwnProfile")
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(me?.name.orEmpty(), me?.avatarKey?.let { avatars[it] }, seed = me?.id ?: 0)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        me?.name.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    me?.let { info ->
                        val lines = listOfNotNull(info.phone?.let { formatPhone(context, it) }, info.username?.let { "@$it" })
                        if (lines.isNotEmpty()) {
                            Text(
                                lines.joinToString(", "),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.padding(end = 8.dp).testTag("editOwnProfile"), shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.profile_edit))
            }
        }
    }
}

@Composable
private fun SettingsCard(shape: Shape, first: Boolean = false, gap: Dp = StackGap, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = if (first) 0.dp else gap)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(@DrawableRes icon: Int, title: String, subtitle: String?, onClick: () -> Unit, tag: String, onLongClick: (() -> Unit)? = null) {
    InfoRow(
        icon = icon,
        value = title,
        label = subtitle,
        modifier = Modifier.testTag(tag),
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

@Composable
private fun SwitchRow(@DrawableRes icon: Int, title: String, subtitle: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    InfoRow(
        icon = icon,
        value = title,
        label = subtitle,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange).testTag(tag),
        trailing = { CheckedSwitch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun InfoLine(value: String, label: String, modifier: Modifier = Modifier, onCopy: (() -> Unit)? = null) {
    InfoRow(
        icon = null,
        value = value,
        label = label,
        modifier = if (onCopy != null) modifier.combinedClickable(onClickLabel = stringResource(R.string.chat_copy), onClick = onCopy, onLongClick = onCopy) else modifier,
    )
}

private val TopShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val MiddleShape = RoundedCornerShape(4.dp)
private val BottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
private val RoundedShape = RoundedCornerShape(20.dp)

private val StackGap = 4.dp
private val GroupGap = 16.dp
