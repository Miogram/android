package cn.ellerotta.core.ui.components.settings

import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.AutoDownloadKind
import cn.ellerotta.core.model.AutoDownloadRule
import cn.ellerotta.core.model.AutoDownloadSettings
import cn.ellerotta.core.model.DataStorageState
import cn.ellerotta.core.model.NetworkType
import cn.ellerotta.core.model.StorageCategory
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.ConfirmDialog
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.components.profile.InfoCard
import cn.ellerotta.core.ui.components.profile.InfoDivider
import cn.ellerotta.core.ui.components.profile.InfoRow
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut
import kotlin.math.roundToInt

class DataStorageActions(
    val onBack: () -> Unit = {},
    val onAutoDownload: (AutoDownloadSettings) -> Unit = {},
    val onClear: (StorageCategory?) -> Unit = {},
)

@Composable
fun DataStorageScreen(state: DataStorageState, busy: Boolean, actions: DataStorageActions) {
    var clearing by rememberSaveable { mutableStateOf<StorageCategory?>(null) }
    var clearingAll by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<NetworkType?>(null) }
    val list = rememberLazyListState()
    val title = stringResource(R.string.data_title)
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    Page(title, rememberTopBarState(list), onBack = actions.onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("dataStorage"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "busy") {
                AnimatedVisibility(busy, enter = MaterialTheme.motionScheme.expandFadeIn(), exit = MaterialTheme.motionScheme.shrinkFadeOut()) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            item(key = "usage") {
                InfoCard(Modifier.padding(top = 8.dp), title = stringResource(R.string.data_storage)) {
                    val usage = state.usage
                    if (usage == null) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(Modifier.size(48.dp).testTag("storageLoading"))
                        }
                    } else {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(size(usage.total), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("storageTotal"))
                            usage.free?.let { free ->
                                Text(stringResource(R.string.data_storage_free, size(free)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(12.dp))
                            UsageBar(usage.bytes, usage.total)
                        }
                        StorageCategory.entries.forEach { category ->
                            InfoDivider()
                            val bytes = usage.bytes[category] ?: 0
                            InfoRow(
                                category.icon,
                                stringResource(category.title),
                                size(bytes),
                                tint = category.color(),
                                trailing = {
                                    if (category in state.clearing) {
                                        LoadingIndicator(Modifier.size(40.dp))
                                    } else {
                                        TextButton(
                                            onClick = { clearing = category },
                                            enabled = bytes > 0,
                                            shapes = ButtonDefaults.shapes(),
                                            modifier = Modifier.heightIn(min = 48.dp).testTag("clear_${category.name}"),
                                        ) { Text(stringResource(R.string.data_clear)) }
                                    }
                                },
                                modifier = Modifier.testTag("storage_${category.name}"),
                            )
                        }
                    }
                }
            }
            item(key = "clearAll") {
                InfoCard(Modifier.padding(top = 16.dp)) {
                    InfoRow(
                        R.drawable.ic_delete,
                        stringResource(R.string.data_clear_all),
                        stringResource(R.string.data_clear_all_hint),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        container = MaterialTheme.colorScheme.errorContainer,
                        valueColor = MaterialTheme.colorScheme.error,
                        onClick = { if ((state.usage?.total ?: 0) > 0) clearingAll = true },
                        modifier = Modifier.testTag("clearAll"),
                    )
                }
            }
            item(key = "autoDownload") {
                InfoCard(Modifier.padding(top = 16.dp, bottom = 24.dp), title = stringResource(R.string.data_auto_download)) {
                    NetworkType.entries.forEachIndexed { index, network ->
                        if (index > 0) InfoDivider()
                        InfoRow(
                            network.icon,
                            stringResource(network.title),
                            ruleText(state.autoDownload.rule(network), ::size),
                            onClick = { editing = network },
                            modifier = Modifier.testTag("autoDownload_${network.name}"),
                        )
                    }
                }
            }
        }
    }
    clearing?.let { category ->
        ConfirmDialog(
            title = stringResource(R.string.data_clear_title, stringResource(category.title)),
            text = stringResource(R.string.data_clear_text),
            confirm = stringResource(R.string.data_clear),
            tag = "clearConfirm",
            onConfirm = {
                clearing = null
                actions.onClear(category)
            },
            onDismiss = { clearing = null },
        )
    }
    if (clearingAll) {
        ConfirmDialog(
            title = stringResource(R.string.data_clear_all_title),
            text = stringResource(R.string.data_clear_text),
            confirm = stringResource(R.string.data_clear),
            tag = "clearAllConfirm",
            onConfirm = {
                clearingAll = false
                actions.onClear(null)
            },
            onDismiss = { clearingAll = false },
        )
    }
    editing?.let { network ->
        RuleDialog(
            network,
            state.autoDownload.rule(network),
            onSave = { rule ->
                editing = null
                actions.onAutoDownload(state.autoDownload.with(network, rule))
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun UsageBar(bytes: Map<StorageCategory, Long>, total: Long) {
    Row(
        Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).testTag("usageBar"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (total > 0) {
            StorageCategory.entries.forEach { category ->
                val share = (bytes[category] ?: 0).toFloat() / total
                if (share > 0f) Box(Modifier.weight(share).fillMaxHeight().background(category.color()))
            }
        }
    }
}

@Composable
private fun RuleDialog(network: NetworkType, rule: AutoDownloadRule, onSave: (AutoDownloadRule) -> Unit, onDismiss: () -> Unit) {
    var kinds by rememberSaveable(network) { mutableStateOf(rule.kinds.map { it.name }.toSet()) }
    var step by rememberSaveable(network) { mutableFloatStateOf(AutoDownloadSettings.SIZES.indexOfFirst { it >= rule.maxBytes }.coerceAtLeast(0).toFloat()) }
    val context = LocalContext.current
    val limit = AutoDownloadSettings.SIZES[step.roundToInt().coerceIn(AutoDownloadSettings.SIZES.indices)]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(network.title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AutoDownloadKind.entries.forEach { kind ->
                    val checked = kind.name in kinds
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(checked, role = Role.Checkbox) { on -> kinds = if (on) kinds + kind.name else kinds - kind.name }
                            .testTag("kind_${kind.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(kind.title), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.data_limit, Formatter.formatShortFileSize(context, limit)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("limit"))
                Text(stringResource(R.string.data_limit_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = step,
                    onValueChange = { step = it },
                    valueRange = 0f..AutoDownloadSettings.SIZES.lastIndex.toFloat(),
                    steps = AutoDownloadSettings.SIZES.size - 2,
                    modifier = Modifier.testTag("limitSlider"),
                )
            }
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = { onSave(AutoDownloadRule(kinds.mapNotNull { name -> AutoDownloadKind.entries.firstOrNull { it.name == name } }.toSet(), limit)) }, modifier = Modifier.testTag("ruleSave")) {
                Text(stringResource(R.string.data_save))
            }
        },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        modifier = Modifier.testTag("ruleDialog"),
    )
}

@Composable
private fun ruleText(rule: AutoDownloadRule, size: (Long) -> String): String {
    if (rule.kinds.isEmpty()) return stringResource(R.string.data_auto_none)
    val names = AutoDownloadKind.entries.filter { it in rule.kinds }.map { stringResource(it.title) }.joinToString(", ")
    val limited = rule.kinds.any { it == AutoDownloadKind.Videos || it == AutoDownloadKind.Files || it == AutoDownloadKind.Gifs }
    return if (limited) stringResource(R.string.data_auto_summary, names, size(rule.maxBytes)) else names
}

@get:StringRes
private val NetworkType.title: Int
    get() = when (this) {
        NetworkType.Mobile -> R.string.data_mobile
        NetworkType.Wifi -> R.string.data_wifi
        NetworkType.Roaming -> R.string.data_roaming
    }

@get:DrawableRes
private val NetworkType.icon: Int
    get() = when (this) {
        NetworkType.Mobile -> R.drawable.ic_smartphone
        NetworkType.Wifi -> R.drawable.ic_wifi
        NetworkType.Roaming -> R.drawable.ic_flight
    }

@get:StringRes
private val AutoDownloadKind.title: Int
    get() = when (this) {
        AutoDownloadKind.Photos -> R.string.data_kind_photos
        AutoDownloadKind.Videos -> R.string.data_kind_videos
        AutoDownloadKind.Files -> R.string.data_kind_files
        AutoDownloadKind.Voice -> R.string.data_kind_voice
        AutoDownloadKind.Gifs -> R.string.data_kind_gifs
    }

@get:StringRes
private val StorageCategory.title: Int
    get() = when (this) {
        StorageCategory.Media -> R.string.data_category_media
        StorageCategory.Streams -> R.string.data_category_streams
        StorageCategory.Music -> R.string.data_category_music
        StorageCategory.Stickers -> R.string.data_category_stickers
        StorageCategory.Avatars -> R.string.data_category_avatars
    }

@get:DrawableRes
private val StorageCategory.icon: Int
    get() = when (this) {
        StorageCategory.Media -> R.drawable.ic_image
        StorageCategory.Streams -> R.drawable.ic_movie
        StorageCategory.Music -> R.drawable.ic_music_note
        StorageCategory.Stickers -> R.drawable.ic_sticker
        StorageCategory.Avatars -> R.drawable.ic_account_circle
    }

@Composable
private fun StorageCategory.color(): Color = when (this) {
    StorageCategory.Media -> MaterialTheme.colorScheme.primary
    StorageCategory.Streams -> MaterialTheme.colorScheme.tertiary
    StorageCategory.Music -> MaterialTheme.colorScheme.secondary
    StorageCategory.Stickers -> MaterialTheme.colorScheme.onSurfaceVariant
    StorageCategory.Avatars -> MaterialTheme.colorScheme.onSecondaryContainer
}
