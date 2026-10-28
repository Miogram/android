package cn.ellerotta.core.ui

import android.graphics.Bitmap
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.FolderLimits
import cn.ellerotta.core.model.FolderTab
import cn.ellerotta.core.ui.components.chats.FolderActions
import cn.ellerotta.core.ui.components.chats.FolderPillRow
import cn.ellerotta.core.ui.components.folders.FolderChatPicker
import cn.ellerotta.core.ui.components.folders.FolderEditScreen
import cn.ellerotta.core.ui.components.folders.FolderEditState
import cn.ellerotta.core.ui.components.folders.FoldersPreviewData
import cn.ellerotta.core.ui.components.folders.FoldersScreen
import cn.ellerotta.core.ui.components.folders.FoldersState
import cn.ellerotta.core.ui.theme.MioTheme
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FoldersTest {
    @get:Rule
    val compose = createComposeRule()

    private val data = FoldersPreviewData

    private fun gone(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()

    @Composable
    private fun Page(state: FoldersState, onReorder: (List<Int>) -> Unit = {}, onOpen: (ChatFolder) -> Unit = {}, onCreate: () -> Unit = {}, onAdd: (cn.ellerotta.core.model.SuggestedFolder) -> Unit = {}) {
        MioTheme(useDynamicColor = false) { FoldersScreen(state, onBack = {}, onOpen = onOpen, onCreate = onCreate, onReorder = onReorder, onAddSuggested = onAdd) }
    }

    @Test
    fun draggingAHandlePastItsNeighbourOrdersTheFoldersOnRelease() {
        val orders = mutableListOf<List<Int>>()
        compose.setContent { Page(data.filled, onReorder = { orders += it }) }
        val handle = compose.onNodeWithTag("folderHandle_2", useUnmergedTree = true)
        handle.performTouchInput { down(center) }
        repeat(5) { handle.performTouchInput { moveBy(Offset(0f, 20.dp.toPx())) } ; compose.waitForIdle() }
        assertTrue(orders.isEmpty(), "nothing is saved while the finger is down")
        handle.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf(listOf(0, 3, 2, 4, 5)), orders)
    }

    @Test
    fun aShortDragThatReachesNoNeighbourChangesNothing() {
        val orders = mutableListOf<List<Int>>()
        compose.setContent { Page(data.filled, onReorder = { orders += it }) }
        val handle = compose.onNodeWithTag("folderHandle_3", useUnmergedTree = true)
        handle.performTouchInput { down(center) }
        handle.performTouchInput { moveBy(Offset(0f, 10.dp.toPx())) }
        compose.waitForIdle()
        handle.performTouchInput { up() }
        compose.waitForIdle()
        assertTrue(orders.isEmpty())
    }

    @Test
    fun talkbackMovesARowUpOrDownWithoutDragging() {
        val orders = mutableListOf<List<Int>>()
        compose.setContent { Page(data.filled, onReorder = { orders += it }) }
        fun actions(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(1, actions("folderRow_0").size)
        assertEquals(1, actions("folderRow_5").size)
        assertEquals(2, actions("folderRow_3").size)
        actions("folderRow_3").first { it.label.contains("up") }.action()
        actions("folderRow_3").first { it.label.contains("down") }.action()
        assertEquals(listOf(listOf(0, 3, 2, 4, 5), listOf(0, 2, 4, 3, 5)), orders)
    }

    @Test
    fun aRowOpensItsFolderAndAllChatsOpensNothing() {
        val opened = mutableListOf<Int>()
        compose.setContent { Page(data.filled, onOpen = { opened += it.id }) }
        compose.onNodeWithTag("folderRow_3").performClick()
        compose.onNodeWithTag("folderRow_0").performClick()
        assertEquals(listOf(3), opened)
    }

    @Test
    fun theLimitTakesTheNewFolderButtonAndTheAddButtonsAway() {
        var created = 0
        var state by mutableStateOf(data.filled)
        compose.setContent { Page(state, onCreate = { created++ }) }
        compose.onNodeWithTag("folderCreate").performClick()
        assertEquals(1, created)
        assertTrue(gone("folderLimit"))
        compose.onNodeWithTag("folders_list").performScrollToNode(hasTestTag("suggestedAdd_Bots"))
        compose.onNodeWithTag("suggestedAdd_Bots").assertIsEnabled()

        state = data.atLimit
        compose.waitUntil { gone("folderCreate") }
        compose.onNodeWithTag("folderLimit").assertExists()
        compose.onNodeWithTag("folders_list").performScrollToNode(hasTestTag("suggestedAdd_Bots"))
        compose.onNodeWithTag("suggestedAdd_Bots").assertIsNotEnabled()
    }

    @Test
    fun aSuggestionIsAddedFromItsRow() {
        val added = mutableListOf<String>()
        compose.setContent { Page(data.filled, onAdd = { added += it.folder.title }) }
        compose.onNodeWithTag("suggestedAdd_Unread").performClick()
        assertEquals(listOf("Unread"), added)
    }

    @Test
    fun suggestionsShowALoadingIndicatorUntilTheyCome() {
        compose.setContent { Page(data.loadingSuggestions) }
        compose.onNodeWithTag("suggestedLoading").assertExists()
    }

    @Composable
    private fun Editor(start: FolderEditState, onSave: () -> Unit = {}, onDelete: () -> Unit = {}, changed: (ChatFolder) -> Unit = {}) {
        var draft by remember { mutableStateOf(start.folder) }
        MioTheme(useDynamicColor = false) {
            FolderEditScreen(
                state = start.copy(folder = draft, changed = draft != start.folder),
                avatars = data.avatars,
                busy = false,
                onBack = {},
                onChange = { draft = it; changed(it) },
                onSave = onSave,
                onDelete = onDelete,
            )
        }
    }

    @Test
    fun aNewFolderCanBeSavedOnceItHasANameAndSomethingToHold() {
        var saved = 0
        var last: ChatFolder? = null
        compose.setContent { Editor(data.newFolder, onSave = { saved++ }, changed = { last = it }) }
        compose.onNodeWithTag("folderEdit").performScrollToNode(hasTestTag("folderNeeds"))
        compose.onNodeWithTag("folderNeeds").assertExists()
        assertTrue(gone("folderSave"))

        compose.onNodeWithTag("folderEdit").performScrollToNode(hasTestTag("folderName"))
        compose.onNodeWithTag("folderName").performTextInput("Reading")
        assertTrue(gone("folderSave"))
        compose.onNodeWithTag("folderEdit").performScrollToNode(hasTestTag("type_bots"))
        compose.onNodeWithTag("type_bots").performClick()
        compose.waitUntil { !gone("folderSave") }
        assertTrue(gone("folderNeeds"))
        assertTrue(last!!.bots)
        assertEquals("Reading", last!!.title)
        compose.onNodeWithTag("folderSave").performClick()
        assertEquals(1, saved)

        compose.onNodeWithTag("folderEdit").performScrollToNode(hasTestTag("folderName"))
        compose.onNodeWithTag("folderName").performTextClearance()
        compose.waitUntil { gone("folderSave") }
    }

    @Test
    fun theIconAndTheTypesAndStatesAreOneTapEach() {
        val changes = mutableListOf<ChatFolder>()
        compose.setContent { Editor(data.editing, changed = { changes += it }) }
        compose.onNodeWithTag("folderIcons").performScrollToNode(hasTestTag("folderIcon_\uD83C\uDFAE"))
        compose.onNodeWithTag("folderIcon_\uD83C\uDFAE").performClick()
        compose.onNodeWithTag("folderIcons").performScrollToNode(hasTestTag("folderIcon_none"))
        assertEquals("\uD83C\uDFAE", changes.last().emoticon)
        compose.onNodeWithTag("folderIcon_none").performClick()
        assertNull(changes.last().emoticon)

        compose.onNodeWithTag("folderEdit").performScrollToNode(hasTestTag("type_contacts"))
        compose.onNodeWithTag("type_contacts").performClick()
        assertTrue(changes.last().contacts)
        compose.onNodeWithTag("type_groups").performClick()
        assertFalse(changes.last().groups)
        compose.onNodeWithTag("folderEdit").performScrollToNode(hasTestTag("exclude_read"))
        compose.onNodeWithTag("exclude_read").performClick()
        assertTrue(changes.last().excludeRead)
        compose.onNodeWithTag("exclude_muted").performClick()
        assertFalse(changes.last().excludeMuted)
    }

    @Test
    fun aChipTakesItsChatOutOfTheFolder() {
        val changes = mutableListOf<ChatFolder>()
        compose.setContent { Editor(data.editing, changed = { changes += it }) }
        compose.onNodeWithTag("folderEdit").performScrollToNode(hasTestTag("included_chip_u101"))
        compose.onNodeWithTag("included_chip_u101").performClick()
        assertEquals(setOf("c202"), changes.last().include)
        compose.onNodeWithTag("folderEdit").performScrollToNode(hasTestTag("excluded_chip_ch303"))
        compose.onNodeWithTag("excluded_chip_ch303").performClick()
        assertEquals(emptySet(), changes.last().exclude)
    }

    @Test
    fun theIncludedPickerKeepsTheDraftAndHandsBackTheChats() {
        val changes = mutableListOf<ChatFolder>()
        compose.setContent { Editor(data.editing, changed = { changes += it }) }
        compose.onNodeWithTag("folderEdit").performScrollToNode(hasTestTag("included_add"))
        compose.onNodeWithTag("included_add").performClick()
        compose.waitUntil { !gone("folderPickList") }
        compose.onNodeWithTag("folderPickChip_c202").assertExists()
        compose.onNodeWithTag("folderPick_u404").performClick()
        compose.onNodeWithTag("folderPickDone").performClick()
        compose.waitUntil { gone("folderPickList") }
        assertEquals(setOf("c202", "u101", "u404"), changes.last().include)
        compose.onNodeWithTag("included_chip_u404").assertExists()
    }

    @Test
    fun theExcludedPickerTakesAChatOutOfWhatTheFolderNames() {
        val changes = mutableListOf<ChatFolder>()
        compose.setContent { Editor(data.editing, changed = { changes += it }) }
        compose.onNodeWithTag("folderEdit").performScrollToNode(hasTestTag("excluded_add"))
        compose.onNodeWithTag("excluded_add").performClick()
        compose.waitUntil { !gone("folderPickList") }
        compose.onNodeWithTag("folderPick_u101").performClick()
        compose.onNodeWithTag("folderPickDone").performClick()
        compose.waitUntil { gone("folderPickList") }
        assertEquals(setOf("ch303", "u101"), changes.last().exclude)
        assertEquals(setOf("c202"), changes.last().include)
    }

    @Test
    fun theChatPickerSearchesAndStopsAtTheFoldersLimit() {
        var done: List<String>? = null
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                FolderChatPicker("Add chats", data.chats, picked = listOf("c202", "u101"), avatars = data.avatars, limit = 3, onBack = {}, onDone = { done = it })
            }
        }
        compose.onNodeWithTag("folderPick_u404").performClick()
        compose.waitUntil { !gone("folderLimit") }
        compose.onNodeWithTag("folderPick_ch303").assertIsNotEnabled()
        compose.onNodeWithTag("folderPick_u101").assertIsEnabled()
        compose.onNodeWithTag("folderPick_u101").performClick()
        compose.waitUntil { gone("folderLimit") }
        compose.onNodeWithTag("folderPick_ch303").assertIsEnabled()

        compose.onNodeWithTag("folderPickSearch").performTextInput("daily")
        compose.waitUntil { gone("folderPick_u404") }
        compose.onNodeWithTag("folderPick_ch303").performClick()
        compose.onNodeWithTag("folderPickDone").performClick()
        assertEquals(listOf("c202", "u404", "ch303"), done)
    }

    @Test
    fun aSharedFolderOffersNameIconAndChatsOnly() {
        compose.setContent { Editor(data.editingShared) }
        compose.onNodeWithTag("folderShared").assertExists()
        compose.onNodeWithTag("included_card").assertExists()
        assertTrue(gone("folderTypes"))
        assertTrue(gone("excluded_card"))
        assertTrue(gone("exclude_muted"))
    }

    @Test
    fun deletingAsksFirstAndOnlyAnExistingFolderCanBeDeleted() {
        var deleted = 0
        compose.setContent { Editor(data.editing, onDelete = { deleted++ }) }
        compose.onNodeWithTag("folderEdit").performScrollToNode(hasTestTag("folderDelete"))
        compose.onNodeWithTag("folderDelete").performClick()
        compose.onNodeWithTag("confirmFolderDelete").assertExists()
        assertEquals(0, deleted)
        compose.onNodeWithTag("confirmFolderDelete").performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun aNewFolderHasNoDeleteButtonAndNoRoomWhenTheAccountIsFull() {
        compose.setContent { Editor(data.newAtLimit) }
        compose.onNodeWithTag("folderLimit").assertExists()
        assertTrue(gone("folderSave"))
        compose.onNodeWithTag("folderEdit").performScrollToNode(hasTestTag("folderNeeds").or(hasTestTag("exclude_archived")))
        assertTrue(gone("folderDelete"))
    }

    @Test
    fun aFolderStillLoadingShowsAnIndicator() {
        compose.setContent { Editor(data.loading) }
        compose.onNodeWithTag("folderLoading").assertExists()
    }

    @Test
    fun aLongPressOnATabOffersEditReorderAndDelete() {
        val log = mutableListOf<String>()
        val tabs = data.folders.map { FolderTab(it, emptyList()) }
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                FolderPillRow(
                    tabs, rememberPagerState { tabs.size }, onTabClick = { log += "tab$it" },
                    actions = FolderActions({ log += "edit${it.id}" }, { log += "reorder" }, { log += "delete${it.id}" }),
                )
            }
        }
        compose.onNodeWithTag("folder_3").performTouchInput { longClick() }
        compose.onNodeWithTag("folderMenu").assertExists()
        compose.onNodeWithTag("folderMenuEdit").performClick()
        compose.onNodeWithTag("folder_3").performTouchInput { longClick() }
        compose.onNodeWithTag("folderMenuReorder").performClick()
        compose.onNodeWithTag("folder_4").performTouchInput { longClick() }
        compose.onNodeWithTag("folderMenuDelete").performClick()
        assertEquals(listOf("edit3", "reorder", "delete4"), log)

        compose.onNodeWithTag("folder_0").performTouchInput { longClick() }
        compose.onNodeWithTag("folderMenuReorder").assertExists()
        assertTrue(gone("folderMenuEdit"))
        assertTrue(gone("folderMenuDelete"))
        compose.onNodeWithTag("folder_2").performClick()
        assertEquals("tab1", log.last())
    }

    @Test
    fun screens() {
        val dir = System.getenv("MIOGRAM_SCREENSHOTS")?.let(::File)?.also { it.mkdirs() }
        var dark by mutableStateOf(false)
        var page by mutableStateOf(0)
        compose.setContent {
            MioTheme(darkTheme = dark, useDynamicColor = false) {
                when (page) {
                    0 -> FoldersScreen(data.filled, {}, {}, {}, {}, {})
                    1 -> FoldersScreen(data.atLimit, {}, {}, {}, {}, {})
                    2 -> FolderEditScreen(data.newFolder, data.avatars, false, {}, {}, {}, {})
                    3 -> FolderEditScreen(data.editing, data.avatars, false, {}, {}, {}, {})
                    4 -> FolderEditScreen(data.editingShared, data.avatars, false, {}, {}, {}, {})
                    5 -> FolderChatPicker("Add chats", data.chats, listOf("c202", "u101"), data.avatars, 100, {}, {})
                    else -> FolderChatPicker("Add chats", data.chats, listOf("c202", "u101"), data.avatars, 2, {}, {})
                }
            }
        }
        val names = listOf("folders", "folders_limit", "folder_new", "folder_edit", "folder_shared", "folder_picker", "folder_picker_full")
        for (isDark in listOf(false, true)) {
            dark = isDark
            for ((index, name) in names.withIndex()) {
                page = index
                compose.waitForIdle()
                val image = compose.onRoot().captureToImage().asAndroidBitmap()
                assertTrue(image.width > 0 && image.height > 0)
                dir?.let { File(it, "${name}_${if (isDark) "dark" else "light"}.png").outputStream().use { out -> image.compress(Bitmap.CompressFormat.PNG, 100, out) } }
            }
        }
    }
}
