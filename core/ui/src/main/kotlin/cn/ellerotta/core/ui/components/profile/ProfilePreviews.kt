package cn.ellerotta.core.ui.components.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.AppPrivacy
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.GiftPage
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.PrivacyKey
import cn.ellerotta.core.model.ProfileEdit
import cn.ellerotta.core.model.UsernameCheck
import cn.ellerotta.core.model.OwnUsernameProblem
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.PrivacyState
import cn.ellerotta.core.model.ProfileGifts
import cn.ellerotta.core.model.SavedGift
import cn.ellerotta.core.model.StoryRing
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme
import cn.ellerotta.core.ui.components.common.previewPhoto
import cn.ellerotta.core.ui.components.profile.ProfilePreviewData as Data

@MioPreviews
@Composable
internal fun OwnProfileScreenPreview() {
    PreviewTheme {
        PeerProfileScreen(
            profile = Data.ownProfile,
            title = Data.ownProfile.name,
            avatars = Data.avatars,
            busy = false,
            onBack = {},
            onMessage = {},
            onMute = {},
            onUnmute = {},
            gifts = Data.gifts,
            giftCallbacks = Data.ownGiftCallbacks,
            onOpenPhoto = {},
            storyRing = StoryRing.Unread,
            onOpenStories = {},
            self = true,
            onOrderSongs = {},
            now = Data.NOW_MILLIS,
        )
    }
}

@MioPreviews
@Composable
internal fun OwnProfileLoadingPreview() {
    PreviewTheme {
        PeerProfileScreen(
            profile = null,
            title = "",
            avatars = Data.avatars,
            busy = true,
            onBack = {},
            onMessage = {},
            onMute = {},
            onUnmute = {},
            placeholder = DialogItem("self", Data.me.name, avatarKey = Data.me.avatarKey, peerId = Data.me.id),
            self = true,
        )
    }
}

@MioPreviews
@Composable
internal fun UserProfileScreenPreview() {
    PreviewTheme {
        PeerProfileScreen(
            profile = Data.userProfile,
            title = Data.userProfile.name,
            avatars = Data.avatars,
            busy = false,
            onBack = {},
            onMessage = {},
            onMute = {},
            onUnmute = {},
            gifts = ProfileGifts(page = Data.giftPage),
            giftCallbacks = Data.giftCallbacks,
            onOpenPhoto = {},
            onSearch = {},
            musicPlaying = true,
            onOpenChannel = {},
            onAddContact = { _, _, _ -> },
            onDeleteContact = {},
            storyRing = StoryRing.CloseFriends,
            onOpenStories = {},
            now = Data.NOW_MILLIS,
        )
    }
}

@MioPreviews
@Composable
internal fun GroupProfileScreenPreview() {
    PreviewTheme {
        PeerProfileScreen(
            profile = Data.groupProfile,
            title = Data.groupProfile.name,
            avatars = Data.avatars,
            busy = false,
            onBack = {},
            onMessage = {},
            onMute = {},
            onUnmute = {},
            onLeave = {},
            onOpenInviteLinks = {},
            onOpenMembers = {},
            onSearch = {},
            now = Data.NOW_MILLIS,
        )
    }
}

@MioPreviews
@Composable
internal fun ChannelProfileScreenPreview() {
    PreviewTheme {
        PeerProfileScreen(
            profile = Data.channelProfile,
            title = Data.channelProfile.name,
            avatars = Data.avatars,
            busy = true,
            onBack = {},
            onMessage = {},
            onMute = {},
            onUnmute = {},
            onLeave = {},
            onOpenMembers = {},
            now = Data.NOW_MILLIS,
        )
    }
}

@MioPreviews
@Composable
internal fun ProfileHeaderPreview() {
    PreviewTheme {
        ProfileHeader(
            name = Data.userProfile.name,
            subtitle = presenceText(Data.userProfile.presence, Data.NOW_MILLIS),
            avatar = Data.avatars["photo_maria"],
            seed = Data.userProfile.id,
            colorIndex = Data.userProfile.colorIndex,
            verified = true,
            premium = false,
            collapse = 0f,
            onAvatarClick = {},
        )
    }
}

@MioPreviews
@Composable
internal fun ProfileHeaderGiftsPreview() {
    PreviewTheme {
        ProfileHeader(
            name = Data.ownProfile.name,
            subtitle = presenceText(Presence.Online, Data.NOW_MILLIS),
            avatar = Data.avatars["photo_me"],
            seed = Data.ownProfile.id,
            colorIndex = Data.ownProfile.colorIndex,
            verified = false,
            premium = true,
            collapse = 0f,
            decoration = profileDecoration(Data.ownProfile, emptyMap(), Data.gifts, emptyMap()),
            onGiftClick = {},
            storyRing = StoryRing.Unread,
            onOpenStories = {},
        )
    }
}

@MioPreviews
@Composable
internal fun ProfileHeaderNoPhotoPreview() {
    PreviewTheme {
        ProfileHeader(
            name = Data.groupProfile.name,
            subtitle = presenceText(Data.groupProfile.presence, Data.NOW_MILLIS),
            avatar = null,
            seed = Data.groupProfile.id,
            colorIndex = Data.groupProfile.colorIndex,
            verified = false,
            premium = false,
            collapse = 0f,
        )
    }
}

@MioPreviews
@Composable
internal fun ProfileActionRowPreview() {
    PreviewTheme {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileActionRow(muted = false, canMute = true, link = "https://t.me/marialopez", onMessage = {}, onMute = {}, onUnmute = {}, onSearch = {}, contact = true)
            ProfileActionRow(muted = true, canMute = true, link = null, onMessage = {}, onMute = {}, onUnmute = {}, onSearch = null)
        }
    }
}

@MioPreviews
@Composable
internal fun ProfileFieldsCardPreview() {
    PreviewTheme {
        ProfileCard(Modifier.padding(vertical = 16.dp)) {
            ProfileField(stringResource(R.string.profile_phone), "+351 912 345 678", onLongClick = {})
            ProfileFieldDivider()
            ProfileField(stringResource(R.string.profile_username), "@marialopez", supporting = stringResource(R.string.profile_username_also, "@mlopez"), onLongClick = {})
            ProfileFieldDivider()
            ProfileField(stringResource(R.string.profile_bio), Data.userProfile.about.orEmpty())
        }
    }
}

@MioPreviews
@Composable
internal fun PersonalChannelSectionPreview() {
    PreviewTheme {
        PersonalChannelSection(Data.personalChannel, Data.avatars, Data.NOW_MILLIS, onClick = {}, modifier = Modifier.padding(vertical = 16.dp))
    }
}

@MioPreviews
@Composable
internal fun ProfileMusicCardPreview() {
    PreviewTheme {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileMusicCard(Data.song, Data.song.thumb, playing = true, pattern = null, accent = profileAccent(2), onClick = {})
            ProfileMusicCard(Data.song, null, playing = false, pattern = null, accent = profileAccent(5), onClick = {})
        }
    }
}

@MioPreviews
@Composable
internal fun InfoCardPreview() {
    PreviewTheme {
        InfoCard(Modifier.padding(vertical = 16.dp), title = stringResource(R.string.profile_account)) {
            InfoRow(R.drawable.ic_smartphone, "+44 7700 900123", stringResource(R.string.profile_phone), onClick = {})
            InfoDivider()
            InfoRow(R.drawable.ic_cake, birthdayText(Data.ownProfile.birthday!!, LocalConfiguration.current.locales[0]), stringResource(R.string.profile_birthday), onClick = {})
            InfoDivider(PlainInset)
            InfoRow(null, Data.ownProfile.id.toString(), stringResource(R.string.profile_id_label))
        }
    }
}

@MioPreviews
@Composable
internal fun GiftsPagePreview() {
    PreviewTheme {
        GiftsPage(Data.gifts, emptyMap(), Data.ownGiftCallbacks, rememberLazyListState(), PaddingValues(top = 4.dp, bottom = 16.dp), onShowCatalogue = {}) {}
    }
}

@MioPreviews
@Composable
internal fun GiftsCollectionPagePreview() {
    val bears = GiftPage(3, listOf(Data.plainGift, Data.plainGift.copy(key = "g6"), Data.plainGift.copy(key = "g7")), null)
    PreviewTheme {
        GiftsPage(Data.gifts.copy(collection = 7, collectionPages = mapOf(7 to bears)), emptyMap(), Data.giftCallbacks, rememberLazyListState(), PaddingValues(top = 4.dp, bottom = 16.dp), onShowCatalogue = {}) {}
    }
}

@MioPreviews
@Composable
internal fun GiftTilesPreview() {
    PreviewTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Data.plainGift, Data.uniqueGift, Data.uniqueGift2).forEach { GiftTilePreviewItem(it) }
        }
    }
}

@MioPreviews
@Composable
internal fun GiftTilesLimitedHiddenPreview() {
    PreviewTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Data.limitedGift, Data.hiddenGift, Data.uniqueGift.copy(key = "g6", saved = false)).forEach { GiftTilePreviewItem(it) }
        }
    }
}

@Composable
private fun GiftTilePreviewItem(gift: SavedGift) {
    val loadSticker: (MediaItem) -> Unit = {}
    GiftTile(gift, emptyMap(), loadSticker, 96.dp, Modifier.width(96.dp)) {}
}

@MioPreviews
@Composable
internal fun GiftSheetContentPreview() {
    PreviewTheme {
        GiftSheetContent(Data.plainGift, emptyMap(), onLoadSticker = {}, onToggleSaved = {})
    }
}

@MioPreviews
@Composable
internal fun UniqueGiftSheetContentPreview() {
    PreviewTheme {
        GiftSheetContent(Data.uniqueGift, emptyMap(), onLoadSticker = {}, onToggleSaved = null)
    }
}

@MioPreviews
@Composable
internal fun GiftCatalogueContentPreview() {
    PreviewTheme {
        GiftCatalogueContent(Data.catalogue, emptyMap(), onLoadSticker = {})
    }
}

@MioPreviews
@Composable
internal fun SongOrderContentPreview() {
    PreviewTheme {
        SongOrderContent(Data.songs, emptyMap(), onMove = { _, _ -> })
    }
}

@MioPreviews
@Composable
internal fun WallpapersScreenPreview() {
    PreviewTheme {
        WallpapersScreen(Data.wallpapers, busy = false, onBack = {}, onPick = {})
    }
}

@MioPreviews
@Composable
internal fun PrivacyScreenPreview() {
    PreviewTheme {
        PrivacyScreen(
            state = Data.privacy,
            app = Data.appPrivacy,
            busy = false,
            onBack = {},
            onApp = {},
            onPrivacy = { _, _ -> },
            onGlobal = {},
            onAccountTtl = {},
        )
    }
}

@MioPreviews
@Composable
internal fun PrivacyLoadingPreview() {
    PreviewTheme {
        PrivacyScreen(
            state = PrivacyState(),
            app = AppPrivacy(),
            busy = true,
            onBack = {},
            onApp = {},
            onPrivacy = { _, _ -> },
            onGlobal = {},
            onAccountTtl = {},
        )
    }
}

@MioPreviews
@Composable
internal fun PrivacyRuleDialogPreview() {
    PreviewTheme {
        val setting = Data.privacy.settings.first { it.key == PrivacyKey.LastSeen }
        ChoiceDialog(
            title = stringResource(titleOf(setting.key)),
            hint = stringResource(R.string.privacy_exceptions_kept),
            choices = setting.key.choices,
            selected = setting.base,
            label = { stringResource(baseText(it)) },
            tag = { it.name },
            onChoose = {},
            onDismiss = {},
        )
    }
}

@MioPreviews
@Composable
internal fun SessionsScreenPreview() {
    PreviewTheme {
        SessionsScreen(Data.sessions, busy = false, onBack = {}, onTerminate = {}, onTerminateOthers = {}, now = Data.NOW_MILLIS)
    }
}

@MioPreviews
@Composable
internal fun SessionsLoadingPreview() {
    PreviewTheme {
        SessionsScreen(null, busy = true, onBack = {}, onTerminate = {}, onTerminateOthers = {})
    }
}

@MioPreviews
@Composable
internal fun PresenceTextPreview() {
    PreviewTheme {
        val now = Data.NOW
        val presences = listOf(
            Presence.Online,
            Presence.Offline(now - 20),
            Presence.Offline(now - 25 * 60),
            Presence.Offline(now - 3 * 3600),
            Presence.Offline(now - 30 * 3600),
            Presence.Offline(now - 9 * 86_400),
            Presence.Recently,
            Presence.LastWeek,
            Presence.LastMonth,
            Presence.LongAgo,
            Presence.Bot,
            Presence.Members(count = 2412, online = 87, channel = false),
            Presence.Members(count = 15_300, online = null, channel = true),
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            presences.forEach { Text(presenceText(it, Data.NOW_MILLIS)) }
        }
    }
}

@MioPreviews
@Composable
internal fun EditProfileScreenPreview() {
    PreviewTheme {
        EditProfileScreen(ProfileEdit(Data.editedProfile, username = "alexmorgan2", usernameCheck = UsernameCheck.Free, firstName = "Alexander"), Data.avatars, Data.channelChoices, busy = false, onBack = {}, actions = EditProfileActions())
    }
}

@MioPreviews
@Composable
internal fun EditProfileInvalidPreview() {
    PreviewTheme {
        EditProfileScreen(
            ProfileEdit(Data.editedProfile, firstName = "", username = "ab", usernameCheck = UsernameCheck.Invalid(OwnUsernameProblem.TooShort), about = "Designer, tea drinker, slow runner and a collector of old film cameras", birthday = null, channel = null),
            Data.avatars, null, busy = false, onBack = {}, actions = EditProfileActions(),
        )
    }
}

@MioPreviews
@Composable
internal fun AvatarCropScreenPreview() {
    PreviewTheme {
        AvatarCropScreen(previewPhoto(width = 640, height = 400, start = 0xFF3FD8D8.toInt(), end = 0xFFE86A9A.toInt()), unreadable = false, busy = false, onBack = {}, onApply = {})
    }
}

@MioPreviews
@Composable
internal fun AvatarCropUnreadablePreview() {
    PreviewTheme {
        AvatarCropScreen(null, unreadable = true, busy = false, onBack = {}, onApply = {})
    }
}
