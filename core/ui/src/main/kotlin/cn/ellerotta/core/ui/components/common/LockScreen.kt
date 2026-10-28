package cn.ellerotta.core.ui.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R

@Composable
fun LockScreen(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize().testTag("lockScreen"), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MorphingShape(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, size = 112.dp) {
                Icon(painterResource(R.drawable.ic_lock), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onUnlock, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 56.dp).testTag("unlock")) {
                Text(stringResource(R.string.lock_unlock))
            }
        }
    }
}
