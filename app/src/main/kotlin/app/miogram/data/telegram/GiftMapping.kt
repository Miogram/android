package app.miogram.data.telegram

import cn.ellerotta.core.model.CatalogueGift
import cn.ellerotta.core.model.GiftAction
import cn.ellerotta.core.model.GiftAttribute
import cn.ellerotta.core.model.GiftBackdrop
import cn.ellerotta.core.model.GiftRarity
import cn.ellerotta.core.model.GiftRarityTier
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.SavedGift
import cn.ellerotta.core.model.UniqueGift
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputSavedStarGiftChat
import cn.ellerotta.ktproto.api.types.InputSavedStarGiftUser
import cn.ellerotta.ktproto.api.types.MessageActionStarGift
import cn.ellerotta.ktproto.api.types.MessageActionStarGiftUnique
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.SavedStarGift
import cn.ellerotta.ktproto.api.types.StarGift
import cn.ellerotta.ktproto.api.types.StarGiftAttributeBackdrop
import cn.ellerotta.ktproto.api.types.StarGiftAttributeModel
import cn.ellerotta.ktproto.api.types.StarGiftAttributePattern
import cn.ellerotta.ktproto.api.types.StarGiftAttributeRarity
import cn.ellerotta.ktproto.api.types.StarGiftAttributeRarityEpic
import cn.ellerotta.ktproto.api.types.StarGiftAttributeRarityLegendary
import cn.ellerotta.ktproto.api.types.StarGiftAttributeRarityRare
import cn.ellerotta.ktproto.api.types.StarGiftAttributeRarityUncommon
import cn.ellerotta.ktproto.api.types.StarGiftUnique
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.TypeMessageAction
import cn.ellerotta.ktproto.api.types.TypeStarGift
import cn.ellerotta.ktproto.api.types.TypeStarGiftAttributeRarity
import cn.ellerotta.ktproto.api.types.TypeUser
import cn.ellerotta.ktproto.api.types.User

internal fun PeerRegistry.savedGiftOf(gift: SavedStarGift, owner: TypeInputPeer, self: Boolean, users: List<TypeUser>, chats: List<TypeChat>, origin: FileOrigin?): SavedGift {
    val input = when {
        !self -> null
        gift.msgId != null && owner is InputPeerSelf -> InputSavedStarGiftUser(gift.msgId!!)
        gift.savedId != null -> InputSavedStarGiftChat(owner, gift.savedId!!)
        gift.msgId != null -> InputSavedStarGiftUser(gift.msgId!!)
        else -> null
    }
    val key = gift.msgId?.let { "giftm$it" } ?: gift.savedId?.let { "gifts$it" } ?: "gift${giftIdOf(gift.gift)}_${gift.date}"
    input?.let { savedGifts[key] = it }
    val sender = gift.fromId
    val (fromName, fromId) = when (sender) {
        is PeerUser -> users.filterIsInstance<User>().firstOrNull { it.id == sender.userId }?.let(::nameOf) to sender.userId
        is PeerChat -> chats.firstOrNull { chatId(it) == sender.chatId }?.let(::titleOf) to sender.chatId
        is PeerChannel -> chats.firstOrNull { chatId(it) == sender.channelId }?.let(::titleOf) to sender.channelId
        null -> null to 0L
    }
    val regular = gift.gift as? StarGift
    return SavedGift(
        key = key,
        giftId = giftIdOf(gift.gift),
        sticker = giftSticker(gift.gift, origin),
        stars = regular?.stars ?: 0,
        date = gift.date,
        title = regular?.title,
        fromName = fromName,
        fromId = fromId,
        nameHidden = gift.nameHidden,
        message = gift.message?.text,
        unique = (gift.gift as? StarGiftUnique)?.let { uniqueOf(it, origin) },
        saved = !gift.unsaved,
        manageKey = input?.let { key },
        convertStars = gift.convertStars,
        limitedTotal = regular?.availabilityTotal,
        pinned = gift.pinnedToTop,
    )
}

internal fun PeerRegistry.catalogueGiftOf(gift: StarGift): CatalogueGift = CatalogueGift(
    id = gift.id,
    sticker = giftSticker(gift, null),
    stars = gift.stars,
    convertStars = gift.convertStars,
    title = gift.title,
    limited = gift.limited,
    soldOut = gift.soldOut,
    remains = gift.availabilityRemains,
    total = gift.availabilityTotal,
)

internal fun PeerRegistry.giftActionOf(action: TypeMessageAction, origin: FileOrigin?): GiftAction? = when (action) {
    is MessageActionStarGift -> GiftAction(
        sticker = giftSticker(action.gift, origin),
        stars = (action.gift as? StarGift)?.stars ?: 0,
        unique = (action.gift as? StarGiftUnique)?.let { uniqueOf(it, origin) },
        message = action.message?.text,
        converted = action.converted,
    )
    is MessageActionStarGiftUnique -> GiftAction(
        sticker = giftSticker(action.gift, origin),
        stars = 0,
        unique = (action.gift as? StarGiftUnique)?.let { uniqueOf(it, origin) },
        upgrade = action.upgrade,
    )
    else -> null
}

private fun giftIdOf(gift: TypeStarGift): Long = when (gift) {
    is StarGift -> gift.id
    is StarGiftUnique -> gift.giftId
}

private fun PeerRegistry.giftSticker(gift: TypeStarGift, origin: FileOrigin?): MediaItem? = when (gift) {
    is StarGift -> (gift.sticker as? Document)?.let { documentOf(null, it, origin) }
    is StarGiftUnique -> gift.attributes.filterIsInstance<StarGiftAttributeModel>().firstOrNull()?.let { (it.document as? Document)?.let { doc -> documentOf(null, doc, origin) } }
}

private fun PeerRegistry.uniqueOf(gift: StarGiftUnique, origin: FileOrigin?): UniqueGift {
    val model = gift.attributes.filterIsInstance<StarGiftAttributeModel>().firstOrNull()
    val pattern = gift.attributes.filterIsInstance<StarGiftAttributePattern>().firstOrNull()
    val backdrop = gift.attributes.filterIsInstance<StarGiftAttributeBackdrop>().firstOrNull()
    return UniqueGift(
        title = gift.title,
        slug = gift.slug,
        num = gift.num,
        model = model?.let { GiftAttribute(it.name, (it.document as? Document)?.let { doc -> documentOf(null, doc, origin) }, rarityOf(it.rarity)) },
        backdrop = backdrop?.let { GiftBackdrop(it.name, it.centerColor, it.edgeColor, it.patternColor, it.textColor, rarityOf(it.rarity)) },
        symbol = pattern?.let { GiftAttribute(it.name, (it.document as? Document)?.let { doc -> documentOf(null, doc, origin) }, rarityOf(it.rarity)) },
        issued = gift.availabilityIssued,
        total = gift.availabilityTotal,
        ownerName = gift.ownerName,
        id = gift.id,
    )
}

private fun rarityOf(rarity: TypeStarGiftAttributeRarity): GiftRarity = when (rarity) {
    is StarGiftAttributeRarity -> GiftRarity.Permille(rarity.permille)
    StarGiftAttributeRarityUncommon -> GiftRarity.Tier(GiftRarityTier.Uncommon)
    StarGiftAttributeRarityRare -> GiftRarity.Tier(GiftRarityTier.Rare)
    StarGiftAttributeRarityEpic -> GiftRarity.Tier(GiftRarityTier.Epic)
    StarGiftAttributeRarityLegendary -> GiftRarity.Tier(GiftRarityTier.Legendary)
}
