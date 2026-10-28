package app.miogram

import cn.ellerotta.ktproto.api.functions.channels.GetChannels
import cn.ellerotta.ktproto.api.functions.channels.JoinChannel
import cn.ellerotta.ktproto.api.functions.contacts.ResolvePhone
import cn.ellerotta.ktproto.api.functions.contacts.ResolveUsername
import cn.ellerotta.ktproto.api.functions.messages.CheckChatInvite
import cn.ellerotta.ktproto.api.functions.messages.ImportChatInvite
import cn.ellerotta.ktproto.api.functions.messages.StartBot
import cn.ellerotta.ktproto.api.types.ChatInvite
import cn.ellerotta.ktproto.api.types.ChatInviteAlready
import cn.ellerotta.ktproto.api.types.InputChannel
import cn.ellerotta.ktproto.api.types.InputPeerChannel
import cn.ellerotta.ktproto.api.types.InputPeerChat
import cn.ellerotta.ktproto.api.types.InputPeerEmpty
import cn.ellerotta.ktproto.api.types.RichMessage
import cn.ellerotta.ktproto.api.types.TypeMessage
import cn.ellerotta.ktproto.api.types.contacts.ResolvedPeer
import cn.ellerotta.ktproto.api.types.messages.ChatInviteJoinResultOk
import cn.ellerotta.ktproto.api.types.messages.ChatInviteJoinResultWebView
import cn.ellerotta.ktproto.api.types.messages.Chats
import cn.ellerotta.ktproto.api.functions.account.GetAuthorizations
import cn.ellerotta.ktproto.api.functions.account.SaveMusic
import cn.ellerotta.ktproto.api.types.InputUserSelf
import cn.ellerotta.ktproto.api.functions.account.GetAccountTTL
import cn.ellerotta.ktproto.api.functions.account.GetGlobalPrivacySettings
import cn.ellerotta.ktproto.api.functions.account.GetPrivacy
import cn.ellerotta.ktproto.api.functions.account.SetAccountTTL
import cn.ellerotta.ktproto.api.functions.account.SetGlobalPrivacySettings
import cn.ellerotta.ktproto.api.functions.account.SetPrivacy
import cn.ellerotta.ktproto.api.types.AccountDaysTTL
import cn.ellerotta.ktproto.api.types.GlobalPrivacySettings
import cn.ellerotta.ktproto.api.types.InputPrivacyKeyPhoneNumber
import cn.ellerotta.ktproto.api.types.InputPrivacyKeyStatusTimestamp
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowAll
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowContacts
import cn.ellerotta.ktproto.api.types.InputPrivacyValueDisallowAll
import cn.ellerotta.ktproto.api.types.InputPrivacyValueDisallowUsers
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowUsers
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowAll
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowContacts
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowUsers
import cn.ellerotta.ktproto.api.types.PrivacyValueDisallowAll
import cn.ellerotta.ktproto.api.types.PrivacyValueDisallowUsers
import cn.ellerotta.ktproto.api.types.TypeDialogFilter
import cn.ellerotta.ktproto.api.types.TypeInputPrivacyRule
import cn.ellerotta.ktproto.api.types.TypePrivacyRule
import cn.ellerotta.ktproto.api.types.account.PrivacyRules
import cn.ellerotta.ktproto.api.functions.account.ResetAuthorization
import cn.ellerotta.ktproto.api.functions.account.UpdateNotifySettings
import cn.ellerotta.ktproto.api.functions.account.GetNotifySettings
import cn.ellerotta.ktproto.api.functions.account.GetNotifyExceptions
import cn.ellerotta.ktproto.api.functions.account.ResetNotifySettings
import cn.ellerotta.ktproto.api.types.NotifyPeer
import cn.ellerotta.ktproto.api.types.TypeInputNotifyPeer
import cn.ellerotta.ktproto.api.types.UpdateNotifySettings as UpdateNotifySettingsUpdate
import cn.ellerotta.ktproto.api.types.InputNotifyBroadcasts
import cn.ellerotta.ktproto.api.types.InputNotifyChats
import cn.ellerotta.ktproto.api.types.InputNotifyUsers
import cn.ellerotta.ktproto.api.functions.auth.ResendCode
import cn.ellerotta.ktproto.api.functions.auth.ResetAuthorizations
import cn.ellerotta.ktproto.api.functions.contacts.Search as ContactsSearch
import cn.ellerotta.ktproto.api.functions.folders.EditPeerFolders
import cn.ellerotta.ktproto.api.functions.messages.DeleteMessages
import cn.ellerotta.ktproto.api.functions.messages.ReadMessageContents
import cn.ellerotta.ktproto.api.functions.messages.EditMessage
import cn.ellerotta.ktproto.api.functions.messages.GetRecentLocations
import cn.ellerotta.ktproto.api.functions.messages.GetDialogFilters
import cn.ellerotta.ktproto.api.functions.messages.GetDialogs
import cn.ellerotta.ktproto.api.functions.messages.GetHistory
import cn.ellerotta.ktproto.api.functions.messages.GetPeerDialogs
import cn.ellerotta.ktproto.api.functions.payments.GetSavedStarGifts
import cn.ellerotta.ktproto.api.functions.payments.GetStarGiftCollections
import cn.ellerotta.ktproto.api.functions.payments.GetStarGifts
import cn.ellerotta.ktproto.api.functions.payments.SaveStarGift
import cn.ellerotta.ktproto.api.types.InputSavedStarGiftUser
import cn.ellerotta.ktproto.api.types.MessageActionChatAddUser
import cn.ellerotta.ktproto.api.types.MessageActionStarGift
import cn.ellerotta.ktproto.api.types.MessageService
import cn.ellerotta.ktproto.api.types.SavedStarGift
import cn.ellerotta.ktproto.api.types.StarGift
import cn.ellerotta.ktproto.api.types.StarGiftAttributeBackdrop
import cn.ellerotta.ktproto.api.types.StarGiftAttributeModel
import cn.ellerotta.ktproto.api.types.StarGiftAttributePattern
import cn.ellerotta.ktproto.api.types.StarGiftAttributeRarity
import cn.ellerotta.ktproto.api.types.StarGiftAttributeRarityRare
import cn.ellerotta.ktproto.api.types.StarGiftCollection
import cn.ellerotta.ktproto.api.types.StarGiftUnique
import cn.ellerotta.ktproto.api.types.payments.SavedStarGifts
import cn.ellerotta.ktproto.api.types.payments.StarGiftCollections
import cn.ellerotta.ktproto.api.types.payments.StarGifts
import cn.ellerotta.ktproto.api.types.payments.StarGiftsNotModified
import cn.ellerotta.ktproto.api.functions.messages.GetAllStickers
import cn.ellerotta.ktproto.api.functions.messages.GetEmojiStickers
import cn.ellerotta.ktproto.api.functions.messages.FaveSticker
import cn.ellerotta.ktproto.api.functions.messages.GetFavedStickers
import cn.ellerotta.ktproto.api.functions.messages.GetRecentStickers
import cn.ellerotta.ktproto.api.functions.messages.GetStickerSet
import cn.ellerotta.ktproto.api.functions.messages.GetStickers
import cn.ellerotta.ktproto.api.functions.messages.GetSavedGifs
import cn.ellerotta.ktproto.api.functions.messages.ReorderStickerSets
import cn.ellerotta.ktproto.api.functions.messages.SaveGif
import cn.ellerotta.ktproto.api.functions.messages.SearchStickers
import cn.ellerotta.ktproto.api.functions.messages.GetEmojiGroups
import cn.ellerotta.ktproto.api.functions.messages.GetEmojiStickerGroups
import cn.ellerotta.ktproto.api.functions.messages.GetFeaturedEmojiStickers
import cn.ellerotta.ktproto.api.functions.messages.GetFeaturedStickers
import cn.ellerotta.ktproto.api.functions.messages.ReadFeaturedStickers
import cn.ellerotta.ktproto.api.types.EmojiGroup
import cn.ellerotta.ktproto.api.types.EmojiGroupGreeting
import cn.ellerotta.ktproto.api.types.EmojiGroupPremium
import cn.ellerotta.ktproto.api.types.StickerSetCovered
import cn.ellerotta.ktproto.api.types.StickerSetFullCovered
import cn.ellerotta.ktproto.api.types.StickerSetMultiCovered
import cn.ellerotta.ktproto.api.types.StickerSetNoCovered
import cn.ellerotta.ktproto.api.types.messages.EmojiGroups
import cn.ellerotta.ktproto.api.types.messages.EmojiGroupsNotModified
import cn.ellerotta.ktproto.api.types.messages.FeaturedStickers
import cn.ellerotta.ktproto.api.types.messages.FeaturedStickersNotModified
import cn.ellerotta.ktproto.api.types.DocumentAttributeAnimated
import cn.ellerotta.ktproto.api.types.messages.FoundStickers
import cn.ellerotta.ktproto.api.types.messages.SavedGifs
import cn.ellerotta.ktproto.api.types.messages.SavedGifsNotModified
import cn.ellerotta.ktproto.api.functions.messages.InstallStickerSet
import cn.ellerotta.ktproto.api.functions.messages.UninstallStickerSet
import cn.ellerotta.ktproto.api.types.InputDocument
import cn.ellerotta.ktproto.api.types.InputMediaDocument
import cn.ellerotta.ktproto.api.types.InputMediaContact
import cn.ellerotta.ktproto.api.types.InputStickerSetID
import cn.ellerotta.ktproto.api.types.InputStickerSetShortName
import cn.ellerotta.ktproto.api.types.InputStickerSetThumb
import cn.ellerotta.ktproto.api.types.StickerSet
import cn.ellerotta.ktproto.api.types.messages.AllStickers
import cn.ellerotta.ktproto.api.types.messages.AllStickersNotModified
import cn.ellerotta.ktproto.api.types.messages.FavedStickers
import cn.ellerotta.ktproto.api.types.messages.FavedStickersNotModified
import cn.ellerotta.ktproto.api.types.messages.RecentStickers
import cn.ellerotta.ktproto.api.types.messages.RecentStickersNotModified
import cn.ellerotta.ktproto.api.types.messages.StickerSet as MessagesStickerSet
import cn.ellerotta.ktproto.api.types.messages.StickerSetNotModified as MessagesStickerSetNotModified
import cn.ellerotta.ktproto.api.types.messages.StickerSetInstallResultSuccess
import cn.ellerotta.ktproto.api.types.messages.Stickers
import cn.ellerotta.ktproto.api.functions.messages.GetMessages
import cn.ellerotta.ktproto.api.functions.messages.Search
import cn.ellerotta.ktproto.api.types.DocumentAttributeVideo
import cn.ellerotta.ktproto.api.types.DocumentAttributeAudio
import cn.ellerotta.ktproto.api.types.InputMessageID
import cn.ellerotta.ktproto.api.types.InputMessagesFilterPhotoVideo
import cn.ellerotta.ktproto.api.types.InputMessagesFilterMusic
import cn.ellerotta.ktproto.api.types.InputMessagesFilterVoice
import cn.ellerotta.ktproto.api.types.InputMessagesFilterPinned
import cn.ellerotta.ktproto.api.types.MessageMediaWebPage
import cn.ellerotta.ktproto.api.types.MessageEntityMention
import cn.ellerotta.ktproto.api.types.MessageEntityCustomEmoji
import cn.ellerotta.ktproto.api.types.DocumentAttributeCustomEmoji
import cn.ellerotta.ktproto.api.functions.messages.GetCustomEmojiDocuments
import cn.ellerotta.ktproto.api.functions.messages.ForwardMessages
import cn.ellerotta.ktproto.api.types.InputMessageEntityMentionName
import cn.ellerotta.ktproto.api.types.MessageEntityBankCard
import cn.ellerotta.ktproto.api.types.MessageEntityBlockquote
import cn.ellerotta.ktproto.api.types.MessageEntityBold
import cn.ellerotta.ktproto.api.types.MessageEntityBotCommand
import cn.ellerotta.ktproto.api.types.MessageEntityCashtag
import cn.ellerotta.ktproto.api.types.MessageEntityCode
import cn.ellerotta.ktproto.api.types.MessageEntityEmail
import cn.ellerotta.ktproto.api.types.MessageEntityFormattedDate
import cn.ellerotta.ktproto.api.types.MessageEntityHashtag
import cn.ellerotta.ktproto.api.types.MessageEntityItalic
import cn.ellerotta.ktproto.api.types.MessageEntityMentionName
import cn.ellerotta.ktproto.api.types.MessageEntityPhone
import cn.ellerotta.ktproto.api.types.MessageEntityPre
import cn.ellerotta.ktproto.api.types.MessageEntitySpoiler
import cn.ellerotta.ktproto.api.types.MessageEntityStrike
import cn.ellerotta.ktproto.api.types.MessageEntityUnderline
import cn.ellerotta.ktproto.api.types.TypeMessageEntity
import cn.ellerotta.ktproto.api.types.TypeInputMedia
import cn.ellerotta.ktproto.api.types.MessageEntityTextUrl
import cn.ellerotta.ktproto.api.types.MessageEntityUrl
import cn.ellerotta.ktproto.api.types.WebPage
import cn.ellerotta.ktproto.api.functions.messages.MarkDialogUnread
import cn.ellerotta.ktproto.api.functions.messages.ReadHistory
import cn.ellerotta.ktproto.api.functions.messages.SendMedia
import cn.ellerotta.ktproto.api.functions.messages.SendMessage
import cn.ellerotta.ktproto.api.functions.messages.ToggleDialogPin
import cn.ellerotta.ktproto.api.functions.upload.GetFile
import cn.ellerotta.ktproto.api.functions.users.GetFullUser
import cn.ellerotta.ktproto.api.types.Authorization
import cn.ellerotta.ktproto.api.types.Birthday
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.ChatPhotoEmpty
import cn.ellerotta.ktproto.api.types.Dialog
import cn.ellerotta.ktproto.api.types.DialogFilter
import cn.ellerotta.ktproto.api.types.DialogFilterDefault
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.DocumentAttributeFilename
import cn.ellerotta.ktproto.api.types.DocumentAttributeSticker
import cn.ellerotta.ktproto.api.types.InputDialogPeer
import cn.ellerotta.ktproto.api.types.InputDocumentFileLocation
import cn.ellerotta.ktproto.api.types.InputFile
import cn.ellerotta.ktproto.api.types.InputFileBig
import cn.ellerotta.ktproto.api.types.InputMediaUploadedDocument
import cn.ellerotta.ktproto.api.types.InputMediaUploadedPhoto
import cn.ellerotta.ktproto.api.types.InputNotifyPeer
import cn.ellerotta.ktproto.api.types.InputPeerPhotoFileLocation
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.InputPhotoFileLocation
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import cn.ellerotta.ktproto.api.types.InputReplyToStory
import cn.ellerotta.ktproto.api.types.InputStickerSetEmpty
import cn.ellerotta.ktproto.api.types.InputUser
import cn.ellerotta.ktproto.api.types.InputUserFromMessage
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.api.types.MessageMediaPhoto
import cn.ellerotta.ktproto.api.types.MessageReplyHeader
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.PeerNotifySettings
import cn.ellerotta.ktproto.api.types.PeerSettings
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.Photo
import cn.ellerotta.ktproto.api.types.EmojiStatus
import cn.ellerotta.ktproto.api.types.EmojiStatusCollectible
import cn.ellerotta.ktproto.api.types.PeerColor
import cn.ellerotta.ktproto.api.types.photos.Photos
import cn.ellerotta.ktproto.api.functions.photos.GetUserPhotos
import cn.ellerotta.ktproto.api.functions.users.GetSavedMusic
import cn.ellerotta.ktproto.api.functions.users.GetUsers
import cn.ellerotta.ktproto.api.types.users.SavedMusic
import cn.ellerotta.ktproto.api.types.PhotoPathSize
import cn.ellerotta.ktproto.api.types.PhotoSize
import cn.ellerotta.ktproto.api.types.PhotoStrippedSize
import cn.ellerotta.ktproto.api.types.TextWithEntities
import cn.ellerotta.ktproto.api.types.TypeInputDialogPeer
import cn.ellerotta.ktproto.api.types.UpdateShortSentMessage
import cn.ellerotta.ktproto.api.types.UpdateNewMessage
import cn.ellerotta.ktproto.api.types.UpdateShortMessage
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.UpdatesTooLong
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.UserFull
import cn.ellerotta.ktproto.api.types.UserProfilePhoto
import cn.ellerotta.ktproto.api.types.account.Authorizations
import cn.ellerotta.ktproto.api.types.auth.CodeTypeCall
import cn.ellerotta.ktproto.api.types.auth.SentCode
import cn.ellerotta.ktproto.api.types.auth.SentCodeTypeSms
import cn.ellerotta.ktproto.api.types.contacts.Found
import cn.ellerotta.ktproto.api.types.messages.AffectedMessages
import cn.ellerotta.ktproto.api.types.messages.DialogFilters
import cn.ellerotta.ktproto.api.types.messages.Dialogs
import cn.ellerotta.ktproto.api.types.messages.DialogsSlice
import cn.ellerotta.ktproto.api.types.messages.TypeDialogs
import cn.ellerotta.ktproto.api.types.messages.PeerDialogs
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.api.types.storage.FilePartial
import cn.ellerotta.ktproto.api.types.upload.File
import cn.ellerotta.ktproto.api.types.users.UserFull as UsersUserFull
import cn.ellerotta.ktproto.client.ClientConfig
import cn.ellerotta.ktproto.client.InMemorySessionStorage
import cn.ellerotta.ktproto.client.SessionStorage
import cn.ellerotta.ktproto.client.StoredSession
import cn.ellerotta.ktproto.session.ProtocolLogger
import cn.ellerotta.ktproto.session.SessionConfig
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeCluster
import cn.ellerotta.ktproto.testing.FakeServerKey
import cn.ellerotta.ktproto.tl.TlWriter
import cn.ellerotta.ktproto.api.functions.channels.GetFullChannel
import cn.ellerotta.ktproto.api.types.BotInfo
import cn.ellerotta.ktproto.api.types.BotMenuButton
import cn.ellerotta.ktproto.api.functions.messages.GetAvailableReactions
import cn.ellerotta.ktproto.api.functions.messages.GetRecentReactions
import cn.ellerotta.ktproto.api.functions.messages.GetTopReactions
import cn.ellerotta.ktproto.api.functions.messages.SendReaction
import cn.ellerotta.ktproto.api.functions.messages.GetMessageReactionsList
import cn.ellerotta.ktproto.api.functions.messages.GetUnreadReactions
import cn.ellerotta.ktproto.api.functions.messages.ReadReactions
import cn.ellerotta.ktproto.api.types.messages.AffectedHistory
import cn.ellerotta.ktproto.api.types.messages.MessageReactionsList
import cn.ellerotta.ktproto.api.types.AvailableReaction
import cn.ellerotta.ktproto.api.types.ChannelFull
import cn.ellerotta.ktproto.api.types.ChatReactionsSome
import cn.ellerotta.ktproto.api.types.DocumentEmpty
import cn.ellerotta.ktproto.api.types.MessagePeerReaction
import cn.ellerotta.ktproto.api.types.MessageReactions
import cn.ellerotta.ktproto.api.types.PhotoEmpty
import cn.ellerotta.ktproto.api.types.ReactionCount
import cn.ellerotta.ktproto.api.types.ReactionCustomEmoji
import cn.ellerotta.ktproto.api.types.ReactionEmoji
import cn.ellerotta.ktproto.api.types.ReactionPaid
import cn.ellerotta.ktproto.api.types.TypeReaction
import cn.ellerotta.ktproto.api.types.UpdateMessageReactions
import cn.ellerotta.ktproto.api.types.UpdateShort
import cn.ellerotta.ktproto.api.types.messages.AvailableReactions
import cn.ellerotta.ktproto.api.types.messages.AvailableReactionsNotModified
import cn.ellerotta.ktproto.api.types.messages.Reactions
import cn.ellerotta.ktproto.api.types.messages.ReactionsNotModified
import cn.ellerotta.ktproto.api.types.messages.ChatFull as MessagesChatFull
import cn.ellerotta.ktproto.api.types.MessageFwdHeader
import cn.ellerotta.ktproto.api.functions.messages.SetTyping
import cn.ellerotta.ktproto.api.functions.messages.SaveDraft
import cn.ellerotta.ktproto.api.types.DraftMessage
import cn.ellerotta.ktproto.api.types.DraftMessageEmpty
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.TypePeer
import cn.ellerotta.ktproto.api.types.InputPeerUserFromMessage
import cn.ellerotta.ktproto.api.types.MessageMediaContact
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.TypeMessageMedia
import cn.ellerotta.ktproto.api.types.TypeUpdate
import cn.ellerotta.ktproto.api.types.UpdateMessageID
import cn.ellerotta.ktproto.api.types.UpdateNewChannelMessage
import cn.ellerotta.ktproto.api.types.updates.State
import cn.ellerotta.ktproto.api.types.UpdateDraftMessage
import cn.ellerotta.ktproto.api.types.InputMessagesFilterEmpty
import cn.ellerotta.ktproto.api.types.messages.MessagesSlice
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

class FakeTelegram(password: String? = null) : AutoCloseable {
    val cluster = FakeCluster(dcCount = 2, fileDcId = 2, password = password, passwordHint = password?.let { "starts with ${it.first()}" })
    private val loginPassword = password != null
    val avatarBytes = jpeg(96, 96, 0.8f)
    private val saved = CopyOnWriteArrayList<Message>()

    val incoming = CopyOnWriteArrayList<Message>()

    val getMessagesCalls: MutableList<List<Int>> = CopyOnWriteArrayList()

    private val services = CopyOnWriteArrayList<MessageService>()
    private var nextId = 10

    @Volatile var photoDownloads: Int = 0

    val photoTypes: MutableList<String> = CopyOnWriteArrayList()

    val fileOffsets: MutableList<Long> = CopyOnWriteArrayList()

    @Volatile var lastRevoke: Boolean? = null

    @Volatile var lastUpload: ByteArray? = null

    @Volatile var lastSendMessage: SendMessage? = null

    @Volatile var failSends = 0

    @Volatile var loseSendAnswers = 0

    private val sentRandomIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    @Volatile var sendMessageCalls = 0

    fun savedTexts(text: String): Int = saved.count { it.message == text }

    @Volatile var failMediaSends = 0

    @Volatile var sendMediaCalls = 0

    @Volatile var lastForward: ForwardMessages? = null

    @Volatile var lastSentId: Int = 0

    @Volatile var lastSendReaction: SendReaction? = null

    val unreadReactions: MutableMap<Long, List<Int>> = ConcurrentHashMap()

    val unreadReactionsCalls: MutableList<Int> = CopyOnWriteArrayList()

    val readReactionsCalls: MutableList<Long> = CopyOnWriteArrayList()

    val reactorsCalls: MutableList<Pair<Int, String?>> = CopyOnWriteArrayList()

    val availableReactionsHashes: MutableList<Int> = CopyOnWriteArrayList()

    @Volatile var lastSendMedia: SendMedia? = null

    val contactSends: MutableList<SendMedia> = CopyOnWriteArrayList()

    private val uploaded = ConcurrentHashMap<Long, ByteArray>()
    @Volatile private var sessionUser = 0L

    var selfId = 0L
        get() = field.takeIf { it != 0L } ?: sessionUser

    @Volatile var aliceShowsStatus = false

    @Volatile var aliceIsScam = false

    val alice: User get() = User(
        id = 501, accessHash = 9, firstName = "Alice", lastName = "Example", photo = UserProfilePhoto(photoId = 55, dcId = 2),
        emojiStatus = if (aliceShowsStatus) EmojiStatus(EMOJI_HEART) else null, scam = aliceIsScam,
    )

    @Volatile var aliceWearsGift = false

    val aliceFull: User get() = User(
        id = alice.id, accessHash = alice.accessHash, firstName = "Alice", lastName = "Example", photo = alice.photo,
        emojiStatus = if (aliceWearsGift) {
            EmojiStatusCollectible(
                collectibleId = 6_201, documentId = EMOJI_STAR, title = "Lucky Star", slug = "LuckyStar-1", patternDocumentId = EMOJI_HEART,
                centerColor = 0x2E7D32, edgeColor = 0x0D47A1, patternColor = 0x81C784, textColor = 0xFFFFFF,
            )
        } else {
            EmojiStatus(EMOJI_HEART)
        },
        profileColor = PeerColor(color = 5, backgroundEmojiId = EMOJI_STAR),
    )

    fun profileSong(n: Int) = Document(
        id = MUSIC_ID + 100 + n, accessHash = 1, fileReference = byteArrayOf(3), date = NOW, mimeType = "audio/mpeg", size = 4_000_000, dcId = 2,
        thumbs = listOf(PhotoSize("m", 320, 320, 96)),
        attributes = listOf(DocumentAttributeAudio(duration = 185, title = "Profile Song $n", performer = "Band"), DocumentAttributeFilename("profile$n.mp3")),
    )
    private fun messageSong(id: Long): Document? =
        saved.asSequence().mapNotNull { (it.media as? MessageMediaDocument)?.document as? Document }.firstOrNull { it.id == id }

    val ownSongs: MutableList<Document> = java.util.Collections.synchronizedList(mutableListOf(profileSong(3), profileSong(4), profileSong(5)))

    val saveMusicCalls: MutableList<Pair<Long, Long?>> = java.util.concurrent.CopyOnWriteArrayList()

    val bob = User(contact = true, id = 502, accessHash = 10, firstName = "Bob")

    @Volatile var carolIsSupport = false

    val carol: User get() = User(id = 503, accessHash = 11, firstName = "Carol", lastName = "Writer", username = "carol_writer", phone = "15550001111", support = carolIsSupport)
    val helperBot = User(bot = true, botAttachMenu = true, botHasMainApp = true, botInfoVersion = 1, id = 504, accessHash = 12, firstName = "Helper", username = "helperbot")

    val joinedChannels: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    fun news() = Channel(broadcast = true, left = NEWS_ID !in joinedChannels, id = NEWS_ID, accessHash = NEWS_HASH, title = "Kotlin News", username = "kotlinnews", photo = ChatPhotoEmpty, date = NOW, participantsCount = 1_200)

    fun bookClub() = Channel(megagroup = true, left = BOOK_CLUB_ID !in joinedChannels, id = BOOK_CLUB_ID, accessHash = 6, title = "Book club", photo = ChatPhotoEmpty, date = NOW, participantsCount = 42)

    val dave = User(min = true, id = 505, accessHash = 77, firstName = "Dave")

    val bookClubMessages: List<Message> = listOf(Message(id = 3, fromId = PeerUser(dave.id), peerId = PeerChannel(BOOK_CLUB_ID), date = NOW, message = "chapter two tonight"))

    val newsPosts: List<Message> = (1..120).map { Message(post = true, id = it, peerId = PeerChannel(NEWS_ID), date = NOW - (120 - it) * 60, message = "post $it") }

    val searchCalls: MutableList<Search> = CopyOnWriteArrayList()

    val historyCalls: MutableList<Triple<Long, Int, Int>> = CopyOnWriteArrayList()

    val typingCalls: MutableList<SetTyping> = CopyOnWriteArrayList()

    val drafts: MutableMap<Long, DraftMessage> = ConcurrentHashMap()

    val editCalls: MutableList<EditMessage> = CopyOnWriteArrayList()

    val saveDraftCalls: MutableList<SaveDraft> = CopyOnWriteArrayList()

    @Volatile var lastStartBot: StartBot? = null

    val pinned: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    val archived: MutableSet<Long> = ConcurrentHashMap.newKeySet<Long>().apply { add(bob.id) }
    val muted: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    val read: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    val muteUntil = ConcurrentHashMap<Long, Int>()

    val ownMutes = ConcurrentHashMap<Long, Int>()

    fun muteUntilOf(id: Long): Int? = ownMutes[id] ?: muteUntil[id] ?: if (id in muted) Int.MAX_VALUE else null

    val readUpTo: MutableMap<Long, Int> = ConcurrentHashMap()

    val listed: MutableList<User> = CopyOnWriteArrayList()

    private val strangers = ConcurrentHashMap<Long, Message>()

    val dialogsCalls = AtomicInteger()

    val peerDialogsCalls: MutableList<List<String>> = CopyOnWriteArrayList()

    val repeated: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    @Volatile var afterDialogs: ((Int?) -> Unit)? = null

    @Volatile var contactsPins: List<TypeInputPeer> = emptyList()

    val filters: MutableList<TypeDialogFilter> = CopyOnWriteArrayList()

    @Volatile var staggered = false

    suspend fun messageFromStranger(userId: Long, text: String): Message {
        val message = cluster.updates.message(userId, text, push = false, container = true)
        strangers[userId] = message
        cluster.updates.push(cluster.updates.last!!)
        return message
    }

    private fun dialogsOf(archive: Boolean): Dialogs {
        val me = User(self = true, id = selfId, accessHash = 1, firstName = "Test", lastName = "User")
        val people = (listOf(me, alice, bob) + listed).filter { (it.id in archived) == archive }
        val dialogs = people.map { user ->
            val top = if (user.id == selfId) saved.lastOrNull()?.id ?: 0 else 1
            dialog(PeerUser(user.id), top, archive)
        }
        val messages = (if (archive) emptyList() else saved.takeLast(1)) + people.filter { it.id != selfId }.map {
            val age = if (staggered) listed.indexOfFirst { listedUser -> listedUser.id == it.id } + 1 else 0
            Message(id = 1, peerId = PeerUser(it.id), date = NOW - age, message = "hi from ${it.firstName?.lowercase()}")
        }
        val wrote = strangers.filterKeys { (it in archived) == archive }
        val (groupDialogs, groupMessages, groupChats) = groups.dialogs(archive)
        val rows = (dialogs + wrote.map { (id, message) -> dialog(PeerUser(id), message.id, archive) } + groupDialogs).filter { peerKeyOf((it as Dialog).peer) !in removals.gone }
        return Dialogs(
            rows + rows.filter { (it as Dialog).peer.let { peer -> peer is PeerUser && peer.userId in repeated } },
            messages + wrote.values + groupMessages,
            groupChats,
            listOf(me, alice, bob) + listed + wrote.keys.map(cluster.updates::user),
        )
    }

    private fun dialogsIn(folder: Int?): Dialogs = when (folder) {
        1 -> dialogsOf(archive = true)
        0 -> dialogsOf(archive = false)
        else -> {
            val main = dialogsOf(archive = false)
            val archive = dialogsOf(archive = true)
            Dialogs(main.dialogs + archive.dialogs, main.messages + archive.messages, main.chats + archive.chats, main.users + archive.users)
        }
    }

    private fun pageOf(all: Dialogs, call: GetDialogs): TypeDialogs {
        val after = if (call.offsetPeer is InputPeerEmpty) -1 else all.dialogs.indexOfLast { peerKeyOf((it as Dialog).peer) == keyOf(call.offsetPeer) }
        val rest = all.dialogs.drop(after + 1)
        val page = rest.take(call.limit)
        if (after < 0 && page.size == rest.size) return all
        return DialogsSlice(all.dialogs.size, page, all.messages, all.chats, all.users)
    }

    private fun peerKeyOf(peer: TypePeer): String = when (peer) {
        is PeerUser -> "u${peer.userId}"
        is PeerChat -> "c${peer.chatId}"
        is PeerChannel -> "ch${peer.channelId}"
        else -> ""
    }

    private fun keyOf(peer: TypeInputPeer): String = when (peer) {
        is InputPeerChat -> "c${peer.chatId}"
        is InputPeerChannel -> "ch${peer.channelId}"
        else -> "u${peerIdOf(peer)}"
    }

    val sessions: MutableList<Authorization> = CopyOnWriteArrayList(
        listOf(
            session(1, current = true, device = "Pixel 9", platform = "Android", app = "miogram"),
            session(2, current = false, device = "iPhone 17", platform = "iOS", app = "Telegram iOS"),
            session(3, current = false, device = "Chrome 140", platform = "Web", app = "Telegram Web K"),
        ),
    )

    private fun session(hash: Long, current: Boolean, device: String, platform: String, app: String) = Authorization(
        current = current,
        hash = hash,
        deviceModel = device,
        platform = platform,
        systemVersion = "1",
        apiId = 12345,
        appName = app,
        appVersion = "0.1.0",
        dateCreated = NOW,
        dateActive = NOW,
        ip = "127.0.0.1",
        country = "Netherlands",
        region = "Amsterdam",
    )

    private fun userOf(peer: TypeInputDialogPeer) = ((peer as InputDialogPeer).peer as InputPeerUser).userId

    private fun userIdOf(peer: TypeInputPeer) = if (peer == InputPeerSelf) selfId else (peer as InputPeerUser).userId

    private fun peerIdOf(peer: TypeInputPeer): Long = when (peer) {
        is InputPeerUser -> peer.userId
        is InputPeerChat -> peer.chatId
        is InputPeerChannel -> peer.channelId
        else -> selfId
    }

    @Volatile var lastNotifySettings: UpdateNotifySettings? = null

    val defaultMutes: MutableMap<String, Int> = ConcurrentHashMap()

    val defaultPreviews: MutableMap<String, Boolean> = ConcurrentHashMap()

    private fun default(scope: String) = PeerNotifySettings(showPreviews = defaultPreviews[scope] ?: true, muteUntil = defaultMutes[scope] ?: 0)

    private fun scopeOf(peer: TypeInputNotifyPeer): String? = when (peer) {
        InputNotifyUsers -> "users"
        InputNotifyChats -> "chats"
        InputNotifyBroadcasts -> "broadcasts"
        else -> null
    }

    val groups = FakeGroups(this)

    val removals = FakeRemovals()

    val search = FakeSearch(this)

    val sharedMedia = FakeSharedMedia(this)

    val bots = FakeBots(this)

    val articles = FakeArticles(this)

    val streams = FakeStreams(this)

    val ephemeral = FakeEphemeral(this)

    val attach = FakeAttach(this)

    val polls = FakePolls(this)

    val pins = FakePins(this)

    val sending = FakeSending(this)

    val translations = FakeTranslations(this)

    val wallpapers = FakeWallpapers()

    val profileEdit = FakeProfileEdit(this)

    val twoStep = FakeTwoStep(this)

    fun selfUser(): User = profileEdit.overlay(User(self = true, premium = articles.premium, id = selfId, accessHash = selfId * 7, firstName = "Test", lastName = "User $selfId", phone = "99966"))

    val contacts = FakeContacts(this)

    val safety = FakeSafety(this)

    val stories = FakeStories(this)

    val privacy: MutableMap<Int, List<TypePrivacyRule>> = ConcurrentHashMap(
        mapOf(
            InputPrivacyKeyStatusTimestamp.constructorId to listOf(PrivacyValueDisallowUsers(listOf(alice.id)), PrivacyValueAllowContacts),
            InputPrivacyKeyPhoneNumber.constructorId to listOf(PrivacyValueDisallowAll),
        ),
    )

    @Volatile var lastSetPrivacy: SetPrivacy? = null

    @Volatile var globalPrivacy = GlobalPrivacySettings(keepArchivedUnmuted = true)

    @Volatile var accountTtl = 365

    private fun ruleOf(rule: TypeInputPrivacyRule): TypePrivacyRule? = when (rule) {
        InputPrivacyValueAllowAll -> PrivacyValueAllowAll
        InputPrivacyValueAllowContacts -> PrivacyValueAllowContacts
        InputPrivacyValueDisallowAll -> PrivacyValueDisallowAll
        is InputPrivacyValueAllowUsers -> PrivacyValueAllowUsers(rule.users.map { (it as InputUser).userId })
        is InputPrivacyValueDisallowUsers -> PrivacyValueDisallowUsers(rule.users.map { (it as InputUser).userId })
        else -> null
    }

    private fun rulesOf(key: Int) = PrivacyRules(privacy[key] ?: listOf(PrivacyValueAllowAll), emptyList(), listOf(alice, bob))

    init {
        cluster.servers.forEach { server ->
            server.webfileDcId = cluster.fileDcId
            attach.register(server)
            server.rpc.on(GetDialogs.CONSTRUCTOR_ID) { request ->
                dialogsCalls.incrementAndGet()
                val call = GetDialogs.decode(request)
                val answer = pageOf(dialogsIn(call.folderId), call)
                afterDialogs?.invoke(call.folderId)
                FakeAnswer.Result(answer)
            }
            server.rpc.on(GetPeerDialogs.CONSTRUCTOR_ID) { request ->
                val wanted = GetPeerDialogs.decode(request).peers.map { keyOf((it as InputDialogPeer).peer) }
                peerDialogsCalls += wanted
                val all = listOf(dialogsOf(archive = false), dialogsOf(archive = true))
                val dialogs = all.flatMap { it.dialogs }.filter { peerKeyOf((it as Dialog).peer) in wanted }
                val state = State(pts = cluster.updates.pts, qts = cluster.updates.qts, date = NOW, seq = cluster.updates.seq, unreadCount = 0)
                FakeAnswer.Result(PeerDialogs(dialogs, all.flatMap { it.messages }, all.flatMap { it.chats }, all.flatMap { it.users }, state))
            }
            server.rpc.on(GetDialogFilters.CONSTRUCTOR_ID) {
                val contacts = DialogFilter(contacts = true, id = 2, title = TextWithEntities("Contacts", emptyList()), pinnedPeers = contactsPins, includePeers = emptyList(), excludePeers = emptyList())
                FakeAnswer.Result(DialogFilters(filters = listOf(DialogFilterDefault, contacts) + filters))
            }
            server.rpc.on(ToggleDialogPin.CONSTRUCTOR_ID) { request ->
                val call = ToggleDialogPin.decode(request)
                val id = userOf(call.peer)
                if (call.pinned) pinned += id else pinned -= id
                BOOL_TRUE
            }
            server.rpc.on(EditPeerFolders.CONSTRUCTOR_ID) { request ->
                EditPeerFolders.decode(request).folderPeers.forEach { peer ->
                    val id = peerIdOf(peer.peer)
                    if (peer.folderId == 1) archived += id else archived -= id
                }
                FakeAnswer.Result(UpdatesTooLong)
            }
            server.rpc.on(UpdateNotifySettings.CONSTRUCTOR_ID) { request ->
                val call = UpdateNotifySettings.decode(request)
                lastNotifySettings = call
                scopeOf(call.peer)?.let { scope ->
                    defaultMutes[scope] = call.settings.muteUntil ?: 0
                    call.settings.showPreviews?.let { defaultPreviews[scope] = it } ?: defaultPreviews.remove(scope)
                    return@on BOOL_TRUE
                }
                val id = peerIdOf((call.peer as InputNotifyPeer).peer)
                val until = call.settings.muteUntil ?: run {
                    ownMutes -= id
                    muted -= id
                    muteUntil -= id
                    return@on BOOL_TRUE
                }
                ownMutes[id] = until
                if (until > 0) muted += id else muted -= id
                if (until in 1 until Int.MAX_VALUE) muteUntil[id] = until else muteUntil -= id
                BOOL_TRUE
            }
            server.rpc.on(GetNotifyExceptions.CONSTRUCTOR_ID) {
                val all = listOf(dialogsOf(archive = false), dialogsOf(archive = true))
                val updates = all.flatMap { it.dialogs }.mapNotNull { dialog ->
                    val peer = (dialog as Dialog).peer
                    val id = when (peer) {
                        is PeerUser -> peer.userId
                        is PeerChat -> peer.chatId
                        is PeerChannel -> peer.channelId
                        else -> return@mapNotNull null
                    }
                    muteUntilOf(id)?.let { UpdateNotifySettingsUpdate(NotifyPeer(peer), PeerNotifySettings(muteUntil = it)) }
                }
                FakeAnswer.Result(Updates(updates, all.flatMap { it.users }.distinctBy { (it as? User)?.id }, all.flatMap { it.chats }, NOW, 0))
            }
            server.rpc.on(ResetNotifySettings.CONSTRUCTOR_ID) {
                ownMutes.clear()
                muted.clear()
                muteUntil.clear()
                defaultMutes.clear()
                defaultPreviews.clear()
                BOOL_TRUE
            }
            server.rpc.on(GetNotifySettings.CONSTRUCTOR_ID) { request ->
                val settings = when (val peer = GetNotifySettings.decode(request).peer) {
                    is InputNotifyPeer -> PeerNotifySettings(muteUntil = muteUntilOf(peerIdOf(peer.peer)))
                    InputNotifyUsers -> default("users")
                    InputNotifyChats -> default("chats")
                    InputNotifyBroadcasts -> default("broadcasts")
                    else -> PeerNotifySettings()
                }
                FakeAnswer.Result(settings)
            }
            groups.register(server)
            removals.register(server, this)
            search.register(server)
            sharedMedia.register(server)
            bots.register(server)
            articles.register(server)
            streams.register(server)
            ephemeral.register(server)
            polls.register(server)
            pins.register(server)
            sending.register(server)
            translations.register(server)
            wallpapers.register(server, cluster)
            profileEdit.register(server, cluster)
            if (!loginPassword) twoStep.register(server)
            contacts.register(server)
            safety.register(server)
            stories.register(server)
            server.rpc.on(ReadHistory.CONSTRUCTOR_ID) { request ->
                val call = ReadHistory.decode(request)
                (call.peer as? InputPeerUser)?.let {
                    read += it.userId
                    readUpTo[it.userId] = call.maxId
                }
                FakeAnswer.Result(AffectedMessages(pts = 1, ptsCount = 1))
            }
            server.rpc.on(MarkDialogUnread.CONSTRUCTOR_ID) { BOOL_TRUE }
            server.rpc.on(GetUsers.CONSTRUCTOR_ID) {
                val w = TlWriter()
                w.writeVector(listOf(selfUser())) { it.serialize(this) }
                FakeAnswer.Raw(w.toByteArray())
            }
            server.rpc.on(GetFullUser.CONSTRUCTOR_ID) { request ->
                answerLate("profile")
                val user = when (val id = GetFullUser.decode(request).id) {
                    is InputUser -> listOf(alice, bob, carol, helperBot, bots.gifBot).firstOrNull { it.id == id.userId }?.let { if (it.id == alice.id) aliceFull else it }
                        ?: return@on FakeAnswer.Error(400, "USER_ID_INVALID")
                    is InputUserFromMessage -> User(id = dave.id, accessHash = DAVE_HASH, firstName = "Dave")
                    else -> profileEdit.overlay(User(self = true, premium = articles.premium, id = selfId, accessHash = 1, firstName = "Test", lastName = "User", phone = "9996627777", username = "tester"))
                }
                val own = user.self && profileEdit.edited
                val about = if (own) profileEdit.about.orEmpty() else if (user.id == alice.id) "likes compose" else "testing ktproto"
                val full = UserFull(
                    id = user.id,
                    settings = safety.bars[user.id] ?: PeerSettings(),
                    blocked = user.id in safety.blocked,
                    notifySettings = PeerNotifySettings(muteUntil = muteUntilOf(user.id)),
                    commonChatsCount = 2,
                    about = about,
                    birthday = if (own) profileEdit.birthday else Birthday(day = 14, month = 3, year = 1990),
                    botInfo = if (user.id == helperBot.id) BotInfo(userId = helperBot.id, commands = bots.helperCommands, menuButton = BotMenuButton("Menu", FakeBots.MENU_APP_URL)) else null,
                    savedMusic = if (user.id == alice.id) profileSong(1) else if (user.self) ownSongs.firstOrNull() else null,
                    personalChannelId = if (own) profileEdit.personalChannelId else if (user.id == alice.id) NEWS_ID else null,
                    personalChannelMessage = if (user.id == alice.id || (own && profileEdit.personalChannelId != null)) newsPosts.last().id else null,
                    wallpaper = wallpapers.chats[user.id]?.paper,
                    wallpaperOverridden = wallpapers.chats[user.id]?.overridden == true,
                )
                FakeAnswer.Result(UsersUserFull(full, if (user.id == alice.id) listOf(news()) else if (own) listOfNotNull(profileEdit.personalChannel()) else emptyList(), listOf(user)))
            }
            server.rpc.on(GetUserPhotos.CONSTRUCTOR_ID) { request ->
                val asked = GetUserPhotos.decode(request).userId
                val id = (asked as? InputUser)?.userId
                FakeAnswer.Result(Photos(if (id == alice.id) listOf(photo) else if (asked == InputUserSelf || id == selfId) profileEdit.photos() else emptyList(), emptyList()))
            }
            server.rpc.on(GetSavedMusic.CONSTRUCTOR_ID) { request ->
                val songs = when (val id = GetSavedMusic.decode(request).id) {
                    InputUserSelf -> ownSongs.toList()
                    is InputUser -> if (id.userId == alice.id) listOf(profileSong(1), profileSong(2)) else emptyList()
                    else -> emptyList()
                }
                FakeAnswer.Result(SavedMusic(songs.size, songs))
            }
            server.rpc.on(SaveMusic.CONSTRUCTOR_ID) { request ->
                val call = SaveMusic.decode(request)
                val id = (call.id as InputDocument).id
                val after = (call.afterId as? InputDocument)?.id
                saveMusicCalls += id to after
                synchronized(ownSongs) {
                    val song = ownSongs.firstOrNull { it.id == id } ?: messageSong(id) ?: return@on FakeAnswer.Error(400, "DOCUMENT_INVALID")
                    ownSongs.remove(song)
                    if (!call.unsave) ownSongs.add(after?.let { a -> ownSongs.indexOfFirst { it.id == a } + 1 } ?: 0, song)
                }
                BOOL_TRUE
            }
            server.rpc.on(GetAuthorizations.CONSTRUCTOR_ID) {
                answerLate("sessions")
                FakeAnswer.Result(Authorizations(authorizationTtlDays = 180, authorizations = sessions.toList()))
            }
            server.rpc.on(ResetAuthorization.CONSTRUCTOR_ID) { request ->
                val hash = ResetAuthorization.decode(request).hash
                sessions.removeAll { it.hash == hash }
                BOOL_TRUE
            }
            server.rpc.on(ResetAuthorizations.CONSTRUCTOR_ID) {
                sessions.removeAll { !it.current }
                BOOL_TRUE
            }
            server.rpc.on(GetPrivacy.CONSTRUCTOR_ID) { request ->
                FakeAnswer.Result(rulesOf(GetPrivacy.decode(request).key.constructorId))
            }
            server.rpc.on(SetPrivacy.CONSTRUCTOR_ID) { request ->
                val call = SetPrivacy.decode(request)
                lastSetPrivacy = call
                privacy[call.key.constructorId] = call.rules.mapNotNull(::ruleOf)
                FakeAnswer.Result(rulesOf(call.key.constructorId))
            }
            server.rpc.on(GetGlobalPrivacySettings.CONSTRUCTOR_ID) { FakeAnswer.Result(globalPrivacy) }
            server.rpc.on(SetGlobalPrivacySettings.CONSTRUCTOR_ID) { request ->
                globalPrivacy = SetGlobalPrivacySettings.decode(request).settings
                FakeAnswer.Result(globalPrivacy)
            }
            server.rpc.on(GetAccountTTL.CONSTRUCTOR_ID) { FakeAnswer.Result(AccountDaysTTL(accountTtl)) }
            server.rpc.on(SetAccountTTL.CONSTRUCTOR_ID) { request ->
                accountTtl = SetAccountTTL.decode(request).ttl.days
                BOOL_TRUE
            }
            server.rpc.on(GetAllStickers.CONSTRUCTOR_ID) { request ->
                val hash = GetAllStickers.decode(request).hash
                allStickersHashes += hash
                if (hash == allStickersHash()) return@on FakeAnswer.Result(AllStickersNotModified)
                FakeAnswer.Result(AllStickers(allStickersHash(), ordered(stickerSets().filter { it.id in installedSets })))
            }
            server.rpc.on(GetEmojiStickers.CONSTRUCTOR_ID) { request ->
                val hash = GetEmojiStickers.decode(request).hash
                emojiStickersHashes += hash
                if (hash == allStickersHash()) return@on FakeAnswer.Result(AllStickersNotModified)
                FakeAnswer.Result(AllStickers(allStickersHash(), ordered(stickerSets().filter { it.emojis && it.id in installedSets })))
            }
            server.rpc.on(ReorderStickerSets.CONSTRUCTOR_ID) { request ->
                val call = ReorderStickerSets.decode(request)
                reorders += call.emojis to call.order
                setOrder.removeAll(call.order.toSet())
                setOrder.addAll(0, call.order)
                BOOL_TRUE
            }
            server.rpc.on(GetStickerSet.CONSTRUCTOR_ID) { request ->
                val call = GetStickerSet.decode(request)
                val id = when (val input = call.stickerset) {
                    is InputStickerSetID -> input.id
                    is InputStickerSetShortName -> stickerSets().firstOrNull { it.shortName == input.shortName }?.id
                    else -> null
                }
                val set = stickerSets().firstOrNull { it.id == id } ?: return@on FakeAnswer.Error(400, "STICKERSET_INVALID")
                stickerSetHashes += set.id to call.hash
                if (call.hash == set.hash) return@on FakeAnswer.Result(MessagesStickerSetNotModified)
                FakeAnswer.Result(MessagesStickerSet(set, emptyList(), emptyList(), (stickerDocuments() + customEmojiDocuments()).filter { setOf(it) == set.id }))
            }
            server.rpc.on(InstallStickerSet.CONSTRUCTOR_ID) { request ->
                val input = InstallStickerSet.decode(request).stickerset as InputStickerSetID
                installedSets += input.id
                FakeAnswer.Result(StickerSetInstallResultSuccess)
            }
            server.rpc.on(UninstallStickerSet.CONSTRUCTOR_ID) { request ->
                val input = UninstallStickerSet.decode(request).stickerset as InputStickerSetID
                installedSets -= input.id
                BOOL_TRUE
            }
            server.rpc.on(GetRecentStickers.CONSTRUCTOR_ID) { request ->
                val hash = GetRecentStickers.decode(request).hash
                recentHashes += hash
                if (hash == RECENT_HASH) return@on FakeAnswer.Result(RecentStickersNotModified)
                FakeAnswer.Result(RecentStickers(RECENT_HASH, emptyList(), stickerDocuments().filter { it.id == CAT_ID }, listOf(NOW)))
            }
            server.rpc.on(GetFavedStickers.CONSTRUCTOR_ID) { request ->
                val hash = GetFavedStickers.decode(request).hash
                favedHashes += hash
                if (hash == favedHash()) return@on FakeAnswer.Result(FavedStickersNotModified)
                FakeAnswer.Result(FavedStickers(favedHash(), emptyList(), favedStickers.mapNotNull { id -> stickerDocuments().firstOrNull { it.id == id } }))
            }
            server.rpc.on(FaveSticker.CONSTRUCTOR_ID) { request ->
                val call = FaveSticker.decode(request)
                val input = call.id as? InputDocument ?: return@on FakeAnswer.Error(400, "STICKER_ID_INVALID")
                if (!input.fileReference.contentEquals(stickerReference)) return@on FakeAnswer.Error(400, "FILE_REFERENCE_EXPIRED")
                val id = input.id
                if (call.unfave) favedStickers.remove(id) else if (id !in favedStickers) favedStickers.add(0, id)
                BOOL_TRUE
            }
            server.rpc.on(GetStickers.CONSTRUCTOR_ID) { request ->
                val emoji = GetStickers.decode(request).emoticon
                FakeAnswer.Result(Stickers(0, stickerDocuments().filter { doc -> doc.attributes.any { it is DocumentAttributeSticker && it.alt == emoji } }))
            }
            server.rpc.on(GetSavedGifs.CONSTRUCTOR_ID) { request ->
                val hash = GetSavedGifs.decode(request).hash
                savedGifsHashes += hash
                if (hash == savedGifsHash()) return@on FakeAnswer.Result(SavedGifsNotModified)
                FakeAnswer.Result(SavedGifs(savedGifsHash(), savedGifs.mapNotNull { id -> gifDocuments().firstOrNull { it.id == id } }))
            }
            server.rpc.on(SaveGif.CONSTRUCTOR_ID) { request ->
                val call = SaveGif.decode(request)
                val id = (call.id as? InputDocument)?.id ?: return@on FakeAnswer.Error(400, "GIF_ID_INVALID")
                if (call.unsave) savedGifs.remove(id) else if (id !in savedGifs) savedGifs.add(0, id)
                BOOL_TRUE
            }
            server.rpc.on(SearchStickers.CONSTRUCTOR_ID) { request ->
                val call = SearchStickers.decode(request)
                if (call.emoticon.isNotEmpty()) {
                    categorySearches += call.emoticon to call.emojis
                    val emoticons = call.emoticon.split(' ')
                    val documents = if (call.emojis) customEmojiDocuments() else stickerDocuments()
                    val found = documents.filter { doc -> doc.attributes.any { (it as? DocumentAttributeSticker)?.alt in emoticons || (it as? DocumentAttributeCustomEmoji)?.alt in emoticons } }
                    return@on FakeAnswer.Result(FoundStickers(hash = 0, stickers = found))
                }
                stickerSearches += call.q
                val found = stickerDocuments().filter { doc -> call.q.isNotBlank() && doc.attributes.any { it is DocumentAttributeSticker && (it.stickerset as? InputStickerSetID)?.id == CATS_ID } && "cat".startsWith(call.q.lowercase()) }
                FakeAnswer.Result(FoundStickers(hash = 0, stickers = found))
            }
            server.rpc.on(GetFeaturedStickers.CONSTRUCTOR_ID) { request ->
                val hash = GetFeaturedStickers.decode(request).hash
                featuredHashes += hash
                if (hash == FEATURED_HASH) return@on FakeAnswer.Result(FeaturedStickersNotModified(4))
                val sets = stickerSets()
                fun set(id: Long) = sets.first { it.id == id }
                val docs = stickerDocuments()
                val covered = listOf(
                    StickerSetCovered(set(CATS_ID), docs.first { it.id == CAT_ID }),
                    StickerSetCovered(set(DOGS_ID), docs.first { it.id == DOG_ID }),
                    StickerSetMultiCovered(set(CLIPS_ID), docs.filter { it.id == CLIP_ID }),
                    StickerSetNoCovered(set(BIRDS_ID)),
                )
                FakeAnswer.Result(FeaturedStickers(hash = FEATURED_HASH, count = 4, sets = covered, unread = listOf(DOGS_ID, CLIPS_ID).filter { it !in readFeatured }))
            }
            server.rpc.on(GetFeaturedEmojiStickers.CONSTRUCTOR_ID) { request ->
                if (GetFeaturedEmojiStickers.decode(request).hash == FEATURED_HASH) return@on FakeAnswer.Result(FeaturedStickersNotModified(1))
                val stars = stickerSets().first { it.id == EMOJI_SET }
                val covered = StickerSetFullCovered(stars, emptyList(), emptyList(), customEmojiDocuments())
                FakeAnswer.Result(FeaturedStickers(hash = FEATURED_HASH, count = 1, sets = listOf(covered), unread = listOf(EMOJI_SET).filter { it !in readFeatured }))
            }
            server.rpc.on(ReadFeaturedStickers.CONSTRUCTOR_ID) { request ->
                readFeatured += ReadFeaturedStickers.decode(request).id
                BOOL_TRUE
            }
            server.rpc.on(GetEmojiGroups.CONSTRUCTOR_ID) { request ->
                val hash = GetEmojiGroups.decode(request).hash
                emojiGroupHashes += hash
                if (hash == EMOJI_GROUPS_HASH) return@on FakeAnswer.Result(EmojiGroupsNotModified)
                val groups = listOf(
                    EmojiGroup("Love", EMOJI_HEART, listOf("\u2764", "\uD83D\uDE0D")),
                    EmojiGroupGreeting("Hello", EMOJI_STAR, listOf("\uD83D\uDC4B")),
                    EmojiGroupPremium("Premium", EMOJI_STAR),
                )
                FakeAnswer.Result(EmojiGroups(EMOJI_GROUPS_HASH, groups))
            }
            server.rpc.on(GetEmojiStickerGroups.CONSTRUCTOR_ID) { request ->
                if (GetEmojiStickerGroups.decode(request).hash == EMOJI_GROUPS_HASH) return@on FakeAnswer.Result(EmojiGroupsNotModified)
                FakeAnswer.Result(EmojiGroups(EMOJI_GROUPS_HASH, listOf(EmojiGroup("Cats", EMOJI_STAR, listOf("\uD83D\uDE00", "\uD83D\uDE3A")))))
            }
            server.rpc.on(GetSavedStarGifts.CONSTRUCTOR_ID) { request ->
                val call = GetSavedStarGifts.decode(request)
                val theirs = listOf(SavedStarGift(pinnedToTop = true, fromId = PeerUser(bob.id), date = NOW, gift = pepe()), SavedStarGift(fromId = PeerUser(selfId), date = NOW, gift = teddy()))
                val everything = if (call.peer == InputPeerSelf) ownGifts.toList() else if ((call.peer as? InputPeerUser)?.userId == alice.id) theirs else emptyList()
                val all = call.collectionId?.let { id -> everything.filter { id in it.collectionId.orEmpty() } } ?: everything
                val start = call.offset.toIntOrNull() ?: 0
                val page = all.drop(start).take(minOf(call.limit, 2))
                val next = (start + page.size).takeIf { it < all.size }?.toString()
                val me = User(self = true, id = selfId, accessHash = 1, firstName = "Test", lastName = "User")
                FakeAnswer.Result(SavedStarGifts(count = all.size, gifts = page, nextOffset = next, chats = emptyList(), users = listOf(me, alice, bob)))
            }
            server.rpc.on(SaveStarGift.CONSTRUCTOR_ID) { request ->
                val call = SaveStarGift.decode(request)
                val msgId = (call.stargift as InputSavedStarGiftUser).msgId
                val index = ownGifts.indexOfFirst { it.msgId == msgId }
                if (index < 0) return@on FakeAnswer.Error(400, "SAVED_ID_EMPTY")
                val old = ownGifts[index]
                ownGifts[index] = SavedStarGift(nameHidden = old.nameHidden, unsaved = call.unsave, fromId = old.fromId, date = old.date, gift = old.gift, message = old.message, msgId = old.msgId, convertStars = old.convertStars, collectionId = old.collectionId)
                BOOL_TRUE
            }
            server.rpc.on(GetStarGiftCollections.CONSTRUCTOR_ID) { request ->
                val call = GetStarGiftCollections.decode(request)
                FakeAnswer.Result(StarGiftCollections(if (call.peer == InputPeerSelf) giftCollections.toList() else emptyList()))
            }
            server.rpc.on(GetStarGifts.CONSTRUCTOR_ID) { request ->
                val hash = GetStarGifts.decode(request).hash
                starGiftHashes += hash
                if (hash == STAR_GIFTS_HASH) return@on FakeAnswer.Result(StarGiftsNotModified)
                val soldOut = StarGift(limited = true, soldOut = true, id = 63, sticker = giftSticker(GIFT_STICKERS[3]), stars = 2_500, availabilityRemains = 0, availabilityTotal = 500, convertStars = 2_000, firstSaleDate = NOW - 600, lastSaleDate = NOW - 60)
                FakeAnswer.Result(StarGifts(STAR_GIFTS_HASH, listOf(teddy(), soldOut), emptyList(), emptyList()))
            }
            server.rpc.on(SetTyping.CONSTRUCTOR_ID) { request ->
                typingCalls += SetTyping.decode(request)
                BOOL_TRUE
            }
            server.rpc.on(SaveDraft.CONSTRUCTOR_ID) { request ->
                val call = SaveDraft.decode(request)
                saveDraftCalls += call
                val user = userIdOf(call.peer)
                val rich = call.richMessage?.let { input -> articles.accept(input).let { (kept, error) -> kept ?: return@on FakeAnswer.Error(400, error!!) } }
                if (call.message.isEmpty() && rich == null) drafts -= user else drafts[user] = DraftMessage(replyTo = call.replyTo, message = call.message, entities = call.entities, date = NOW, richMessage = rich)
                BOOL_TRUE
            }
            server.rpc.on(Search.CONSTRUCTOR_ID) { request ->
                answerLate("search")
                val call = Search.decode(request)
                searchCalls += call
                sharedMedia.search(call)?.let { return@on it }
                if (call.filter == InputMessagesFilterPinned) return@on pins.search(call, if (call.peer == InputPeerSelf) saved.toList() else emptyList())
                if (call.filter == InputMessagesFilterEmpty) {
                    val found = (if (call.peer == InputPeerSelf) saved.reversed() else emptyList()).filter { call.q.isNotEmpty() && it.message.contains(call.q, ignoreCase = true) }
                    val first = found.indexOfFirst { call.offsetId == 0 || it.id < call.offsetId }.let { if (it < 0) found.size else it }
                    val start = (first + call.addOffset).coerceIn(0, found.size)
                    val page = found.drop(start).take(call.limit)
                    return@on FakeAnswer.Result(if (found.size > call.limit) MessagesSlice(count = found.size, messages = page, topics = emptyList(), chats = emptyList(), users = emptyList()) else Messages(page, emptyList(), emptyList(), emptyList()))
                }
                if (call.filter == InputMessagesFilterMusic) {
                    val songs = (if (call.peer == InputPeerSelf) saved.reversed() else emptyList()).filter { message ->
                        ((message.media as? MessageMediaDocument)?.document as? Document)?.attributes?.any { it is DocumentAttributeAudio && !it.voice } == true
                    }
                    musicCalls += call
                    val first = songs.indexOfFirst { call.offsetId == 0 || it.id < call.offsetId }.let { if (it < 0) songs.size else it }
                    val start = (first + call.addOffset).coerceIn(0, songs.size)
                    val page = songs.subList(start, (start + call.limit).coerceAtMost(songs.size)).filter { call.minId == 0 || it.id > call.minId }
                    return@on FakeAnswer.Result(Messages(page, emptyList(), emptyList(), emptyList()))
                }
                if (call.filter == InputMessagesFilterVoice) {
                    val voices = (if (call.peer == InputPeerSelf) saved.reversed() else emptyList()).filter { message ->
                        ((message.media as? MessageMediaDocument)?.document as? Document)?.attributes?.any { it is DocumentAttributeAudio && it.voice } == true
                    }
                    voiceCalls += call
                    val first = voices.indexOfFirst { call.offsetId == 0 || it.id < call.offsetId }.let { if (it < 0) voices.size else it }
                    val start = (first + call.addOffset).coerceIn(0, voices.size)
                    val page = voices.subList(start, (start + call.limit).coerceAtMost(voices.size)).filter { call.minId == 0 || it.id > call.minId }
                    return@on FakeAnswer.Result(Messages(page, emptyList(), emptyList(), listOf(alice)))
                }
                if (call.filter != InputMessagesFilterPhotoVideo) return@on FakeAnswer.Error(400, "FILTER_NOT_SUPPORTED")
                val visual = (if (call.peer == InputPeerSelf) saved.reversed() else emptyList()).filter { it.media is MessageMediaPhoto || (it.media as? MessageMediaDocument)?.video == true }
                val first = visual.indexOfFirst { call.offsetId == 0 || it.id < call.offsetId }.let { if (it < 0) visual.size else it }
                val start = (first + call.addOffset).coerceIn(0, visual.size)
                FakeAnswer.Result(Messages(visual.drop(start).take(call.limit), emptyList(), emptyList(), emptyList()))
            }
            server.rpc.on(GetMessages.CONSTRUCTOR_ID) { request ->
                val ids = GetMessages.decode(request).id.map { (it as InputMessageID).id }
                getMessagesCalls += ids
                val fresh = (saved + incoming).filter { it.id in ids }.map { m ->
                    val doc = (m.media as? MessageMediaDocument)?.document as? Document
                    val current = stickerDocuments().firstOrNull { it.id == doc?.id }
                    if (current == null) m else Message(out = m.out, id = m.id, peerId = m.peerId, date = m.date, message = m.message, media = MessageMediaDocument(document = current))
                }
                FakeAnswer.Result(Messages(fresh, emptyList(), emptyList(), emptyList()))
            }
            server.rpc.on(GetHistory.CONSTRUCTOR_ID) { request ->
                val call = GetHistory.decode(request)
                val channel = (call.peer as? InputPeerChannel)?.channelId
                val all: List<Pair<Int, TypeMessage>> = when {
                    call.peer == InputPeerSelf -> (saved.map { it.id to it } + services.map { it.id to it }).sortedByDescending { it.first }
                    channel == NEWS_ID -> newsPosts.reversed().map { it.id to it }
                    channel == BOOK_CLUB_ID -> bookClubMessages.reversed().map { it.id to it }
                    helperBot.id == (call.peer as? InputPeerUser)?.userId -> bots.helperHistory.reversed().map { it.id to it }
                    else -> groups.history(call.peer)?.first?.reversed()?.map { it.id to it } ?: wallpapers.history(call.peer).reversed().map { it.id to it }
                }
                val first = if (call.offsetId == 0) 0 else all.indexOfFirst { it.first < call.offsetId }.let { if (it < 0) all.size else it }
                val start = first + call.addOffset
                val window = all.subList(start.coerceIn(0, all.size), (start + call.limit).coerceIn(0, all.size))
                val messages = window.filter { call.minId == 0 || it.first > call.minId }.map { it.second }
                historyCalls += Triple(channel ?: 0L, call.offsetId, call.addOffset)
                val chats = when {
                    channel == NEWS_ID -> listOf(news())
                    channel == BOOK_CLUB_ID -> listOf(bookClub())
                    call.peer == InputPeerSelf && saved.any { (it.fwdFrom?.fromId as? PeerChannel)?.channelId == NEWS_ID } -> listOf(news())
                    else -> groups.history(call.peer)?.second.orEmpty()
                }
                FakeAnswer.Result(Messages(messages, emptyList(), chats, if (channel == BOOK_CLUB_ID) listOf(alice, bob, dave) else listOf(alice, bob, bots.gifBot, helperBot)))
            }
            server.rpc.on(ResolveUsername.CONSTRUCTOR_ID) { request ->
                val username = ResolveUsername.decode(request).username.lowercase()
                bots.resolve(username)?.let { return@on FakeAnswer.Result(ResolvedPeer(PeerUser(it.id), emptyList(), listOf(it))) }
                when (username) {
                    carol.username -> FakeAnswer.Result(ResolvedPeer(PeerUser(carol.id), emptyList(), listOf(carol)))
                    helperBot.username -> FakeAnswer.Result(ResolvedPeer(PeerUser(helperBot.id), emptyList(), listOf(helperBot)))
                    "kotlinnews" -> FakeAnswer.Result(ResolvedPeer(PeerChannel(NEWS_ID), listOf(news()), emptyList()))
                    else -> groups.resolve(username)?.let { FakeAnswer.Result(ResolvedPeer(PeerChannel(it.id), listOf(it), emptyList())) }
                        ?: FakeAnswer.Error(400, "USERNAME_NOT_OCCUPIED")
                }
            }
            server.rpc.on(ResolvePhone.CONSTRUCTOR_ID) { request ->
                if (ResolvePhone.decode(request).phone != carol.phone) return@on FakeAnswer.Error(400, "PHONE_NOT_OCCUPIED")
                FakeAnswer.Result(ResolvedPeer(PeerUser(carol.id), emptyList(), listOf(carol)))
            }
            server.rpc.on(CheckChatInvite.CONSTRUCTOR_ID) { request ->
                val hash = CheckChatInvite.decode(request).hash
                if (hash == GUARDED_INVITE) return@on FakeAnswer.Result(ChatInvite(megagroup = true, title = "Guarded club", photo = photo, participantsCount = 7, color = 0))
                if (hash != BOOK_CLUB_INVITE) return@on FakeAnswer.Error(400, "INVITE_HASH_EXPIRED")
                if (BOOK_CLUB_ID in joinedChannels) return@on FakeAnswer.Result(ChatInviteAlready(bookClub()))
                FakeAnswer.Result(ChatInvite(megagroup = true, title = "Book club", about = "we read about kotlin", photo = photo, participantsCount = 42, color = 0))
            }
            server.rpc.on(ImportChatInvite.CONSTRUCTOR_ID) { request ->
                val hash = ImportChatInvite.decode(request).hash
                if (hash == GUARDED_INVITE) return@on FakeAnswer.Result(ChatInviteJoinResultWebView(botId = helperBot.id, queryId = 1, users = listOf(helperBot)))
                if (hash != BOOK_CLUB_INVITE) return@on FakeAnswer.Error(400, "INVITE_HASH_EXPIRED")
                if (!joinedChannels.add(BOOK_CLUB_ID)) return@on FakeAnswer.Error(400, "USER_ALREADY_PARTICIPANT")
                FakeAnswer.Result(ChatInviteJoinResultOk(Updates(emptyList(), emptyList(), listOf(bookClub()), NOW, 0)))
            }
            server.rpc.on(JoinChannel.CONSTRUCTOR_ID) { request ->
                val channel = JoinChannel.decode(request).channel as? InputChannel
                channel?.let { groups.join(it.channelId) }?.let { return@on it }
                if (channel?.channelId != NEWS_ID || channel.accessHash != NEWS_HASH) return@on FakeAnswer.Error(400, "CHANNEL_INVALID")
                joinedChannels += NEWS_ID
                FakeAnswer.Result(ChatInviteJoinResultOk(Updates(emptyList(), emptyList(), listOf(news()), NOW, 0)))
            }
            server.rpc.on(StartBot.CONSTRUCTOR_ID) { request ->
                val call = StartBot.decode(request)
                if ((call.bot as? InputUser)?.userId !in setOf(helperBot.id, bots.gifBot.id)) return@on FakeAnswer.Error(400, "BOT_INVALID")
                lastStartBot = call
                FakeAnswer.Result(Updates(emptyList(), emptyList(), emptyList(), NOW, 0))
            }
            server.rpc.on(GetChannels.CONSTRUCTOR_ID) { request ->
                val ids = GetChannels.decode(request).id.mapNotNull { (it as? InputChannel)?.channelId }
                FakeAnswer.Result(Chats((listOf(news(), bookClub()) + groups.channels()).filter { it.id in ids }))
            }
            server.rpc.on(ResendCode.CONSTRUCTOR_ID) { request ->
                val call = ResendCode.decode(request)
                FakeAnswer.Result(SentCode(type = SentCodeTypeSms(length = 5), phoneCodeHash = call.phoneCodeHash, nextType = CodeTypeCall, timeout = 60))
            }
            server.rpc.on(SendMessage.CONSTRUCTOR_ID) { request ->
                val call = SendMessage.decode(request)
                sendMessageCalls++
                if (failSends > 0) {
                    failSends--
                    return@on FakeAnswer.Error(403, "CHAT_WRITE_FORBIDDEN")
                }
                if (!sentRandomIds.add(call.randomId)) return@on FakeAnswer.Error(500, "RANDOM_ID_DUPLICATE")
                groups.forum.send(call)?.let { return@on it }
                groups.discussion.send(call)?.let { return@on it }
                val rich = call.richMessage?.let { input -> articles.accept(input).let { (kept, error) -> kept ?: return@on FakeAnswer.Error(400, error!!) } }
                val reply = (call.replyTo as? InputReplyToMessage)?.let { MessageReplyHeader(replyToMsgId = it.replyToMsgId) }
                call.scheduleDate?.let { date ->
                    lastSendMessage = call
                    if (call.clearDraft) drafts -= peerIdOf(call.peer)
                    sending.schedule { scheduledId -> Message(out = true, id = scheduledId, peerId = PeerUser(selfId), date = date, message = call.message, replyTo = reply, entities = call.entities?.map(::storedEntity)) }
                    return@on FakeAnswer.Result(Updates(emptyList(), emptyList(), emptyList(), NOW, 0))
                }
                val id = sentId(call.peer)
                lastSendMessage = call
                if (call.replyTo is InputReplyToStory) stories.replies += call
                lastSentId = id
                if (call.clearDraft) drafts -= peerIdOf(call.peer)
                saved += Message(out = true, id = id, peerId = PeerUser(selfId), date = NOW, message = call.message, replyTo = reply, entities = call.entities?.map(::storedEntity), richMessage = rich)
                if (loseSendAnswers > 0) {
                    loseSendAnswers--
                    return@on FakeAnswer.Error(500, "MSG_WAIT_FAILED")
                }
                val peer = peerOf(call.peer)
                val sent = Message(
                    out = true, silent = call.silent, post = isBroadcast(peer), id = id, fromId = senderIn(peer), peerId = peer, date = sentDate(), message = call.message,
                    replyTo = reply, entities = call.entities?.map(::storedEntity),
                )
                if (sent.peerId is PeerChannel) return@on FakeAnswer.Result(sentUpdates(listOf(sent to call.randomId)))
                FakeAnswer.Result(UpdateShortSentMessage(out = true, id = id, pts = cluster.updates.sent(sent), ptsCount = 1, date = sent.date))
            }
            server.rpc.on(GetCustomEmojiDocuments.CONSTRUCTOR_ID) { request ->
                val ids = GetCustomEmojiDocuments.decode(request).documentId
                customEmojiCalls += ids
                val w = TlWriter()
                w.writeVector(customEmojiDocuments().filter { it.id in ids }) { it.serialize(this) }
                FakeAnswer.Raw(w.toByteArray())
            }
            server.rpc.on(ForwardMessages.CONSTRUCTOR_ID) { request ->
                val call = ForwardMessages.decode(request)
                lastForward = call
                if (call.scheduleDate != null) return@on FakeAnswer.Result(Updates(emptyList(), emptyList(), emptyList(), NOW, 0))
                val keep = call.fromPeer == InputPeerSelf && call.toPeer == InputPeerSelf
                val sent = call.id.zip(call.randomId).mapNotNull { (id, randomId) ->
                    val original = originalOf(call.fromPeer, id) ?: return@mapNotNull null
                    val origin = original.fwdFrom?.fromId ?: original.fromId ?: original.peerId
                    val header = if (call.dropAuthor) null else MessageFwdHeader(fromId = origin, date = original.date, channelPost = original.fwdFrom?.channelPost ?: original.id.takeIf { original.post })
                    val text = if (call.dropMediaCaptions && original.media != null) "" else original.message
                    val copy = sentId(call.toPeer)
                    if (keep) saved += Message(out = true, id = copy, peerId = PeerUser(selfId), date = NOW, message = text, media = original.media, fwdFrom = header)
                    Message(out = true, id = copy, fromId = PeerUser(selfId), peerId = peerOf(call.toPeer), date = sentDate(), message = text, media = original.media, fwdFrom = header) to randomId
                }
                FakeAnswer.Result(sentUpdates(sent))
            }
            server.rpc.on(GetAvailableReactions.CONSTRUCTOR_ID) { request ->
                val hash = GetAvailableReactions.decode(request).hash
                availableReactionsHashes += hash
                if (hash == REACTIONS_HASH) return@on FakeAnswer.Result(AvailableReactionsNotModified)
                fun reaction(emoji: String, inactive: Boolean = false, premium: Boolean = false) = AvailableReaction(
                    inactive = inactive, premium = premium, reaction = emoji, title = emoji, staticIcon = DocumentEmpty(1), appearAnimation = DocumentEmpty(2),
                    selectAnimation = DocumentEmpty(3), activateAnimation = DocumentEmpty(4), effectAnimation = DocumentEmpty(5),
                )
                val all = listOf(reaction(THUMBS_UP), reaction(HEART), reaction(FIRE), reaction("\uD83E\uDD21", inactive = true), reaction("\uD83E\uDD84", premium = true))
                FakeAnswer.Result(AvailableReactions(REACTIONS_HASH, all))
            }
            server.rpc.on(GetTopReactions.CONSTRUCTOR_ID) { request ->
                if (GetTopReactions.decode(request).hash == TOP_REACTIONS_HASH) return@on FakeAnswer.Result(ReactionsNotModified)
                FakeAnswer.Result(Reactions(TOP_REACTIONS_HASH, listOf(ReactionEmoji(FIRE))))
            }
            server.rpc.on(GetRecentReactions.CONSTRUCTOR_ID) { FakeAnswer.Result(Reactions(0, emptyList())) }
            server.rpc.on(GetFullChannel.CONSTRUCTOR_ID) { request ->
                val channel = GetFullChannel.decode(request).channel as? InputChannel
                channel?.let { groups.fullChannel(it.channelId) }?.let { return@on FakeAnswer.Result(it) }
                if (channel?.channelId != NEWS_ID) return@on FakeAnswer.Error(400, "CHANNEL_INVALID")
                val full = ChannelFull(
                    id = NEWS_ID, about = "news about kotlin", readInboxMaxId = 0, readOutboxMaxId = 0, unreadCount = 0, chatPhoto = PhotoEmpty(0),
                    notifySettings = PeerNotifySettings(), botInfo = emptyList(), pts = 1, participantsCount = 1_200,
                    availableReactions = ChatReactionsSome(listOf(ReactionEmoji(THUMBS_UP), ReactionEmoji(FIRE))),
                )
                FakeAnswer.Result(MessagesChatFull(full, listOf(news()), emptyList()))
            }
            server.rpc.on(GetMessageReactionsList.CONSTRUCTOR_ID) { request ->
                val call = GetMessageReactionsList.decode(request)
                reactorsCalls += call.id to call.offset
                val first = call.offset == null
                val peer = if (first) alice else bob
                FakeAnswer.Result(
                    MessageReactionsList(
                        count = 2,
                        reactions = listOf(MessagePeerReaction(peerId = PeerUser(peer.id), date = NOW, reaction = ReactionEmoji(THUMBS_UP))),
                        chats = emptyList(),
                        users = listOf(peer),
                        nextOffset = if (first) REACTORS_NEXT else null,
                    ),
                )
            }
            server.rpc.on(GetUnreadReactions.CONSTRUCTOR_ID) { request ->
                val call = GetUnreadReactions.decode(request)
                val userId = (call.peer as? InputPeerUser)?.userId ?: return@on FakeAnswer.Error(400, "PEER_ID_INVALID")
                unreadReactionsCalls += call.minId
                val ids = unreadReactions[userId].orEmpty().filter { it >= call.minId }.sortedDescending().take(call.limit)
                FakeAnswer.Result(Messages(ids.map { Message(out = true, id = it, peerId = PeerUser(userId), date = NOW, message = "liked") }, emptyList(), emptyList(), emptyList()))
            }
            server.rpc.on(ReadReactions.CONSTRUCTOR_ID) { request ->
                val userId = (ReadReactions.decode(request).peer as? InputPeerUser)?.userId ?: return@on FakeAnswer.Error(400, "PEER_ID_INVALID")
                readReactionsCalls += userId
                unreadReactions.remove(userId)
                FakeAnswer.Result(AffectedHistory(pts = cluster.updates.pts, ptsCount = 0, offset = 0))
            }
            server.rpc.on(SendReaction.CONSTRUCTOR_ID) { request ->
                val call = SendReaction.decode(request)
                lastSendReaction = call
                val index = saved.indexOfFirst { it.id == call.msgId }
                if (call.peer != InputPeerSelf || index < 0) return@on FakeAnswer.Error(400, "MESSAGE_ID_INVALID")
                val old = saved[index]
                val reactions = reacted(old.reactions, call.reaction.orEmpty())
                saved[index] = old.withReactions(reactions)
                FakeAnswer.Result(Updates(listOf(UpdateMessageReactions(PeerUser(selfId), call.msgId, reactions = reactions)), listOf(alice, bob), emptyList(), NOW, 0))
            }
            server.rpc.on(ContactsSearch.CONSTRUCTOR_ID) { request ->
                val query = ContactsSearch.decode(request).q.lowercase()
                val news = Channel(id = NEWS_ID, accessHash = 5, title = "Kotlin News", username = "kotlinnews", photo = ChatPhotoEmpty, date = NOW, broadcast = true)
                val mine = listOf(alice, bob).filter { it.firstName.orEmpty().lowercase().startsWith(query) }
                val global = if ("kotlin".startsWith(query) || query.startsWith("kot")) listOf(news) else emptyList()
                val people = if (query.length >= 2 && "carol".startsWith(query)) listOf(carol) else emptyList()
                FakeAnswer.Result(Found(mine.map { PeerUser(it.id) }, global.map { PeerChannel(it.id) } + people.map { PeerUser(it.id) }, global, mine + people))
            }
            server.rpc.on(GetRecentLocations.CONSTRUCTOR_ID) { request -> attach.recent(GetRecentLocations.decode(request)) }
            server.rpc.on(EditMessage.CONSTRUCTOR_ID) { request ->
                val call = EditMessage.decode(request)
                editCalls += call
                sending.edit(call)?.let { return@on it }
                attach.edit(call)?.let { return@on it }
                val index = saved.indexOfFirst { it.id == call.id }
                if (index < 0) return@on FakeAnswer.Error(400, "MESSAGE_ID_INVALID")
                val old = saved[index]
                val rich = call.richMessage?.let { input -> articles.accept(input).let { (kept, error) -> kept ?: return@on FakeAnswer.Error(400, error!!) } } ?: old.richMessage
                val entities = call.entities?.map(::storedEntity) ?: old.entities
                saved[index] = Message(out = old.out, id = old.id, peerId = old.peerId, date = old.date, message = call.message ?: old.message, editDate = NOW, replyTo = old.replyTo, media = old.media, entities = entities, richMessage = rich)
                FakeAnswer.Result(Updates(emptyList(), emptyList(), emptyList(), NOW, 0))
            }
            server.rpc.on(ReadMessageContents.CONSTRUCTOR_ID) { request ->
                readContents += ReadMessageContents.decode(request).id
                FakeAnswer.Result(AffectedMessages(pts = 1, ptsCount = 1))
            }
            server.rpc.on(DeleteMessages.CONSTRUCTOR_ID) { request ->
                val call = DeleteMessages.decode(request)
                saved.removeAll { it.id in call.id }
                sharedMedia.delete(call.id)
                lastRevoke = call.revoke
                FakeAnswer.Result(AffectedMessages(pts = 1, ptsCount = call.id.size))
            }
            server.rpc.on(SendMedia.CONSTRUCTOR_ID) { request ->
                val call = SendMedia.decode(request)
                sendMediaCalls++
                if (failMediaSends > 0) {
                    failMediaSends--
                    return@on FakeAnswer.Error(403, "CHAT_SEND_MEDIA_FORBIDDEN")
                }
                if (!sentRandomIds.add(call.randomId)) return@on FakeAnswer.Error(500, "RANDOM_ID_DUPLICATE")
                lastSendMedia = call
                attach.accept(call)?.let { return@on it }
                (call.media as? InputMediaContact)?.let { contact ->
                    contactSends += call
                    val card = MessageMediaContact(contact.phoneNumber, contact.firstName, contact.lastName, contact.vcard, 0)
                    return@on FakeAnswer.Result(sentUpdates(listOf(Message(out = true, id = sentId(call.peer), fromId = PeerUser(selfId), peerId = peerOf(call.peer), date = sentDate(), message = "", media = card) to call.randomId)))
                }
                val reply = (call.replyTo as? InputReplyToMessage)?.let { MessageReplyHeader(replyToMsgId = it.replyToMsgId) }
                fun sent(id: Int, message: String, media: TypeMessageMedia, entities: List<TypeMessageEntity>? = null): FakeAnswer {
                    saved += Message(out = true, id = id, peerId = PeerUser(selfId), date = NOW, message = message, entities = entities, media = media, replyTo = reply)
                    val peer = peerOf(call.peer)
                    val own = Message(
                        out = true, silent = call.silent, post = isBroadcast(peer), id = id, fromId = senderIn(peer), peerId = peer, date = sentDate(), message = message,
                        entities = entities, media = media, replyTo = reply,
                    )
                    return FakeAnswer.Result(sentUpdates(listOf(own to call.randomId)))
                }
                (call.media as? InputMediaDocument)?.let { media ->
                    val input = media.id as? InputDocument ?: return@on FakeAnswer.Error(400, "MEDIA_INVALID")
                    val document = (stickerDocuments() + gifDocuments()).firstOrNull { it.id == input.id && it.accessHash == input.accessHash } ?: return@on FakeAnswer.Error(400, "MEDIA_INVALID")
                    if (!input.fileReference.contentEquals(stickerReference)) return@on FakeAnswer.Error(400, "FILE_REFERENCE_EXPIRED")
                    return@on sent(sentId(call.peer), "", MessageMediaDocument(document = document))
                }
                val document = documentOfUpload(call.media, UPLOADED_ID + nextIdIn(call.peer)) ?: return@on FakeAnswer.Error(400, "MEDIA_INVALID")
                lastUpload = uploaded[document.id]
                val media = MessageMediaDocument(document = document)
                val entities = call.entities?.map(::storedEntity)
                if (call.scheduleDate != null) {
                    sending.schedule { id -> Message(out = true, id = id, peerId = PeerUser(selfId), date = call.scheduleDate!!, message = call.message, entities = entities, media = media, replyTo = reply) }
                    return@on FakeAnswer.Result(Updates(emptyList(), emptyList(), emptyList(), NOW, 0))
                }
                sent(sentId(call.peer), call.message, media, entities)
            }
            server.rpc.on(GetFile.CONSTRUCTOR_ID) { request ->
                val call = GetFile.decode(request)
                stories.file(call)?.let { return@on it }
                when (val location = call.location) {
                    is InputPeerPhotoFileLocation -> FakeAnswer.Result(File(FilePartial, 0, if (call.offset == 0L) avatarBytes else ByteArray(0)))
                    is InputPhotoFileLocation -> {
                        val bytes = photoSizes[location.thumbSize]
                        if (location.id != PHOTO_ID || bytes == null) return@on FakeAnswer.Error(400, "LOCATION_INVALID")
                        photoDownloads++
                        photoTypes += location.thumbSize
                        val start = minOf(call.offset, bytes.size.toLong()).toInt()
                        FakeAnswer.Result(File(FilePartial, 0, bytes.copyOfRange(start, minOf(start + call.limit, bytes.size))))
                    }
                    is InputStickerSetThumb -> {
                        val id = (location.stickerset as? InputStickerSetID)?.id
                        val set = stickerSets().firstOrNull { it.id == id && it.thumbVersion == location.thumbVersion }
                        val bytes = set?.let { setThumbFiles[it.id] } ?: return@on FakeAnswer.Error(400, "LOCATION_INVALID")
                        setThumbDownloads += SetThumbDownload(set.id, location.thumbVersion, server.dcId)
                        if (server.dcId != set.thumbDcId) return@on FakeAnswer.Error(303, "FILE_MIGRATE_${set.thumbDcId}")
                        FakeAnswer.Result(File(FilePartial, 0, if (call.offset == 0L) bytes else ByteArray(0)))
                    }
                    is InputDocumentFileLocation -> {
                        wallpapers.file(call, location)?.let { return@on it }
                        stickerFiles[location.id]?.let { bytes ->
                            if (!location.fileReference.contentEquals(stickerReference)) return@on FakeAnswer.Error(400, "FILE_REFERENCE_EXPIRED")
                            if (location.thumbSize.isEmpty() && call.offset == 0L) documentDownloads += location.id
                            val file = if (location.thumbSize.isEmpty()) bytes else stickerPreviewBytes.also { stickerThumbDownloads += "${location.id}@${location.thumbSize}" }
                            return@on FakeAnswer.Result(File(FilePartial, 0, if (call.offset == 0L) file else ByteArray(0)))
                        }
                        if (location.id == SAVED_GIF_ID || location.id == FOUND_GIF_ID) {
                            if (location.thumbSize.isEmpty() && call.offset == 0L) documentDownloads += location.id
                            val file = if (location.thumbSize.isEmpty()) gifBytes else stickerPreviewBytes
                            return@on FakeAnswer.Result(File(FilePartial, 0, if (call.offset == 0L) file else ByteArray(0)))
                        }
                        uploaded[location.id]?.let { bytes ->
                            val start = minOf(call.offset, bytes.size.toLong()).toInt()
                            return@on FakeAnswer.Result(File(FilePartial, 0, bytes.copyOfRange(start, minOf(start + call.limit, bytes.size))))
                        }
                        if (location.id == STICKER_ID) {
                            val start = minOf(call.offset, stickerBytes.size.toLong()).toInt()
                            return@on FakeAnswer.Result(File(FilePartial, 0, stickerBytes.copyOfRange(start, minOf(start + call.limit, stickerBytes.size))))
                        }
                        if (location.id >= MUSIC_ID && location.thumbSize == "m") {
                            return@on FakeAnswer.Result(File(FilePartial, 0, if (call.offset == 0L) musicCoverBytes else ByteArray(0)))
                        }
                        if (location.id == VIDEO_ID && location.thumbSize == "m") {
                            return@on FakeAnswer.Result(File(FilePartial, 0, if (call.offset == 0L) videoThumbBytes else ByteArray(0)))
                        }
                        if (location.id != FakeCluster.FILE_ID && location.id != VIDEO_ID) return@on FakeAnswer.Error(400, "FILE_ID_INVALID")
                        if (server.dcId != cluster.fileDcId) return@on FakeAnswer.Error(303, "FILE_MIGRATE_${cluster.fileDcId}")
                        if (location.id == VIDEO_ID && !location.fileReference.contentEquals(videoReference)) return@on FakeAnswer.Error(400, "FILE_REFERENCE_EXPIRED")
                        cluster.downloadPartErrors[call.offset]?.poll()?.let { return@on it }
                        fileOffsets += call.offset
                        val bytes = cluster.fileBytes
                        val start = minOf(call.offset, bytes.size.toLong()).toInt()
                        FakeAnswer.Result(File(FilePartial, 0, bytes.copyOfRange(start, minOf(start + call.limit, bytes.size))))
                    }
                    else -> FakeAnswer.Error(400, "LOCATION_INVALID")
                }
            }
        }
    }

    suspend fun revokeSession() {
        cluster.revokeAuthorization(selfId, "SESSION_REVOKED")
        cluster.updates.tooLong()
    }

    val stickerBytes: ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write("""{"v":"5.7.4","fr":60,"ip":0,"op":60,"w":512,"h":512,"layers":[]}""".encodeToByteArray()) }
    }.toByteArray()

    fun addStickerMessage() {
        val document = Document(
            id = STICKER_ID,
            accessHash = 1,
            fileReference = byteArrayOf(2),
            date = NOW,
            mimeType = "application/x-tgsticker",
            size = stickerBytes.size.toLong(),
            dcId = 2,
            attributes = listOf(DocumentAttributeSticker(alt = "\uD83D\uDE00", stickerset = InputStickerSetEmpty), DocumentAttributeFilename("sticker.tgs")),
        )
        saved += Message(out = true, id = nextId++, peerId = PeerUser(selfId), date = NOW, message = "", media = MessageMediaDocument(document = document))
    }

    val photoBytes: ByteArray = jpeg(800, 600, 0.8f)

    val strippedBytes: ByteArray = jpeg(40, 30, 0.2f).let { full ->
        var at = 2
        while (true) {
            val marker = full[at + 1].toInt() and 0xff
            val length = ((full[at + 2].toInt() and 0xff) shl 8) or (full[at + 3].toInt() and 0xff)
            at += 2 + length
            if (marker == 0xda) break
        }
        byteArrayOf(1, 30, 40) + full.copyOfRange(at, full.size - 2)
    }

    private fun jpeg(width: Int, height: Int, quality: Float): ByteArray {
        val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) for (y in 0 until height) image.setRGB(x, y, if (x < width / 2) 0x40a0e0 else 0xe0a040)
        val writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        javax.imageio.ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            val param = writer.defaultWriteParam.apply {
                compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality
            }
            writer.write(null, javax.imageio.IIOImage(image, null, null), param)
        }
        writer.dispose()
        return out.toByteArray()
    }

    val photoSizes: Map<String, ByteArray> = mapOf("m" to jpeg(320, 240, 0.8f), "x" to photoBytes, "w" to jpeg(1280, 960, 0.8f))

    val photo = Photo(
        id = PHOTO_ID,
        accessHash = 1,
        fileReference = byteArrayOf(4),
        date = NOW,
        sizes = listOf(
            PhotoStrippedSize("i", strippedBytes),
            PhotoSize("m", 320, 240, photoSizes.getValue("m").size),
            PhotoSize("x", 800, 600, photoBytes.size),
            PhotoSize("w", 1280, 960, photoSizes.getValue("w").size),
        ),
        dcId = 2,
    )

    fun addPhotoMessage() {
        saved += Message(id = nextId++, peerId = PeerUser(selfId), date = NOW, message = "", media = MessageMediaPhoto(photo = photo))
    }

    fun addAlbum(): List<Int> = List(2) { index ->
        val id = nextId++
        saved += Message(id = id, peerId = PeerUser(selfId), date = NOW, message = if (index == 0) "two cats" else "", media = MessageMediaPhoto(photo = photo), groupedId = ALBUM_ID)
        id
    }

    internal fun saveMessage(make: (id: Int) -> Message): Int {
        val id = nextId++
        saved += make(id)
        return id
    }

    internal fun rememberRandomId(randomId: Long): Boolean = sentRandomIds.add(randomId)

    internal fun newMessageId(): Int = nextId++

    private fun nextIdIn(peer: TypeInputPeer): Int {
        val newest = if ((peer as? InputPeerUser)?.userId == helperBot.id) bots.helperHistory.maxOf { it.id } else wallpapers.history(peer).maxOfOrNull { it.id } ?: 0
        nextId = maxOf(nextId, newest + 1)
        return nextId
    }

    private fun sentId(peer: TypeInputPeer): Int = nextIdIn(peer).also { nextId++ }

    private val sendClock = AtomicInteger(NOW)

    internal fun sentDate(): Int = sendClock.incrementAndGet()

    internal fun peerOf(peer: TypeInputPeer): TypePeer = when (peer) {
        is InputPeerChat -> PeerChat(peer.chatId)
        is InputPeerChannel -> PeerChannel(peer.channelId)
        is InputPeerUserFromMessage -> PeerUser(peer.userId)
        else -> PeerUser(peerIdOf(peer))
    }

    private fun originalOf(peer: TypeInputPeer, id: Int): Message? {
        val chat = when {
            peer == InputPeerSelf -> saved
            (peer as? InputPeerChannel)?.channelId == NEWS_ID -> newsPosts
            (peer as? InputPeerChannel)?.channelId == BOOK_CLUB_ID -> bookClubMessages
            else -> incoming + groups.history(peer)?.first.orEmpty()
        }
        return chat.firstOrNull { it.id == id }
    }

    private val channelPts = ConcurrentHashMap<Long, Int>()

    fun channelPts(id: Long): Int = channelPts[id] ?: CHANNEL_START_PTS

    internal fun sentUpdates(sent: List<Pair<Message, Long?>>, more: List<TypeUpdate> = emptyList()): Updates {
        val updates = sent.flatMap { (message, randomId) -> listOfNotNull(randomId?.let { UpdateMessageID(message.id, it) }, newMessage(message)) }
        val messages = sent.map { it.first }
        val users = (listOf(selfId) + messages.flatMap { listOfNotNull((it.peerId as? PeerUser)?.userId, it.viaBotId) }).distinct().mapNotNull(::userOf)
        val chats = messages.mapNotNull { chatOf(it.peerId) }.distinct()
        return Updates(updates + more, users, chats, NOW, 0)
    }

    private fun newMessage(message: Message): TypeUpdate {
        val channel = (message.peerId as? PeerChannel)?.channelId ?: return UpdateNewMessage(message, cluster.updates.sent(message), 1)
        return groups.discussion.posted(channel, message) ?: groups.forum.posted(channel, message)
            ?: UpdateNewChannelMessage(message, channelPts.merge(channel, CHANNEL_START_PTS + 1) { old, _ -> old + 1 }!!, 1)
    }

    private fun userOf(id: Long): User? = when {
        id == selfId -> User(self = true, id = selfId, accessHash = 1, firstName = "Test", lastName = "User")
        strangers.containsKey(id) -> cluster.updates.user(id)
        else -> (listOf(alice, bob, carol, helperBot, bots.gifBot) + listed).firstOrNull { it.id == id }
    }

    private fun chatOf(peer: TypePeer): TypeChat? = when (peer) {
        is PeerChat -> groups.family().takeIf { it.id == peer.chatId }
        is PeerChannel -> (listOf(news(), bookClub()) + groups.channels()).firstOrNull { it.id == peer.channelId }
        else -> null
    }

    private fun isBroadcast(peer: TypePeer): Boolean = (chatOf(peer) as? Channel)?.broadcast == true

    private fun senderIn(peer: TypePeer): TypePeer? = if (isBroadcast(peer)) null else PeerUser(selfId)

    internal fun documentOfUpload(media: TypeInputMedia, id: Long): Document? {
        val (file, mimeType) = when (media) {
            is InputMediaUploadedDocument -> media.file to media.mimeType
            is InputMediaUploadedPhoto -> media.file to "image/jpeg"
            else -> return null
        }
        val (fileId, parts, name) = when (file) {
            is InputFile -> Triple(file.id, file.parts, file.name)
            is InputFileBig -> Triple(file.id, file.parts, file.name)
            else -> return null
        }
        val bytes = cluster.uploadedFile(fileId, parts) ?: return null
        lastUpload = bytes
        uploaded[id] = bytes
        return Document(
            id = id,
            accessHash = 1,
            fileReference = byteArrayOf(3),
            date = NOW,
            mimeType = mimeType,
            size = bytes.size.toLong(),
            dcId = 2,
            attributes = (media as? InputMediaUploadedDocument)?.attributes ?: listOf(DocumentAttributeFilename(name)),
        )
    }

    val videoThumbBytes: ByteArray = jpeg(320, 180, 0.8f)

    @Volatile var videoReference: ByteArray = byteArrayOf(5)
        private set

    fun expireVideoReference() {
        videoReference = byteArrayOf(6, videoReference.last())
        saved.replaceAll { m ->
            if ((m.media as? MessageMediaDocument)?.document.let { it is Document && it.id == VIDEO_ID }) {
                Message(id = m.id, peerId = m.peerId, date = m.date, message = m.message, media = MessageMediaDocument(video = true, document = videoDocument()))
            } else {
                m
            }
        }
    }

    fun addVideoMessage() {
        saved += Message(id = nextId++, peerId = PeerUser(selfId), date = NOW, message = "", media = MessageMediaDocument(video = true, document = videoDocument()))
    }

    fun videoDocument() = Document(
        id = VIDEO_ID,
        accessHash = 1,
        fileReference = videoReference,
        date = NOW,
        mimeType = "video/mp4",
        size = cluster.fileBytes.size.toLong(),
        thumbs = listOf(PhotoStrippedSize("i", strippedBytes), PhotoSize("m", 320, 180, videoThumbBytes.size)),
        dcId = cluster.fileDcId,
        attributes = listOf(DocumentAttributeVideo(supportsStreaming = true, duration = 83.6, w = 1280, h = 720), DocumentAttributeFilename("clip.mp4")),
    )

    fun addLinkMessage() {
        val page = WebPage(
            id = 31,
            url = "https://core.telegram.org/api/files",
            displayUrl = "core.telegram.org/api/files",
            hash = 0,
            siteName = "Telegram APIs",
            title = "Uploading and Downloading Files",
            description = "How to transfer large data batches correctly.",
            photo = photo,
        )
        saved += Message(id = nextId++, peerId = PeerUser(selfId), date = NOW, message = "see https://core.telegram.org/api/files", media = MessageMediaWebPage(webpage = page))
    }

    val entityText = "\uD83D\uDE00 ask @carol_writer, read this post or example.com/docs"

    fun addEntityMessage(): Int {
        val id = nextId++
        fun at(part: String) = entityText.indexOf(part)
        val entities = listOf(
            MessageEntityMention(at("@carol_writer"), "@carol_writer".length),
            MessageEntityTextUrl(at("this post"), "this post".length, "https://t.me/kotlinnews/30"),
            MessageEntityUrl(at("example.com/docs"), "example.com/docs".length),
        )
        saved += Message(id = id, peerId = PeerUser(selfId), date = NOW, message = entityText, entities = entities)
        return id
    }

    val richText = "\uD83D\uDE00 bold italic under strike spoiler code\nfun main() {}\nquoted words\n#kotlin \$TON /start a@b.co +16502530000 4111111111111111 Alice tomorrow"

    fun addRichMessage(): Int {
        val id = nextId++
        fun at(part: String) = richText.indexOf(part).also { check(it >= 0) { part } }
        val entities = listOf(
            MessageEntityBold(at("bold"), 4),
            MessageEntityItalic(at("italic"), 6),
            MessageEntityUnderline(at("under"), 5),
            MessageEntityStrike(at("strike"), 6),
            MessageEntitySpoiler(at("spoiler"), 7),
            MessageEntityCode(at("code\n"), 4),
            MessageEntityPre(at("fun main"), "fun main() {}".length, "kotlin"),
            MessageEntityBlockquote(collapsed = true, offset = at("quoted"), length = "quoted words".length),
            MessageEntityHashtag(at("#kotlin"), 7),
            MessageEntityCashtag(at("\$TON"), 4),
            MessageEntityBotCommand(at("/start"), 6),
            MessageEntityEmail(at("a@b.co"), 6),
            MessageEntityPhone(at("+1650"), 12),
            MessageEntityBankCard(at("4111"), 16),
            MessageEntityMentionName(at("Alice"), 5, alice.id),
            MessageEntityFormattedDate(relative = true, offset = at("tomorrow"), length = 8, date = NOW + 86_400),
        )
        saved += Message(id = id, peerId = PeerUser(selfId), date = NOW, message = richText, entities = entities)
        return id
    }

    fun addSavedMessages(count: Int) {
        repeat(count) { saved += Message(out = true, id = nextId++, peerId = PeerUser(selfId), date = NOW, message = "note $it") }
    }

    fun addHashtagMessage(): Int {
        val id = nextId++
        val text = "weekly #kotlin news"
        saved += Message(id = id, peerId = PeerUser(selfId), date = NOW, message = text, entities = listOf(MessageEntityHashtag(text.indexOf("#kotlin"), 7)))
        return id
    }

    fun addForwardedMessage(): Int {
        val id = nextId++
        saved += Message(id = id, peerId = PeerUser(selfId), date = NOW, message = "post 5", fwdFrom = MessageFwdHeader(fromId = PeerChannel(NEWS_ID), date = NOW - 60, channelPost = 5))
        return id
    }

    fun addReply(replyTo: Int): Int {
        val id = nextId++
        saved += Message(out = true, id = id, peerId = PeerUser(selfId), date = NOW, message = "an answer", replyTo = MessageReplyHeader(replyToMsgId = replyTo))
        return id
    }

    suspend fun pushReply(replyTo: Int): Int {
        val id = nextId++
        val message = Message(out = true, id = id, peerId = PeerUser(selfId), date = NOW, message = "a live answer", replyTo = MessageReplyHeader(replyToMsgId = replyTo))
        saved += message
        cluster.updates.message(selfId, message.message, push = false)
        val pts = (cluster.updates.last as UpdateShortMessage).pts
        cluster.updates.push(Updates(listOf(UpdateNewMessage(message, pts, 1)), emptyList(), emptyList(), NOW, 0))
        return id
    }

    fun addSaved(make: (id: Int) -> Message): Int {
        val id = nextId++
        saved += make(id)
        return id
    }

    fun savedMessage(id: Int): Message? = saved.firstOrNull { it.id == id }

    fun savedMessages(): List<Message> = saved.toList()

    fun addSavedService(make: (id: Int) -> MessageService): Int {
        val id = nextId++
        services += make(id)
        return id
    }

    fun replaceSaved(message: Message) {
        val index = saved.indexOfFirst { it.id == message.id }
        if (index >= 0) saved[index] = message
    }

    val voiceCalls: MutableList<Search> = CopyOnWriteArrayList()

    val readContents: MutableList<Int> = CopyOnWriteArrayList()

    fun addVoiceMessages(unread: List<Boolean>, own: Set<Int> = emptySet()): List<Int> = unread.mapIndexed { n, notListened ->
        val document = Document(
            id = VOICE_ID + n,
            accessHash = 1,
            fileReference = byteArrayOf(4),
            date = NOW,
            mimeType = "audio/ogg",
            size = 20_000,
            dcId = 2,
            attributes = listOf(DocumentAttributeAudio(voice = true, duration = 3, waveform = byteArrayOf(1, 2, 3))),
        )
        val id = nextId++
        val out = n in own
        saved += Message(
            out = out,
            mediaUnread = notListened,
            id = id,
            fromId = PeerUser(if (out) selfId else alice.id),
            peerId = PeerUser(selfId),
            date = NOW,
            message = "",
            media = MessageMediaDocument(document = document),
        )
        id
    }

    val musicCalls: MutableList<Search> = CopyOnWriteArrayList()

    val musicCoverBytes: ByteArray = ByteArray(96) { (it * 3).toByte() }

    fun addMusicMessages(count: Int): List<Int> = (1..count).map { n ->
        val document = Document(
            id = MUSIC_ID + n,
            accessHash = 1,
            fileReference = byteArrayOf(3),
            date = NOW,
            mimeType = "audio/mpeg",
            size = 4_000_000,
            dcId = 2,
            thumbs = listOf(PhotoSize("m", 320, 320, musicCoverBytes.size)),
            attributes = listOf(DocumentAttributeAudio(duration = 185, title = "Song $n", performer = "Band"), DocumentAttributeFilename("song$n.mp3")),
        )
        val id = nextId++
        saved += Message(out = true, id = id, peerId = PeerUser(selfId), date = NOW, message = "", media = MessageMediaDocument(document = document))
        id
    }

    fun addDocumentMessage(): Int {
        val document = Document(
            id = FakeCluster.FILE_ID,
            accessHash = 1,
            fileReference = byteArrayOf(1),
            date = NOW,
            mimeType = "application/octet-stream",
            size = cluster.fileBytes.size.toLong(),
            dcId = cluster.fileDcId,
            attributes = listOf(DocumentAttributeFilename("big.bin")),
        )
        val id = nextId++
        saved += Message(id = id, peerId = PeerUser(selfId), date = NOW, message = "", media = MessageMediaDocument(document = document))
        return id
    }

    val installedSets: MutableSet<Long> = ConcurrentHashMap.newKeySet<Long>().apply { add(CATS_ID) }

    val setOrder: MutableList<Long> = CopyOnWriteArrayList()

    val reorders: MutableList<Pair<Boolean, List<Long>>> = CopyOnWriteArrayList()

    val favedStickers: MutableList<Long> = CopyOnWriteArrayList(listOf(CAT_TWO_ID))

    private fun favedHash(): Long = if (favedStickers == listOf(CAT_TWO_ID)) FAVED_HASH else FAVED_HASH + favedStickers.hashCode()

    private fun ordered(sets: List<StickerSet>): List<StickerSet> = sets.sortedBy { set -> setOrder.indexOf(set.id).let { if (it < 0) Int.MAX_VALUE else it } }

    val savedGifs: MutableList<Long> = CopyOnWriteArrayList(listOf(SAVED_GIF_ID))

    val savedGifsHashes: MutableList<Long> = CopyOnWriteArrayList()

    val stickerSearches: MutableList<String> = CopyOnWriteArrayList()

    val categorySearches: MutableList<Pair<String, Boolean>> = CopyOnWriteArrayList()

    val featuredHashes: MutableList<Long> = CopyOnWriteArrayList()

    val readFeatured: MutableList<Long> = CopyOnWriteArrayList()

    val emojiGroupHashes: MutableList<Int> = CopyOnWriteArrayList()

    private fun savedGifsHash(): Long = GIFS_HASH + savedGifs.size

    fun gifDocuments(): List<Document> = listOf(SAVED_GIF_ID, FOUND_GIF_ID).map { id ->
        Document(
            id = id,
            accessHash = id + 1,
            fileReference = stickerReference,
            date = NOW,
            mimeType = "video/mp4",
            size = gifBytes.size.toLong(),
            thumbs = listOf(PhotoSize("m", 90, 90, stickerPreviewBytes.size)),
            dcId = 2,
            attributes = listOf(DocumentAttributeAnimated, DocumentAttributeVideo(duration = 1.0, w = 240, h = 180), DocumentAttributeFilename("gif.mp4")),
        )
    }

    val gifBytes: ByteArray = byteArrayOf(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70)

    val allStickersHashes: MutableList<Long> = CopyOnWriteArrayList()

    @Volatile var stickerReference: ByteArray = byteArrayOf(7)
        private set

    fun expireStickerReference() {
        stickerReference = byteArrayOf(8, stickerReference.last())
    }

    val webpBytes: ByteArray = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()

    private val stickerFiles get() = mapOf(CAT_ID to stickerBytes, CAT_TWO_ID to stickerBytes, DOG_ID to webpBytes, CLIP_ID to videoStickerBytes, EMOJI_STAR to stickerBytes, EMOJI_HEART to webpBytes) +
        GIFT_STICKERS.associateWith { stickerBytes }

    val documentDownloads: MutableList<Long> = CopyOnWriteArrayList()

    val stickerSetHashes: MutableList<Pair<Long, Int>> = CopyOnWriteArrayList()

    val emojiStickersHashes: MutableList<Long> = CopyOnWriteArrayList()

    val recentHashes: MutableList<Long> = CopyOnWriteArrayList()
    val favedHashes: MutableList<Long> = CopyOnWriteArrayList()

    val customEmojiCalls: MutableList<List<Long>> = CopyOnWriteArrayList()

    fun customEmojiDocuments(): List<Document> = listOf(
        customEmoji(EMOJI_STAR, "\u2B50", "application/x-tgsticker", textColor = true),
        customEmoji(EMOJI_HEART, "\u2764", "image/webp", textColor = false),
    )

    private fun customEmoji(id: Long, alt: String, mimeType: String, textColor: Boolean) = Document(
        id = id,
        accessHash = id + 1,
        fileReference = stickerReference,
        date = NOW,
        mimeType = mimeType,
        size = stickerFiles.getValue(id).size.toLong(),
        dcId = 2,
        attributes = listOf(DocumentAttributeCustomEmoji(textColor = textColor, alt = alt, stickerset = InputStickerSetID(EMOJI_SET, 1)), DocumentAttributeFilename("emoji")),
    )

    val emojiText = "nice \u2B50 and \u2764"

    fun addCustomEmojiMessages() {
        fun at(part: String) = emojiText.indexOf(part)
        saved += Message(
            id = nextId++, peerId = PeerUser(selfId), date = NOW, message = emojiText,
            entities = listOf(MessageEntityCustomEmoji(at("\u2B50"), 1, EMOJI_STAR), MessageEntityCustomEmoji(at("\u2764"), 1, EMOJI_HEART)),
        )
        saved += Message(
            id = nextId++, peerId = PeerUser(selfId), date = NOW, message = "\u2B50 \u2B50",
            entities = listOf(MessageEntityCustomEmoji(0, 1, EMOJI_STAR), MessageEntityCustomEmoji(2, 1, EMOJI_MISSING)),
        )
    }

    fun othersReactions(): MessageReactions = MessageReactions(
        canSeeList = true,
        results = listOf(ReactionCount(reaction = ReactionEmoji(THUMBS_UP), count = 2), ReactionCount(reaction = ReactionPaid, count = 5), ReactionCount(reaction = ReactionCustomEmoji(EMOJI_HEART), count = 1)),
        recentReactions = listOf(
            MessagePeerReaction(peerId = PeerUser(alice.id), date = NOW, reaction = ReactionEmoji(THUMBS_UP)),
            MessagePeerReaction(peerId = PeerUser(bob.id), date = NOW, reaction = ReactionEmoji(THUMBS_UP)),
        ),
    )

    fun addReactedMessage(): Int {
        val id = nextId++
        saved += Message(id = id, peerId = PeerUser(selfId), date = NOW, message = "react to me", reactions = othersReactions())
        return id
    }

    suspend fun pushReactions(id: Int, reactions: MessageReactions) {
        val index = saved.indexOfFirst { it.id == id }
        saved[index] = saved[index].withReactions(reactions)
        cluster.updates.push(UpdateShort(UpdateMessageReactions(PeerUser(selfId), id, reactions = reactions), NOW))
    }

    suspend fun pushRichDraft(userId: Long, rich: RichMessage) {
        val draft = DraftMessage(message = "", date = NOW + 60, richMessage = rich)
        drafts[userId] = draft
        cluster.updates.push(UpdateShort(UpdateDraftMessage(PeerUser(userId), draft = draft), NOW))
    }

    suspend fun pushDraft(userId: Long, text: String?, entities: List<TypeMessageEntity>? = null) {
        val draft = text?.let { DraftMessage(message = it, entities = entities, date = NOW + 60) }
        if (draft == null) drafts -= userId else drafts[userId] = draft
        cluster.updates.push(UpdateShort(UpdateDraftMessage(PeerUser(userId), draft = draft ?: DraftMessageEmpty()), NOW))
    }

    private fun reacted(old: MessageReactions?, chosen: List<TypeReaction>): MessageReactions {
        val others = old?.results.orEmpty().mapNotNull { count ->
            val left = if (count.chosenOrder != null) count.count - 1 else count.count
            if (left <= 0) null else ReactionCount(reaction = count.reaction, count = left)
        }
        val counts = others.map { count ->
            val order = chosen.indexOf(count.reaction)
            if (order >= 0) ReactionCount(chosenOrder = order, reaction = count.reaction, count = count.count + 1) else count
        } + chosen.filter { reaction -> others.none { it.reaction == reaction } }.mapIndexed { order, reaction -> ReactionCount(chosenOrder = order, reaction = reaction, count = 1) }
        return MessageReactions(canSeeList = true, results = counts, recentReactions = old?.recentReactions)
    }

    private fun Message.withReactions(reactions: MessageReactions?) = Message(
        out = out, id = id, fromId = fromId, peerId = peerId, fwdFrom = fwdFrom, replyTo = replyTo, date = date, message = message, media = media,
        entities = entities, editDate = editDate, reactions = reactions,
    )

    private fun allStickersHash(): Long = 1_000L + installedSets.sorted().sum() + if (setOrder.isEmpty()) 0 else setOrder.hashCode()

    private fun setOf(doc: Document): Long? = doc.attributes.firstNotNullOfOrNull { attribute ->
        when (attribute) {
            is DocumentAttributeSticker -> attribute.stickerset
            is DocumentAttributeCustomEmoji -> attribute.stickerset
            else -> null
        } as? InputStickerSetID
    }?.id

    fun stickerSets(): List<StickerSet> = listOf(
        StickerSet(
            installedDate = if (CATS_ID in installedSets) NOW else null, id = CATS_ID, accessHash = 81, title = "Cats", shortName = "cats",
            thumbs = listOf(PhotoPathSize("j", outlineBytes), PhotoSize("a", 100, 100, stickerBytes.size)), thumbDcId = 2, thumbVersion = 3, count = 2, hash = 11,
        ),
        StickerSet(installedDate = if (DOGS_ID in installedSets) NOW else null, id = DOGS_ID, accessHash = 82, title = "Dogs", shortName = "dogs", count = 1, hash = 12),
        StickerSet(
            installedDate = if (CLIPS_ID in installedSets) NOW else null, id = CLIPS_ID, accessHash = 83, title = "Clips", shortName = "clips",
            thumbs = listOf(PhotoSize("s", 100, 100, webpBytes.size), PhotoSize("v", 100, 100, videoStickerBytes.size)), thumbDcId = 2, thumbVersion = 1, count = 1, hash = 13,
        ),
        StickerSet(
            installedDate = if (BIRDS_ID in installedSets) NOW else null, id = BIRDS_ID, accessHash = 84, title = "Birds", shortName = "birds",
            thumbs = listOf(PhotoSize("s", 100, 100, webpBytes.size)), thumbDcId = 1, thumbVersion = 2, count = 0, hash = 14,
        ),
        StickerSet(
            installedDate = if (EMOJI_SET in installedSets) NOW else null, emojis = true, id = EMOJI_SET, accessHash = 1, title = "Stars", shortName = "stars",
            thumbDocumentId = EMOJI_STAR, count = 2, hash = 15,
        ),
    )

    private val setThumbFiles get() = mapOf(CATS_ID to stickerBytes, CLIPS_ID to videoStickerBytes, BIRDS_ID to webpBytes)

    data class SetThumbDownload(val setId: Long, val version: Int, val dcId: Int)

    val setThumbDownloads: MutableList<SetThumbDownload> = CopyOnWriteArrayList()

    val videoStickerBytes: ByteArray = byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte(), 0x80.toByte())

    fun stickerDocuments(): List<Document> = listOf(
        stickerDocument(CAT_ID, "\uD83D\uDE00", CATS_ID, 81, "application/x-tgsticker", preview = true),
        stickerDocument(CAT_TWO_ID, "\uD83D\uDE3A", CATS_ID, 81, "application/x-tgsticker"),
        stickerDocument(DOG_ID, "\uD83D\uDC36", DOGS_ID, 82, "image/webp"),
        stickerDocument(CLIP_ID, "\uD83D\uDC38", CLIPS_ID, 83, "video/webm", preview = true),
    )

    private fun stickerDocument(id: Long, alt: String, set: Long, setHash: Long, mimeType: String, preview: Boolean = false) = Document(
        id = id,
        accessHash = id + 1,
        fileReference = stickerReference,
        date = NOW,
        mimeType = mimeType,
        size = stickerFiles.getValue(id).size.toLong(),
        thumbs = listOfNotNull(PhotoSize("m", 128, 128, stickerPreviewBytes.size).takeIf { preview }, PhotoPathSize("j", outlineBytes)),
        dcId = 2,
        attributes = listOf(DocumentAttributeSticker(alt = alt, stickerset = InputStickerSetID(set, setHash)), DocumentAttributeFilename("sticker")),
    )

    val outlinePath = "M100,100h312v312h-312z"
    val outlineBytes: ByteArray = outlinePath.drop(1).dropLast(1).map { (192 + OUTLINE_LOOKUP.indexOf(it)).toByte() }.toByteArray()

    val stickerPreviewBytes: ByteArray = ByteArrayOutputStream().also { out ->
        javax.imageio.ImageIO.write(java.awt.image.BufferedImage(128, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", out)
    }.toByteArray()

    val stickerThumbDownloads: MutableList<String> = CopyOnWriteArrayList()

    private fun giftSticker(id: Long) = Document(
        id = id,
        accessHash = id + 1,
        fileReference = stickerReference,
        date = NOW,
        mimeType = "application/x-tgsticker",
        size = stickerBytes.size.toLong(),
        dcId = 2,
        attributes = listOf(DocumentAttributeSticker(alt = "\uD83C\uDF81", stickerset = InputStickerSetEmpty), DocumentAttributeFilename("gift.tgs")),
    )

    fun teddy() = StarGift(limited = true, id = 61, sticker = giftSticker(GIFT_STICKERS[0]), stars = 50, availabilityRemains = 120, availabilityTotal = 1_000, convertStars = 40)

    fun pepe() = StarGiftUnique(
        id = 6_201,
        giftId = 62,
        title = "Plush Pepe",
        slug = "plushpepe-123",
        num = 123,
        attributes = listOf(
            StarGiftAttributeModel(name = "Gold", document = giftSticker(GIFT_STICKERS[1]), rarity = StarGiftAttributeRarity(15)),
            StarGiftAttributeBackdrop(name = "Midnight", backdropId = 3, centerColor = 0x334455, edgeColor = 0x112233, patternColor = 0x556677, textColor = 0xffffff, rarity = StarGiftAttributeRarityRare),
            StarGiftAttributePattern(name = "Star", document = giftSticker(GIFT_STICKERS[2]), rarity = StarGiftAttributeRarity(8)),
        ),
        availabilityIssued = 500,
        availabilityTotal = 1_000,
    )

    val ownGifts: MutableList<SavedStarGift> = CopyOnWriteArrayList(
        listOf(
            SavedStarGift(fromId = PeerUser(501), date = NOW, gift = teddy(), message = TextWithEntities("happy birthday", emptyList()), msgId = 70, convertStars = 40),
            SavedStarGift(fromId = PeerUser(502), date = NOW - 86_400, gift = pepe(), msgId = 71, collectionId = listOf(GIFT_COLLECTION)),
            SavedStarGift(nameHidden = true, unsaved = true, date = NOW - 172_800, gift = teddy(), msgId = 72, convertStars = 40),
        ),
    )

    val giftCollections: MutableList<StarGiftCollection> = CopyOnWriteArrayList(
        listOf(StarGiftCollection(collectionId = GIFT_COLLECTION, title = "Favourites", icon = giftSticker(GIFT_STICKERS[1]), giftsCount = 1, hash = 1)),
    )

    val starGiftHashes: MutableList<Int> = CopyOnWriteArrayList()

    fun addGiftServiceMessage() {
        services += MessageService(id = nextId++, fromId = PeerUser(501), peerId = PeerUser(selfId), date = NOW, action = MessageActionStarGift(gift = teddy(), message = TextWithEntities("for you", emptyList()), convertStars = 40))
    }

    fun addAddUserMessage(from: Long, users: List<Long>, out: Boolean = false) {
        services += MessageService(out = out, id = nextId++, fromId = PeerUser(from), peerId = PeerUser(selfId), date = NOW, action = MessageActionChatAddUser(users))
    }

    fun addDogStickerMessage() {
        saved += Message(id = nextId++, peerId = PeerUser(selfId), date = NOW, message = "", media = MessageMediaDocument(document = stickerDocuments().single { it.id == DOG_ID }))
    }

    fun config(apiId: Int, apiHash: String, logger: ProtocolLogger, storage: SessionStorage = InMemorySessionStorage()) = ClientConfig(
        apiId = apiId,
        apiHash = apiHash,
        testMode = true,
        dcs = cluster.servers.map { it.address },
        serverKeys = listOf(FakeServerKey.shared.publicKey),
        storage = object : SessionStorage by storage {
            override suspend fun save(session: StoredSession) {
                storage.save(session)
                sessionUser = session.userId ?: 0L
            }
        },
        session = SessionConfig(fetchFutureSalts = false),
        logger = logger,
    )

    internal fun storedEntity(entity: TypeMessageEntity): TypeMessageEntity =
        if (entity is InputMessageEntityMentionName) MessageEntityMentionName(entity.offset, entity.length, (entity.userId as InputUser).userId) else entity

    private fun dialog(peer: PeerUser, top: Int, archive: Boolean) = Dialog(
        pinned = peer.userId in pinned,
        peer = peer,
        topMessage = top,
        readInboxMaxId = 0,
        readOutboxMaxId = 0,
        unreadCount = if (peer.userId != selfId && peer.userId !in read) 1 else 0,
        unreadMentionsCount = 0,
        unreadReactionsCount = unreadReactions[peer.userId]?.size ?: 0,
        unreadPollVotesCount = 0,
        notifySettings = PeerNotifySettings(muteUntil = muteUntilOf(peer.userId)),
        folderId = if (archive) 1 else null,
        draft = drafts[peer.userId],
    )

    override fun close() = cluster.close()

    val late: MutableMap<String, kotlinx.coroutines.CompletableDeferred<Unit>> = ConcurrentHashMap()

    suspend fun answerLate(name: String) {
        late[name]?.await()
    }

    companion object {
        const val NOW = 1_790_000_000

        const val CHANNEL_START_PTS = 5

        const val STICKER_ID = 778L
        const val UPLOADED_ID = 5_000L
        const val PHOTO_ID = 4_242L
        const val VIDEO_ID = 4_343L
        const val ALBUM_ID = 4_444L

        const val MUSIC_ID = 880_000L
        const val VOICE_ID = 890_000L
        const val CATS_ID = 801L
        const val DOGS_ID = 802L
        const val CLIPS_ID = 803L
        const val BIRDS_ID = 804L
        const val CAT_ID = 811L
        const val CAT_TWO_ID = 812L
        const val DOG_ID = 821L
        const val CLIP_ID = 831L
        const val SAVED_GIF_ID = 861L
        const val FOUND_GIF_ID = 862L
        const val GIFS_HASH = 33L
        const val RECENT_HASH = 31L
        const val FEATURED_HASH = 41L
        const val EMOJI_GROUPS_HASH = 42
        const val STAR_GIFTS_HASH = 41
        val GIFT_STICKERS = listOf(901L, 902L, 903L, 904L)
        const val GIFT_COLLECTION = 5
        const val FAVED_HASH = 32L
        const val REACTORS_NEXT = "next"
        const val NEWS_ID = 9_001L
        const val NEWS_HASH = 5L
        const val BOOK_CLUB_ID = 9_002L
        const val BOOK_CLUB_INVITE = "BookClubInvite"

        const val GUARDED_INVITE = "GuardedInvite"
        const val DAVE_HASH = 78L
        const val EMOJI_STAR = 951L
        const val EMOJI_HEART = 952L
        const val EMOJI_MISSING = 953L
        const val EMOJI_SET = 950L
        const val REACTIONS_HASH = 51
        const val TOP_REACTIONS_HASH = 52L
        const val THUMBS_UP = "\uD83D\uDC4D"
        const val HEART = "\u2764\uFE0F"
        const val FIRE = "\uD83D\uDD25"
        const val OUTLINE_LOOKUP = "AACAAAAHAAALMAAAQASTAVAAAZaacaaaahaaalmaaaqastava.az0123456789-,"

        internal val BOOL_TRUE = FakeAnswer.Raw(byteArrayOf(0xb5.toByte(), 0x75, 0x72, 0x99.toByte()))
        internal val BOOL_FALSE = FakeAnswer.Raw(byteArrayOf(0x37, 0x97.toByte(), 0x79, 0xbc.toByte()))
    }
}
