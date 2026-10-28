package app.miogram.data.telegram

import cn.ellerotta.core.model.CatalogueGift
import cn.ellerotta.core.model.GiftCollection
import cn.ellerotta.core.model.GiftPage
import cn.ellerotta.ktproto.api.functions.payments.GetSavedStarGifts
import cn.ellerotta.ktproto.api.functions.payments.GetStarGiftCollections
import cn.ellerotta.ktproto.api.functions.payments.GetStarGifts
import cn.ellerotta.ktproto.api.functions.payments.SaveStarGift
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.InputPeerEmpty
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.StarGift
import cn.ellerotta.ktproto.api.types.payments.SavedStarGifts
import cn.ellerotta.ktproto.api.types.payments.StarGiftCollections
import cn.ellerotta.ktproto.api.types.payments.StarGifts
import cn.ellerotta.ktproto.tl.decode
import cn.ellerotta.ktproto.tl.toByteArray
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val GIFTS_PAGE = 30

internal suspend fun TelegramClientHolder.savedGifts(dialogKey: String?, offset: String, limit: Int): GiftPage = giftPageOf(dialogKey, savedGiftsAnswer(dialogKey, offset, limit))

private suspend fun TelegramClientHolder.savedGiftsAnswer(dialogKey: String?, offset: String, limit: Int, collectionId: Int? = null): SavedStarGifts =
    client().invoke(GetSavedStarGifts(peer = dialogKey?.let(peers::peer) ?: InputPeerSelf, collectionId = collectionId, offset = offset, limit = limit))

private fun TelegramClientHolder.giftPageOf(dialogKey: String?, result: SavedStarGifts): GiftPage {
    val owner = if (dialogKey == null) InputPeerSelf else peers.inputPeer(dialogKey) ?: InputPeerEmpty
    peers.rememberUsers(result.users)
    val origin = GiftsOrigin(dialogKey)
    val gifts = result.gifts.map { peers.savedGiftOf(it, owner, self = dialogKey == null, users = result.users, chats = result.chats, origin = origin) }
    return GiftPage(result.count, gifts, result.nextOffset?.ifEmpty { null })
}

@Singleton
class GiftsRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val lock = Mutex()
    private var catalogueHash = 0
    private var catalogue: List<CatalogueGift> = emptyList()

    suspend fun gifts(dialogKey: String?, offset: String = "", collectionId: Int? = null): GiftPage {
        val result = telegram.savedGiftsAnswer(dialogKey, offset, GIFTS_PAGE, collectionId)
        val page = telegram.giftPageOf(dialogKey, result)
        if (offset.isEmpty() && collectionId == null) {
            telegram.cache?.changeProfile(dialogKey ?: SELF_KEY) { it.copy(gifts = result.toByteArray()) }
            telegram.memory["gifts:${dialogKey ?: SELF_KEY}"] = page
        }
        return page
    }

    suspend fun collections(dialogKey: String?): List<GiftCollection> {
        val result = telegram.client().invoke(GetStarGiftCollections(peer = dialogKey?.let(telegram.peers::peer) ?: InputPeerSelf, hash = 0))
        if (result !is StarGiftCollections) return emptyList()
        val origin = GiftsOrigin(dialogKey)
        return result.collections.map { collection ->
            GiftCollection(collection.collectionId, collection.title, (collection.icon as? Document)?.let { telegram.peers.documentOf(null, it, origin) }, collection.giftsCount)
        }
    }

    suspend fun stored(dialogKey: String?): GiftPage? {
        val remembered: GiftPage? = telegram.memory["gifts:${dialogKey ?: SELF_KEY}"]
        val bytes = telegram.cache?.profile(dialogKey ?: SELF_KEY)?.gifts ?: return remembered
        return runCatching { telegram.giftPageOf(dialogKey, SavedStarGifts.decode(bytes)) }.onFailure { telegram.log("gifts: cannot read the stored page of $dialogKey: ${it.message}") }.getOrNull() ?: remembered
    }

    suspend fun setSaved(manageKey: String, saved: Boolean) {
        val gift = telegram.peers.savedGifts[manageKey] ?: error("unknown gift $manageKey")
        telegram.client().invoke(SaveStarGift(unsave = !saved, stargift = gift))
    }

    suspend fun catalogue(): List<CatalogueGift> = lock.withLock {
        val result = telegram.client().invoke(GetStarGifts(catalogueHash))
        if (result is StarGifts) {
            catalogueHash = result.hash
            catalogue = result.gifts.filterIsInstance<StarGift>().map { telegram.peers.catalogueGiftOf(it) }
        }
        catalogue
    }
}
