package app.miogram.feature.groups

import app.miogram.data.AvatarStore
import app.miogram.data.telegram.ContactsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NewGroupMembersViewModel @Inject constructor(contacts: ContactsRepository, avatarStore: AvatarStore) : MemberPickerViewModel(contacts, avatarStore) {
    init {
        startLoading()
    }
}
