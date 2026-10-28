package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ArticleAlign
import cn.ellerotta.core.model.ArticleBlock
import cn.ellerotta.core.model.ArticleCell
import cn.ellerotta.core.model.ArticleVerticalAlign

internal class PlacedCell(val row: Int, val column: Int, val rowspan: Int, val colspan: Int, val cell: ArticleCell)

internal fun placeCells(rows: List<List<ArticleCell>>): List<PlacedCell> {
    val taken = HashSet<Long>()
    fun key(row: Int, column: Int) = row.toLong() shl 32 or column.toLong()
    val placed = mutableListOf<PlacedCell>()
    rows.forEachIndexed { row, cells ->
        var column = 0
        cells.forEach { cell ->
            while (key(row, column) in taken) column++
            val rowspan = cell.rowspan.coerceIn(1, rows.size - row)
            val colspan = cell.colspan.coerceAtLeast(1)
            for (r in row until row + rowspan) for (c in column until column + colspan) taken += key(r, c)
            placed += PlacedCell(row, column, rowspan, colspan, cell)
            column += colspan
        }
    }
    return placed
}

@Composable
internal fun ArticleTable(block: ArticleBlock.Table, scope: ArticleScope) {
    val placed = remember(block) { placeCells(block.rows) }
    val color = LocalContentColor.current
    val padding = if (block.compact) COMPACT_PADDING else CELL_PADDING
    Column(Modifier.fillMaxWidth().testTag("table")) {
        ArticleParagraph(
            block.title,
            scope,
            MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            tag = "tableTitle",
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val viewport = constraints.maxWidth
            val direction = if (scope.rtl) LayoutDirection.Rtl else LocalLayoutDirection.current
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    Layout(
                        content = {
                            placed.forEach { cell ->
                                val tint = when {
                                    cell.cell.header -> color.copy(alpha = HEADER_TINT)
                                    block.striped && cell.row % 2 == 1 -> color.copy(alpha = STRIPE_TINT)
                                    else -> Color.Transparent
                                }
                                val frame = Modifier.background(tint)
                                Box(if (block.bordered) frame.border(BORDER, color.copy(alpha = BORDER_ALPHA)) else frame)
                            }
                            placed.forEach { cell ->
                                Box(Modifier.testTag("cell_${cell.row}_${cell.column}").padding(padding), propagateMinConstraints = true) {
                                    cell.cell.text?.let { text ->
                                        val style = MaterialTheme.typography.bodyMedium
                                        ArticleParagraph(
                                            text,
                                            scope,
                                            if (cell.cell.header) style.copy(fontWeight = FontWeight.SemiBold) else style,
                                            textAlign = when (cell.cell.align) {
                                                ArticleAlign.Start -> TextAlign.Start
                                                ArticleAlign.Center -> TextAlign.Center
                                                ArticleAlign.End -> TextAlign.End
                                            },
                                            tag = "cellText",
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("tableGrid"),
                    ) { measurables, constraints ->
                        val frames = measurables.subList(0, placed.size)
                        val contents = measurables.subList(placed.size, measurables.size)
                        val columns = placed.maxOfOrNull { it.column + it.colspan } ?: 0
                        val maxCell = MAX_CELL.roundToPx()
                        val widths = IntArray(columns) { MIN_CELL_WIDTH.roundToPx() }
                        val natural = contents.mapIndexed { index, content -> minOf(content.maxIntrinsicWidth(Constraints.Infinity), maxCell * placed[index].colspan) }
                        placed.forEachIndexed { index, cell -> if (cell.colspan == 1) widths[cell.column] = maxOf(widths[cell.column], natural[index]) }
                        placed.forEachIndexed { index, cell ->
                            if (cell.colspan == 1) return@forEachIndexed
                            val spanned = cell.column until cell.column + cell.colspan
                            val missing = natural[index] - spanned.sumOf { widths[it] }
                            if (missing > 0) spanned.forEachIndexed { i, c -> widths[c] += missing / cell.colspan + if (i < missing % cell.colspan) 1 else 0 }
                        }
                        val total = widths.sum()
                        if (columns > 0 && total < viewport) {
                            val grow = viewport - total
                            var given = 0
                            for (c in 0 until columns) {
                                val share = if (c == columns - 1) grow - given else grow * widths[c] / total
                                widths[c] += share
                                given += share
                            }
                        }
                        val lefts = IntArray(columns + 1).also { for (c in 0 until columns) it[c + 1] = it[c] + widths[c] }
                        val placeables = contents.mapIndexed { index, content ->
                            val cell = placed[index]
                            val width = lefts[cell.column + cell.colspan] - lefts[cell.column]
                            content.measure(Constraints(minWidth = width, maxWidth = width))
                        }
                        val rows = block.rows.size
                        val heights = IntArray(rows) { (if (block.compact) COMPACT_HEIGHT else CELL_HEIGHT).roundToPx() }
                        placed.forEachIndexed { index, cell -> if (cell.rowspan == 1) heights[cell.row] = maxOf(heights[cell.row], placeables[index].height) }
                        placed.forEachIndexed { index, cell ->
                            if (cell.rowspan == 1) return@forEachIndexed
                            val last = cell.row + cell.rowspan - 1
                            val missing = placeables[index].height - (cell.row..last).sumOf { heights[it] }
                            if (missing > 0) heights[last] += missing
                        }
                        val tops = IntArray(rows + 1).also { for (r in 0 until rows) it[r + 1] = it[r] + heights[r] }
                        val width = lefts[columns]
                        val height = tops[rows]
                        val frameables = frames.mapIndexed { index, frame ->
                            val cell = placed[index]
                            frame.measure(Constraints.fixed(lefts[cell.column + cell.colspan] - lefts[cell.column], tops[cell.row + cell.rowspan] - tops[cell.row]))
                        }
                        layout(width, height) {
                            placed.forEachIndexed { index, cell ->
                                val frame = frameables[index]
                                val content = placeables[index]
                                val left = lefts[cell.column]
                                val x = if (layoutDirection == LayoutDirection.Rtl) width - left - frame.width else left
                                val top = tops[cell.row]
                                frame.place(x, top)
                                val offset = when (cell.cell.valign) {
                                    ArticleVerticalAlign.Top -> 0
                                    ArticleVerticalAlign.Middle -> (frame.height - content.height) / 2
                                    ArticleVerticalAlign.Bottom -> frame.height - content.height
                                }
                                content.place(x, top + offset)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val CELL_PADDING = 8.dp
private val COMPACT_PADDING = 5.dp
private val MIN_CELL_WIDTH = 32.dp
private val MAX_CELL = 240.dp
private val CELL_HEIGHT = 36.dp
private val COMPACT_HEIGHT = 18.dp
private val BORDER = 0.5.dp
private const val BORDER_ALPHA = 0.3f
private const val HEADER_TINT = 0.08f
private const val STRIPE_TINT = 0.04f
