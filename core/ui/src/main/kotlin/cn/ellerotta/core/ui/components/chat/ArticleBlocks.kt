package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ArticleAlign
import cn.ellerotta.core.model.ArticleAnchor
import cn.ellerotta.core.model.ArticleBlock
import cn.ellerotta.core.model.ArticleListItem
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.ArticleText
import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.VoicePlayback
import cn.ellerotta.core.model.anchorName
import cn.ellerotta.core.model.anchors
import cn.ellerotta.core.model.flatten
import cn.ellerotta.core.model.walk
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut
import java.util.IdentityHashMap
import kotlinx.coroutines.launch

@Stable
internal class ArticleHost(
    val downloads: Map<String, DownloadState> = emptyMap(),
    val onDownload: (MediaItem) -> Unit = {},
    val playback: VoicePlayback? = null,
    val onToggleVoice: (MediaItem) -> Unit = {},
    val onOpenMedia: (Int) -> Unit = {},
    val onButton: ((BotButton) -> Unit)? = null,
    val enabled: Boolean = true,
    val onShowMore: (() -> Unit)? = null,
    val loadingMore: Boolean = false,
    val onToggleCheck: ((ArticleListItem) -> Unit)? = null,
    val onVoiceSpeed: () -> Unit = {},
)

internal class ArticleScope(
    val messageId: Int,
    val linkColor: Color,
    val accent: Color,
    val revealed: Boolean,
    val onReveal: () -> Unit,
    val actions: TextActions,
    val press: TextPress,
    val emoji: InlineEmoji,
    val inline: ArticleInline,
    val host: ArticleHost,
    val rtl: Boolean,
    val mediaIndex: (ArticleBlock) -> Int?,
)

@Composable
internal fun ArticleView(
    message: MessageItem,
    article: ArticleMessage,
    linkColor: Color,
    accent: Color,
    actions: TextActions,
    press: TextPress,
    emoji: InlineEmoji,
    host: ArticleHost,
) {
    var revealed by remember(message.id) { mutableStateOf(false) }
    var footnote by remember { mutableStateOf<ArticleText?>(null) }
    val anchors = remember(article) { article.anchors() }
    val requesters = remember(article) { List(article.blocks.size) { BringIntoViewRequester() } }
    val coroutines = rememberCoroutineScope()
    val removed = bubbleColors().danger
    val marked = accent.copy(alpha = MARKED_ALPHA)
    val inline = remember(host, removed, marked, article, anchors, requesters) {
        ArticleInline(
            onButton = host.onButton,
            onAnchor = { link ->
                when (val anchor = anchors[anchorName(link)]) {
                    is ArticleAnchor.Footnote -> footnote = anchor.text
                    is ArticleAnchor.Block -> {
                        val target = (anchor.index until article.blocks.size).firstOrNull { article.blocks[it] !is ArticleBlock.Anchor } ?: anchor.index
                        coroutines.launch { requesters[target].bringIntoView() }
                    }
                    null -> Unit
                }
            },
            downloads = host.downloads,
            onDownload = host.onDownload,
            removed = removed,
            marked = marked,
            enabled = host.enabled,
        )
    }
    val media = remember(article) {
        val found = IdentityHashMap<ArticleBlock, Int>()
        article.blocks.forEach { top ->
            top.walk(onBlock = { block ->
                val item = (block as? ArticleBlock.Photo)?.media ?: (block as? ArticleBlock.Video)?.media
                if (item != null) found[block] = found.size
            })
        }
        found
    }
    val scope = ArticleScope(message.id, linkColor, accent, revealed, { revealed = true }, actions, press, emoji, inline, host, article.rtl, media::get)
    val wide = article.part || article.blocks.any { it.wide }
    val cap = with(LocalDensity.current) { PART_MAX_HEIGHT.toPx() }
    var height by remember { mutableIntStateOf(0) }
    val part = if (article.part) {
        Modifier
            .heightIn(max = PART_MAX_HEIGHT)
            .clipToBounds()
            .fadedBottom(height > cap)
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .onSizeChanged { height = it.height }
    } else {
        Modifier
    }
    Column((if (wide) Modifier.fillMaxWidth() else Modifier).testTag("article_${message.id}")) {
        Column(Modifier.testTag("articleBlocks_${message.id}").then(part), verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
            article.blocks.forEachIndexed { index, block ->
                Box(Modifier.bringIntoViewRequester(requesters[index])) { ArticleBlockView(block, scope) }
            }
        }
        val more = host.onShowMore
        if (article.part && more != null) {
            FilledTonalButton(
                onClick = more,
                enabled = host.enabled,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = SHOW_MORE_HEIGHT).testTag("showMore_${message.id}"),
            ) {
                if (host.loadingMore) {
                    LoadingIndicator(Modifier.size(24.dp).testTag("showMoreProgress"), color = LocalContentColor.current)
                    Text(stringResource(R.string.common_cancel), Modifier.padding(start = 8.dp))
                } else {
                    Text(stringResource(R.string.chat_article_show_more))
                }
            }
        }
    }
    footnote?.let { text ->
        ModalBottomSheet(onDismissRequest = { footnote = null }, modifier = Modifier.testTag("footnote")) {
            Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) { ArticleParagraph(text, scope, tag = "footnoteText") }
        }
    }
}

private val ArticleBlock.wide: Boolean
    get() = when (this) {
        is ArticleBlock.Paragraph, is ArticleBlock.Heading, is ArticleBlock.Footer, is ArticleBlock.Thinking, is ArticleBlock.Items,
        is ArticleBlock.Quote, is ArticleBlock.QuoteBlocks, is ArticleBlock.Pullquote, is ArticleBlock.Details, is ArticleBlock.Anchor,
        -> false
        else -> true
    }

@Composable
internal fun ArticleBlockView(block: ArticleBlock, scope: ArticleScope) {
    val body = MaterialTheme.typography.bodyLarge
    when (block) {
        is ArticleBlock.Paragraph -> ArticleParagraph(block.text, scope)
        is ArticleBlock.Heading -> {
            val scale = HEADING_SCALES[(block.level - 1).coerceIn(0, HEADING_SCALES.lastIndex)]
            val style = body.copy(fontSize = body.fontSize * scale, lineHeight = body.lineHeight * scale, fontWeight = FontWeight.Bold)
            Box(Modifier.semantics { heading() }) { ArticleParagraph(block.text, scope, style, tag = "heading${block.level}") }
        }
        is ArticleBlock.Footer -> ArticleParagraph(block.text, scope, MaterialTheme.typography.bodyMedium.copy(color = bubbleColors().secondary), tag = "footer")
        is ArticleBlock.Thinking -> Box(Modifier.shimmer(LocalContentColor.current).testTag("thinking")) {
            ArticleParagraph(block.text, scope, body.copy(fontStyle = FontStyle.Italic))
        }
        is ArticleBlock.Preformatted -> ArticlePre(block, scope)
        is ArticleBlock.Items -> ArticleList(block, scope)
        is ArticleBlock.Quote -> QuoteBlock(block.collapsed, scope.accent) { maxLines, onOverflow ->
            ArticleParagraph(block.text, scope, maxLines = maxLines, onOverflow = onOverflow)
            ArticleAuthor(block.author, scope)
        }
        is ArticleBlock.QuoteBlocks -> QuoteBlock(false, scope.accent) { _, _ ->
            Column(verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
                block.blocks.forEach { ArticleBlockView(it, scope) }
                ArticleAuthor(block.author, scope)
            }
        }
        is ArticleBlock.Pullquote -> Column(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("pullquote"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArticleParagraph(block.text, scope, body.copy(fontStyle = FontStyle.Italic), textAlign = TextAlign.Center)
            ArticleAuthor(block.author, scope, TextAlign.Center)
        }
        is ArticleBlock.Details -> ArticleDetails(block, scope)
        is ArticleBlock.Math -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                block.source,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(scope.linkColor.copy(alpha = 0.12f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("math"),
            )
        }
        ArticleBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 4.dp).testTag("divider"), color = LocalContentColor.current.copy(alpha = 0.2f))
        is ArticleBlock.Anchor -> Unit
        ArticleBlock.Unsupported -> UnsupportedBlock()
        is ArticleBlock.Photo -> ArticleVisualBlock(block, block.media, block.spoiler, block.caption, scope)
        is ArticleBlock.Video -> ArticleVisualBlock(block, block.media, block.spoiler, block.caption, scope)
        is ArticleBlock.Collage -> ArticleCollage(block, scope)
        is ArticleBlock.Slideshow -> ArticleSlideshow(block, scope)
        is ArticleBlock.Audio -> ArticleAudio(block, scope)
        is ArticleBlock.Document -> ArticleDocument(block, scope)
        is ArticleBlock.Map -> ArticleMap(block, scope)
        is ArticleBlock.Table -> ArticleTable(block, scope)
        is ArticleBlock.Buttons -> ArticleButtons(block, scope)
    }
}

@Composable
internal fun ArticleParagraph(
    text: ArticleText,
    scope: ArticleScope,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    onOverflow: (Boolean) -> Unit = {},
    softWrap: Boolean = true,
    textAlign: TextAlign = TextAlign.Unspecified,
    tag: String? = null,
) {
    val run = remember(text) { text.flatten() }
    if (run.text.isEmpty()) return
    RichParagraph(
        run.text, run.entities, scope.messageId, scope.linkColor, scope.revealed, scope.onReveal, scope.actions, scope.press, scope.emoji,
        maxLines = maxLines,
        onOverflow = onOverflow,
        style = style,
        marks = run.marks,
        article = scope.inline,
        softWrap = softWrap,
        textAlign = textAlign,
        tag = tag ?: "messageText_${scope.messageId}",
    )
}

@Composable
private fun ArticleAuthor(author: ArticleText, scope: ArticleScope, textAlign: TextAlign = TextAlign.Unspecified) {
    ArticleParagraph(author, scope, MaterialTheme.typography.labelLarge.copy(color = bubbleColors().secondary), textAlign = textAlign, tag = "quoteAuthor")
}

@Composable
private fun ArticlePre(block: ArticleBlock.Preformatted, scope: ArticleScope) {
    val color = LocalContentColor.current
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .testTag("pre"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                block.language.ifBlank { stringResource(R.string.chat_code) },
                style = MaterialTheme.typography.labelMedium,
                color = color.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            IconButton(onClick = { copied(context, block.text.flatten().text) }, modifier = Modifier.testTag("copyCode"), shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_content_copy), stringResource(R.string.chat_copy_code), Modifier.size(18.dp))
            }
        }
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
            ArticleParagraph(block.text, scope, MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), softWrap = false, tag = "preText")
        }
    }
}

@Composable
private fun ArticleList(block: ArticleBlock.Items, scope: ArticleScope) {
    Column(verticalArrangement = Arrangement.spacedBy(LIST_GAP), modifier = Modifier.testTag("list")) {
        block.items.forEachIndexed { index, item ->
            Row {
                Box(Modifier.widthIn(min = MARKER_WIDTH).padding(end = 6.dp), contentAlignment = Alignment.TopCenter) {
                    if (item.checkbox) {
                        Icon(
                            painterResource(if (item.checked) R.drawable.ic_check_box else R.drawable.ic_check_box_outline_blank),
                            stringResource(if (item.checked) R.string.chat_article_checked else R.string.chat_article_unchecked),
                            tint = if (item.checked) scope.accent else bubbleColors().secondary,
                            modifier = Modifier.padding(top = 2.dp)
                                .then(
                                    scope.host.onToggleCheck?.takeIf { scope.host.enabled }
                                        ?.let { toggle -> Modifier.clickable(role = Role.Checkbox) { toggle(item) }.minimumInteractiveComponentSize() }
                                        ?: Modifier,
                                )
                                .size(20.dp)
                                .testTag(if (item.checked) "checkboxChecked" else "checkboxOpen"),
                        )
                    } else {
                        val marker = block.number(index)?.let { "$it." } ?: "\u2022"
                        Text(marker, style = MaterialTheme.typography.bodyLarge, color = bubbleColors().secondary, modifier = Modifier.testTag("marker"))
                    }
                }
                Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(LIST_GAP)) {
                    item.blocks.forEach { ArticleBlockView(it, scope) }
                }
            }
        }
    }
}

@Composable
private fun ArticleDetails(block: ArticleBlock.Details, scope: ArticleScope) {
    var open by remember(block) { mutableStateOf(block.open) }
    val color = LocalContentColor.current
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.06f))
            .testTag("details"),
    ) {
        Row(
            Modifier.clickable { open = !open }.padding(horizontal = 10.dp, vertical = 6.dp).testTag("detailsToggle"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f, fill = false)) { ArticleParagraph(block.title, scope, MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)) }
            Icon(
                painterResource(R.drawable.ic_expand_more),
                stringResource(if (open) R.string.chat_quote_less else R.string.chat_quote_more),
                tint = scope.accent,
                modifier = Modifier.padding(start = 6.dp).rotate(if (open) 180f else 0f),
            )
        }
        AnimatedVisibility(open, enter = MaterialTheme.motionScheme.expandFadeIn(), exit = MaterialTheme.motionScheme.shrinkFadeOut()) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
                block.blocks.forEach { ArticleBlockView(it, scope) }
            }
        }
    }
}

@Composable
private fun ArticleButtons(block: ArticleBlock.Buttons, scope: ArticleScope) {
    val arrangement = when (block.align) {
        ArticleAlign.Start -> Arrangement.spacedBy(BUTTON_GAP, Alignment.Start)
        ArticleAlign.Center -> Arrangement.spacedBy(BUTTON_GAP, Alignment.CenterHorizontally)
        ArticleAlign.End -> Arrangement.spacedBy(BUTTON_GAP, Alignment.End)
        null -> Arrangement.spacedBy(BUTTON_GAP)
    }
    Row(Modifier.fillMaxWidth().testTag("articleButtons"), horizontalArrangement = arrangement) {
        block.buttons.forEach { button ->
            val size = if (block.align == null) Modifier.weight(1f) else Modifier
            KeyboardButton(
                button.action,
                { scope.host.onButton?.invoke(button.action) },
                size.heightIn(min = 40.dp).testTag("articleButton_${button.action.text}"),
                enabled = scope.host.enabled && scope.host.onButton != null,
            )
        }
    }
}

@Composable
private fun UnsupportedBlock() {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val color = LocalContentColor.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(start = 12.dp, end = 12.dp, top = 10.dp)
            .testTag("articleUnsupported"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_info), null, Modifier.size(20.dp))
            Text(stringResource(R.string.chat_article_unsupported_title), style = MaterialTheme.typography.titleSmall)
        }
        Text(stringResource(R.string.chat_article_unsupported_text), style = MaterialTheme.typography.bodySmall, color = bubbleColors().secondary, modifier = Modifier.padding(top = 4.dp))
        TextButton(
            onClick = { runCatching { uri.openUri("market://details?id=${context.packageName}") } },
            colors = ButtonDefaults.textButtonColors(contentColor = bubbleColors().accent),
            modifier = Modifier.align(Alignment.End).testTag("articleUpdate"),
        ) { Text(stringResource(R.string.chat_article_update)) }
    }
}

private fun Modifier.fadedBottom(on: Boolean): Modifier = if (!on) {
    this
} else {
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = PART_FADE.toPx()
            val top = size.height - fade
            drawRect(
                Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = top, endY = size.height),
                topLeft = Offset(0f, top),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn,
            )
        }
}

@Composable
private fun Modifier.shimmer(color: Color): Modifier {
    val shift by rememberInfiniteTransition(label = "thinking").animateFloat(-0.5f, 1.5f, infiniteRepeatable(tween(SHIMMER_CYCLE, easing = LinearEasing)), label = "shift")
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val center = shift * size.width
            val brush = Brush.linearGradient(
                listOf(color.copy(alpha = 0.4f), color, color.copy(alpha = 0.4f)),
                start = Offset(center - size.width / 3, 0f),
                end = Offset(center + size.width / 3, 0f),
            )
            drawRect(brush, blendMode = BlendMode.SrcIn)
        }
}

private val HEADING_SCALES = listOf(1.5f, 1.35f, 1.2f, 1.1f, 1f, 0.92f)
private val BLOCK_GAP = 6.dp
private val LIST_GAP = 4.dp
private val BUTTON_GAP = 4.dp
private val MARKER_WIDTH = 22.dp
private const val MARKED_ALPHA = 0.25f
private const val SHIMMER_CYCLE = 1_600

private val PART_MAX_HEIGHT = 900.dp
private val PART_FADE = 64.dp
private val SHOW_MORE_HEIGHT = 48.dp
