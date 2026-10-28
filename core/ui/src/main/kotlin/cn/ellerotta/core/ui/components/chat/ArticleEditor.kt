package cn.ellerotta.core.ui.components.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.ellerotta.core.model.ArticleCounts
import cn.ellerotta.core.model.ArticleGate
import cn.ellerotta.core.model.ArticleLimit
import cn.ellerotta.core.model.ArticleListItem
import cn.ellerotta.core.model.ArticleMediaKind
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.ArticleRow
import cn.ellerotta.core.model.ArticleRowKind
import cn.ellerotta.core.model.ArticleUpload
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.model.articleCommandOf
import cn.ellerotta.core.model.articleOfRows
import cn.ellerotta.core.model.asFormattedText
import cn.ellerotta.core.model.counts
import cn.ellerotta.core.model.currentUpload
import cn.ellerotta.core.model.edited
import cn.ellerotta.core.model.newTableRow
import cn.ellerotta.core.model.toggled
import cn.ellerotta.core.model.uploadsPending
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.TopBarColors
import cn.ellerotta.core.ui.components.common.TopBarPill
import cn.ellerotta.core.ui.components.common.TopBarTokens

class ChatArticleActions(
    val gate: ArticleGate,
    val onSend: (ArticleMessage, Int?) -> Unit,
    val onClose: (ArticleMessage, Int?) -> Unit = { _, _ -> },
    val onDeleteDraft: () -> Unit = {},
    val onEdit: ((Int, ArticleMessage) -> Unit)? = null,
    val onToggle: ((Int, ArticleListItem) -> Unit)? = null,
    val uploads: Map<Long, ArticleUpload> = emptyMap(),
    val onPickMedia: ((ArticleMediaKind) -> Unit)? = null,
    val onRetryUpload: (Long) -> Unit = {},
    val onRemoveUpload: (Long) -> Unit = {},
    val onClearUploads: () -> Unit = {},
)

private val ROW_KINDS = listOf(
    ArticleRowKind.Paragraph, ArticleRowKind.Heading1, ArticleRowKind.Heading2, ArticleRowKind.Heading3, ArticleRowKind.Bullet,
    ArticleRowKind.Numbered, ArticleRowKind.Check, ArticleRowKind.Quote, ArticleRowKind.Code, ArticleRowKind.Math, ArticleRowKind.Table,
    ArticleRowKind.Divider,
)

private val MULTILINE = setOf(ArticleRowKind.Code, ArticleRowKind.Math)

private val PLAIN = setOf(ArticleRowKind.Code, ArticleRowKind.Math, ArticleRowKind.Divider, ArticleRowKind.Table, ArticleRowKind.Block)

private val FIXED = setOf(ArticleRowKind.Table, ArticleRowKind.Media, ArticleRowKind.Block)

private val LISTS = setOf(ArticleRowKind.Bullet, ArticleRowKind.Numbered, ArticleRowKind.Check)

private val EDITOR_FORMATS = FormatAction.entries - FormatAction.Quote

@Composable
internal fun ArticleEditor(
    initial: List<ArticleRow>,
    gate: ArticleGate,
    onSend: (ArticleMessage) -> Unit,
    onClose: (ArticleMessage) -> Unit,
    editing: Boolean = false,
    uploads: Map<Long, ArticleUpload> = emptyMap(),
    onPickMedia: ((ArticleMediaKind) -> Unit)? = null,
    onRetryUpload: (Long) -> Unit = {},
    onRemoveUpload: (Long) -> Unit = {},
) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false)) {
        ArticleEditorContent(initial, gate, onSend, onClose, editing, uploads, onPickMedia, onRetryUpload, onRemoveUpload)
    }
}

@Composable
internal fun ArticleEditorContent(
    initial: List<ArticleRow>,
    gate: ArticleGate,
    onSend: (ArticleMessage) -> Unit,
    onClose: (ArticleMessage) -> Unit,
    editing: Boolean = false,
    uploads: Map<Long, ArticleUpload> = emptyMap(),
    onPickMedia: ((ArticleMediaKind) -> Unit)? = null,
    onRetryUpload: (Long) -> Unit = {},
    onRemoveUpload: (Long) -> Unit = {},
) {
    val start = initial.ifEmpty { listOf(ArticleRow(ArticleRowKind.Paragraph)) }
    var rows by remember { mutableStateOf(start) }
    var nextId by remember { mutableIntStateOf((start.maxOf { it.id }) + 1) }
    var selections by remember { mutableStateOf(mapOf<Int, TextRange>()) }
    var focused by remember { mutableStateOf<Int?>(start.last().id) }
    var focusTarget by remember { mutableStateOf<Int?>(start.last().id) }
    var linking by remember { mutableStateOf<EditorCell?>(null) }
    var cell by remember { mutableStateOf<EditorCell?>(null) }
    val requesters = remember { HashMap<Int, FocusRequester>() }
    val placed = remember { initial.mapNotNullTo(HashSet()) { it.upload?.id } }
    val article = remember(rows, uploads) { articleOfRows(rows, uploads) }
    val counts = remember(article) { article.counts() }
    val over = counts.over(gate.limits)
    val premiumNeeded = !gate.allowed && article.blocks.isNotEmpty() && article.asFormattedText() == null
    val uploading = uploadsPending(rows, uploads)
    val canSend = article.blocks.isNotEmpty() && over.isEmpty() && !premiumNeeded && !uploading

    fun row(id: Int) = rows.firstOrNull { it.id == id }
    fun replace(id: Int, transform: (ArticleRow) -> List<ArticleRow>) {
        rows = rows.flatMap { if (it.id == id) transform(it) else listOf(it) }
    }
    fun newRow(kind: ArticleRowKind, text: FormattedText = FormattedText("")): ArticleRow = ArticleRow(kind, text, id = nextId++)
    fun focus(id: Int, at: Int) {
        selections = selections + (id to TextRange(at))
        focusTarget = id
    }
    fun change(current: ArticleRow, value: TextFieldValue) {
        focused = current.id
        var text = current.text.edited(value.text)
        var kind = current.kind
        var selection = value.selection
        if (kind == ArticleRowKind.Paragraph) {
            articleCommandOf(text.text)?.let { command ->
                if (command.kind == ArticleRowKind.Divider) {
                    val next = newRow(ArticleRowKind.Paragraph)
                    replace(current.id) { listOf(it.copy(kind = ArticleRowKind.Divider, text = FormattedText("")), next) }
                    focus(next.id, 0)
                    return
                }
                if (command.kind == ArticleRowKind.Table) {
                    replace(current.id) { listOf(newTableRow(it.id, text.edited(text.text.drop(command.length)))) }
                    focusTarget = current.id
                    return
                }
                val media = command.media
                if (media != null) {
                    if (onPickMedia == null) return@let
                    val rest = text.edited(text.text.drop(command.length))
                    replace(current.id) { listOf(it.copy(text = rest)) }
                    focus(current.id, 0)
                    onPickMedia(media)
                    return
                }
                kind = command.kind
                text = text.edited(text.text.drop(command.length))
                selection = TextRange((selection.start - command.length).coerceAtLeast(0))
            }
        }
        val breakAt = if (kind in MULTILINE) -1 else text.text.indexOf('\n')
        if (breakAt >= 0) {
            val before = text.slice(0, breakAt)
            val after = text.slice(breakAt + 1, text.text.length)
            if (kind in LISTS && before.text.isEmpty() && after.text.isEmpty()) {
                replace(current.id) { listOf(it.copy(kind = ArticleRowKind.Paragraph, text = before)) }
                focus(current.id, 0)
                return
            }
            val next = newRow(if (kind in LISTS) kind else ArticleRowKind.Paragraph, after)
            replace(current.id) { listOf(it.copy(kind = kind, text = before), next) }
            focus(next.id, 0)
            return
        }
        replace(current.id) { listOf(it.copy(kind = kind, text = text)) }
        selections = selections + (current.id to selection)
    }
    fun setKind(id: Int, kind: ArticleRowKind) {
        replace(id) { current ->
            when {
                kind == ArticleRowKind.Divider -> listOf(current.copy(kind = kind, text = FormattedText("")), newRow(ArticleRowKind.Paragraph))
                kind == ArticleRowKind.Table -> listOf(newTableRow(current.id, current.text))
                kind in PLAIN -> listOf(current.copy(kind = kind, text = FormattedText(current.text.text)))
                else -> listOf(current.copy(kind = kind))
            }
        }
        if (kind == ArticleRowKind.Table) focusTarget = id
    }
    fun changeCell(current: ArticleRow, line: Int, column: Int, value: TextFieldValue) {
        focused = current.id
        val text = current.cells[line][column].edited(value.text.replace('\n', ' '))
        replace(current.id) { listOf(it.copy(cells = it.cells.updated(line, column, text))) }
        cell = EditorCell(current.id, line, column, value.selection)
    }
    fun add(after: Int?) {
        val next = newRow(ArticleRowKind.Paragraph)
        rows = if (after == null) rows + next else rows.flatMap { if (it.id == after) listOf(it, next) else listOf(it) }
        focus(next.id, 0)
    }
    fun remove(id: Int) {
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) return
        rows[index].upload?.let { onRemoveUpload(it.id) }
        rows = (rows - rows[index]).ifEmpty { listOf(newRow(ArticleRowKind.Paragraph)) }
        val next = rows[(index - 1).coerceAtLeast(0)]
        focus(next.id, next.text.text.length)
    }
    fun move(id: Int, by: Int) {
        val index = rows.indexOfFirst { it.id == id }
        val target = index + by
        if (index < 0 || target !in rows.indices) return
        rows = rows.toMutableList().apply { add(target, removeAt(index)) }
        focusTarget = id
    }
    fun close() = onClose(article)

    BackHandler { close() }
    LaunchedEffect(focusTarget, rows) {
        val id = focusTarget ?: return@LaunchedEffect
        requesters[id]?.let { requester ->
            runCatching { requester.requestFocus() }
            focusTarget = null
        }
    }
    LaunchedEffect(uploads) {
        val fresh = uploads.values.filter { it.id !in placed }.sortedBy { it.id }
        if (fresh.isEmpty()) return@LaunchedEffect
        val added = fresh.map { upload ->
            placed += upload.id
            ArticleRow(ArticleRowKind.Media, upload = upload, id = nextId++)
        }
        val after = focused?.takeIf { id -> rows.any { it.id == id } }
        rows = if (after == null) rows + added else rows.flatMap { if (it.id == after) listOf(it) + added else listOf(it) }
        focus(added.last().id, 0)
    }

    Surface(Modifier.fillMaxSize().testTag("articleEditor")) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            EditorTopBar(counts, gate, canSend, editing, onClose = ::close, onSend = { if (canSend) onSend(article) })
            EditorNotes(counts, gate, over, premiumNeeded, uploading)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                var number = 0
                rows.forEach { current ->
                    number = if (current.kind == ArticleRowKind.Numbered) number + 1 else 0
                    val requester = requesters.getOrPut(current.id) { FocusRequester() }
                    EditorRow(
                        current,
                        number = number,
                        selection = selections[current.id] ?: TextRange(current.text.text.length),
                        requester = requester,
                        onFocus = { focused = current.id },
                        onValue = { change(current, it) },
                        onLanguage = { language -> replace(current.id) { listOf(it.copy(language = language)) } },
                        onCheck = { replace(current.id) { listOf(it.copy(checked = !it.checked)) } },
                        media = {
                            EditorMedia(
                                current,
                                current.currentUpload(uploads),
                                onRetry = { current.upload?.let { onRetryUpload(it.id) } },
                                onRemove = { remove(current.id) },
                            )
                        },
                        table = { modifier ->
                            EditorTable(
                                current,
                                focused = cell,
                                requester = requester,
                                maxColumns = gate.limits.tableColumns,
                                onFocus = { line, column ->
                                    focused = current.id
                                    if (cell?.let { it.row == current.id && it.line == line && it.column == column } != true) {
                                        cell = EditorCell(current.id, line, column, TextRange(current.cells[line][column].text.length))
                                    }
                                },
                                onCell = { line, column, value -> changeCell(current, line, column, value) },
                                onChange = { changed -> replace(current.id) { listOf(changed) } },
                                modifier = modifier,
                            )
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { add(null) }, modifier = Modifier.testTag("articleAddEnd"), shapes = ButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_add), null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.chat_article_add))
                }
            }
            val active = focused?.let(::row)
            val selection = active?.let { selections[it.id] }
            val activeCell = cell?.takeIf { active?.kind == ArticleRowKind.Table && it.row == active.id && it.line in active.cells.indices && it.column < active.columns }
            if (active != null && activeCell != null && !activeCell.selection.collapsed) {
                FormattingBar(EDITOR_FORMATS) { action ->
                    if (action == FormatAction.Link) {
                        linking = activeCell
                    } else {
                        replace(active.id) { listOf(it.copy(cells = it.cells.updated(activeCell.line, activeCell.column, formatted(it.cells[activeCell.line][activeCell.column], action, activeCell.selection)))) }
                    }
                }
            } else if (active != null && active.kind !in PLAIN && selection != null && !selection.collapsed) {
                FormattingBar(EDITOR_FORMATS) { action ->
                    if (action == FormatAction.Link) {
                        linking = EditorCell(active.id, -1, -1, selection)
                    } else {
                        replace(active.id) { listOf(it.copy(text = formatted(it.text, action, selection))) }
                    }
                }
            } else if (active != null) {
                RowBar(
                    active.kind,
                    onKind = { setKind(active.id, it) },
                    onAdd = { add(active.id) },
                    onUp = { move(active.id, -1) },
                    onDown = { move(active.id, 1) },
                    onRemove = { remove(active.id) },
                    onPickMedia = onPickMedia,
                )
            }
        }
    }
    linking?.let { target ->
        val current = row(target.row)
        val text = current?.let { if (target.line < 0) it.text else it.cells.getOrNull(target.line)?.getOrNull(target.column) }
        if (current == null || text == null) {
            linking = null
        } else {
            LinkDialog(
                initial = linkAt(text, target.selection),
                onConfirm = { url ->
                    linking = null
                    val linked = text.toggled(EntityType.TextUrl(url), target.selection.min, target.selection.max)
                    replace(target.row) { listOf(if (target.line < 0) it.copy(text = linked) else it.copy(cells = it.cells.updated(target.line, target.column, linked))) }
                },
                onDismiss = { linking = null },
            )
        }
    }
}

private fun FormattedText.slice(start: Int, end: Int): FormattedText {
    val inside = entities.mapNotNull { entity ->
        val from = maxOf(entity.offset, start)
        val to = minOf(entity.end, end)
        if (to <= from) null else TextEntity(from - start, to - from, entity.type)
    }
    return FormattedText(text.substring(start, end), inside)
}

@Composable
private fun EditorTopBar(counts: ArticleCounts, gate: ArticleGate, canSend: Boolean, editing: Boolean, onClose: () -> Unit, onSend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = TopBarTokens.EdgeInset, end = TopBarTokens.EdgeInset, top = TopBarTokens.EdgeInset, bottom = TopBarTokens.ContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopBarPill(R.drawable.ic_close, stringResource(R.string.chat_article_close), onClose, Modifier.testTag("articleClose"))
        Text(stringResource(R.string.chat_article_editor), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 16.dp))
        val long = counts.length > gate.limits.length
        Text(
            stringResource(R.string.chat_article_counter, counts.length, gate.limits.length),
            style = MaterialTheme.typography.labelMedium,
            color = if (long) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp).testTag("articleCounter"),
        )
        val send = TopBarColors(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, MaterialTheme.colorScheme.surface)
        TopBarPill(
            if (editing) R.drawable.ic_check else R.drawable.ic_send,
            stringResource(if (editing) R.string.chat_article_save else R.string.chat_send),
            onSend,
            Modifier.testTag("articleSend"),
            enabled = canSend,
            colors = send,
        )
    }
}

@Composable
private fun EditorNotes(counts: ArticleCounts, gate: ArticleGate, over: Set<ArticleLimit>, premiumNeeded: Boolean, uploading: Boolean) {
    val limits = gate.limits
    over.forEach { limit ->
        val text = when (limit) {
            ArticleLimit.Length -> stringResource(R.string.chat_article_over_length, counts.length, limits.length)
            ArticleLimit.Blocks -> stringResource(R.string.chat_article_over_blocks, counts.blocks, limits.blocks)
            ArticleLimit.Depth -> stringResource(R.string.chat_article_over_depth, counts.depth, limits.depth)
            ArticleLimit.Media -> stringResource(R.string.chat_article_over_media, counts.media, limits.media)
            ArticleLimit.TableColumns -> stringResource(R.string.chat_article_over_columns, counts.tableColumns, limits.tableColumns)
        }
        Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp).testTag("articleOver_${limit.name}"))
    }
    if (uploading) {
        Text(
            stringResource(R.string.chat_article_uploading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp).testTag("articleUploading"),
        )
    }
    if (premiumNeeded) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("articlePremium")) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_star), null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.chat_article_premium), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EditorRow(
    row: ArticleRow,
    number: Int,
    selection: TextRange,
    requester: FocusRequester,
    onFocus: () -> Unit,
    onValue: (TextFieldValue) -> Unit,
    onLanguage: (String) -> Unit,
    onCheck: () -> Unit,
    table: @Composable (Modifier) -> Unit,
    media: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val body = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
    val style = when (row.kind) {
        ArticleRowKind.Heading1 -> MaterialTheme.typography.headlineSmall.copy(color = colors.onSurface, fontWeight = FontWeight.Bold)
        ArticleRowKind.Heading2 -> MaterialTheme.typography.titleLarge.copy(color = colors.onSurface, fontWeight = FontWeight.Bold)
        ArticleRowKind.Heading3 -> MaterialTheme.typography.titleMedium.copy(color = colors.onSurface, fontWeight = FontWeight.Bold)
        ArticleRowKind.Code, ArticleRowKind.Math -> body.copy(fontFamily = FontFamily.Monospace)
        else -> body
    }
    val focusable = Modifier.focusRequester(requester).onFocusChanged { if (it.isFocused) onFocus() }
    Row(Modifier.fillMaxWidth().testTag("articleRow_${row.id}"), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(32.dp).padding(top = 2.dp), contentAlignment = Alignment.TopCenter) {
            RowMarker(row, number, onCheck)
        }
        when (row.kind) {
            ArticleRowKind.Table -> table(Modifier.weight(1f))
            ArticleRowKind.Divider -> HorizontalDivider(
                Modifier.weight(1f).padding(vertical = 14.dp).focusRequester(requester).onFocusChanged { if (it.isFocused) onFocus() }
                    .clickable { onFocus() }.testTag("articleDivider_${row.id}"),
            )
            ArticleRowKind.Block -> {
                val label = row.block?.let { articlePreview(ArticleMessage(listOf(it))) }.orEmpty()
                Surface(
                    color = colors.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f).focusRequester(requester).onFocusChanged { if (it.isFocused) onFocus() }.clickable { onFocus() },
                ) {
                    Text(label, style = body, modifier = Modifier.padding(12.dp))
                }
            }
            else -> Column(Modifier.weight(1f)) {
                if (row.kind == ArticleRowKind.Media) media()
                if (row.kind == ArticleRowKind.Code) {
                    val hint = stringResource(R.string.chat_article_language)
                    BasicTextField(
                        value = row.language,
                        onValueChange = onLanguage,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelMedium.copy(color = colors.primary),
                        cursorBrush = SolidColor(colors.primary),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint }.testTag("articleLanguage_${row.id}"),
                        decorationBox = { field ->
                            if (row.language.isEmpty()) Text(hint, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                            field()
                        },
                    )
                }
                val quote = row.kind == ArticleRowKind.Quote
                val background = when (row.kind) {
                    ArticleRowKind.Code, ArticleRowKind.Math -> Modifier.background(colors.surfaceContainerHigh, MaterialTheme.shapes.small).padding(6.dp)
                    ArticleRowKind.Quote -> Modifier.background(colors.primary.copy(alpha = 0.08f), MaterialTheme.shapes.small).padding(6.dp)
                    else -> Modifier.padding(vertical = 4.dp)
                }
                val transformation = if (row.kind in PLAIN) null else EntitiesTransformation(row.text.entities, composerColors())
                val hint = stringResource(
                    when (row.kind) {
                        ArticleRowKind.Code -> R.string.chat_article_code_hint
                        ArticleRowKind.Math -> R.string.chat_article_math_hint
                        ArticleRowKind.Media -> R.string.chat_article_caption
                        else -> R.string.chat_article_hint
                    },
                )
                val size = row.text.text.length
                BasicTextField(
                    value = TextFieldValue(row.text.text, TextRange(selection.start.coerceIn(0, size), selection.end.coerceIn(0, size))),
                    onValueChange = onValue,
                    textStyle = if (quote) style.copy(fontStyle = FontStyle.Italic) else style,
                    cursorBrush = SolidColor(colors.primary),
                    visualTransformation = transformation ?: VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth().then(focusable).testTag("articleText_${row.id}"),
                    decorationBox = { field ->
                        Box(background) {
                            if (row.text.text.isEmpty()) Text(hint, style = style.copy(color = colors.onSurfaceVariant))
                            field()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RowMarker(row: ArticleRow, number: Int, onCheck: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val marker: TextStyle = MaterialTheme.typography.labelLarge.copy(color = colors.primary)
    when (row.kind) {
        ArticleRowKind.Heading1 -> Text("H1", style = marker)
        ArticleRowKind.Heading2 -> Text("H2", style = marker)
        ArticleRowKind.Heading3 -> Text("H3", style = marker)
        ArticleRowKind.Bullet -> Text("\u2022", style = MaterialTheme.typography.bodyLarge)
        ArticleRowKind.Numbered -> Text("$number.", style = MaterialTheme.typography.bodyLarge)
        ArticleRowKind.Check -> Icon(
            painterResource(if (row.checked) R.drawable.ic_check_box else R.drawable.ic_check_box_outline_blank),
            stringResource(if (row.checked) R.string.chat_article_checked else R.string.chat_article_unchecked),
            tint = colors.primary,
            modifier = Modifier.clickable(role = Role.Checkbox, onClick = onCheck).minimumInteractiveComponentSize().size(24.dp).testTag("articleCheck_${row.id}"),
        )
        ArticleRowKind.Quote -> Box(Modifier.width(3.dp).height(24.dp).background(colors.primary, MaterialTheme.shapes.extraSmall))
        ArticleRowKind.Code -> Icon(painterResource(R.drawable.ic_code), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        ArticleRowKind.Math -> Text("\u2211", style = marker)
        ArticleRowKind.Table -> Icon(painterResource(R.drawable.ic_table), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        ArticleRowKind.Paragraph, ArticleRowKind.Divider, ArticleRowKind.Media, ArticleRowKind.Block -> Unit
    }
}

@Composable
private fun RowBar(
    kind: ArticleRowKind,
    onKind: (ArticleRowKind) -> Unit,
    onAdd: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    onPickMedia: ((ArticleMediaKind) -> Unit)?,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().testTag("articleRowBar")) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            var menu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { menu = true }, enabled = kind !in FIXED, modifier = Modifier.testTag("articleKind"), shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(kindLabel(kind)))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    ROW_KINDS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(kindLabel(option))) },
                            onClick = {
                                menu = false
                                onKind(option)
                            },
                            modifier = Modifier.testTag("articleKind_${option.name}"),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAdd, modifier = Modifier.testTag("articleAdd"), shapes = IconButtonDefaults.shapes()) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.chat_article_add)) }
            onPickMedia?.let { pick ->
                var picking by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { picking = true }, modifier = Modifier.testTag("articleMedia"), shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_add_a_photo), stringResource(R.string.chat_article_media_add))
                    }
                    DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                        ArticleMediaKind.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(mediaLabel(option))) },
                                leadingIcon = { Icon(painterResource(mediaIcon(option)), null) },
                                onClick = {
                                    picking = false
                                    pick(option)
                                },
                                modifier = Modifier.testTag("articleMedia_${option.name}"),
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onUp, modifier = Modifier.testTag("articleUp"), shapes = IconButtonDefaults.shapes()) { Icon(painterResource(R.drawable.ic_keyboard_arrow_up), stringResource(R.string.chat_article_up)) }
            IconButton(onClick = onDown, modifier = Modifier.testTag("articleDown"), shapes = IconButtonDefaults.shapes()) { Icon(painterResource(R.drawable.ic_keyboard_arrow_down), stringResource(R.string.chat_article_down)) }
            IconButton(onClick = onRemove, modifier = Modifier.testTag("articleRemove"), shapes = IconButtonDefaults.shapes()) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.chat_article_remove)) }
        }
    }
}

private fun kindLabel(kind: ArticleRowKind): Int = when (kind) {
    ArticleRowKind.Paragraph -> R.string.chat_article_kind_text
    ArticleRowKind.Heading1 -> R.string.chat_article_kind_heading1
    ArticleRowKind.Heading2 -> R.string.chat_article_kind_heading2
    ArticleRowKind.Heading3 -> R.string.chat_article_kind_heading3
    ArticleRowKind.Bullet -> R.string.chat_article_kind_list
    ArticleRowKind.Numbered -> R.string.chat_article_kind_numbered
    ArticleRowKind.Check -> R.string.chat_article_kind_checklist
    ArticleRowKind.Quote -> R.string.chat_article_kind_quote
    ArticleRowKind.Code -> R.string.chat_article_kind_code
    ArticleRowKind.Math -> R.string.chat_article_kind_math
    ArticleRowKind.Table -> R.string.chat_article_kind_table
    ArticleRowKind.Divider -> R.string.chat_article_kind_divider
    ArticleRowKind.Media -> R.string.chat_article_kind_media
    ArticleRowKind.Block -> R.string.chat_article_kind_block
}
