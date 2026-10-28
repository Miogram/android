package cn.ellerotta.core.ui.components.profile

import cn.ellerotta.core.model.AppPrivacy
import cn.ellerotta.core.model.Birthday
import cn.ellerotta.core.model.CatalogueGift
import cn.ellerotta.core.model.ChannelPost
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.GiftAttribute
import cn.ellerotta.core.model.GiftBackdrop
import cn.ellerotta.core.model.GiftCollection
import cn.ellerotta.core.model.GiftPage
import cn.ellerotta.core.model.GiftRarity
import cn.ellerotta.core.model.GiftRarityTier
import cn.ellerotta.core.model.GlobalPrivacy
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.OwnUsername
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.PersonalChannel
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.PrivacyBase
import cn.ellerotta.core.model.PrivacyKey
import cn.ellerotta.core.model.PrivacySetting
import cn.ellerotta.core.model.PrivacyState
import cn.ellerotta.core.model.ProfileDetails
import cn.ellerotta.core.model.ProfileGifts
import cn.ellerotta.core.model.ProfileInfo
import cn.ellerotta.core.model.SavedGift
import cn.ellerotta.core.model.SessionInfo
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.model.UniqueGift
import cn.ellerotta.core.model.Wallpaper
import cn.ellerotta.core.model.WallpaperChoices
import cn.ellerotta.core.model.WallpaperKind
import cn.ellerotta.core.ui.components.common.previewPhoto

internal object ProfilePreviewData {
    const val NOW = 1_790_000_000
    const val NOW_MILLIS = NOW * 1000L

    val avatars: Map<String, ByteArray> = mapOf(
        "photo_me" to previewPhoto(start = 0xFF5B8DEF.toInt(), end = 0xFF7ED67E.toInt()),
        "photo_maria" to previewPhoto(start = 0xFFE86A9A.toInt(), end = 0xFFFFB85C.toInt()),
        "photo_channel" to previewPhoto(start = 0xFF8C86FF.toInt(), end = 0xFF3FD8D8.toInt()),
        "photo_club" to previewPhoto(start = 0xFFFF885E.toInt(), end = 0xFF8C86FF.toInt()),
        "photo_news" to previewPhoto(start = 0xFF3FD8D8.toInt(), end = 0xFF5B8DEF.toInt()),
    )

    val editedProfile = ProfileDetails(
        key = "u1001", id = 1001, name = "Alex Morgan", kind = PeerKind.Saved, firstName = "Alex", lastName = "Morgan", avatarKey = "photo_me", isSelf = true,
        about = "Designer, tea drinker, slow runner.",
        usernameList = listOf(
            OwnUsername("alexmorgan", active = true, editable = true),
            OwnUsername("alex", active = true, editable = false),
            OwnUsername("morgan", active = false, editable = false),
        ),
        birthday = Birthday(day = 14, month = 3, year = null),
        channel = PersonalChannel("ch77", "Slow running", avatarKey = "photo_channel", subscribers = 1_200),
    )

    val channelChoices = listOf(
        PersonalChannel("ch77", "Slow running", avatarKey = "photo_channel", subscribers = 1_200),
        PersonalChannel("ch78", "Tea notes", avatarKey = "photo_news", subscribers = 40),
    )

    val me = ProfileInfo(id = 1001, name = "Alex Morgan", phone = "447700900123", username = "alexmorgan", avatarKey = "photo_me")

    val ownProfile = ProfileDetails(
        key = "u1001",
        id = 1001,
        name = "Alex Morgan",
        kind = PeerKind.User,
        presence = Presence.Online,
        avatarKey = "photo_me",
        phone = "447700900123",
        usernames = listOf("alexmorgan", "alex"),
        about = "Coffee, film photography and long walks",
        birthday = Birthday(day = 14, month = 3, year = 1994),
        premium = true,
        colorIndex = 5,
        isSelf = true,
        dcId = 2,
    )

    val song = MediaItem(
        key = "doc501",
        fileName = "sunrise_drive.mp3",
        size = 4_200_000,
        dcId = 2,
        isPhoto = false,
        kind = MediaKind.Music,
        thumb = previewPhoto(start = 0xFFFFB85C.toInt(), end = 0xFFE86A9A.toInt()),
        duration = 214,
        title = "Sunrise Drive",
        performer = "The Night Owls",
    )

    val songs = listOf(
        song,
        song.copy(key = "doc502", fileName = "blue_hour.mp3", size = 3_600_000, title = "Blue Hour", performer = "Marta Reis", duration = 187, thumb = previewPhoto(start = 0xFF3FD8D8.toInt(), end = 0xFF5B8DEF.toInt())),
        song.copy(key = "doc503", fileName = "slow_tea.mp3", size = 5_100_000, title = "Slow Tea", performer = "The Night Owls", duration = 241, thumb = null),
    )

    val personalChannel = PersonalChannel(
        key = "ch3003",
        title = "Maria's sketchbook",
        avatarKey = "photo_channel",
        verified = true,
        subscribers = 1280,
        post = ChannelPost(
            id = 42,
            date = NOW - 7200,
            text = "New series: ten city corners in ink. Which one is your favourite?",
            entities = listOf(TextEntity(0, 11, EntityType.Bold)),
        ),
    )

    val userProfile = ProfileDetails(
        key = "u2002",
        id = 2002,
        name = "Maria Lopez",
        kind = PeerKind.User,
        presence = Presence.Offline(NOW - 1500),
        avatarKey = "photo_maria",
        phone = "351912345678",
        usernames = listOf("marialopez", "mlopez"),
        about = "Designer. Plants. Bikes. Drawing a little every day",
        birthday = Birthday(day = 2, month = 11, year = null),
        verified = true,
        colorIndex = 2,
        music = song,
        channel = personalChannel,
        dcId = 4,
        contact = true,
    )

    val groupProfile = ProfileDetails(
        key = "ch4004",
        id = 4004,
        name = "Film Photography Club",
        kind = PeerKind.Group,
        presence = Presence.Members(count = 2412, online = 87, channel = false),
        avatarKey = "photo_club",
        usernames = listOf("filmclub"),
        about = "Swap tips, scans and darkroom stories. Be kind, credit the photographer",
        colorIndex = 3,
        member = true,
        canInvite = true,
        requestsPending = 3,
        canViewMembers = true,
        dcId = 1,
    )

    val channelProfile = ProfileDetails(
        key = "ch5005",
        id = 5005,
        name = "Morning Notes",
        kind = PeerKind.Channel,
        presence = Presence.Members(count = 15_300, online = null, channel = true),
        avatarKey = "photo_news",
        usernames = listOf("morningnotes"),
        about = "Five short reads before your first coffee",
        verified = true,
        colorIndex = 6,
        muted = true,
        member = true,
        canViewMembers = true,
        dcId = 5,
    )

    fun sticker(key: String, emoji: String) = MediaItem(key = key, fileName = "$key.tgs", size = null, dcId = 2, isPhoto = false, kind = MediaKind.Sticker, emoji = emoji)

    val luckyCat = UniqueGift(
        title = "Lucky Cat",
        slug = "luckycat-128",
        num = 128,
        model = GiftAttribute("Golden", sticker("model_cat", "🐱"), GiftRarity.Permille(15)),
        backdrop = GiftBackdrop("Sapphire", centerColor = 0x5B8DEF, edgeColor = 0x2A4C9C, patternColor = 0x1B3570, textColor = 0xFFFFFF, rarity = GiftRarity.Permille(20)),
        symbol = GiftAttribute("Star", null, GiftRarity.Tier(GiftRarityTier.Rare)),
        issued = 3000,
        total = 5000,
        ownerName = "Alex Morgan",
    )

    val snowGlobe = UniqueGift(
        title = "Snow Globe",
        slug = "snowglobe-7",
        num = 7,
        model = GiftAttribute("Winter Night", sticker("model_globe", "🔮"), GiftRarity.Tier(GiftRarityTier.Legendary)),
        backdrop = GiftBackdrop("Rose", centerColor = 0xF3B0C3, edgeColor = 0xC0607E, patternColor = 0x8E3A55, textColor = 0x2B0A14, rarity = GiftRarity.Tier(GiftRarityTier.Epic)),
        issued = 900,
        total = 1000,
    )

    val plainGift = SavedGift(
        key = "g1",
        giftId = 1,
        sticker = sticker("gift_bear", "🧸"),
        stars = 50,
        date = NOW - 86_400 * 3,
        title = "Teddy Bear",
        fromName = "Sam Lee",
        fromId = 3003,
        message = "Happy birthday!",
        manageKey = "m1",
        convertStars = 40,
    )

    val uniqueGift = SavedGift(key = "g2", giftId = 2, sticker = sticker("gift_cat", "🐱"), stars = 0, date = NOW - 86_400 * 20, fromName = "Maria Lopez", unique = luckyCat, pinned = true, manageKey = "m2")

    val uniqueGift2 = SavedGift(key = "g3", giftId = 3, sticker = sticker("gift_globe", "🔮"), stars = 0, date = NOW - 86_400 * 40, fromName = "Sam Lee", unique = snowGlobe, pinned = true)

    val limitedGift = SavedGift(key = "g4", giftId = 4, sticker = sticker("gift_rocket", "🚀"), stars = 250, date = NOW - 86_400 * 60, title = "Rocket", fromName = "Nina Park", limitedTotal = 10_000)

    val hiddenGift = SavedGift(key = "g5", giftId = 5, sticker = sticker("gift_rose", "🌹"), stars = 25, date = NOW - 86_400 * 90, fromName = null, nameHidden = true, saved = false, manageKey = "m5", convertStars = 20)

    val giftPage = GiftPage(count = 24, gifts = listOf(uniqueGift, plainGift, uniqueGift2, limitedGift, hiddenGift), nextOffset = "next")

    val catalogue = listOf(
        CatalogueGift(id = 11, sticker = sticker("cat_heart", "💝"), stars = 15, convertStars = 13),
        CatalogueGift(id = 12, sticker = sticker("cat_bear", "🧸"), stars = 50, convertStars = 40),
        CatalogueGift(id = 13, sticker = sticker("cat_rocket", "🚀"), stars = 250, convertStars = 200, limited = true, remains = 1840, total = 10_000),
        CatalogueGift(id = 14, sticker = sticker("cat_ring", "💍"), stars = 1000, convertStars = 850, limited = true, soldOut = true, remains = 0, total = 5000),
        CatalogueGift(id = 15, sticker = sticker("cat_cake", "🎂"), stars = 100, convertStars = 85),
    )

    val collections = listOf(GiftCollection(7, "Bears", sticker("gift_bear", "🧸"), 3), GiftCollection(8, "Rare ones", null, 2))

    val gifts = ProfileGifts(page = giftPage, catalogue = catalogue, collections = collections)

    val giftCallbacks = GiftCallbacks(onLoadSticker = {}, onMore = {})

    val ownGiftCallbacks = GiftCallbacks(onLoadSticker = {}, onMore = {}, onToggleSaved = { _, _ -> }, onOpenCatalogue = {})

    val privacy = PrivacyState(
        settings = listOf(
            PrivacySetting(PrivacyKey.LastSeen, PrivacyBase.Contacts, allowed = 2, disallowed = 1),
            PrivacySetting(PrivacyKey.PhoneNumber, PrivacyBase.Nobody),
            PrivacySetting(PrivacyKey.FindByPhone, PrivacyBase.Contacts),
            PrivacySetting(PrivacyKey.ProfilePhoto, PrivacyBase.Everybody),
            PrivacySetting(PrivacyKey.Forwards, PrivacyBase.Everybody, disallowed = 3),
            PrivacySetting(PrivacyKey.Calls, PrivacyBase.Contacts),
            PrivacySetting(PrivacyKey.PeerToPeer, PrivacyBase.Contacts),
            PrivacySetting(PrivacyKey.Invites, PrivacyBase.Everybody),
            PrivacySetting(PrivacyKey.VoiceMessages, PrivacyBase.Everybody),
            PrivacySetting(PrivacyKey.Bio, PrivacyBase.Everybody),
            PrivacySetting(PrivacyKey.Birthday, PrivacyBase.CloseFriends),
            PrivacySetting(PrivacyKey.Gifts, PrivacyBase.Everybody, allowed = 1),
        ),
        global = GlobalPrivacy(archiveAndMuteNonContacts = true, hideReadMarks = false),
        accountTtlDays = 365,
        twoStepEnabled = true,
    )

    val appPrivacy = AppPrivacy(lock = true, lockAfterMinutes = 5, secureWindow = false, hideNotificationContent = true)

    val sessions = listOf(
        SessionInfo(hash = 1, device = "Pixel 9", platform = "Android", system = "Android 16", app = "Miogram", appVersion = "0.9.0", location = "Lisbon, Portugal", ip = "203.0.113.7", dateActive = NOW, current = true, official = false),
        SessionInfo(hash = 2, device = "MacBook Air", platform = "macOS", system = "macOS 26", app = "Miogram", appVersion = "0.8.2", location = "Lisbon, Portugal", ip = "203.0.113.8", dateActive = NOW - 3600, current = false, official = false),
        SessionInfo(hash = 3, device = "Chrome 140", platform = "Windows", system = "Windows 11", app = "Web", appVersion = "2.1", location = "Porto, Portugal", ip = "198.51.100.23", dateActive = NOW - 86_400 * 2, current = false, official = true),
        SessionInfo(hash = 4, device = "iPad Air", platform = "iOS", system = "iPadOS 26", app = "Miogram", appVersion = "0.9.0", location = "Madrid, Spain", ip = "192.0.2.44", dateActive = NOW - 86_400 * 9, current = false, official = false),
    )

    val wallpapers = WallpaperChoices(
        wallpapers = listOf(
            Wallpaper(key = "wp1", kind = WallpaperKind.Fill, colors = listOf(0xDBDDBB, 0x6BA587, 0xD5D88D, 0x88B884), isDefault = true, slug = "meadow"),
            Wallpaper(key = "wp2", kind = WallpaperKind.Fill, colors = listOf(0x7FA381, 0xFFF5C5), rotation = 45, slug = "dusk"),
            Wallpaper(key = "wp3", kind = WallpaperKind.Fill, colors = listOf(0x4F5BD5), slug = "ocean"),
            Wallpaper(key = "wp4", kind = WallpaperKind.Pattern, colors = listOf(0xFEC496, 0xDD6CB9, 0x962FBF, 0x4F5BD5), intensity = 50, slug = "sunset"),
            Wallpaper(key = "wp5", kind = WallpaperKind.Fill, colors = listOf(0x1B2836, 0x2A3F54), dark = true, slug = "night"),
            Wallpaper(key = "wp6", kind = WallpaperKind.Image, colors = listOf(0x3A4A5C, 0x161C26), blur = true, dark = true, slug = "city"),
        ),
        chosen = "wp2",
    )
}
