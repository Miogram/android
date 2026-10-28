package app.miogram.feature.groups

import android.net.Uri
import app.miogram.data.AvatarStore
import app.miogram.data.Attachments
import app.miogram.data.telegram.ContactsRepository
import app.miogram.data.telegram.GroupsRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.model.CreatedChat
import cn.ellerotta.core.model.MissingInvitee
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel(assistedFactory = NewGroupViewModel.Factory::class)
class NewGroupViewModel @AssistedInject constructor(
    @Assisted private val members: List<String>,
    private val groups: GroupsRepository,
    private val attachments: Attachments,
    contacts: ContactsRepository,
    avatarStore: AvatarStore,
) : WorkViewModel() {
    private val nameState = MutableStateFlow("")
    private val photoState = MutableStateFlow<ByteArray?>(null)
    private val membersState = MutableStateFlow<List<ContactItem>>(emptyList())
    private val missing = MissingInvites(groups) { block -> work(block) }

    private var created: CreatedChat? = null
    private var photoSet = false
    private var invited: List<MissingInvitee>? = null

    val name: StateFlow<String> = nameState.asStateFlow()

    val photo: StateFlow<ByteArray?> = photoState.asStateFlow()

    val people: StateFlow<List<ContactItem>> = membersState.asStateFlow()
    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars

    val prompt = missing.prompt

    val done: SharedFlow<CreatedChat> = missing.done

    init {
        work {
            val byKey = contacts.stored().associateBy { it.key }
            membersState.value = members.mapNotNull { byKey[it] }
            avatarStore.load(membersState.value.map { it.avatarKey })
        }
    }

    fun setName(text: String) {
        if (created == null) nameState.value = text
    }

    fun pickPhoto(uri: Uri) {
        if (created != null) return
        work { photoState.value = attachments.squareJpeg(uri) }
    }

    fun removePhoto() {
        if (created == null) photoState.value = null
    }

    fun create() {
        val title = nameState.value
        if (title.isBlank()) return
        work {
            val chat = created ?: groups.createGroup(title).also { created = it }
            if (!photoSet) {
                photoState.value?.let { groups.setPhoto(chat.key, it) }
                photoSet = true
            }
            val left = invited ?: groups.invite(chat.key, members).also { invited = it }
            missing.settle(chat, left)
        }
    }

    fun sendLinks() = missing.send()

    fun skipLinks() = missing.skip()

    @AssistedFactory
    interface Factory {
        fun create(members: List<String>): NewGroupViewModel
    }
}
