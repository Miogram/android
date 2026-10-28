package cn.ellerotta.core.ui.components.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.UsernameAvailability
import cn.ellerotta.core.model.UsernameProblem
import cn.ellerotta.core.model.UsernameStatus
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme
import cn.ellerotta.core.ui.components.groups.GroupsPreviewData as Data

@MioPreviews
@Composable
internal fun MembersScreenPreview() {
    PreviewTheme {
        MembersScreen(
            title = Data.TITLE,
            members = Data.members,
            avatars = Data.avatars,
            query = "",
            more = false,
            loaded = true,
            busy = false,
            canBan = true,
            basicGroup = false,
            onBack = {},
            onQuery = {},
            onMore = {},
            onOpenProfile = {},
            onRemove = { _, _ -> },
        )
    }
}

@MioPreviews
@Composable
internal fun MembersEmptySearchPreview() {
    PreviewTheme {
        MembersScreen(
            title = Data.TITLE,
            members = emptyList(),
            avatars = Data.avatars,
            query = "zebra",
            more = false,
            loaded = true,
            busy = false,
            canBan = false,
            basicGroup = true,
            onBack = {},
            onQuery = {},
            onMore = {},
            onOpenProfile = {},
            onRemove = { _, _ -> },
        )
    }
}

@MioPreviews
@Composable
internal fun InviteLinksScreenPreview() {
    PreviewTheme {
        InviteLinksScreen(
            links = Data.inviteLinks,
            requestsPending = 3,
            busy = false,
            onBack = {},
            onCreate = {},
            onRevoke = {},
            onDelete = {},
            onOpenRequests = {},
        )
    }
}

@MioPreviews
@Composable
internal fun InviteLinksLoadingPreview() {
    PreviewTheme {
        InviteLinksScreen(links = null, requestsPending = 0, busy = true, onBack = {}, onCreate = {}, onRevoke = {}, onDelete = {}, onOpenRequests = {})
    }
}

@MioPreviews
@Composable
internal fun CreateInviteDialogPreview() {
    PreviewTheme {
        CreateInviteDialog(onCreate = {}, onDismiss = {})
    }
}

@MioPreviews
@Composable
internal fun JoinRequestsScreenPreview() {
    PreviewTheme {
        JoinRequestsScreen(
            requests = Data.joinRequests,
            avatars = Data.avatars,
            more = false,
            loaded = true,
            busy = false,
            onBack = {},
            onDecide = { _, _ -> },
            onMore = {},
            onOpenProfile = {},
        )
    }
}

@MioPreviews
@Composable
internal fun JoinRequestsEmptyPreview() {
    PreviewTheme {
        JoinRequestsScreen(requests = emptyList(), avatars = emptyMap(), more = false, loaded = true, busy = false, onBack = {}, onDecide = { _, _ -> }, onMore = {}, onOpenProfile = {})
    }
}

@MioPreviews
@Composable
internal fun TopicsScreenPreview() {
    PreviewTheme {
        TopicsScreen(
            title = Data.TITLE,
            topics = Data.topics,
            emoji = emptyMap(),
            files = emptyMap(),
            more = false,
            loaded = true,
            busy = false,
            canCreate = true,
            onBack = {},
            onOpenTopic = {},
            onMore = {},
            onCreate = { _, _ -> },
            onOpenProfile = {},
        )
    }
}

@MioPreviews
@Composable
internal fun TopicsEmptyPreview() {
    PreviewTheme {
        TopicsScreen(
            title = Data.TITLE,
            topics = emptyList(),
            emoji = emptyMap(),
            files = emptyMap(),
            more = false,
            loaded = true,
            busy = false,
            canCreate = false,
            onBack = {},
            onOpenTopic = {},
            onMore = {},
            onCreate = { _, _ -> },
            onOpenProfile = {},
        )
    }
}

@MioPreviews
@Composable
internal fun TopicIconsPreview() {
    PreviewTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Data.topics.forEach { TopicIcon(it, icon = null, iconFile = null, size = 40) }
        }
    }
}

@MioPreviews
@Composable
internal fun CreateTopicDialogPreview() {
    PreviewTheme {
        CreateTopicDialog(onDismiss = {}, onCreate = { _, _ -> })
    }
}

@MioPreviews
@Composable
internal fun MemberPickerPreview() {
    PreviewTheme {
        MemberPickerScreen(
            title = stringResource(R.string.new_group_title),
            state = MemberPickerState(Data.pickable, Data.pickable.take(2), loaded = true),
            avatars = Data.avatars,
            confirmLabel = stringResource(R.string.common_next),
            busy = false,
            onBack = {},
            onQuery = {},
            onToggle = {},
            onConfirm = {},
        )
    }
}

@MioPreviews
@Composable
internal fun MemberPickerSkipPreview() {
    PreviewTheme {
        MemberPickerScreen(
            title = stringResource(R.string.members_add_channel),
            state = MemberPickerState(Data.pickable, loaded = true),
            avatars = Data.avatars,
            confirmLabel = stringResource(R.string.members_add_confirm),
            busy = false,
            onBack = {},
            onQuery = {},
            onToggle = {},
            onConfirm = {},
            skipLabel = stringResource(R.string.picker_skip),
        )
    }
}

@MioPreviews
@Composable
internal fun NewGroupScreenPreview() {
    PreviewTheme {
        NewGroupScreen(
            name = Data.TITLE,
            photo = Data.avatars["photo_alex"],
            members = Data.pickable.take(3),
            avatars = Data.avatars,
            busy = false,
            onBack = {},
            onName = {},
            onPickPhoto = {},
            onRemovePhoto = {},
            onCreate = {},
        )
    }
}

@MioPreviews
@Composable
internal fun NewChannelScreenPreview() {
    PreviewTheme {
        NewChannelScreen(name = "", about = "", photo = null, busy = false, onBack = {}, onName = {}, onAbout = {}, onPickPhoto = {}, onRemovePhoto = {}, onCreate = {})
    }
}

@MioPreviews
@Composable
internal fun ChannelTypePrivatePreview() {
    PreviewTheme {
        ChannelTypeScreen(
            public = false, username = "", status = UsernameStatus.Idle, link = "https://t.me/+AbCdEf123456", busy = false,
            onBack = {}, onPublic = {}, onUsername = {}, onDone = {},
        )
    }
}

@MioPreviews
@Composable
internal fun ChannelTypePublicPreview() {
    PreviewTheme {
        ChannelTypeScreen(
            public = true, username = "filmclub", status = UsernameStatus.Checked("filmclub", UsernameAvailability.Available), link = null, busy = false,
            onBack = {}, onPublic = {}, onUsername = {}, onDone = {},
        )
    }
}

@MioPreviews
@Composable
internal fun ChannelTypeTakenPreview() {
    PreviewTheme {
        ChannelTypeScreen(
            public = true, username = "kotlin", status = UsernameStatus.Checked("kotlin", UsernameAvailability.Taken), link = null, busy = false,
            onBack = {}, onPublic = {}, onUsername = {}, onDone = {},
        )
    }
}

@MioPreviews
@Composable
internal fun ChannelTypeProblemPreview() {
    PreviewTheme {
        ChannelTypeScreen(
            public = true, username = "1ab", status = UsernameStatus.Problem(UsernameProblem.BadStart), link = null, busy = false,
            onBack = {}, onPublic = {}, onUsername = {}, onDone = {},
        )
    }
}

@MioPreviews
@Composable
internal fun MissingInviteesPreview() {
    PreviewTheme { MissingInviteesDialog(Data.missing, onSend = {}, onSkip = {}) }
}

@MioPreviews
@Composable
internal fun EditChatScreenPreview() {
    PreviewTheme {
        EditChatScreen(
            channel = false, name = Data.TITLE, about = "Analog cameras, developing at home and walks with a roll of film",
            photo = Data.avatars["photo_maria"], changed = true, busy = false,
            onBack = {}, onName = {}, onAbout = {}, onPickPhoto = {}, onRemovePhoto = {}, onSave = {},
        )
    }
}
