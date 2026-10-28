package cn.ellerotta.core.ui.components.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.shake
import kotlinx.coroutines.delay

@Composable
fun PasswordScreen(hint: String?, busy: Boolean, wrongCount: Int, onBack: () -> Unit, onCheckPassword: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val password: TextFieldState = rememberTextFieldState()
    var visible by rememberSaveable { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val submit = {
        if (password.text.isNotBlank() && !busy) {
            keyboard?.hide()
            onCheckPassword(password.text.toString())
        }
    }

    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(password) {
        snapshotFlow { password.text.toString() }.collect { if (it.isNotEmpty()) wrong = false }
    }
    LaunchedEffect(wrongCount) {
        if (wrongCount == 0) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
        password.clearText()
        wrong = true
    }

    AuthScreenLayout(onBack = onBack) {
        AuthHeader(title = stringResource(R.string.password_title), subtitle = stringResource(R.string.password_intro))

        InlineError(if (wrong) stringResource(R.string.error_password_invalid) else null, textModifier = Modifier.testTag("wrongPassword"))
        if (wrong) Spacer(Modifier.height(12.dp))

        OutlinedSecureTextField(
            state = password,
            label = { Text(stringResource(R.string.password_label)) },
            supportingText = if (!hint.isNullOrEmpty()) {
                { Text(stringResource(R.string.password_hint, hint), modifier = Modifier.testTag("passwordHint")) }
            } else {
                null
            },
            isError = wrong,
            enabled = !busy,
            textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
            leadingIcon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                IconButton(onClick = { visible = !visible }, modifier = Modifier.testTag("togglePassword"), shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                        stringResource(if (visible) R.string.password_hide else R.string.password_show),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            onKeyboardAction = { submit() },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .shake(wrongCount)
                .focusRequester(focusRequester)
                .testTag("password"),
        )

        Spacer(Modifier.height(32.dp))

        ContinueButton(
            enabled = password.text.isNotBlank(),
            busy = busy,
            label = stringResource(R.string.password_unlock),
            onClick = submit,
            modifier = Modifier.testTag("checkPassword"),
        )
    }
}
