package cn.ellerotta.core.model

sealed interface GiftRarity {
    data class Permille(val value: Int) : GiftRarity

    data class Tier(val tier: GiftRarityTier) : GiftRarity
}

enum class GiftRarityTier { Uncommon, Rare, Epic, Legendary }

data class GiftAttribute(val name: String, val sticker: MediaItem?, val rarity: GiftRarity?)

data class GiftBackdrop(val name: String, val centerColor: Int, val edgeColor: Int, val patternColor: Int, val textColor: Int, val rarity: GiftRarity?)

data class UniqueGift(
    val title: String,
    val slug: String,
    val num: Int,
    val model: GiftAttribute? = null,
    val backdrop: GiftBackdrop? = null,
    val symbol: GiftAttribute? = null,
    val issued: Int = 0,
    val total: Int = 0,
    val ownerName: String? = null,
    val id: Long = 0,
)

data class SavedGift(
    val key: String,
    val giftId: Long,
    val sticker: MediaItem?,
    val stars: Long,
    val date: Int,
    val title: String? = null,
    val fromName: String? = null,
    val fromId: Long = 0,
    val nameHidden: Boolean = false,
    val message: String? = null,
    val unique: UniqueGift? = null,
    val saved: Boolean = true,
    val manageKey: String? = null,
    val convertStars: Long? = null,
    val limitedTotal: Int? = null,
    val pinned: Boolean = false,
)

data class GiftPage(val count: Int, val gifts: List<SavedGift>, val nextOffset: String?)

data class GiftCollection(val id: Int, val title: String, val icon: MediaItem? = null, val count: Int = 0)

data class ProfileGifts(
    val page: GiftPage? = null,
    val catalogue: List<CatalogueGift>? = null,
    val collections: List<GiftCollection> = emptyList(),
    val collection: Int? = null,
    val collectionPages: Map<Int, GiftPage> = emptyMap(),
) {
    val shown: GiftPage? get() = if (collection == null) page else collectionPages[collection]
}

data class CatalogueGift(
    val id: Long,
    val sticker: MediaItem?,
    val stars: Long,
    val convertStars: Long,
    val title: String? = null,
    val limited: Boolean = false,
    val soldOut: Boolean = false,
    val remains: Int? = null,
    val total: Int? = null,
)

data class GiftAction(
    val sticker: MediaItem?,
    val stars: Long,
    val unique: UniqueGift? = null,
    val message: String? = null,
    val converted: Boolean = false,
    val upgrade: Boolean = false,
)
