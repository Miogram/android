package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoldersTest {
    @Test
    fun limitsFollowTheConfigAndPremium() {
        val config = AppConfig(mapOf("dialog_filters_limit_default" to 4.0, "dialog_filters_limit_premium" to 9.0, "dialog_filters_chats_limit_default" to 30.0))
        assertEquals(FolderLimits(4, 30), config.folderLimits(premium = false))
        assertEquals(FolderLimits(9, FolderLimits.PREMIUM_CHATS, premium = true), config.folderLimits(premium = true))
        assertEquals(FolderLimits(10, 100), AppConfig().folderLimits(premium = false))
    }

    @Test
    fun anotherFolderFitsBelowTheLimitOnly() {
        val limits = FolderLimits(folders = 3)
        assertTrue(limits.canAddFolder(2))
        assertFalse(limits.canAddFolder(3))
        assertEquals(2, listOf(ChatFolder.all(""), ChatFolder(2, "a"), ChatFolder(3, "b")).real.size)
    }

    @Test
    fun aFolderNeedsATitleAndSomethingToHold() {
        assertFalse(ChatFolder(2, "  ", contacts = true).savable())
        assertFalse(ChatFolder(2, "empty").savable())
        assertTrue(ChatFolder(2, "types", bots = true).savable())
        assertTrue(ChatFolder(2, "named", include = setOf("u1")).savable())
        assertTrue(ChatFolder(2, "pins", pinned = listOf("u1")).savable())
    }

    @Test
    fun pickingChatsKeepsPinnedOnesPinnedAndDropsTheRest() {
        val folder = ChatFolder(2, "f", pinned = listOf("u1", "u2"), include = setOf("u3"), exclude = setOf("u9"))
        val edited = folder.includingOnly(listOf("u1", "u3", "u4", "u9"))
        assertEquals(listOf("u1"), edited.pinned)
        assertEquals(setOf("u3", "u4", "u9"), edited.include)
        assertEquals(emptySet(), edited.exclude)
    }

    @Test
    fun excludingTakesTheChatsOutOfWhatIsNamed() {
        val folder = ChatFolder(2, "f", pinned = listOf("u1"), include = setOf("u2", "u3"))
        val edited = folder.excluding(listOf("u1", "u2"))
        assertEquals(setOf("u1", "u2"), edited.exclude)
        assertEquals(emptyList(), edited.pinned)
        assertEquals(setOf("u3"), edited.include)
    }

    @Test
    fun movingAnItemKeepsTheRest() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), list.moved(0, 2))
        assertEquals(listOf("a", "d", "b", "c"), list.moved(3, 1))
        assertEquals(list, list.moved(1, 1))
        assertEquals(list, list.moved(1, 9))
    }
}
