package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ForwardPreview
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.theme.MioShapes

internal data class ComposerCard(val key: Any, val title: AnnotatedString, val text: String)

@Composable
internal fun ComposerPreview(card: ComposerCard?, accent: Color, closeLabel: String, onClose: () -> Unit, modifier: Modifier = Modifier, tag: String = "composerStrip", closeTag: String = "closeStrip") {
    val last = remember { LastCard() }
    if (card != null) last.card = card
    val motion = MaterialTheme.motionScheme
    val offsetSpec = motion.defaultSpatialSpec<IntOffset>()
    val sizeSpec = motion.defaultSpatialSpec<IntSize>()
    val effects = motion.fastEffectsSpec<Float>()
    AnimatedVisibility(
        card != null,
        modifier = modifier,
        enter = slideInVertically(offsetSpec) { it / 2 } + expandVertically(sizeSpec) + fadeIn(effects),
        exit = slideOutVertically(offsetSpec) { it / 2 } + shrinkVertically(sizeSpec) + fadeOut(effects),
    ) {
        val current = last.card ?: return@AnimatedVisibility
        val grow = remember(current.key) { Animatable(0f) }
        val growSpec = motion.fastSpatialSpec<Float>()
        LaunchedEffect(current.key) { grow.animateTo(1f, growSpec) }
        val slide = with(LocalDensity.current) { 24.dp.toPx() }
        Surface(shape = MioShapes.previewBar, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag(tag)) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(3.dp, 40.dp).graphicsLayer { scaleY = grow.value }.clip(MioShapes.accentBar).background(accent))
                Column(
                    Modifier.weight(1f).padding(start = 10.dp).graphicsLayer {
                        alpha = grow.value
                        translationX = (1f - grow.value) * slide
                    },
                ) {
                    Text(current.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(current.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag(closeTag), shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_close), closeLabel, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private class LastCard {
    var card: ComposerCard? = null
}

@Composable
internal fun replyCard(message: MessageItem?): ComposerCard? = message?.let {
    val title = it.senderName ?: stringResource(R.string.chat_reply_to)
    ComposerCard(it.id, AnnotatedString(title), quotedText(it))
}

@Composable
internal fun editCard(message: MessageItem?): ComposerCard? = message?.let { ComposerCard(it.id, AnnotatedString(stringResource(R.string.chat_editing)), it.text) }

@Composable
internal fun forwardCard(forward: ForwardPreview?): ComposerCard? {
    val messages = forward?.messages?.takeIf { it.isNotEmpty() } ?: return null
    val you = stringResource(R.string.chats_you)
    val names = messages.map { if (it.out) you else it.senderName ?: forward.fromTitle }.distinct()
    val title = if (names.size == 1) {
        val from = stringResource(R.string.chat_forwarding_from, "\u0000")
        val (before, after) = from.split('\u0000').let { it.first() to it.getOrElse(1) { "" } }
        buildAnnotatedString {
            append(before)
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(names.single()) }
            append(after)
        }
    } else {
        AnnotatedString(pluralStringResource(R.plurals.chat_forwarding_messages, messages.size, messages.size))
    }
    val text = if (messages.size == 1) quotedText(messages.single()) else pluralStringResource(R.plurals.chat_messages_count, messages.size, messages.size)
    return ComposerCard(messages.map { it.id }, title, text)
}

@Composable
private fun quotedText(message: MessageItem): String = messagePreview(message).ifEmpty { message.media?.fileName ?: stringResource(R.string.chat_message) }
