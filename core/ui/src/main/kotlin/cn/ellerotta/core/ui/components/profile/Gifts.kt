package cn.ellerotta.core.ui.components.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.CatalogueGift
import cn.ellerotta.core.model.GiftRarity
import cn.ellerotta.core.model.GiftRarityTier
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.ProfileGifts
import cn.ellerotta.core.model.SavedGift
import cn.ellerotta.core.model.UniqueGift
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.StickerView
import cn.ellerotta.core.ui.components.chat.previewOf
import cn.ellerotta.core.ui.components.common.CheckedSwitch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class GiftCallbacks(
    val onLoadSticker: (MediaItem) -> Unit,
    val onMore: () -> Unit,
    val onToggleSaved: ((SavedGift, Boolean) -> Unit)? = null,
    val onOpenCatalogue: (() -> Unit)? = null,
    val onCollection: (Int?) -> Unit = {},
)

@Composable
fun GiftSheet(gift: SavedGift, stickers: Map<String, ByteArray>, onLoadSticker: (MediaItem) -> Unit, onToggleSaved: ((Boolean) -> Unit)?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)), modifier = Modifier.testTag("giftSheet")) {
        GiftSheetContent(gift, stickers, onLoadSticker, onToggleSaved)
    }
}

@Composable
internal fun GiftSheetContent(gift: SavedGift, stickers: Map<String, ByteArray>, onLoadSticker: (MediaItem) -> Unit, onToggleSaved: ((Boolean) -> Unit)?) {
    val locale = LocalConfiguration.current.locales[0]
    val unique = gift.unique
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
        GiftHeader(unique, stickers, onLoadSticker, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GiftSticker(unique?.model?.sticker ?: gift.sticker, stickers, onLoadSticker, 120.dp)
                val title = unique?.let { stringResource(R.string.gift_unique_title, it.title, it.num) } ?: gift.title ?: stringResource(R.string.gift_title)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = unique?.backdrop?.let { Color(0xFF000000.toInt() or it.textColor) } ?: MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("giftTitle"))
                gift.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        InfoCard(Modifier.padding(top = 12.dp)) {
            InfoRow(null, gift.fromName ?: stringResource(R.string.gift_hidden_sender), stringResource(R.string.gift_from))
            InfoDivider(PlainInset)
            InfoRow(null, dateText(gift.date, locale), stringResource(R.string.gift_date))
            if (unique == null && gift.stars > 0) {
                InfoDivider(PlainInset)
                InfoRow(null, pluralStringResource(R.plurals.gift_stars, gift.stars.toInt(), gift.stars.toInt()), stringResource(R.string.gift_value))
            }
            unique?.let { UniqueRows(it) }
            gift.limitedTotal?.let {
                InfoDivider(PlainInset)
                InfoRow(null, stringResource(R.string.gift_limited, it))
            }
        }
        gift.convertStars?.takeIf { it > 0 && onToggleSaved != null }?.let { stars ->
            Text(
                pluralStringResource(R.plurals.gift_convert, stars.toInt(), stars.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp).testTag("giftConvert"),
            )
        }
        onToggleSaved?.let { toggle ->
            Row(
                Modifier.fillMaxWidth().clickable { toggle(!gift.saved) }.padding(horizontal = 32.dp, vertical = 12.dp).testTag("giftShow"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.gift_show), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                CheckedSwitch(checked = gift.saved, onCheckedChange = { toggle(it) })
            }
        }
    }
}

@Composable
private fun UniqueRows(unique: UniqueGift) {
    unique.model?.let {
        InfoDivider(PlainInset)
        InfoRow(null, attributeText(it.name, it.rarity), stringResource(R.string.gift_model), modifier = Modifier.testTag("giftModel"))
    }
    unique.backdrop?.let {
        InfoDivider(PlainInset)
        InfoRow(null, attributeText(it.name, it.rarity), stringResource(R.string.gift_backdrop), modifier = Modifier.testTag("giftBackdrop"))
    }
    unique.symbol?.let {
        InfoDivider(PlainInset)
        InfoRow(null, attributeText(it.name, it.rarity), stringResource(R.string.gift_symbol), modifier = Modifier.testTag("giftSymbol"))
    }
    if (unique.total > 0) {
        InfoDivider(PlainInset)
        InfoRow(null, stringResource(R.string.gift_issued, unique.issued, unique.total))
    }
}

@Composable
fun GiftCatalogueSheet(gifts: List<CatalogueGift>?, stickers: Map<String, ByteArray>, onLoadSticker: (MediaItem) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)), modifier = Modifier.testTag("giftCatalogueSheet")) {
        GiftCatalogueContent(gifts, stickers, onLoadSticker)
    }
}

@Composable
internal fun GiftCatalogueContent(gifts: List<CatalogueGift>?, stickers: Map<String, ByteArray>, onLoadSticker: (MediaItem) -> Unit) {
    Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.profile_gifts_catalogue), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.gift_payments_left),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp).testTag("giftPaymentsLeft"),
        )
        LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(gifts.orEmpty(), key = { it.id }) { gift ->
                Surface(shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.testTag("catalogue_${gift.id}")) {
                    Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        GiftSticker(gift.sticker, stickers, onLoadSticker, 64.dp)
                        Text(pluralStringResource(R.plurals.gift_stars, gift.stars.toInt(), gift.stars.toInt()), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        val remains = gift.remains
                        val total = gift.total
                        when {
                            gift.soldOut -> Text(stringResource(R.string.gift_sold_out), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            gift.limited && remains != null && total != null -> Text(stringResource(R.string.gift_left, remains, total), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GiftSheets(gifts: ProfileGifts, stickers: Map<String, ByteArray>, callbacks: GiftCallbacks?, openKey: String?, catalogueOpen: Boolean, onClose: () -> Unit, onCloseCatalogue: () -> Unit) {
    if (callbacks == null) return
    gifts.page?.gifts?.firstOrNull { it.key == openKey }?.let { gift ->
        val toggle = callbacks.onToggleSaved?.takeIf { gift.manageKey != null }
        GiftSheet(gift, stickers, callbacks.onLoadSticker, toggle?.let { { saved: Boolean -> it(gift, saved) } }, onClose)
    }
    if (catalogueOpen) GiftCatalogueSheet(gifts.catalogue, stickers, callbacks.onLoadSticker, onCloseCatalogue)
}

@Composable
internal fun GiftSticker(sticker: MediaItem?, stickers: Map<String, ByteArray>, onLoadSticker: (MediaItem) -> Unit, size: Dp) {
    if (sticker == null) {
        Icon(painterResource(R.drawable.ic_redeem), null, Modifier.size(size / 2), tint = MaterialTheme.colorScheme.primary)
        return
    }
    LaunchedEffect(sticker.key) { if (stickers[sticker.key] == null) onLoadSticker(sticker) }
    StickerView(sticker, stickers[sticker.key], size = size, preview = stickers.previewOf(sticker))
}

@Composable
private fun attributeText(name: String, rarity: GiftRarity?): String {
    val label = when (rarity) {
        is GiftRarity.Permille -> stringResource(R.string.gift_rarity_permille, String.format(LocalConfiguration.current.locales[0], "%.1f", rarity.value / 10.0))
        is GiftRarity.Tier -> stringResource(
            when (rarity.tier) {
                GiftRarityTier.Uncommon -> R.string.gift_rarity_uncommon
                GiftRarityTier.Rare -> R.string.gift_rarity_rare
                GiftRarityTier.Epic -> R.string.gift_rarity_epic
                GiftRarityTier.Legendary -> R.string.gift_rarity_legendary
            },
        )
        null -> null
    }
    return if (label == null) name else "$name · $label"
}

private fun dateText(date: Int, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(Instant.ofEpochSecond(date.toLong()).atZone(ZoneId.systemDefault()))
