package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R

@Composable
internal fun EmojiSearchField(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.chat_emoji_search),
    tag: String = "emojiSearch",
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    onFocus: (Boolean) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = color, contentColor = colors.onSurface, modifier = modifier.fillMaxWidth().heightIn(min = SEARCH_HEIGHT)) {
        Row(Modifier.padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_search), null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
            Box(Modifier.weight(1f).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
                BasicTextField(
                    query,
                    onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) }.testTag(tag),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.common_clear_search), Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                }
            }
        }
    }
}

private val SEARCH_HEIGHT = 48.dp
