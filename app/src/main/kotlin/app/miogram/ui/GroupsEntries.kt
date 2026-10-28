package app.miogram.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import app.miogram.feature.groups.AddMembersViewModel
import app.miogram.feature.groups.ChannelTypeViewModel
import app.miogram.feature.groups.EditChatViewModel
import app.miogram.feature.groups.InviteLinksViewModel
import app.miogram.feature.groups.JoinRequestsViewModel
import app.miogram.feature.groups.MembersViewModel
import app.miogram.feature.groups.NewChannelViewModel
import app.miogram.feature.groups.NewGroupMembersViewModel
import app.miogram.feature.groups.NewGroupViewModel
import app.miogram.feature.groups.TopicsViewModel
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import androidx.compose.ui.res.stringResource
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.model.CreatedChat
import cn.ellerotta.core.ui.components.groups.ChannelTypeScreen
import cn.ellerotta.core.ui.components.groups.EditChatScreen
import cn.ellerotta.core.ui.components.groups.InviteLinksScreen
import cn.ellerotta.core.ui.components.groups.JoinRequestsScreen
import cn.ellerotta.core.ui.components.groups.MemberPickerScreen
import cn.ellerotta.core.ui.components.groups.MembersScreen
import cn.ellerotta.core.ui.components.groups.MissingInviteesDialog
import cn.ellerotta.core.ui.components.groups.NewChannelScreen
import cn.ellerotta.core.ui.components.groups.NewGroupScreen
import cn.ellerotta.core.ui.components.groups.TopicsScreen

internal fun EntryProviderScope<Route>.groupsEntries(navigator: Navigator, back: () -> Unit) {
    entry<Route.InviteLinks> { route ->
        val model = hiltViewModel<InviteLinksViewModel, InviteLinksViewModel.Factory>(key = "invites_" + route.dialogKey) { it.create(route.dialogKey) }
        val links by model.links.collectAsStateWithLifecycle()
        val pending by model.pending.collectAsStateWithLifecycle()
        LaunchedEffect(model) { model.refreshPending() }
        Work(model) { busy ->
            InviteLinksScreen(
                links = links,
                requestsPending = pending,
                busy = busy,
                onBack = back,
                onCreate = model::create,
                onRevoke = model::revoke,
                onDelete = model::delete,
                onOpenRequests = { navigator.go(Route.JoinRequests(route.dialogKey, route.title)) },
            )
        }
    }
    entry<Route.JoinRequests> { route ->
        val model = hiltViewModel<JoinRequestsViewModel, JoinRequestsViewModel.Factory>(key = "requests_" + route.dialogKey) { it.create(route.dialogKey) }
        val state by model.requests.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        Work(model) { busy ->
            JoinRequestsScreen(
                requests = state.requests,
                avatars = avatars,
                more = state.more,
                loaded = state.loaded,
                busy = busy,
                onBack = back,
                onDecide = model::decide,
                onMore = model::more,
                onOpenProfile = { navigator.go(Route.PeerProfile(it.userKey, it.name)) },
            )
        }
    }
    entry<Route.Members> { route ->
        val model = hiltViewModel<MembersViewModel, MembersViewModel.Factory>(key = "members_" + route.dialogKey) { it.create(route.dialogKey) }
        val state by model.members.collectAsStateWithLifecycle()
        val info by model.info.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        LaunchedEffect(model) { model.reload() }
        Work(model) { busy ->
            MembersScreen(
                title = stringResource(if (route.channel) R.string.members_title_channel else R.string.members_title),
                members = state.members,
                avatars = avatars,
                query = state.query,
                more = state.more,
                loaded = state.loaded,
                busy = busy,
                canBan = info?.canBan == true,
                basicGroup = info?.basicGroup == true,
                onBack = back,
                onQuery = model::search,
                onMore = model::more,
                onOpenProfile = { navigator.go(Route.PeerProfile(it.userKey, it.name)) },
                onRemove = model::remove,
                onAdd = if (info?.canInvite == true) ({ navigator.go(Route.AddMembers(route.dialogKey, route.title, route.channel)) }) else null,
            )
        }
    }
    entry<Route.NewGroupMembers> {
        val model = hiltViewModel<NewGroupMembersViewModel>()
        val state by model.picker.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        Work(model) { busy ->
            MemberPickerScreen(
                title = stringResource(R.string.new_group_title),
                state = state,
                avatars = avatars,
                confirmLabel = stringResource(R.string.common_next),
                busy = busy,
                onBack = back,
                onQuery = model::search,
                onToggle = model::toggle,
                onConfirm = { navigator.go(Route.NewGroup(model.picked)) },
            )
        }
    }
    entry<Route.NewGroup> { route ->
        val model = hiltViewModel<NewGroupViewModel, NewGroupViewModel.Factory>(key = "newgroup_" + route.members.joinToString(",")) { it.create(route.members) }
        val name by model.name.collectAsStateWithLifecycle()
        val photo by model.photo.collectAsStateWithLifecycle()
        val people by model.people.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        val prompt by model.prompt.collectAsStateWithLifecycle()
        val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(model::pickPhoto) }
        LaunchedEffect(model) { model.done.collect { openCreated(navigator, it) } }
        Work(model) { busy ->
            NewGroupScreen(
                name = name,
                photo = photo,
                members = people,
                avatars = avatars,
                busy = busy,
                onBack = back,
                onName = model::setName,
                onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemovePhoto = model::removePhoto,
                onCreate = model::create,
            )
            prompt?.let { MissingInviteesDialog(it.missing, onSend = model::sendLinks, onSkip = model::skipLinks) }
        }
    }
    entry<Route.NewChannel> {
        val model = hiltViewModel<NewChannelViewModel>()
        val name by model.name.collectAsStateWithLifecycle()
        val about by model.about.collectAsStateWithLifecycle()
        val photo by model.photo.collectAsStateWithLifecycle()
        val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(model::pickPhoto) }
        LaunchedEffect(model) {
            model.done.collect {
                navigator.back()
                navigator.go(Route.ChannelType(it.key, it.title))
            }
        }
        Work(model) { busy ->
            NewChannelScreen(
                name = name,
                about = about,
                photo = photo,
                busy = busy,
                onBack = back,
                onName = model::setName,
                onAbout = model::setAbout,
                onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemovePhoto = model::removePhoto,
                onCreate = model::create,
            )
        }
    }
    entry<Route.ChannelType> { route ->
        val model = hiltViewModel<ChannelTypeViewModel, ChannelTypeViewModel.Factory>(key = "channeltype_" + route.dialogKey) { it.create(route.dialogKey) }
        val public by model.public.collectAsStateWithLifecycle()
        val username by model.username.collectAsStateWithLifecycle()
        val status by model.status.collectAsStateWithLifecycle()
        val link by model.link.collectAsStateWithLifecycle()
        LaunchedEffect(model) {
            model.done.collect {
                navigator.back()
                navigator.go(Route.AddMembers(route.dialogKey, route.title, channel = true, openAfter = true))
            }
        }
        Work(model) { busy ->
            ChannelTypeScreen(
                public = public,
                username = username,
                status = status,
                link = link,
                busy = busy,
                onBack = { openCreated(navigator, CreatedChat(route.dialogKey, route.title)) },
                onPublic = model::setPublic,
                onUsername = model::setUsername,
                onDone = model::save,
            )
        }
    }
    entry<Route.AddMembers> { route ->
        val model = hiltViewModel<AddMembersViewModel, AddMembersViewModel.Factory>(key = "addmembers_" + route.dialogKey) { it.create(route.dialogKey, route.title) }
        val state by model.picker.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        val prompt by model.prompt.collectAsStateWithLifecycle()
        val finish = { if (route.openAfter) openCreated(navigator, CreatedChat(route.dialogKey, route.title)) else back() }
        LaunchedEffect(model) { model.done.collect { finish() } }
        Work(model) { busy ->
            MemberPickerScreen(
                title = stringResource(if (route.channel) R.string.members_add_channel else R.string.members_add),
                state = state,
                avatars = avatars,
                confirmLabel = stringResource(R.string.members_add_confirm),
                busy = busy,
                onBack = finish,
                onQuery = model::search,
                onToggle = model::toggle,
                onConfirm = model::add,
                skipLabel = if (route.openAfter) stringResource(R.string.picker_skip) else null,
                onSkip = finish,
            )
            prompt?.let { MissingInviteesDialog(it.missing, onSend = model::sendLinks, onSkip = model::skipLinks) }
        }
    }
    entry<Route.EditChat> { route ->
        val model = hiltViewModel<EditChatViewModel, EditChatViewModel.Factory>(key = "editchat_" + route.dialogKey) { it.create(route.dialogKey) }
        val state by model.edit.collectAsStateWithLifecycle()
        val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(model::pickPhoto) }
        LaunchedEffect(model) { model.saved.collect { back() } }
        Work(model) { busy ->
            EditChatScreen(
                channel = state.channel,
                name = state.name,
                about = state.about,
                photo = state.photo,
                changed = state.changed,
                busy = busy,
                onBack = back,
                onName = model::setName,
                onAbout = model::setAbout,
                onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemovePhoto = model::removePhoto,
                onSave = model::save,
            )
        }
    }
    entry<Route.Topics> { route ->
        val model = hiltViewModel<TopicsViewModel, TopicsViewModel.Factory>(key = "topics_" + route.dialogKey) { it.create(route.dialogKey) }
        val state by model.topics.collectAsStateWithLifecycle()
        val info by model.info.collectAsStateWithLifecycle()
        val emoji by model.emoji.collectAsStateWithLifecycle()
        val files by model.files.collectAsStateWithLifecycle()
        LaunchedEffect(model) { model.refresh() }
        LaunchedEffect(model) { model.created.collect { navigator.go(Route.Chat(route.dialogKey, it.title, topicId = it.id)) } }
        Work(model) { busy ->
            TopicsScreen(
                title = route.title,
                topics = state.topics,
                emoji = emoji,
                files = files,
                more = state.more,
                loaded = state.loaded,
                busy = busy,
                canCreate = info?.canManageTopics == true,
                onBack = back,
                onOpenTopic = { navigator.go(Route.Chat(route.dialogKey, it.title, topicId = it.id)) },
                onMore = model::more,
                onCreate = model::create,
                onOpenProfile = { navigator.go(Route.PeerProfile(route.dialogKey, route.title)) },
            )
        }
    }
}

private fun openCreated(navigator: Navigator, chat: CreatedChat) {
    navigator.backTo(Route.Chats)
    navigator.go(Route.Chat(chat.key, chat.title))
}
