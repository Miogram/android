package app.miogram.feature.chat

import android.net.Uri
import app.miogram.data.Attachments
import cn.ellerotta.core.model.AttachmentDraft
import cn.ellerotta.core.model.AttachmentItem
import cn.ellerotta.core.model.AttachmentKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PickedAttachment(val item: AttachmentItem, val uri: Uri)

internal class ChatAttachments(private val scope: CoroutineScope, private val attachments: Attachments) {
    private val state = MutableStateFlow<AttachmentDraft?>(null)
    private val uris = LinkedHashMap<Int, Uri>()
    private var ids = 0

    val draft: StateFlow<AttachmentDraft?> = state.asStateFlow()

    fun pick(picked: List<Uri>, asFile: Boolean) {
        if (picked.isEmpty() || state.value != null) return
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                picked.distinct().take(AttachmentDraft.MAX_ITEMS).map { uri ->
                    val file = attachments.describe(uri)
                    val kind = AttachmentKind.of(file.mimeType)
                    val preview = if (kind.visual) attachments.preview(uri, video = kind == AttachmentKind.Video) else null
                    val id = ids++
                    uris[id] = uri
                    AttachmentItem(id, file.name, kind, file.size.coerceAtLeast(0), preview)
                }
            }
            state.value = AttachmentDraft(items, asFile)
        }
    }

    fun asFile(on: Boolean) = state.update { it?.copy(asFile = on) }

    fun spoiler(on: Boolean) = state.update { it?.copy(spoiler = on) }

    fun remove(id: Int) {
        uris -= id
        state.update { draft -> draft?.copy(items = draft.items.filter { it.id != id })?.takeIf { it.items.isNotEmpty() } }
    }

    fun close() {
        uris.clear()
        state.value = null
    }

    fun take(): Pair<AttachmentDraft, List<PickedAttachment>>? {
        val draft = state.value ?: return null
        val picked = draft.items.mapNotNull { item -> uris[item.id]?.let { PickedAttachment(item, it) } }
        close()
        return draft to picked
    }
}
