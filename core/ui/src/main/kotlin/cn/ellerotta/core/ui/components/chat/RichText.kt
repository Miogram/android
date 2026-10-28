package cn.ellerotta.core.ui.components.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.os.Build
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.os.ConfigurationCompat
import cn.ellerotta.core.model.ArticleMark
import cn.ellerotta.core.model.ArticleMarkType
import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.BotButtonStyle
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.TextBlock
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.model.blocksOf
import cn.ellerotta.core.model.linkTarget
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.profile.copyToClipboard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

@Stable
class TextActions(
    val onOpenLink: (String) -> Unit = {},
    val onCommand: ((String) -> Unit)? = null,
    val onRemind: ((Int, Int) -> Unit)? = null,
    val onHashtag: ((String) -> Unit)? = null,
)

@Stable
internal class TextPress {
    var copy: String? = null
}

internal class RichText(val text: AnnotatedString, val ranges: List<Pair<TextEntity, IntRange>>, val spoilers: List<IntRange>, val wavy: List<IntRange> = emptyList())

internal data class RichColors(val link: Color, val code: Color, val removed: Color = Color.Unspecified, val marked: Color = Color.Unspecified)

internal fun buildRichText(
    text: String,
    entities: List<TextEntity>,
    colors: RichColors,
    revealed: Boolean,
    dateText: (EntityType.FormattedDate) -> String,
    inlineEmoji: (Long) -> Boolean = { false },
    marks: List<ArticleMark> = emptyList(),
    inlineMark: (Int, ArticleMark) -> String? = { _, _ -> null },
    onTap: (TextEntity) -> Unit,
): RichText {
    val spans = marks.filter { it.length > 0 }
    val edges = (entities.flatMap { listOf(it.offset, it.end) } + spans.flatMap { listOf(it.offset, it.end) } + 0 + text.length)
        .filter { it in 0..text.length }.toSortedSet().toList()
    class Replacement(val offset: Int, val end: Int, val append: AnnotatedString.Builder.() -> Unit)
    val replaced = entities.mapNotNull { entity ->
        when (val type = entity.type) {
            is EntityType.FormattedDate -> if (type.formats) Replacement(entity.offset, entity.end) { append(dateText(type)) } else null
            is EntityType.CustomEmoji -> if (inlineEmoji(type.documentId)) Replacement(entity.offset, entity.end) { appendInlineContent(emojiId(type.documentId), entity.part(text)) } else null
            else -> null
        }
    } + marks.mapIndexedNotNull { index, mark ->
        if (mark.length == 0) return@mapIndexedNotNull null
        inlineMark(index, mark)?.let { id -> Replacement(mark.offset, mark.end) { appendInlineContent(id, text.substring(mark.offset, mark.end)) } }
    }
    val mapped = HashMap<Int, Int>()
    val pieces = mutableListOf<Triple<Int, Int, IntRange>>()
    val built = buildAnnotatedString {
        for (i in 0 until edges.size - 1) {
            val from = edges[i]
            val to = edges[i + 1]
            mapped[from] = length
            val replacement = replaced.firstOrNull { from >= it.offset && to <= it.end }
            val start = length
            when {
                replacement == null -> append(text, from, to)
                replacement.offset != from -> Unit
                else -> replacement.append(this)
            }
            if (length > start) pieces += Triple(from, to, start until length)
        }
        mapped[text.length] = length
        for ((from, to, range) in pieces) {
            val covering = entities.filter { it.offset <= from && it.end >= to }
            val marked = spans.filter { it.offset <= from && it.end >= to }.map { it.type }
            pieceStyle(covering, colors, revealed, marked)?.let { addStyle(it, range.first, range.last + 1) }
        }
    }
    val ranges = entities.mapNotNull { entity ->
        val start = mapped[entity.offset] ?: return@mapNotNull null
        val end = mapped[entity.end] ?: return@mapNotNull null
        if (end <= start) null else entity to (start until end)
    }
    val wavy = spans.filter { it.type == ArticleMarkType.Changed }.mapNotNull { mark ->
        val start = mapped[mark.offset] ?: return@mapNotNull null
        val end = mapped[mark.end] ?: return@mapNotNull null
        if (end <= start) null else start until end
    }
    val spoilers = if (revealed) emptyList() else ranges.filter { it.first.type == EntityType.Spoiler }.map { it.second }
    val annotated = AnnotatedString.Builder(built).apply {
        for ((entity, range) in ranges) {
            val tag = tagOf(text, entity) ?: continue
            if (spoilers.any { it.first < range.last + 1 && range.first < it.last + 1 }) continue
            addLink(LinkAnnotation.Clickable(tag, PLAIN_LINK, { onTap(entity) }), range.first, range.last + 1)
        }
        spoilers.forEach { range -> addLink(LinkAnnotation.Clickable(SPOILER_TAG, PLAIN_LINK, { onTap(TextEntity(0, 0, EntityType.Spoiler)) }), range.first, range.last + 1) }
    }.toAnnotatedString()
    return RichText(annotated, ranges, spoilers, wavy)
}

private fun tagOf(text: String, entity: TextEntity): String? = linkTarget(text, entity) ?: when (val type = entity.type) {
    EntityType.Hashtag, EntityType.Cashtag, EntityType.BotCommand, EntityType.BankCard, EntityType.Code -> entity.part(text)
    is EntityType.FormattedDate -> "date:${type.date}"
    else -> null
}

private val EntityType.isLink: Boolean
    get() = when (this) {
        EntityType.Url, is EntityType.TextUrl, EntityType.Mention, is EntityType.MentionName, EntityType.Hashtag, EntityType.Cashtag,
        EntityType.BotCommand, EntityType.Email, EntityType.Phone, EntityType.BankCard, is EntityType.FormattedDate,
        -> true
        else -> false
    }

internal fun pieceStyle(covering: List<TextEntity>, colors: RichColors, revealed: Boolean, marks: List<ArticleMarkType> = emptyList()): SpanStyle? {
    if (covering.isEmpty() && marks.isEmpty()) return null
    val types = covering.map { it.type }
    val removed = ArticleMarkType.Removed in marks
    val formula = marks.any { it is ArticleMarkType.Math }
    val decorations = buildList {
        if (EntityType.Underline in types || types.any { it.isLink }) add(TextDecoration.Underline)
        if (EntityType.Strike in types || removed) add(TextDecoration.LineThrough)
    }
    val hidden = !revealed && EntityType.Spoiler in types
    val small = ArticleMarkType.Subscript in marks || ArticleMarkType.Superscript in marks
    return SpanStyle(
        color = when {
            hidden -> Color.Transparent
            removed -> colors.removed
            types.any { it.isLink } || ArticleMarkType.Added in marks -> colors.link
            else -> Color.Unspecified
        },
        fontSize = if (small) SCRIPT_SIZE.em else TextUnit.Unspecified,
        fontWeight = if (EntityType.Bold in types) FontWeight.Bold else null,
        fontStyle = if (EntityType.Italic in types) FontStyle.Italic else null,
        fontFamily = if (EntityType.Code in types || formula) FontFamily.Monospace else null,
        baselineShift = when {
            ArticleMarkType.Subscript in marks -> BaselineShift.Subscript
            ArticleMarkType.Superscript in marks -> BaselineShift.Superscript
            else -> null
        },
        background = when {
            hidden -> Color.Unspecified
            EntityType.Code in types || formula -> colors.code
            ArticleMarkType.Marked in marks -> colors.marked
            else -> Color.Unspecified
        },
        textDecoration = if (decorations.isEmpty() || hidden) null else TextDecoration.combine(decorations),
    )
}

private fun copyOf(text: String, entity: TextEntity, longDate: (EntityType.FormattedDate) -> String): String? = when (val type = entity.type) {
    is EntityType.TextUrl -> type.url
    is EntityType.FormattedDate -> longDate(type)
    EntityType.Spoiler, is EntityType.CustomEmoji, EntityType.Bold, EntityType.Italic, EntityType.Underline, EntityType.Strike,
    is EntityType.Pre, is EntityType.Blockquote,
    -> null
    else -> entity.part(text)
}

internal fun emojiId(documentId: Long): String = "emoji:$documentId"

@Immutable
internal class InlineEmoji(val documents: Map<Long, MediaItem>, val files: Map<String, ByteArray>) {
    fun ready(documentId: Long): Boolean = documents[documentId]?.let { files[it.key] } != null
}

@Composable
internal fun MessageText(message: MessageItem, linkColor: Color, actions: TextActions, press: TextPress, emoji: InlineEmoji = InlineEmoji(emptyMap(), emptyMap()), lastLine: LastLine? = null) {
    val blocks = remember(message.text, message.entities) { blocksOf(message.text, message.entities) }
    var revealed by remember(message.id) { mutableStateOf(false) }
    val context = LocalContext.current
    if (lastLine != null && blocks.lastOrNull() !is TextBlock.Paragraph) lastLine.right = Float.POSITIVE_INFINITY
    Column {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is TextBlock.Paragraph -> {
                    val report = lastLine?.takeIf { index == blocks.lastIndex }?.let { line -> { result: TextLayoutResult -> line.right = lastLineEnd(result) } }
                    RichParagraph(block.text, block.entities, message.id, linkColor, revealed, { revealed = true }, actions, press, emoji, onLayout = report)
                }
                is TextBlock.Quote -> QuoteBlock(block.collapsed, linkColor) { maxLines, onOverflow ->
                    RichParagraph(block.text, block.entities, message.id, linkColor, revealed, { revealed = true }, actions, press, emoji, maxLines, onOverflow)
                }
                is TextBlock.Pre -> PreBlock(block.text, block.language) { copied(context, it) }
            }
        }
    }
}

internal fun lastLineEnd(result: TextLayoutResult): Float {
    if (result.lineCount == 0) return 0f
    val line = result.lineCount - 1
    val rtl = result.getParagraphDirection(result.getLineStart(line)) == ResolvedTextDirection.Rtl
    return when {
        rtl != (result.layoutInput.layoutDirection == LayoutDirection.Rtl) -> Float.POSITIVE_INFINITY
        rtl -> result.size.width - result.getLineLeft(line)
        else -> result.getLineRight(line)
    }
}

internal fun copied(context: Context, text: String) {
    copyToClipboard(context, "text", text)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, R.string.chat_copied, Toast.LENGTH_SHORT).show()
}

@Immutable
internal class ArticleInline(
    val onButton: ((BotButton) -> Unit)?,
    val onAnchor: (String) -> Unit,
    val downloads: Map<String, DownloadState>,
    val onDownload: (MediaItem) -> Unit,
    val removed: Color,
    val marked: Color,
    val enabled: Boolean = true,
)

@Composable
internal fun RichParagraph(
    text: String,
    entities: List<TextEntity>,
    messageId: Int,
    linkColor: Color,
    revealed: Boolean,
    onReveal: () -> Unit,
    actions: TextActions,
    press: TextPress,
    emoji: InlineEmoji,
    maxLines: Int = Int.MAX_VALUE,
    onOverflow: (Boolean) -> Unit = {},
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    marks: List<ArticleMark> = emptyList(),
    article: ArticleInline? = null,
    softWrap: Boolean = true,
    textAlign: TextAlign = TextAlign.Unspecified,
    onLayout: ((TextLayoutResult) -> Unit)? = null,
    tag: String = "messageText_$messageId",
) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val relative = entities.any { (it.type as? EntityType.FormattedDate)?.relative == true }
    val now by produceState(System.currentTimeMillis() / 1000, relative) {
        while (relative) {
            delay(1_000)
            value = System.currentTimeMillis() / 1000
        }
    }
    var dateMenu by remember { mutableStateOf<EntityType.FormattedDate?>(null) }
    val haptics = LocalHapticFeedback.current
    val tap by rememberUpdatedState { entity: TextEntity ->
        when (val type = entity.type) {
            EntityType.Spoiler -> onReveal()
            EntityType.Email, EntityType.Phone -> linkTarget(text, entity)?.let { runCatching { uri.openUri(it) } }
            EntityType.Hashtag -> actions.onHashtag?.invoke(entity.part(text)) ?: copied(context, entity.part(text))
            EntityType.Cashtag, EntityType.BankCard, EntityType.Code -> copied(context, entity.part(text))
            EntityType.BotCommand -> actions.onCommand?.invoke(entity.part(text)) ?: copied(context, entity.part(text))
            is EntityType.FormattedDate -> dateMenu = type
            else -> linkTarget(text, entity)?.let { target -> if (article != null && target.startsWith("#")) article.onAnchor(target) else actions.onOpenLink(target) }
        }
    }
    val colors = RichColors(linkColor, linkColor.copy(alpha = 0.12f), article?.removed ?: Color.Unspecified, article?.marked ?: Color.Unspecified)
    val used = entities.mapNotNull { (it.type as? EntityType.CustomEmoji)?.documentId }
    val ready = used.filter(emoji::ready).toSet()
    val inlineMark: (Int, ArticleMark) -> String? = if (article == null) {
        { _, _ -> null }
    } else {
        { index, mark ->
            when (mark.type) {
                is ArticleMarkType.Button -> "button:$index"
                is ArticleMarkType.Image -> "image:$index"
                else -> null
            }
        }
    }
    val rich = remember(text, entities, revealed, colors, now, locale, ready, marks, article != null) {
        buildRichText(text, entities, colors, revealed, { dateEntityText(it, now, locale, zone) }, { it in ready }, marks, inlineMark) { tap(it) }
    }
    val density = LocalDensity.current
    val side = with(density) { (style.fontSize * EMOJI_SCALE).toDp() }
    val measurer = rememberTextMeasurer()
    val pillStyle = style.copy(fontWeight = FontWeight.SemiBold, fontSize = style.fontSize * PILL_TEXT, baselineShift = null, background = Color.Unspecified)
    val inline = remember(ready, emoji, side, marks, article, pillStyle, density) {
        val emojiContent = ready.associate { id ->
            val media = emoji.documents.getValue(id)
            emojiId(id) to InlineTextContent(Placeholder(EMOJI_SCALE.em, EMOJI_SCALE.em, PlaceholderVerticalAlign.TextCenter)) {
                val tint = LocalContentColor.current
                StickerView(media, emoji.files[media.key], Modifier.then(if (media.textColor) Modifier.tinted(tint) else Modifier).testTag("emoji_$id"), size = side)
            }
        }
        if (article == null) return@remember emojiContent
        emojiContent + marks.mapIndexedNotNull { index, mark ->
            when (val type = mark.type) {
                is ArticleMarkType.Button -> {
                    val label = text.substring(mark.offset, mark.end)
                    val link = type.action.style == BotButtonStyle.Link
                    val padding = if (link) 0.dp else PILL_PADDING
                    val width = with(density) { (measurer.measure(label, pillStyle, maxLines = 1).size.width + (padding * 2 + PILL_GAP).toPx()).toSp() }
                    "button:$index" to InlineTextContent(Placeholder(width, PILL_HEIGHT.em, PlaceholderVerticalAlign.TextCenter)) {
                        InlinePill(label, type.action, pillStyle, padding, article)
                    }
                }
                is ArticleMarkType.Image -> {
                    val ratio = if (type.width > 0 && type.height > 0) type.width.toFloat() / type.height else 1f
                    "image:$index" to InlineTextContent(Placeholder((EMOJI_SCALE * ratio).em, EMOJI_SCALE.em, PlaceholderVerticalAlign.TextCenter)) {
                        val state = article.downloads[type.media.key]
                        LaunchedEffect(type.media.key) { if (state == null) article.onDownload(type.media) }
                        StillImage(type.media, state, modifier = Modifier.fillMaxSize().testTag("inlineImage_${type.media.key}"))
                    }
                }
                else -> null
            }
        }.toMap()
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val particles = LocalContentColor.current
    val phase = spoilerPhase(rich.spoilers.isNotEmpty())
    val configuration = LocalViewConfiguration.current
    val exactLinks = remember(configuration) {
        object : ViewConfiguration by configuration {
            override val minimumTouchTargetSize: DpSize get() = DpSize.Zero
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides exactLinks) {
        Text(
            rich.text,
            style = style,
            maxLines = maxLines,
            overflow = if (softWrap) TextOverflow.Ellipsis else TextOverflow.Clip,
            softWrap = softWrap,
            textAlign = textAlign,
            inlineContent = inline,
            onTextLayout = {
                layout = it
                onOverflow(it.hasVisualOverflow)
                onLayout?.invoke(it)
            },
            modifier = Modifier
                .pointerInput(rich) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val copy = layout?.let { result ->
                            val offset = result.getOffsetForPosition(down.position)
                            val box = result.getBoundingBox(offset.coerceAtMost(maxOf(result.layoutInput.text.length - 1, 0)))
                            if (!box.inflate(8.dp.toPx()).contains(down.position)) return@let null
                            rich.ranges.firstOrNull { (_, range) -> offset in range && range !in rich.spoilers }?.first
                                ?.let { copyOf(text, it) { date -> longDateText(date.date, locale, zone) } }
                        }
                        press.copy = copy
                        if (copy == null) return@awaitEachGesture
                        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var change = down
                            while (change.pressed && (change.position - down.position).getDistance() <= viewConfiguration.touchSlop) {
                                change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                            }
                            true
                        }
                        if (ended != null) return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        copied(context, copy)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .drawWithContent {
                    drawContent()
                    val result = layout ?: return@drawWithContent
                    spoilerParticles(result, rich.spoilers, particles, phase?.value ?: 0f, messageId)
                    for (range in rich.wavy) wavyUnderline(result, range, linkColor)
                }
                .testTag(tag),
        )
    }
    dateMenu?.let { date ->
        DateEntityDialog(
            date = date,
            locale = locale,
            zone = zone,
            onCopy = { copied(context, longDateText(date.date, locale, zone)) },
            onCalendar = {
                val insert = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, date.date * 1000L)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, date.date * 1000L + 3_600_000L)
                try {
                    context.startActivity(insert)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.chat_no_calendar, Toast.LENGTH_SHORT).show()
                }
            },
            onRemind = actions.onRemind?.takeIf { date.date > System.currentTimeMillis() / 1000 }?.let { remind -> { remind(messageId, date.date) } },
            onDismiss = { dateMenu = null },
        )
    }
}

@Composable
private fun InlinePill(label: String, action: BotButton, style: TextStyle, padding: Dp, article: ArticleInline) {
    val enabled = article.enabled && article.onButton != null && action !is BotButton.Disabled
    val colors = colorsOf(action.style)
    Box(Modifier.fillMaxSize().padding(end = PILL_GAP), contentAlignment = Alignment.Center) {
        Surface(
            onClick = { article.onButton?.invoke(action) },
            enabled = enabled,
            shape = CircleShape,
            color = if (enabled) colors.containerColor else colors.disabledContainerColor,
            contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
            modifier = Modifier.fillMaxSize().testTag("inlineButton_$label"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(label, style = style.copy(color = LocalContentColor.current), maxLines = 1, modifier = Modifier.padding(horizontal = padding))
            }
        }
    }
}

private fun DrawScope.wavyUnderline(layout: TextLayoutResult, range: IntRange, color: Color) {
    val first = layout.getLineForOffset(range.first)
    val last = layout.getLineForOffset(range.last)
    val amplitude = 1.5.dp.toPx()
    val step = 3.dp.toPx()
    for (line in first..last) {
        val from = if (line == first) layout.getHorizontalPosition(range.first, true) else layout.getLineLeft(line)
        val to = if (line == last) layout.getHorizontalPosition(range.last + 1, true) else layout.getLineRight(line)
        val left = minOf(from, to)
        val right = maxOf(from, to)
        val y = layout.getLineBaseline(line) + 2.5.dp.toPx()
        val path = Path().apply { moveTo(left, y) }
        var x = left
        var up = true
        while (x < right) {
            val next = minOf(x + step, right)
            path.quadraticTo((x + next) / 2, if (up) y - amplitude else y + amplitude, next, y)
            x = next
            up = !up
        }
        drawPath(path, color, style = Stroke(1.dp.toPx()))
    }
}

internal fun Modifier.tinted(color: Color): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(color, blendMode = BlendMode.SrcIn)
    }

@Composable
internal fun QuoteBlock(collapsed: Boolean, accent: Color, content: @Composable (maxLines: Int, onOverflow: (Boolean) -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var overflowing by remember { mutableStateOf(false) }
    Column(
        Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.10f))
            .drawBehind { drawRect(accent, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 11.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
            .testTag("quote"),
    ) {
        content(if (collapsed && !expanded) COLLAPSED_LINES else Int.MAX_VALUE) { overflowing = it }
        if (collapsed && (overflowing || expanded)) {
            Icon(
                painterResource(R.drawable.ic_expand_more),
                stringResource(if (expanded) R.string.chat_quote_less else R.string.chat_quote_more),
                tint = accent,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(50))
                    .clickable { expanded = !expanded }
                    .rotate(if (expanded) 180f else 0f)
                    .testTag("quoteExpand"),
            )
        }
    }
}

@Composable
private fun PreBlock(text: String, language: String, onCopy: (String) -> Unit) {
    val color = LocalContentColor.current
    Column(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .testTag("pre"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                language.ifBlank { stringResource(R.string.chat_code) },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            IconButton(onClick = { onCopy(text) }, modifier = Modifier.testTag("copyCode"), shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_content_copy), stringResource(R.string.chat_copy_code), Modifier.size(18.dp))
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun DateEntityDialog(
    date: EntityType.FormattedDate,
    locale: Locale,
    zone: ZoneId,
    onCopy: () -> Unit,
    onCalendar: () -> Unit,
    onRemind: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(longDateText(date.date, locale, zone)) },
        text = {
            Column {
                ListItem(
                    onClick = {
                        onDismiss()
                        onCopy()
                    },
                    leadingContent = { Icon(painterResource(R.drawable.ic_content_copy), null) },
                    modifier = Modifier.testTag("dateCopy"),
                ) { Text(stringResource(R.string.chat_date_copy)) }
                ListItem(
                    onClick = {
                        onDismiss()
                        onCalendar()
                    },
                    leadingContent = { Icon(painterResource(R.drawable.ic_event), null) },
                    modifier = Modifier.testTag("dateCalendar"),
                ) { Text(stringResource(R.string.chat_date_calendar)) }
                if (onRemind != null) {
                    ListItem(
                        onClick = {
                            onDismiss()
                            onRemind()
                        },
                        leadingContent = { Icon(painterResource(R.drawable.ic_notifications), null) },
                        modifier = Modifier.testTag("dateRemind"),
                    ) { Text(stringResource(R.string.chat_date_remind)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

internal fun longDateText(date: Int, locale: Locale, zone: ZoneId): String {
    val time = Instant.ofEpochSecond(date.toLong()).atZone(zone)
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(time) + ", " +
        DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale).format(time)
}

internal fun dateEntityText(type: EntityType.FormattedDate, now: Long, locale: Locale, zone: ZoneId): String {
    if (type.relative) {
        val diff = type.date - now
        val seconds = abs(diff)
        val (amount, unit) = when {
            seconds < 60 -> seconds to RelativeDateTimeFormatter.RelativeUnit.SECONDS
            seconds < 3_600 -> seconds / 60 to RelativeDateTimeFormatter.RelativeUnit.MINUTES
            seconds < 86_400 -> seconds / 3_600 to RelativeDateTimeFormatter.RelativeUnit.HOURS
            seconds < 7 * 86_400 -> seconds / 86_400 to RelativeDateTimeFormatter.RelativeUnit.DAYS
            seconds < 30 * 86_400 -> seconds / (7 * 86_400) to RelativeDateTimeFormatter.RelativeUnit.WEEKS
            seconds < 365 * 86_400 -> seconds / (30 * 86_400) to RelativeDateTimeFormatter.RelativeUnit.MONTHS
            else -> seconds / (365 * 86_400) to RelativeDateTimeFormatter.RelativeUnit.YEARS
        }
        val direction = if (diff >= 0) RelativeDateTimeFormatter.Direction.NEXT else RelativeDateTimeFormatter.Direction.LAST
        return RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale)).format(amount.toDouble(), direction, unit)
    }
    val time = Instant.ofEpochSecond(type.date.toLong()).atZone(zone)
    val parts = buildList {
        if (type.dayOfWeek) add(DateTimeFormatter.ofPattern("EEEE", locale).format(time))
        if (type.shortTime) add(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time))
        if (type.longTime) add(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale).format(time))
        if (type.shortDate) {
            val sameYear = time.year == Instant.ofEpochSecond(now).atZone(zone).year
            val skeleton = if (sameYear) "MMMd" else "yMMMd"
            add(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(time))
        }
        if (type.longDate) add(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(time))
    }
    return parts.joinToString(", ")
}

internal const val SPOILER_TAG = "spoiler"

@Composable
internal fun spoilerPhase(hidden: Boolean): State<Float>? = if (hidden) {
    rememberInfiniteTransition(label = "spoiler").animateFloat(0f, 1f, infiniteRepeatable(tween(SPOILER_CYCLE, easing = LinearEasing)), label = "phase")
} else {
    null
}

internal fun DrawScope.spoilerParticles(result: TextLayoutResult, ranges: List<IntRange>, color: Color, phase: Float, seed: Int) {
    val turn = phase * 2 * PI.toFloat()
    for (range in ranges) {
        val path = result.getPathForRange(range.first, range.last + 1)
        val bounds = path.getBounds()
        clipPath(path) {
            drawRect(color.copy(alpha = 0.08f), bounds.topLeft, bounds.size)
            val step = 5.dp.toPx()
            val count = ((bounds.width * bounds.height) / (step * step)).toInt().coerceIn(8, 600)
            repeat(count) { i ->
                val spot = (i + 1) * 7919 + seed
                val x = bounds.left + (spot * 0.618f % 1f) * bounds.width + sin(turn + i) * step
                val y = bounds.top + (spot * 0.414f % 1f) * bounds.height + cos(turn + i * 1.3f) * step / 2
                drawCircle(color.copy(alpha = 0.35f + 0.45f * abs(sin(turn * 2 + i))), 0.9.dp.toPx(), Offset(x, y))
            }
        }
    }
}

@Composable
internal fun QuoteText(text: String, entities: List<TextEntity>, emoji: InlineEmoji, maxLines: Int, style: TextStyle, modifier: Modifier = Modifier) {
    val kept = remember(entities) { entities.filter { it.type in QUOTE_STYLES || it.type is EntityType.CustomEmoji } }
    val ready = kept.mapNotNull { (it.type as? EntityType.CustomEmoji)?.documentId }.filter(emoji::ready).toSet()
    val rich = remember(text, kept, ready) {
        val built = buildRichText(text, kept, RichColors(Color.Unspecified, Color.Unspecified), revealed = false, { "" }, { it in ready }) {}
        val styled = AnnotatedString(built.text.text, built.text.spanStyles, built.text.paragraphStyles)
        RichText(
            AnnotatedString.Builder(styled).apply { built.text.getStringAnnotations(0, built.text.length).forEach { addStringAnnotation(it.tag, it.item, it.start, it.end) } }.toAnnotatedString(),
            built.ranges,
            built.spoilers,
        )
    }
    val side = with(LocalDensity.current) { (style.fontSize * EMOJI_SCALE).toDp() }
    val inline = remember(ready, emoji, side) {
        ready.associate { id ->
            val media = emoji.documents.getValue(id)
            emojiId(id) to InlineTextContent(Placeholder(EMOJI_SCALE.em, EMOJI_SCALE.em, PlaceholderVerticalAlign.TextCenter)) {
                val tint = LocalContentColor.current
                StickerView(media, emoji.files[media.key], Modifier.then(if (media.textColor) Modifier.tinted(tint) else Modifier), size = side)
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val particles = LocalContentColor.current
    val phase = spoilerPhase(rich.spoilers.isNotEmpty())
    Text(
        rich.text,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = inline,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val result = layout ?: return@drawWithContent
            spoilerParticles(result, rich.spoilers, particles, phase?.value ?: 0f, 0)
        },
    )
}

private val QUOTE_STYLES = setOf(EntityType.Bold, EntityType.Italic, EntityType.Underline, EntityType.Strike, EntityType.Spoiler)

private val PLAIN_LINK = TextLinkStyles(SpanStyle())
private const val COLLAPSED_LINES = 3

private const val EMOJI_SCALE = 1.2f
private const val SPOILER_CYCLE = 4_000

private const val PILL_TEXT = 0.85f
private const val PILL_HEIGHT = 1.5f
private val PILL_PADDING = 10.dp
private val PILL_GAP = 4.dp

private const val SCRIPT_SIZE = 0.75f
