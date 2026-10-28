package app.miogram.feature.profile

import androidx.lifecycle.viewModelScope
import android.net.Uri
import app.miogram.data.Attachments
import app.miogram.data.AvatarStore
import app.miogram.data.telegram.ProfileEditRepository
import app.miogram.data.telegram.ProfileRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.Birthday
import cn.ellerotta.core.model.OwnUsername
import cn.ellerotta.core.model.PersonalChannel
import cn.ellerotta.core.model.ProfileDetails
import cn.ellerotta.core.model.ProfileEdit
import cn.ellerotta.core.model.ProfileRules
import cn.ellerotta.core.model.UiError
import cn.ellerotta.core.model.UsernameCheck
import cn.ellerotta.ktproto.session.RpcException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val edits: ProfileEditRepository,
    private val profiles: ProfileRepository,
    private val avatarStore: AvatarStore,
    private val attachments: Attachments,
) : WorkViewModel() {
    private val editState = MutableStateFlow<ProfileEdit?>(null)
    private val choicesState = MutableStateFlow<List<PersonalChannel>?>(null)
    private val savedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val typedUsernames = MutableSharedFlow<String>(extraBufferCapacity = 16)

    val edit: StateFlow<ProfileEdit?> = editState.asStateFlow()

    val channels: StateFlow<List<PersonalChannel>?> = choicesState.asStateFlow()

    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars

    val saved: SharedFlow<Unit> = savedEvents.asSharedFlow()

    init {
        viewModelScope.launch { profiles.own.filterNotNull().collect(::adopt) }
        viewModelScope.launch {
            typedUsernames.debounce(USERNAME_DEBOUNCE_MILLIS).distinctUntilChanged().mapLatest { typed -> typed to check(typed) }.collect { (typed, result) ->
                editState.update { state -> if (state != null && state.username == typed && state.usernameCheck == UsernameCheck.Checking) state.copy(usernameCheck = result) else state }
            }
        }
        viewModelScope.launch {
            profiles.stored(null)?.let { adopt(it) }
            refresh(editState.value != null) { adopt(profiles.profile(null)) }
        }
    }

    private suspend fun adopt(details: ProfileDetails) {
        avatarStore.load(listOf(details.avatarKey, details.channel?.avatarKey))
        avatarStore.loadBig(listOf(details.avatarKey))
        val current = editState.value
        if (current == null) {
            val limit = runCatching { edits.aboutLimit(details.premium) }.getOrDefault(if (details.premium) ProfileRules.PREMIUM_ABOUT_LIMIT else ProfileRules.DEFAULT_ABOUT_LIMIT)
            editState.update { it ?: ProfileEdit(details, aboutLimit = limit) }
        } else {
            editState.update { it?.copy(saved = details) }
        }
    }

    fun setFirstName(text: String) = editState.update { it?.copy(firstName = text.take(ProfileRules.NAME_MAX)) }

    fun setLastName(text: String) = editState.update { it?.copy(lastName = text.take(ProfileRules.NAME_MAX)) }

    fun setAbout(text: String) = editState.update { it?.copy(about = text.take(it.aboutLimit)) }

    fun setUsername(text: String) {
        val username = text.trim().removePrefix("@")
        editState.update { state ->
            state?.copy(username = username, usernameCheck = verdict(state, username))
        }
        typedUsernames.tryEmit(username)
    }

    private fun verdict(state: ProfileEdit, username: String): UsernameCheck = when {
        username.isEmpty() || username == state.savedUsername -> UsernameCheck.Idle
        ProfileRules.usernameProblem(username) != null -> UsernameCheck.Invalid(ProfileRules.usernameProblem(username))
        username.equals(state.savedUsername, ignoreCase = true) -> UsernameCheck.Free
        else -> UsernameCheck.Checking
    }

    private suspend fun check(username: String): UsernameCheck {
        val state = editState.value ?: return UsernameCheck.Idle
        if (verdict(state, username) != UsernameCheck.Checking) return UsernameCheck.Idle
        return try {
            edits.checkUsername(username)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            UsernameCheck.Unknown
        }
    }

    fun captureUri(): Uri = attachments.captureUri()

    fun cameraDenied() = fail(UiError.CameraDenied)

    fun ownKey(): String? = editState.value?.saved?.key

    fun setBirthday(birthday: Birthday?) = editState.update { it?.copy(birthday = birthday) }

    fun setChannel(channel: PersonalChannel?) = editState.update { it?.copy(channel = channel) }

    fun loadChannels() {
        if (choicesState.value != null) return
        work {
            val choices = edits.personalChannelChoices()
            avatarStore.load(choices.map { it.avatarKey })
            choicesState.value = choices
        }
    }

    fun save() {
        val start = editState.value ?: return
        if (!start.canSave) return
        work {
            if (start.nameChanged || start.aboutChanged) {
                adopt(edits.updateProfile(
                    firstName = start.firstName.trim().takeIf { start.nameChanged },
                    lastName = start.lastName.trim().takeIf { start.nameChanged },
                    about = start.about.trim().takeIf { start.aboutChanged },
                ))
            }
            if (start.usernameChanged) {
                try {
                    adopt(edits.updateUsername(start.username))
                } catch (e: RpcException) {
                    val refused = when (e.errorMessage) {
                        "USERNAME_INVALID" -> UsernameCheck.Invalid(null)
                        "USERNAME_OCCUPIED" -> UsernameCheck.Taken
                        "USERNAME_PURCHASE_AVAILABLE" -> UsernameCheck.ForSale
                        else -> throw e
                    }
                    editState.update { it?.copy(usernameCheck = refused) }
                    return@work
                }
            }
            if (start.birthdayChanged) adopt(edits.setBirthday(start.birthday))
            if (start.channelChanged) adopt(edits.setPersonalChannel(start.channel?.key))
            savedEvents.tryEmit(Unit)
        }
    }

    fun toggleUsername(username: OwnUsername) {
        if (username.editable) return
        changeUsernames({ list -> list.map { if (it.username == username.username) it.copy(active = !username.active) else it } }) {
            edits.toggleUsername(username.username, !username.active)
        }
    }

    fun moveUsername(username: OwnUsername, up: Boolean) {
        val list = editState.value?.saved?.usernameList ?: return
        val active = list.filter { it.active }
        val from = active.indexOfFirst { it.username == username.username }
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in active.indices) return
        val moved = active.toMutableList().apply { add(to, removeAt(from)) }
        val order = moved.iterator()
        changeUsernames({ all -> all.map { if (it.active) order.next() else it } }) { edits.reorderUsernames(moved.map { it.username }) }
    }

    private fun changeUsernames(change: (List<OwnUsername>) -> List<OwnUsername>, call: suspend () -> ProfileDetails) {
        val state = editState.value ?: return
        work {
            editState.update { it?.copy(saved = it.saved.copy(usernameList = change(it.saved.usernameList))) }
            try {
                adopt(call())
            } catch (e: Exception) {
                editState.update { it?.copy(saved = state.saved) }
                runCatching { adopt(profiles.profile(null)) }
                throw e
            }
        }
    }

    private companion object {
        const val USERNAME_DEBOUNCE_MILLIS = 400L
    }
}
