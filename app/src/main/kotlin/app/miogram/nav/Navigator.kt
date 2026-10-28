package app.miogram.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import cn.ellerotta.core.model.AttachMenuBot
import cn.ellerotta.core.model.AttachPeerType
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import javax.inject.Inject
import javax.inject.Singleton

sealed interface Route : NavKey {
    data object Splash : Route
    data object Welcome : Route
    data object Phone : Route
    data object QrLogin : Route
    data object Code : Route
    data object Password : Route
    data object SignUp : Route
    data object Chats : Route
    data object Archive : Route

    data class Chat(
        val dialogKey: String,
        val title: String,
        val messageId: Int? = null,
        val draft: String? = null,
        val startParameter: String? = null,
        val forward: Forwarding? = null,
        val topicId: Int? = null,
        val threadId: Int? = null,
        val comments: Boolean = false,
        val search: Boolean = false,
        val attach: AttachLaunch? = null,
        val proximity: Boolean = false,
    ) : Route

    data class Scheduled(val dialogKey: String) : Route

    data class NewPoll(val dialogKey: String, val kind: PeerKind, val replyTo: Int? = null, val topicId: Int? = null) : Route

    data class SendLocation(val dialogKey: String, val title: String, val replyTo: Int? = null, val topicId: Int? = null, val alertName: String? = null) : Route

    data class Place(val dialogKey: String, val messageId: Int, val place: GeoPlace, val title: String = "") : Route

    data class ShareContact(val dialogKey: String, val replyTo: Int? = null, val topicId: Int? = null) : Route

    data class Topics(val dialogKey: String, val title: String) : Route

    data class ShareTarget(val text: String) : Route

    data class AttachTarget(val attach: AttachLaunch, val kinds: Set<AttachPeerType>) : Route

    data class Media(val dialogKey: String, val messageId: Int, val articleIndex: Int? = null, val profilePhotos: Boolean = false, val gifs: Boolean = false) : Route

    data class Stories(val peerKey: String) : Route

    data class StoryComposer(val editId: Int = 0) : Route

    data class PeerProfile(val dialogKey: String, val title: String) : Route

    data object EditProfile : Route

    data class AvatarCrop(val uri: String, val temporary: Boolean = false) : Route

    data class InviteLinks(val dialogKey: String, val title: String) : Route

    data class JoinRequests(val dialogKey: String, val title: String) : Route

    data class Members(val dialogKey: String, val title: String, val channel: Boolean = false) : Route

    data object NewGroupMembers : Route

    data class NewGroup(val members: List<String>) : Route

    data object NewChannel : Route

    data class ChannelType(val dialogKey: String, val title: String) : Route

    data class AddMembers(val dialogKey: String, val title: String, val channel: Boolean = false, val openAfter: Boolean = false) : Route

    data class EditChat(val dialogKey: String, val title: String) : Route
    data object Sessions : Route

    data object Folders : Route

    data class FolderEdit(val folderId: Int) : Route
    data object Privacy : Route

    data object TwoStep : Route

    data object Notifications : Route

    data object DataStorage : Route

    data object Blocked : Route

    data object BlockPicker : Route

    data class Wallpapers(val dialogKey: String? = null) : Route
    data object Log : Route
}

data class Forwarding(
    val fromKey: String,
    val ids: List<Int>,
    val dropAuthor: Boolean = false,
    val dropCaptions: Boolean = false,
    val messages: List<MessageItem> = emptyList(),
    val fromTitle: String = "",
)

data class AttachLaunch(val bot: AttachMenuBot, val startParameter: String? = null)

@Singleton
class Navigator @Inject constructor() {
    val backStack: SnapshotStateList<Route> = mutableStateListOf(Route.Splash)

    val current: Route get() = backStack.last()

    fun go(route: Route) {
        backStack.add(route)
    }

    fun replaceAll(route: Route) {
        backStack.add(route)
        backStack.removeRange(0, backStack.size - 1)
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun backTo(route: Route) {
        while (current != route && back()) Unit
    }
}
