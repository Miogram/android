package app.miogram.feature.groups

import androidx.lifecycle.viewModelScope
import app.miogram.data.AvatarStore
import app.miogram.data.telegram.ContactsRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.model.pickableContacts
import cn.ellerotta.core.model.sortedByPresence
import cn.ellerotta.core.model.toggled
import cn.ellerotta.core.ui.components.groups.MemberPickerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class MemberPickerViewModel(
    private val contacts: ContactsRepository,
    private val avatarStore: AvatarStore,
) : WorkViewModel() {
    private val listState = MutableStateFlow<List<ContactItem>>(emptyList())
    private val queryState = MutableStateFlow("")
    private val pickedState = MutableStateFlow<List<String>>(emptyList())
    private val loadedState = MutableStateFlow(false)

    val picker: StateFlow<MemberPickerState> = combine(listState, queryState, pickedState, loadedState) { list, query, picked, loaded ->
        val byKey = list.associateBy { it.key }
        MemberPickerState(pickableContacts(list, query), picked.mapNotNull { byKey[it] }, query, loaded)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MemberPickerState())

    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars

    val picked: List<String> get() = pickedState.value

    protected fun startLoading() {
        viewModelScope.launch {
            show(contacts.stored())
            refresh(listState.value.isNotEmpty()) {
                try {
                    show(contacts.contacts())
                } finally {
                    loadedState.value = true
                }
            }
        }
    }

    fun search(query: String) {
        queryState.value = query
    }

    fun toggle(person: ContactItem) {
        pickedState.value = toggled(pickedState.value, person.key)
    }

    private fun show(list: List<ContactItem>) {
        listState.value = list.sortedByPresence()
        avatarStore.load(list.map { it.avatarKey })
    }
}
