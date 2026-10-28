package cn.ellerotta.core.ui.components.chats

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.EmptyState

@Composable
internal fun EmptyChats(text: Int, onFindPeople: (() -> Unit)? = null) {
    val action: (@Composable () -> Unit)? = onFindPeople?.let { find ->
        {
            Button(onClick = find, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp).testTag("findPeople")) {
                Text(stringResource(R.string.chats_find_people))
            }
        }
    }
    EmptyState(R.drawable.ic_folder, stringResource(text), "emptyChats", Modifier.padding(top = 80.dp), action)
}

@Composable
internal fun ChatPlaceholders() {
    val alpha by rememberInfiniteTransition(label = "placeholder").animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    val color = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(Modifier.fillMaxSize().alpha(alpha).testTag("placeholders")) {
        repeat(9) { i ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(color, CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width((90 + i * 13 % 60).dp).height(12.dp).background(color, CircleShape))
                    Box(Modifier.width((160 + i * 29 % 80).dp).height(10.dp).background(color, CircleShape))
                }
            }
        }
    }
}
