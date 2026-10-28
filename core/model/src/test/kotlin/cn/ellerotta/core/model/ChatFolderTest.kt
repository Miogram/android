package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatFolderTest {
    private val saved = DialogItem("u1", "me", isSaved = true, kind = PeerKind.Saved, date = 50)
    private val friend = DialogItem("u2", "friend", kind = PeerKind.User, contact = true, date = 40, unread = 2)
    private val stranger = DialogItem("u3", "stranger", kind = PeerKind.User, date = 30)
    private val bot = DialogItem("u4", "bot", kind = PeerKind.Bot, date = 20, muted = true, unread = 1)
    private val group = DialogItem("c5", "group", kind = PeerKind.Group, date = 60, pinned = true)
    private val channel = DialogItem("ch6", "news", kind = PeerKind.Channel, date = 10, archived = true, unreadMark = true)
    private val all = listOf(saved, friend, stranger, bot, group, channel)

    @Test
    fun allChatsSkipTheArchiveAndPutPinnedFirst() {
        val folder = ChatFolder.all("")
        assertEquals(listOf("c5", "u1", "u2", "u3", "u4"), folder.pick(all).map { it.key })
    }

    @Test
    fun typeFlagsIncludeAndExclude() {
        assertEquals(listOf("u2"), ChatFolder(1, "contacts", contacts = true).pick(all).map { it.key })
        assertEquals(listOf("u3"), ChatFolder(1, "others", nonContacts = true).pick(all).map { it.key })
        assertEquals(listOf("c5", "ch6"), ChatFolder(1, "rooms", groups = true, broadcasts = true).pick(all).map { it.key })
        assertEquals(listOf("u4"), ChatFolder(1, "bots", bots = true).pick(all).map { it.key })
        val unread = ChatFolder(1, "unread", contacts = true, nonContacts = true, bots = true, groups = true, broadcasts = true, excludeMuted = true, excludeRead = true, excludeArchived = true, include = setOf("c5"), exclude = setOf("u2"))
        assertEquals(listOf("c5"), unread.pick(all).map { it.key })
        assertFalse(ChatFolder(1, "x", nonContacts = true).matches(saved))
    }

    @Test
    fun folderPinsKeepTheirOrder() {
        val folder = ChatFolder(1, "mine", pinned = listOf("u3", "u2"), include = setOf("u4"))
        assertEquals(listOf("u3", "u2", "u4"), folder.pick(all).map { it.key })
    }

    @Test
    fun aRepeatedPinOrRowShowsOnce() {
        val folder = ChatFolder(1, "mine", pinned = listOf("u3", "u2", "u3"), include = setOf("u4"))
        assertEquals(listOf("u3", "u2", "u4"), folder.pick(all).map { it.key })
        val rooms = ChatFolder(1, "rooms", groups = true, broadcasts = true)
        assertEquals(listOf("c5", "ch6"), rooms.pick(all + channel.copy(archived = false)).map { it.key })
        assertEquals(listOf("c5", "u1", "u2", "u3", "u4"), ChatFolder.all("").pick(all + group + saved).map { it.key })
    }

    @Test
    fun tabCountsUnreadChats() {
        val tab = FolderTab(ChatFolder(1, "x", bots = true, broadcasts = true), listOf(bot, channel))
        assertEquals(2, tab.unreadChats)
        assertFalse(tab.mutedOnly)
        assertTrue(FolderTab(ChatFolder(1, "x"), listOf(bot)).mutedOnly)
    }

    @Test
    fun onlyTypeFlagsMakeAFolderReadTheWholeList() {
        assertTrue(ChatFolder(1, "x", contacts = true).hasRules)
        assertTrue(ChatFolder(1, "x", nonContacts = true).hasRules)
        assertTrue(ChatFolder(1, "x", groups = true).hasRules)
        assertTrue(ChatFolder(1, "x", broadcasts = true).hasRules)
        assertTrue(ChatFolder(1, "x", bots = true).hasRules)
        assertFalse(ChatFolder(1, "x", pinned = listOf("u1"), include = setOf("u2")).hasRules)
        assertFalse(ChatFolder(1, "x", excludeMuted = true, excludeRead = true, excludeArchived = true, exclude = setOf("u1")).hasRules)
    }

    @Test
    fun namedChatsAreThePinnedOnesThenTheIncludedOnesEachOnce() {
        assertEquals(listOf("u3", "u2", "u4"), ChatFolder(1, "mine", pinned = listOf("u3", "u2", "u3"), include = setOf("u4", "u2")).explicit)
        assertEquals(emptyList(), ChatFolder(1, "none").explicit)
    }

    @Test
    fun whatRemovesAChatDependsOnItsKindAndOwner() {
        assertEquals(listOf(ChatRemoval.Clear), saved.removals)
        assertEquals(listOf(ChatRemoval.Clear, ChatRemoval.Delete), friend.removals)
        assertEquals(listOf(ChatRemoval.Clear, ChatRemoval.Delete), bot.removals)
        assertEquals(listOf(ChatRemoval.Clear, ChatRemoval.Delete), group.removals)
        assertEquals(listOf(ChatRemoval.Clear, ChatRemoval.Delete, ChatRemoval.DeleteForEveryone), group.copy(owner = true).removals)
        assertEquals(listOf(ChatRemoval.Delete, ChatRemoval.DeleteForEveryone), group.copy(owner = true, forum = true).removals)
        assertEquals(listOf(ChatRemoval.Delete), channel.removals)
        assertEquals(listOf(ChatRemoval.Delete, ChatRemoval.DeleteForEveryone), channel.copy(owner = true).removals)
    }

    @Test
    fun onlyAPersonsChatCanTakeItsMessagesFromTheOtherSideToo() {
        assertTrue(friend.revokable(ChatRemoval.Delete))
        assertTrue(friend.revokable(ChatRemoval.Clear))
        assertFalse(bot.revokable(ChatRemoval.Delete))
        assertFalse(saved.revokable(ChatRemoval.Clear))
        assertFalse(group.copy(owner = true).revokable(ChatRemoval.DeleteForEveryone))
    }
}
