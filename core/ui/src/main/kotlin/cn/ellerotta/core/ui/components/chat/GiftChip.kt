package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.R

@Composable
internal fun GiftChip(message: MessageItem, stickers: Map<String, ByteArray>, modifier: Modifier = Modifier) {
    val gift = message.gift ?: return
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = GIFT_CHIP_ALPHA),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.widthIn(max = 240.dp).testTag("giftChip_${message.id}"),
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                gift.sticker?.let { StickerView(it, stickers[it.key], size = 96.dp, preview = stickers.previewOf(it)) }
                val sender = message.senderName
                val who = when {
                    message.out -> stringResource(R.string.chat_gift_you_sent)
                    sender == null -> stringResource(R.string.gift_title)
                    gift.upgrade -> stringResource(R.string.chat_gift_upgraded, sender)
                    else -> stringResource(R.string.chat_gift_sent, sender)
                }
                Text(who, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                val unique = gift.unique
                val detail = if (unique != null) stringResource(R.string.gift_unique_title, unique.title, unique.num) else pluralStringResource(R.plurals.gift_stars, gift.stars.toInt(), gift.stars.toInt())
                Text(detail, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.testTag("giftChipDetail"))
                gift.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
    }
}

internal const val GIFT_CHIP_ALPHA = 0.92f
