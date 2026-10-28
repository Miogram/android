package cn.ellerotta.core.ui.components.chat

import cn.ellerotta.core.model.EmojiCategoryGroup
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.PickedEmoji

data class ChatStickerActions(
    val onOpenPanel: () -> Unit,
    val onOpenTab: (setKey: String) -> Unit,
    val onLoad: (MediaItem) -> Unit,
    val onSend: (MediaItem) -> Unit,
    val onDraft: (String) -> Unit,
    val onShowSet: (MediaItem) -> Unit,
    val onInstall: (setKey: String, install: Boolean) -> Unit,
    val onDismissSet: () -> Unit,
    val onOpenEmoji: () -> Unit = {},
    val onSearchEmoji: (String) -> Unit = {},
    val onPickEmoji: (PickedEmoji) -> Unit = {},
    val onSearchStickers: (String) -> Unit = {},
    val onOpenGifs: () -> Unit = {},
    val onSearchGifs: (String) -> Unit = {},
    val onMoreGifs: () -> Unit = {},
    val onSendGif: (MediaItem) -> Unit = {},
    val onKeyboardHeight: (Int) -> Unit = {},
    val onChooseTone: (base: String, toned: String) -> Unit = { _, _ -> },
    val onSearchEmojiCategory: (EmojiCategoryGroup) -> Unit = {},
    val onSearchStickerCategory: (EmojiCategoryGroup) -> Unit = {},
    val onReadFeatured: (List<String>) -> Unit = {},
    val onFave: (MediaItem, Boolean) -> Unit = { _, _ -> },
    val onRemoveGif: (MediaItem) -> Unit = {},
    val onReorderSets: (setKeys: List<String>, emoji: Boolean) -> Unit = { _, _ -> },
)
