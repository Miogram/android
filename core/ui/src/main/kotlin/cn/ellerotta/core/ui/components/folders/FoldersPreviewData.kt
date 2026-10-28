package cn.ellerotta.core.ui.components.folders

import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.FolderLimits
import cn.ellerotta.core.model.SuggestedFolder
import cn.ellerotta.core.ui.components.chats.ChatsPreviewData

internal object FoldersPreviewData {
    val avatars: Map<String, ByteArray> = ChatsPreviewData.avatars

    val chats = ChatsPreviewData.dialogs + ChatsPreviewData.archived

    val personal = ChatFolder(id = 2, title = "Personal", emoticon = "\uD83D\uDC64", contacts = true, nonContacts = true, include = setOf("u404"))
    val work = ChatFolder(id = 3, title = "Work", emoticon = "\uD83D\uDCBC", groups = true, bots = true, excludeMuted = true, include = setOf("c202", "u101"), exclude = setOf("ch303"))
    val channels = ChatFolder(id = 4, title = "Channels", broadcasts = true, excludeRead = true)
    val shared = ChatFolder(id = 5, title = "Film club", emoticon = "\uD83C\uDFAE", include = setOf("c202", "ch303"), shared = true)

    val folders = listOf(ChatFolder.all(""), personal, work, channels, shared)

    val suggested = listOf(
        SuggestedFolder(ChatFolder(id = 1, title = "Unread", excludeRead = true, contacts = true, nonContacts = true, groups = true, broadcasts = true, bots = true), "All chats with something you have not read"),
        SuggestedFolder(ChatFolder(id = 2, title = "Bots", bots = true), "Chat with bots in one place"),
    )

    val filled = FoldersState(folders, suggested, limits = FolderLimits())
    val loadingSuggestions = FoldersState(listOf(ChatFolder.all(""), personal), suggestedLoading = true)
    val empty = FoldersState(listOf(ChatFolder.all("")), suggested)

    val atLimit = FoldersState(
        listOf(ChatFolder.all("")) + (1..10).map { ChatFolder(id = it + 1, title = "Folder $it", contacts = it % 2 == 0, groups = it % 2 == 1) },
        suggested,
        limits = FolderLimits(folders = 10),
    )

    val newFolder = FolderEditState(ChatFolder(id = ChatFolder.NEW, title = ""), isNew = true, chats = chats, folderCount = 3)
    val newReady = FolderEditState(ChatFolder(id = ChatFolder.NEW, title = "Reading", emoticon = "\uD83D\uDCDA", bots = true), isNew = true, chats = chats, folderCount = 3, changed = true)
    val editing = FolderEditState(work, isNew = false, chats = chats, folderCount = 4, changed = true)
    val editingShared = FolderEditState(shared, isNew = false, chats = chats, folderCount = 4)
    val newAtLimit = FolderEditState(ChatFolder(id = ChatFolder.NEW, title = "One more", contacts = true), isNew = true, chats = chats, limits = FolderLimits(folders = 10), folderCount = 10, changed = true)
    val loading = FolderEditState(null, isNew = false)
}
