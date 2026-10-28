package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R

class ChatTranslateActions(
    val onTranslate: (id: Int) -> Unit,
    val onShowOriginal: (id: Int) -> Unit,
)

@Composable
internal fun TranslatedLine(id: Int, language: String, color: Color, onShowOriginal: (() -> Unit)?) {
    Row(
        Modifier.padding(top = 2.dp).testTag("translated_$id"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(R.drawable.ic_translate), null, Modifier.size(14.dp), tint = color)
        Text(stringResource(R.string.chat_translated, language.uppercase()), style = MaterialTheme.typography.labelMedium, color = color)
        if (onShowOriginal != null) {
            TextButton(
                onClick = onShowOriginal,
                colors = ButtonDefaults.textButtonColors(contentColor = color),
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier.heightIn(min = 28.dp).testTag("showOriginal_$id"),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.chat_show_original), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
