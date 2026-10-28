package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.model.cleared
import cn.ellerotta.core.model.toggled
import cn.ellerotta.core.ui.R

enum class FormatAction(val icon: Int, val label: Int, val tag: String) {
    Bold(R.drawable.ic_format_bold, R.string.chat_format_bold, "formatBold"),
    Italic(R.drawable.ic_format_italic, R.string.chat_format_italic, "formatItalic"),
    Underline(R.drawable.ic_format_underlined, R.string.chat_format_underline, "formatUnderline"),
    Strike(R.drawable.ic_strikethrough_s, R.string.chat_format_strike, "formatStrike"),
    Spoiler(R.drawable.ic_visibility_off, R.string.chat_format_spoiler, "formatSpoiler"),
    Code(R.drawable.ic_code, R.string.chat_format_code, "formatCode"),
    Quote(R.drawable.ic_format_quote, R.string.chat_format_quote, "formatQuote"),
    Link(R.drawable.ic_link, R.string.chat_format_link, "formatLink"),
    Clear(R.drawable.ic_format_clear, R.string.chat_format_clear, "formatClear"),
}

fun formatted(draft: FormattedText, action: FormatAction, selection: TextRange): FormattedText {
    val start = selection.min
    val end = selection.max
    return when (action) {
        FormatAction.Bold -> draft.toggled(EntityType.Bold, start, end)
        FormatAction.Italic -> draft.toggled(EntityType.Italic, start, end)
        FormatAction.Underline -> draft.toggled(EntityType.Underline, start, end)
        FormatAction.Strike -> draft.toggled(EntityType.Strike, start, end)
        FormatAction.Spoiler -> draft.toggled(EntityType.Spoiler, start, end)
        FormatAction.Code -> draft.toggled(EntityType.Code, start, end)
        FormatAction.Quote -> draft.toggled(EntityType.Blockquote(), start, end)
        FormatAction.Clear -> draft.cleared(start, end)
        FormatAction.Link -> draft
    }
}

fun linkUrl(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return ""
    val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(trimmed)
    return if (scheme) trimmed else "https://$trimmed"
}

fun linkAt(draft: FormattedText, selection: TextRange): String =
    draft.entities.firstOrNull { it.type is EntityType.TextUrl && it.offset <= selection.min && it.end >= selection.max }
        ?.let { (it.type as EntityType.TextUrl).url }.orEmpty()

@Composable
internal fun FormattingBar(actions: List<FormatAction> = FormatAction.entries, onAction: (FormatAction) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().testTag("formattingBar")) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            actions.forEach { action ->
                IconButton(onClick = { onAction(action) }, modifier = Modifier.testTag(action.tag), shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(action.icon), stringResource(action.label))
                }
            }
        }
    }
}

@Composable
internal fun LinkDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var url by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_format_link)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                label = { Text(stringResource(R.string.chat_link_url)) },
                modifier = Modifier.fillMaxWidth().testTag("linkUrl"),
            )
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = { onConfirm(linkUrl(url)) }, enabled = url.isNotBlank(), modifier = Modifier.testTag("confirmLink")) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            if (initial.isNotEmpty()) {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { onConfirm("") }, modifier = Modifier.testTag("removeLink")) { Text(stringResource(R.string.chat_link_remove)) }
            } else {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}

internal class EntitiesTransformation(
    private val entities: List<TextEntity>,
    private val colors: RichColors,
    private val drawn: Set<Long> = emptySet(),
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(styled(text.text), OffsetMapping.Identity)

    private fun styled(text: String): AnnotatedString = buildAnnotatedString {
        append(text)
        val edges = (entities.flatMap { listOf(it.offset, it.end) } + 0 + text.length).filter { it in 0..text.length }.toSortedSet().toList()
        for (i in 0 until edges.size - 1) {
            val from = edges[i]
            val to = edges[i + 1]
            val covering = entities.filter { it.offset <= from && it.end >= to }
            pieceStyle(covering, colors, revealed = true)?.let { addStyle(it, from, to) }
            if (covering.any { it.type == EntityType.Spoiler || it.type is EntityType.Blockquote }) addStyle(SpanStyle(background = colors.code), from, to)
            if (covering.any { (it.type as? EntityType.CustomEmoji)?.documentId in drawn }) addStyle(SpanStyle(color = Color.Transparent), from, to)
        }
    }

    override fun equals(other: Any?): Boolean = other is EntitiesTransformation && other.entities == entities && other.colors == colors && other.drawn == drawn

    override fun hashCode(): Int = (entities.hashCode() * 31 + colors.hashCode()) * 31 + drawn.hashCode()
}

internal val EntitiesSaver: Saver<List<TextEntity>, ArrayList<String>> = Saver(
    save = { entities -> entities.mapNotNullTo(ArrayList()) { entity -> encode(entity.type)?.let { "${entity.offset},${entity.length},$it" } } },
    restore = { saved ->
        saved.mapNotNull { line ->
            val parts = line.split(',', limit = 4)
            val type = decode(parts[2], parts.getOrElse(3) { "" }) ?: return@mapNotNull null
            TextEntity(parts[0].toInt(), parts[1].toInt(), type)
        }
    },
)

private fun encode(type: EntityType): String? = when (type) {
    EntityType.Bold -> "b,"
    EntityType.Italic -> "i,"
    EntityType.Underline -> "u,"
    EntityType.Strike -> "s,"
    EntityType.Spoiler -> "sp,"
    EntityType.Code -> "c,"
    is EntityType.Pre -> "pre,${type.language}"
    is EntityType.Blockquote -> "q,${type.collapsed}"
    is EntityType.TextUrl -> "url,${type.url}"
    is EntityType.MentionName -> "mn,${type.userId}"
    is EntityType.CustomEmoji -> "ce,${type.documentId}"
    else -> null
}

private fun decode(kind: String, argument: String): EntityType? = when (kind) {
    "b" -> EntityType.Bold
    "i" -> EntityType.Italic
    "u" -> EntityType.Underline
    "s" -> EntityType.Strike
    "sp" -> EntityType.Spoiler
    "c" -> EntityType.Code
    "pre" -> EntityType.Pre(argument)
    "q" -> EntityType.Blockquote(argument.toBoolean())
    "url" -> EntityType.TextUrl(argument)
    "mn" -> argument.toLongOrNull()?.let(EntityType::MentionName)
    "ce" -> argument.toLongOrNull()?.let(EntityType::CustomEmoji)
    else -> null
}

@Composable
internal fun composerColors(): RichColors {
    val primary = MaterialTheme.colorScheme.primary
    return RichColors(primary, primary.copy(alpha = 0.12f))
}
