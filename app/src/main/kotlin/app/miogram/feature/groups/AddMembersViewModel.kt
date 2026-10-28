package app.miogram.feature.groups

import app.miogram.data.AvatarStore
import app.miogram.data.telegram.ContactsRepository
import app.miogram.data.telegram.GroupsRepository
import cn.ellerotta.core.model.CreatedChat
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow

@HiltViewModel(assistedFactory = AddMembersViewModel.Factory::class)
class AddMembersViewModel @AssistedInject constructor(
    @Assisted("dialogKey") private val dialogKey: String,
    @Assisted("title") private val title: String,
    private val groups: GroupsRepository,
    contacts: ContactsRepository,
    avatarStore: AvatarStore,
) : MemberPickerViewModel(contacts, avatarStore) {
    private val missing = MissingInvites(groups) { block -> work(block) }

    val prompt = missing.prompt

    val done: SharedFlow<CreatedChat> = missing.done

    init {
        startLoading()
    }

    fun add() {
        val keys = picked
        if (keys.isEmpty()) return
        work { missing.settle(CreatedChat(dialogKey, title), groups.invite(dialogKey, keys)) }
    }

    fun sendLinks() = missing.send()

    fun skipLinks() = missing.skip()

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("dialogKey") dialogKey: String, @Assisted("title") title: String): AddMembersViewModel
    }
}
