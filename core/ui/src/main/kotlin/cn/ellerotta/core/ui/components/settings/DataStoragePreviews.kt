package cn.ellerotta.core.ui.components.settings

import androidx.compose.runtime.Composable
import cn.ellerotta.core.model.AutoDownloadKind
import cn.ellerotta.core.model.AutoDownloadRule
import cn.ellerotta.core.model.AutoDownloadSettings
import cn.ellerotta.core.model.DataStorageState
import cn.ellerotta.core.model.NetworkType
import cn.ellerotta.core.model.StorageCategory
import cn.ellerotta.core.model.StorageUsage
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme

private object DataStoragePreviewData {
    private const val MB = AutoDownloadSettings.MB

    val state = DataStorageState(
        autoDownload = AutoDownloadSettings().with(NetworkType.Wifi, AutoDownloadRule(AutoDownloadKind.entries.toSet(), 100 * MB)),
        usage = StorageUsage(
            mapOf(
                StorageCategory.Media to 412 * MB,
                StorageCategory.Streams to 96 * MB,
                StorageCategory.Music to 58 * MB,
                StorageCategory.Stickers to 131 * MB,
                StorageCategory.Avatars to 9 * MB,
            ),
            free = 23_500 * MB,
        ),
    )
}

@MioPreviews
@Composable
internal fun DataStoragePreview() {
    PreviewTheme { DataStorageScreen(DataStoragePreviewData.state, busy = false, DataStorageActions()) }
}

@MioPreviews
@Composable
internal fun DataStorageMeasuringPreview() {
    PreviewTheme { DataStorageScreen(DataStorageState(), busy = false, DataStorageActions()) }
}

@MioPreviews
@Composable
internal fun DataStorageClearingPreview() {
    PreviewTheme { DataStorageScreen(DataStoragePreviewData.state.copy(clearing = setOf(StorageCategory.Music)), busy = true, DataStorageActions()) }
}
