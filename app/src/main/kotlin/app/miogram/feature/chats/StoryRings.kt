package app.miogram.feature.chats

import app.miogram.data.DialogsState
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.StoryRing

internal fun DialogItem.withStory(rings: Map<String, StoryRing>): DialogItem {
    val ring = if (isSaved) StoryRing.None else rings[key] ?: StoryRing.None
    return if (ring == story) this else copy(story = ring)
}

internal fun DialogsState.withStories(rings: Map<String, StoryRing>): DialogsState =
    copy(dialogs = dialogs.map { it.withStory(rings) }, archived = archived.map { it.withStory(rings) }, pulled = pulled.map { it.withStory(rings) })
