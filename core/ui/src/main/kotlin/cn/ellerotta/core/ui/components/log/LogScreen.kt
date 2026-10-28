package cn.ellerotta.core.ui.components.log

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.ellerotta.core.model.LogLine
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val logTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

@Composable
fun LogScreen(lines: List<LogLine>, onBack: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }
    Page(stringResource(R.string.log_title), rememberTopBarState(listState, largeTitle = false), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().testTag("logList"),
            state = listState,
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            items(lines) { line ->
                Text(
                    text = logTimeFormat.format(Date(line.time)) + "  " + line.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}
