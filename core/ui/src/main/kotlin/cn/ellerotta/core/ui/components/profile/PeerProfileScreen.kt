package cn.ellerotta.core.ui.components.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import cn.ellerotta.core.ui.components.common.topBarHeight
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatRemoval
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.MuteDuration
import cn.ellerotta.core.model.cover
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.ProfileDetails
import cn.ellerotta.core.model.PersonalChannel
import cn.ellerotta.core.model.ProfileGifts
import cn.ellerotta.core.model.ReportState
import cn.ellerotta.core.model.SavedGift
import cn.ellerotta.core.model.SharedMedia
import cn.ellerotta.core.model.StoryRing
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.leaveLabel
import cn.ellerotta.core.ui.components.chats.ChatRemovalDialog
import cn.ellerotta.core.ui.components.chats.label
import cn.ellerotta.core.ui.components.common.ConfirmDialog
import cn.ellerotta.core.ui.components.common.TopBar
import cn.ellerotta.core.ui.components.common.TopBarPill
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.components.contacts.AddProfileContactDialog
import cn.ellerotta.core.ui.components.safety.ReportActions
import cn.ellerotta.core.ui.components.safety.ReportSheet
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut

@Composable
fun PeerProfileScreen(
    profile: ProfileDetails?,
    title: String,
    avatars: Map<String, ByteArray>,
    busy: Boolean,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onMute: (MuteDuration) -> Unit,
    onUnmute: () -> Unit,
    gifts: ProfileGifts = ProfileGifts(),
    stickers: Map<String, ByteArray> = emptyMap(),
    giftCallbacks: GiftCallbacks? = null,
    onLeave: (() -> Unit)? = null,
    onOpenInviteLinks: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onOpenMembers: (() -> Unit)? = null,
    customEmoji: Map<Long, MediaItem> = emptyMap(),
    onOpenPhoto: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onPlayMusic: () -> Unit = {},
    musicPlaying: Boolean = false,
    onOpenChannel: ((PersonalChannel) -> Unit)? = null,
    placeholder: DialogItem? = null,
    onAddContact: ((firstName: String, lastName: String, sharePhone: Boolean) -> Unit)? = null,
    onDeleteContact: (() -> Unit)? = null,
    storyRing: StoryRing = StoryRing.None,
    onOpenStories: ((String) -> Unit)? = null,
    onOpenApp: (() -> Unit)? = null,
    self: Boolean = false,
    songOrder: List<MediaItem>? = null,
    onOrderSongs: (() -> Unit)? = null,
    onMoveSong: (from: Int, to: Int) -> Unit = { _, _ -> },
    onCloseSongOrder: () -> Unit = {},
    onRemove: ((ChatRemoval, Boolean) -> Unit)? = null,
    onBlock: (() -> Unit)? = null,
    onUnblock: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    report: ReportState? = null,
    reportActions: ReportActions? = null,
    onEditProfile: (() -> Unit)? = null,
    shared: SharedMedia = SharedMedia(),
    sharedActions: SharedMediaActions? = null,
    sharedDownloads: Map<String, DownloadState> = emptyMap(),
    sharedCovers: Map<String, ByteArray> = emptyMap(),
    playerMusic: MusicState? = null,
    forwardTargets: List<DialogItem> = emptyList(),
    now: Long = rememberProfileNow(),
) {
    if (songOrder != null) SongOrderSheet(songOrder, stickers, onMoveSong, onCloseSongOrder)
    var openGift by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogueOpen by rememberSaveable { mutableStateOf(false) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf<ChatRemoval?>(null) }
    var addingContact by rememberSaveable { mutableStateOf(false) }
    var deletingContact by rememberSaveable { mutableStateOf(false) }
    var blocking by rememberSaveable { mutableStateOf(false) }
    val menus = remember { SharedMenuState() }
    val tabs = profileTabs(gifts.takeIf { giftCallbacks != null }, giftCallbacks?.onOpenCatalogue != null, if (profile != null && sharedActions != null) shared.tabs else emptyList())
    val tabKeys = tabs.map { it.key }
    val currentTabs by rememberUpdatedState(tabs)
    val currentShared by rememberUpdatedState(sharedActions)
    val pager = rememberPagerState { tabs.size }
    val pageLists = remember { mutableMapOf<String, LazyListState>() }
    val scope = rememberCoroutineScope()
    val tabSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    var chosenTab by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(tabKeys) {
        val index = chosenTab?.let(tabKeys::indexOf)?.takeIf { it >= 0 } ?: 0
        if (index != pager.currentPage && index < tabKeys.size) pager.scrollToPage(index)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { page ->
            val tab = currentTabs.getOrNull(page) ?: return@collect
            chosenTab = tab.key
            if (tab is ProfileTab.Shared) currentShared?.onTab?.invoke(tab.tab)
        }
    }
    if (report != null && reportActions != null) ReportSheet(report, reportActions)
    GiftSheets(gifts, stickers, giftCallbacks, openGift, catalogueOpen, onClose = { openGift = null }, onCloseCatalogue = { catalogueOpen = false })
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val list = rememberLazyListState()
    val scroll = remember(list, pager) { ProfileScroll(list) { currentTabs.getOrNull(pager.currentPage)?.let { pageLists[it.key] } } }
    val pagePadding = PaddingValues(top = 4.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
    val collapse = rememberHeaderCollapse(list)
    var confetti by rememberSaveable { mutableIntStateOf(0) }
    val birthdayToday = profile?.birthday?.let(::isBirthdayToday) == true
    LaunchedEffect(birthdayToday) { if (birthdayToday) confetti++ }
    val link = profile?.usernames?.firstOrNull()?.let { "https://t.me/$it" }
    val decoration = profileDecoration(profile, customEmoji, gifts, stickers)
    val name = profile?.name ?: placeholder?.title?.takeIf { it.isNotBlank() } ?: title
    val removable = placeholder?.takeIf { onRemove != null && !self }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val pagesHeight = maxHeight - topBarHeight()
        LazyColumn(Modifier.fillMaxSize().nestedScroll(scroll.profile).testTag("peerProfile"), state = list) {
            item(key = "header") {
                ProfileHeader(
                    name = name,
                    subtitle = profile?.let { presenceText(it.presence, now) }.orEmpty(),
                    avatar = avatars.photoOf(profile?.avatarKey ?: placeholder?.avatarKey),
                    seed = profile?.id ?: placeholder?.peerId ?: 0,
                    colorIndex = profile?.colorIndex ?: 5,
                    verified = profile?.verified == true,
                    premium = profile?.premium == true,
                    collapse = collapse,
                    decoration = decoration,
                    onLoadSticker = { giftCallbacks?.onLoadSticker?.invoke(it) },
                    onAvatarClick = onOpenPhoto?.takeIf { profile?.avatarKey != null },
                    onGiftClick = giftCallbacks?.let { { gift: SavedGift -> openGift = gift.key } },
                    storyRing = storyRing,
                    onOpenStories = onOpenStories?.let { show -> (profile?.key ?: placeholder?.key)?.let { key -> { show(key) } } },
                )
            }
            if (!self) {
                item(key = "actions") {
                    ProfileActionRow(
                        muted = profile?.muted == true,
                        canMute = profile != null && !profile.isSelf,
                        link = link,
                        onMessage = onMessage,
                        onMute = onMute,
                        onUnmute = onUnmute,
                        onSearch = onSearch,
                        modifier = Modifier.padding(top = 24.dp),
                        contact = profile?.takeIf { it.kind == PeerKind.User && !it.isSelf && onAddContact != null }?.contact,
                        onAddContact = { addingContact = true },
                        onDeleteContact = { deletingContact = true },
                        blocked = profile?.takeIf { (it.kind == PeerKind.User || it.kind == PeerKind.Bot) && !it.isSelf && onBlock != null && onUnblock != null }?.blocked,
                        onBlock = { blocking = true },
                        onUnblock = { onUnblock?.invoke() },
                        onReport = onReport?.takeIf { profile != null && !profile.isSelf },
                    )
                }
            }
            if (profile?.hasMainApp == true && onOpenApp != null) {
                item(key = "openApp") {
                    FilledTonalButton(
                        onClick = onOpenApp,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp).heightIn(min = 48.dp).testTag("openMainApp"),
                    ) { Text(stringResource(R.string.profile_open_app)) }
                }
            }
            profile?.music?.let { music ->
                item(key = "music") {
                    val pattern = decoration.pattern?.let { rememberPatternImage(it, stickers[it.key]) }
                    ProfileMusicCard(
                        music,
                        music.cover()?.let { stickers[it.key] } ?: music.thumb,
                        musicPlaying,
                        pattern,
                        profileAccent(profile.colorIndex),
                        onPlayMusic,
                        Modifier.padding(top = 24.dp).then(if (decoration.pattern != null) Modifier.patternState(pattern) else Modifier),
                        onLongClick = onOrderSongs?.takeIf { self },
                    )
                }
            }
            profile?.channel?.let { channel ->
                item(key = "channel") {
                    PersonalChannelSection(channel, avatars, now, onOpenChannel?.let { open -> { open(channel) } }, Modifier.padding(top = if (profile.music == null) 12.dp else 0.dp))
                }
            }
            item(key = "gap") { Spacer(Modifier.height(16.dp)) }
            item(key = "busy") {
                AnimatedVisibility(busy, enter = MaterialTheme.motionScheme.expandFadeIn(), exit = MaterialTheme.motionScheme.shrinkFadeOut()) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("profileLoading"))
                }
            }
            if (profile != null) {
                item(key = "info") {
                    val fields = buildList<@Composable () -> Unit> {
                        profile.phone?.let { phone ->
                            add {
                                val formatted = formatPhone(context, phone)
                                ProfileField(stringResource(R.string.profile_phone), formatted, onLongClick = { copyToClipboard(context, "phone", formatted) })
                            }
                        }
                        profile.usernames.firstOrNull()?.let { username ->
                            val also = profile.usernames.drop(1).joinToString(", ") { "@$it" }
                            add {
                                ProfileField(
                                    stringResource(R.string.profile_username), "@$username",
                                    supporting = if (also.isEmpty()) null else stringResource(R.string.profile_username_also, also),
                                    onLongClick = { copyToClipboard(context, "username", "@$username") },
                                    trailing = { Icon(painterResource(R.drawable.ic_qr_code), null, tint = MaterialTheme.colorScheme.primary) },
                                )
                            }
                        }
                        profile.about?.let { about ->
                            add {
                                ProfileField(
                                    stringResource(if (profile.kind == PeerKind.User || profile.kind == PeerKind.Bot || profile.kind == PeerKind.Saved) R.string.profile_bio else R.string.profile_description), about,
                                    onLongClick = { copyToClipboard(context, "about", about) },
                                    modifier = Modifier.testTag("about"),
                                )
                            }
                        }
                        profile.birthday?.let { birthday ->
                            add {
                                ProfileField(
                                    stringResource(if (birthdayToday) R.string.profile_birthday_today else R.string.profile_birthday),
                                    birthdayText(birthday, locale),
                                    onClick = { confetti++ },
                                )
                            }
                        }
                    }
                    if (fields.isNotEmpty()) {
                        ProfileCard {
                            fields.forEachIndexed { index, field ->
                                if (index > 0) ProfileFieldDivider()
                                field()
                            }
                        }
                    }
                    ProfileCard(Modifier.padding(top = if (fields.isEmpty()) 0.dp else 16.dp)) {
                        ProfileField(stringResource(R.string.profile_id_label), profile.id.toString(), onLongClick = { copyToClipboard(context, "id", profile.id.toString()) })
                        profile.dcId?.let { dc ->
                            ProfileFieldDivider()
                            ProfileField(stringResource(R.string.profile_datacenter), stringResource(R.string.profile_dc, dc), modifier = Modifier.testTag("datacenter"))
                        }
                    }
                    if (onOpenMembers != null) {
                        val count = (profile.presence as? Presence.Members)?.count
                        val channel = profile.kind == PeerKind.Channel
                        InfoCard(Modifier.padding(top = 16.dp)) {
                            InfoRow(
                                null,
                                stringResource(if (channel) R.string.members_title_channel else R.string.members_title),
                                count?.let { pluralStringResource(if (channel) R.plurals.presence_subscribers else R.plurals.presence_members, it, it) },
                                modifier = Modifier.testTag("members"),
                                onClick = onOpenMembers,
                            )
                        }
                    }
                    if (onEdit != null) {
                        InfoCard(Modifier.padding(top = 16.dp)) {
                            InfoRow(
                                null,
                                stringResource(if (profile.kind == PeerKind.Channel) R.string.edit_channel_title else R.string.edit_group_title),
                                null,
                                modifier = Modifier.testTag("editChatInfo"),
                                onClick = onEdit,
                            )
                        }
                    }
                    if (onOpenInviteLinks != null) {
                        InfoCard(Modifier.padding(top = 16.dp)) {
                            InfoRow(
                                null,
                                stringResource(R.string.invite_links_title),
                                if (profile.requestsPending > 0) pluralStringResource(R.plurals.invite_waiting, profile.requestsPending, profile.requestsPending) else null,
                                modifier = Modifier.testTag("inviteLinks"),
                                onClick = onOpenInviteLinks,
                            )
                        }
                    }
                    if (onLeave != null) {
                        InfoCard(Modifier.padding(top = 16.dp)) {
                            InfoRow(
                                null, stringResource(leaveLabel(profile.kind)), null,
                                modifier = Modifier.testTag("leaveChat"),
                                valueColor = MaterialTheme.colorScheme.error,
                                onClick = { leaving = true },
                            )
                        }
                    }
                    if (removable != null) {
                        val lines = removable.removals.filter { !(onLeave != null && it == ChatRemoval.Delete && (removable.kind == PeerKind.Group || removable.kind == PeerKind.Channel)) }
                        if (lines.isNotEmpty()) {
                            InfoCard(Modifier.padding(top = 16.dp)) {
                                lines.forEach { removal ->
                                    InfoRow(
                                        null, stringResource(removal.label(removable)), null,
                                        modifier = Modifier.testTag("removal_${removal.name}"),
                                        valueColor = MaterialTheme.colorScheme.error,
                                        onClick = { removing = removal },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(if (tabs.isNotEmpty()) Modifier.height(16.dp) else Modifier.height(24.dp).navigationBarsPadding())
                }
            }
            if (tabs.isNotEmpty()) {
                item(key = "pages") {
                    ProfilePages(tabs, pager, pagesHeight, scroll, onTabClick = { index -> scope.launch { pager.animateScrollToPage(index, animationSpec = tabSpec) } }) { tab ->
                        val state = pageLists.getOrPut(tab.key) { LazyListState() }
                        when (tab) {
                            ProfileTab.Gifts -> if (giftCallbacks != null) {
                                GiftsPage(gifts, stickers, giftCallbacks, state, pagePadding, onShowCatalogue = { catalogueOpen = true }) { openGift = it.key }
                            }
                            is ProfileTab.Shared -> if (profile != null && sharedActions != null) {
                                SharedPage(tab.tab, shared, state, pagePadding, profile.key, sharedActions, sharedDownloads, sharedCovers, playerMusic, avatars, onMenu = { menus.menu = it })
                            }
                        }
                    }
                }
            }
        }
        Confetti(confetti, Modifier.fillMaxSize())
        TopBar(name, rememberTopBarState(list), onBack) {
            if (self && onEditProfile != null) TopBarPill(R.drawable.ic_edit, stringResource(R.string.profile_edit), onEditProfile, Modifier.testTag("profileEdit"))
        }
    }
    if (profile != null && sharedActions != null) {
        val member = profile.kind
        SharedMessageMenus(
            menus, sharedActions, forwardTargets, avatars,
            canDelete = { message -> message.out || member == PeerKind.User || member == PeerKind.Bot || member == PeerKind.Saved || profile.canEdit },
            revokeLabel = { message ->
                when {
                    member == PeerKind.User || member == PeerKind.Bot -> stringResource(R.string.chat_delete_for, profile.name)
                    member == PeerKind.Group -> if (message.out) stringResource(R.string.chat_delete_everyone) else null
                    else -> null
                }
            },
        )
    }
    removing?.let { removal ->
        if (removable != null) {
            ChatRemovalDialog(
                removable,
                removal,
                onConfirm = { revoke ->
                    removing = null
                    onRemove?.invoke(removal, revoke)
                },
                onDismiss = { removing = null },
            )
        }
    }
    if (leaving && onLeave != null) {
        ConfirmDialog(
            title = stringResource(leaveLabel(profile?.kind)),
            text = stringResource(if (profile?.kind == PeerKind.Channel) R.string.chat_leave_channel_text else R.string.chat_leave_group_text, profile?.name ?: title),
            confirm = stringResource(R.string.chat_leave),
            tag = "confirmLeave",
            onConfirm = {
                leaving = false
                onLeave()
            },
            onDismiss = { leaving = false },
        )
    }
    val contactName = profile?.name ?: title
    if (addingContact && onAddContact != null) {
        AddProfileContactDialog(
            name = contactName,
            onAdd = { first, last, share ->
                addingContact = false
                onAddContact(first, last, share)
            },
            onDismiss = { addingContact = false },
        )
    }
    if (blocking && onBlock != null) {
        ConfirmDialog(
            title = stringResource(R.string.block_confirm_title, contactName),
            text = stringResource(R.string.block_confirm_text, contactName),
            confirm = stringResource(R.string.block_action),
            tag = "confirmBlock",
            onConfirm = {
                blocking = false
                onBlock()
            },
            onDismiss = { blocking = false },
        )
    }
    if (deletingContact && onDeleteContact != null) {
        ConfirmDialog(
            title = stringResource(R.string.contacts_delete),
            text = stringResource(R.string.contacts_delete_text, contactName),
            confirm = stringResource(R.string.chat_delete),
            tag = "confirmDeleteContact",
            onConfirm = {
                deletingContact = false
                onDeleteContact()
            },
            onDismiss = { deletingContact = false },
        )
    }
}
