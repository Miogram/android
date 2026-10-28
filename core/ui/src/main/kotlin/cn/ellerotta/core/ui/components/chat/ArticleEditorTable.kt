package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ArticleBlock
import cn.ellerotta.core.model.ArticleRow
import cn.ellerotta.core.model.ArticleText
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.ui.R

internal data class EditorCell(val row: Int, val line: Int, val column: Int, val selection: TextRange = TextRange.Zero)

internal val ArticleRow.table: ArticleBlock.Table get() = block as? ArticleBlock.Table ?: ArticleBlock.Table(ArticleText.Empty, emptyList(), bordered = true)

internal val ArticleRow.columns: Int get() = cells.firstOrNull()?.size ?: 0

internal fun List<List<FormattedText>>.updated(line: Int, column: Int, text: FormattedText): List<List<FormattedText>> =
    mapIndexed { index, cells -> if (index == line) cells.mapIndexed { at, cell -> if (at == column) text else cell } else cells }

@Composable
internal fun EditorTable(
    row: ArticleRow,
    focused: EditorCell?,
    requester: FocusRequester,
    maxColumns: Int,
    onFocus: (line: Int, column: Int) -> Unit,
    onCell: (line: Int, column: Int, TextFieldValue) -> Unit,
    onChange: (ArticleRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val table = row.table
    val here = focused?.takeIf { it.row == row.id }
    val hint = stringResource(R.string.chat_article_cell)
    Column(modifier) {
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Column(if (table.bordered) Modifier.border(1.dp, colors.outlineVariant) else Modifier) {
                row.cells.forEachIndexed { line, cells ->
                    val head = row.header && line == 0
                    val shade = when {
                        head -> Modifier.background(colors.surfaceContainerHigh)
                        table.striped && line % 2 == 0 -> Modifier.background(colors.surfaceContainerLow)
                        else -> Modifier
                    }
                    Row(shade) {
                        cells.forEachIndexed { column, cell ->
                            val selection = here?.takeIf { it.line == line && it.column == column }?.selection ?: TextRange(cell.text.length)
                            val size = cell.text.length
                            val style = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface, fontWeight = if (head) FontWeight.Bold else null)
                            val first = if (line == 0 && column == 0) Modifier.focusRequester(requester) else Modifier
                            BasicTextField(
                                value = TextFieldValue(cell.text, TextRange(selection.start.coerceIn(0, size), selection.end.coerceIn(0, size))),
                                onValueChange = { onCell(line, column, it) },
                                textStyle = style,
                                cursorBrush = SolidColor(colors.primary),
                                visualTransformation = EntitiesTransformation(cell.entities, composerColors()),
                                modifier = Modifier.width(CELL_WIDTH).then(if (table.bordered) Modifier.border(0.5.dp, colors.outlineVariant) else Modifier)
                                    .then(first).onFocusChanged { if (it.isFocused) onFocus(line, column) }
                                    .semantics { contentDescription = hint }.testTag("articleCell_${row.id}_${line}_$column"),
                                decorationBox = { field ->
                                    Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                        if (cell.text.isEmpty()) Text(hint, style = style.copy(color = colors.onSurfaceVariant))
                                        field()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        val width = row.columns
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onChange(row.copy(cells = row.cells + listOf(List(width) { FormattedText("") }))) }, modifier = Modifier.testTag("articleTableAddRow_${row.id}"), shapes = ButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_add), null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.chat_article_table_add_row))
            }
            TextButton(
                onClick = { onChange(row.copy(cells = row.cells.map { it + FormattedText("") })) },
                enabled = width < maxColumns,
                modifier = Modifier.testTag("articleTableAddColumn_${row.id}"),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(painterResource(R.drawable.ic_add), null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.chat_article_table_add_column))
            }
            val line = here?.line?.takeIf { it < row.cells.size } ?: row.cells.lastIndex
            TextButton(
                onClick = { onChange(row.copy(cells = row.cells.filterIndexed { index, _ -> index != line })) },
                enabled = row.cells.size > 1,
                modifier = Modifier.testTag("articleTableRemoveRow_${row.id}"),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.chat_article_table_remove_row))
            }
            val column = here?.column?.takeIf { it < width } ?: (width - 1)
            TextButton(
                onClick = { onChange(row.copy(cells = row.cells.map { cells -> cells.filterIndexed { index, _ -> index != column } })) },
                enabled = width > 1,
                modifier = Modifier.testTag("articleTableRemoveColumn_${row.id}"),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.chat_article_table_remove_column))
            }
            FilterChip(
                selected = row.header,
                onClick = { onChange(row.copy(header = !row.header)) },
                label = { Text(stringResource(R.string.chat_article_table_header), style = if (row.header) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge) },
                modifier = Modifier.testTag("articleTableHeader_${row.id}"),
            )
            FilterChip(
                selected = table.bordered,
                onClick = { onChange(row.copy(block = table.copy(bordered = !table.bordered))) },
                label = { Text(stringResource(R.string.chat_article_table_bordered), style = if (table.bordered) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge) },
                modifier = Modifier.testTag("articleTableBordered_${row.id}"),
            )
            FilterChip(
                selected = table.striped,
                onClick = { onChange(row.copy(block = table.copy(striped = !table.striped))) },
                label = { Text(stringResource(R.string.chat_article_table_striped), style = if (table.striped) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge) },
                modifier = Modifier.testTag("articleTableStriped_${row.id}"),
            )
        }
    }
}

private val CELL_WIDTH = 120.dp
