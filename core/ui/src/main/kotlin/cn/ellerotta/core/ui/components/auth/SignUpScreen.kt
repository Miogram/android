package cn.ellerotta.core.ui.components.auth

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar

@Composable
fun SignUpScreen(busy: Boolean, onBack: () -> Unit, onSignUp: (String, String) -> Unit) {
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    val name = "$first $last".trim()
    val submit = { if (first.isNotBlank() && !busy) onSignUp(first.trim(), last.trim()) }
    AuthScreenLayout(onBack = onBack) {
        Crossfade(name.isEmpty(), animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "avatar") { empty ->
            if (empty) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_add_a_photo), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            } else {
                Avatar(name, null, size = 96, seed = name.lowercase().hashCode().toLong())
            }
        }
        Spacer(Modifier.height(24.dp))
        AuthHeader(title = stringResource(R.string.sign_up_title), subtitle = stringResource(R.string.sign_up_intro))
        OutlinedTextField(
            value = first,
            onValueChange = { first = it.take(64) },
            label = { Text(stringResource(R.string.sign_up_first_name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("firstName"),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = last,
            onValueChange = { last = it.take(64) },
            label = { Text(stringResource(R.string.sign_up_last_name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().testTag("lastName"),
        )
        Spacer(Modifier.height(32.dp))
        ContinueButton(
            enabled = first.isNotBlank(),
            busy = busy,
            label = stringResource(R.string.sign_up_create),
            onClick = submit,
            modifier = Modifier.testTag("signUp"),
        )
    }
}
