package cn.ellerotta.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import cn.ellerotta.core.model.AutoDownloadKind
import cn.ellerotta.core.model.AutoDownloadSettings
import cn.ellerotta.core.model.DataStorageState
import cn.ellerotta.core.model.NetworkType
import cn.ellerotta.core.model.StorageCategory
import cn.ellerotta.core.model.StorageUsage
import cn.ellerotta.core.ui.components.settings.DataStorageActions
import cn.ellerotta.core.ui.components.settings.DataStorageScreen
import cn.ellerotta.core.ui.theme.MioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataStorageTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = DataStorageState(
        usage = StorageUsage(mapOf(StorageCategory.Media to 3_000_000, StorageCategory.Stickers to 500_000, StorageCategory.Music to 0), free = 10_000_000_000),
    )

    @Test
    fun aCategoryIsClearedAfterAsking() {
        var cleared: StorageCategory? = null
        compose.setContent { MioTheme { DataStorageScreen(state, busy = false, DataStorageActions(onClear = { cleared = it })) } }
        compose.onNodeWithTag("storageTotal").assertIsDisplayed()
        compose.onNodeWithTag("dataStorage").performScrollToNode(hasTestTag("clear_Music"))
        compose.onNodeWithTag("clear_Music").assertIsNotEnabled()
        compose.onNodeWithTag("dataStorage").performScrollToNode(hasTestTag("clear_Media"))
        compose.onNodeWithTag("clear_Media").performClick()
        assertEquals(null, cleared, "it asks first")
        compose.onNodeWithTag("clearConfirm").performClick()
        assertEquals(StorageCategory.Media, cleared)
    }

    @Test
    fun aRuleIsChangedInItsDialog() {
        var saved: AutoDownloadSettings? = null
        compose.setContent { MioTheme { DataStorageScreen(state, busy = false, DataStorageActions(onAutoDownload = { saved = it })) } }
        compose.onNodeWithTag("dataStorage").performScrollToNode(hasTestTag("autoDownload_Mobile"))
        compose.onNodeWithTag("autoDownload_Mobile").performClick()
        compose.onNodeWithTag("kind_Photos").performClick()
        compose.onNodeWithTag("kind_Voice").performClick()
        compose.onNodeWithTag("ruleSave").performClick()
        val mobile = saved?.rule(NetworkType.Mobile)
        assertEquals(setOf(AutoDownloadKind.Photos, AutoDownloadKind.Gifs), mobile?.kinds)
        assertEquals(AutoDownloadSettings().mobile.maxBytes, mobile?.maxBytes)
        assertEquals(AutoDownloadSettings().wifi, saved?.wifi, "the other networks stay")
    }
}
