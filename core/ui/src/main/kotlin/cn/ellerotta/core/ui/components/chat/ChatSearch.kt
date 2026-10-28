package cn.ellerotta.core.ui.components.chat

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatSearchState
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.formatChatDate

@Stable
class ChatSearchActions(
    val onQuery: (String) -> Unit,
    val onMore: () -> Unit,
    val onClose: () -> Unit,
)

@Composable
internal fun ChatSearchBar(state: ChatSearchState, index: Int?, onOlder: () -> Unit, onNewer: () -> Unit, onList: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val label = when {
                state.query.isBlank() -> ""
                state.loading && state.results.isEmpty() -> stringResource(R.string.chat_search_searching)
                state.results.isEmpty() -> stringResource(R.string.chat_search_nothing)
                index == null -> pluralStringResource(R.plurals.chat_search_found, state.total, state.total)
                else -> stringResource(R.string.chat_search_counter, index + 1, state.total)
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).clickable(enabled = state.results.isNotEmpty(), onClick = onList).padding(12.dp).testTag("searchCounter"),
            )
            val older = state.results.isNotEmpty() && (index == null || index + 1 < state.results.size)
            IconButton(onClick = onOlder, enabled = older, modifier = Modifier.testTag("searchOlder"), shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_keyboard_arrow_up), stringResource(R.string.chat_search_older))
            }
            IconButton(onClick = onNewer, enabled = index != null && index > 0, modifier = Modifier.testTag("searchNewer"), shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_keyboard_arrow_down), stringResource(R.string.chat_search_newer))
            }
        }
    }
}

@Composable
internal fun ChatSearchResults(state: ChatSearchState, title: String, onOpen: (Int) -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(list, state.results.size) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last -> if (last != null && last >= state.results.size - MORE_AHEAD) onMore() }
    }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val now = remember(state.results) { System.currentTimeMillis() }
    val you = stringResource(R.string.chats_you)
    val highlight = MaterialTheme.colorScheme.primaryContainer
    LazyColumn(modifier.fillMaxSize().testTag("searchResults"), state = list) {
        itemsIndexed(state.results, key = { _, message -> message.id }) { index, message ->
            Column(Modifier.fillMaxWidth().clickable { onOpen(index) }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("searchResult_${message.id}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        message.senderName ?: if (message.out) you else title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatChatDate(message.date, now, locale, DateFormat.is24HourFormat(context)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    highlighted(resultText(message), state.query, highlight),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("searchText_${message.id}"),
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun resultText(message: MessageItem): String = messagePreview(message).replace('\n', ' ')

internal fun highlighted(text: String, query: String, color: Color): AnnotatedString = buildAnnotatedString {
    val needle = query.trim()
    if (needle.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    var at = 0
    while (at < text.length) {
        val found = text.indexOf(needle, at, ignoreCase = true)
        if (found < 0) break
        append(text.substring(at, found))
        withStyle(SpanStyle(background = color, fontWeight = FontWeight.SemiBold)) { append(text.substring(found, found + needle.length)) }
        at = found + needle.length
    }
    append(text.substring(at))
}

private const val MORE_AHEAD = 5
