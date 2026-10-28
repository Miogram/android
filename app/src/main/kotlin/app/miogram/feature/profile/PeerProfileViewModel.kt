package app.miogram.feature.profile

import app.miogram.data.AvatarStore
import app.miogram.data.DialogsStore
import androidx.lifecycle.viewModelScope
import app.miogram.data.telegram.ChannelsRepository
import app.miogram.data.telegram.ContactsRepository
import app.miogram.data.telegram.DialogsRepository
import app.miogram.data.telegram.GiftsRepository
import app.miogram.data.telegram.MediaRepository
import app.miogram.data.telegram.MessagesRepository
import app.miogram.data.telegram.SharedMediaRepository
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.ProfileRepository
import app.miogram.data.telegram.SafetyRepository
import app.miogram.data.telegram.StickersRepository
import app.miogram.data.telegram.StoriesRepository
import app.miogram.feature.chat.ChatCustomEmoji
import app.miogram.data.telegram.UpdatesRepository
import app.miogram.feature.common.ReportFlow
import app.miogram.feature.common.WorkViewModel
import app.miogram.music.MusicPlayer
import app.miogram.push.PushNotifier
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.MusicTrack
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.SharedMedia
import cn.ellerotta.core.model.SharedTab
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.MuteDuration
import cn.ellerotta.core.model.cover
import cn.ellerotta.core.model.ProfileDetails
import cn.ellerotta.core.model.ProfileGifts
import cn.ellerotta.core.model.ReportState
import cn.ellerotta.core.model.ReportTarget
import cn.ellerotta.core.model.SavedGift
import cn.ellerotta.core.model.StoryRing
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PeerProfileViewModel.Factory::class)
class PeerProfileViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String,
    private val profiles: ProfileRepository,
    private val dialogs: DialogsRepository,
    private val channels: ChannelsRepository,
    private val avatarStore: AvatarStore,
    private val dialogsStore: DialogsStore,
    private val notifier: PushNotifier,
    private val contacts: ContactsRepository,
    giftsRepository: GiftsRepository,
    mediaRepository: MediaRepository,
    stickersRepository: StickersRepository,
    private val player: MusicPlayer,
    sharedRepository: SharedMediaRepository,
    private val messages: MessagesRepository,
    private val stories: StoriesRepository,
    private val safety: SafetyRepository,
    updates: UpdatesRepository,
) : WorkViewModel() {
    val isSelf: Boolean = profiles.isSelf(dialogKey)

    private val profileKey: String? = if (isSelf) null else dialogKey

    private val profileState = MutableStateFlow<ProfileDetails?>(null)
    private val giftsState = ProfileGiftsState(viewModelScope, profileKey, giftsRepository, mediaRepository, ::fail, { shown, block -> refreshWith(shown, block) }) { workWith(it) }
    private val sharedState = ProfileSharedState(viewModelScope, sharedRepository, mediaRepository, ::fail)
    private val emoji = ChatCustomEmoji(viewModelScope, stickersRepository, giftsState::loadFile)

    val gifts: StateFlow<ProfileGifts> = giftsState.gifts
    val stickers: StateFlow<Map<String, ByteArray>> = giftsState.stickers

    val customEmoji: StateFlow<Map<Long, MediaItem>> = emoji.documents

    val profile: StateFlow<ProfileDetails?> = profileState.asStateFlow()
    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars

    val storyRing: StateFlow<StoryRing> = if (isSelf) {
        stories.self.map { it?.ring ?: StoryRing.None }.stateIn(viewModelScope, SharingStarted.Eagerly, stories.self.value?.ring ?: StoryRing.None)
    } else {
        stories.rings.map { it[dialogKey] ?: StoryRing.None }.stateIn(viewModelScope, SharingStarted.Eagerly, stories.rings.value[dialogKey] ?: StoryRing.None)
    }

    fun storiesKey(): String? = if (isSelf) stories.self.value?.key else dialogKey

    val placeholder: DialogItem? = if (isSelf) {
        profiles.self.value?.let { DialogItem(dialogKey, it.name, avatarKey = it.avatarKey, peerId = it.id) }
    } else {
        dialogsStore.state.value.let { state -> (state.dialogs + state.archived).firstOrNull { it.key == dialogKey } }
    }

    private val songs = ProfileMusic(profiles, player)

    val music: StateFlow<MusicState?> = songs.music

    val musicOpened: SharedFlow<Unit> = songs.opened

    val shared: StateFlow<SharedMedia> = sharedState.shared
    val sharedDownloads: StateFlow<Map<String, DownloadState>> = sharedState.downloads

    val sharedCovers: StateFlow<Map<String, ByteArray>> = sharedState.covers

    val forwardTargets: StateFlow<List<DialogItem>> = dialogsStore.decorated
        .map { state -> state.dialogs.sortedWith(compareByDescending<DialogItem> { it.pinned }.thenByDescending { it.date }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val orderState = MutableStateFlow<List<MediaItem>?>(null)

    val songOrder: StateFlow<List<MediaItem>?> = orderState.asStateFlow()

    init {
        if (isSelf) viewModelScope.launch { profiles.own.filterNotNull().collect { if (it != profileState.value) show(it) } }
        viewModelScope.launch {
            profiles.stored(profileKey)?.let { if (profileState.value == null) show(it) }
            refresh(profileState.value != null) { load() }
        }
        giftsState.load()
        viewModelScope.launch {
            updates.events().collect { event ->
                if (event is ChatEvent.Blocked && event.key == dialogKey) profileState.update { it?.copy(blocked = event.blocked) }
            }
        }
    }

    fun loadSticker(sticker: MediaItem) = giftsState.loadSticker(sticker)

    fun moreGifts() = giftsState.more()

    fun chooseGiftCollection(id: Int?) = giftsState.chooseCollection(id)

    fun setGiftSaved(gift: SavedGift, saved: Boolean) = giftsState.setSaved(gift, saved)

    fun openGiftCatalogue() = giftsState.openCatalogue()

    fun refresh() {
        if (profileState.value != null) refresh(true) { load() }
    }

    private suspend fun load() = show(profiles.profile(profileKey))

    private fun show(details: ProfileDetails) {
        profileState.value = details
        emoji.requestIds(listOfNotNull(details.emojiStatusId, details.patternEmojiId))
        avatarStore.load(listOf(details.avatarKey, details.channel?.avatarKey))
        avatarStore.loadBig(listOf(details.avatarKey))
        details.music?.cover()?.let(giftsState::loadFile)
        sharedState.start(details.key, details.kind == PeerKind.User && !details.isSelf)
    }

    fun chooseSharedTab(tab: SharedTab) = sharedState.choose(tab)

    fun moreShared(tab: SharedTab) = sharedState.more(tab)

    fun downloadShared(media: MediaItem) = sharedState.download(media)

    fun playShared(message: MessageItem) {
        val key = profileState.value?.key ?: return
        val media = message.media ?: return
        val track = when (media.kind) {
            MediaKind.Music -> MusicTrack(key, message.id, media)
            MediaKind.Voice -> messages.voiceTrackOf(key, message)
            else -> null
        } ?: return
        player.play(track)
    }

    fun openShared(media: MediaItem, ready: (File, String) -> Unit) = work {
        ready(sharedState.file(media), media.mimeType ?: "application/octet-stream")
    }

    fun deleteShared(id: Int, revoke: Boolean) = work {
        val key = profileState.value?.key ?: return@work
        messages.delete(key, listOf(id), revoke)
        sharedState.removed(setOf(id))
    }

    fun playMusic() = work { profileState.value?.let { songs.play(it) } }

    fun orderSongs() = work {
        val key = profileState.value?.key ?: return@work
        orderState.value = profiles.music(key)
    }

    fun closeSongOrder() {
        orderState.value = null
    }

    fun moveSong(from: Int, to: Int) {
        val before = orderState.value ?: return
        if (from !in before.indices || to !in before.indices || from == to) return
        val after = before.toMutableList().apply { add(to, removeAt(from)) }
        orderState.value = after
        work {
            try {
                profiles.moveMusic(after[to].key, after.getOrNull(to - 1)?.key)
            } catch (e: Exception) {
                if (orderState.value == after) orderState.value = before
                throw e
            }
            load()
        }
    }

    fun mute(duration: MuteDuration) = setMuted(true, duration.seconds)

    fun unmute() = setMuted(false, null)

    private fun setMuted(muted: Boolean, forSeconds: Int?) = work {
        if (profileState.value?.muted == null) return@work
        profileState.value = profileState.value?.copy(muted = muted)
        dialogs.setMuted(dialogKey, muted, forSeconds)
        dialogsStore.change(dialogKey) { it.copy(muted = muted, followsDefault = false) }
        load()
    }

    fun addContact(firstName: String, lastName: String, sharePhone: Boolean) {
        work {
            contacts.add(dialogKey, firstName, lastName, sharePhone)
            profileState.value = profileState.value?.copy(contact = true, name = listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }.joinToString(" "))
        }
    }

    fun deleteContact() {
        work {
            contacts.delete(listOf(dialogKey))
            profileState.value = profileState.value?.copy(contact = false)
        }
    }

    fun block() {
        work {
            safety.block(dialogKey)
            profileState.value = profileState.value?.copy(blocked = true)
        }
    }

    fun unblock() {
        work {
            safety.unblock(dialogKey)
            profileState.value = profileState.value?.copy(blocked = false)
        }
    }

    private val reporting = ReportFlow(safety) { workWith(it) }

    val report: StateFlow<ReportState?> = reporting.ui

    fun reportPeer() = reporting.open(ReportTarget.Peer(dialogKey))

    fun reportChoose(id: String) = reporting.choose(id)

    fun reportComment(text: String) = reporting.comment(text)

    fun closeReport() = reporting.close()

    private val leftFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val left: SharedFlow<Unit> = leftFlow.asSharedFlow()

    fun leave() {
        work {
            channels.leave(dialogKey)
            dialogsStore.forget(dialogKey)
            notifier.forget(dialogKey)
            leftFlow.tryEmit(Unit)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String): PeerProfileViewModel
    }
}
