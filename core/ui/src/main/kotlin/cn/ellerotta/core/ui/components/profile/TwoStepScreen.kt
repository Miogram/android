package cn.ellerotta.core.ui.components.profile

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.TwoStepGoal
import cn.ellerotta.core.model.TwoStepSaved
import cn.ellerotta.core.model.TwoStepState
import cn.ellerotta.core.model.TwoStepStatus
import cn.ellerotta.core.model.TwoStepStep
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.auth.AuthHeader
import cn.ellerotta.core.ui.components.auth.AuthScreenLayout
import cn.ellerotta.core.ui.components.auth.ContinueButton
import cn.ellerotta.core.ui.components.auth.InlineError
import cn.ellerotta.core.ui.components.common.ConfirmDialog
import cn.ellerotta.core.ui.components.common.MorphingShape
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.components.common.shake
import cn.ellerotta.core.ui.theme.crossfade
import kotlinx.coroutines.delay

class TwoStepActions(
    val onBack: () -> Unit = {},
    val onStart: (TwoStepGoal) -> Unit = {},
    val onCurrent: (String) -> Unit = {},
    val onNewPassword: (String) -> Unit = {},
    val onHint: (String) -> Unit = {},
    val onEmail: (String?) -> Unit = {},
    val onCode: (String) -> Unit = {},
    val onResend: () -> Unit = {},
    val onEnterPendingCode: () -> Unit = {},
    val onCancelEmail: () -> Unit = {},
    val onForgot: () -> Unit = {},
    val onRecover: (String) -> Unit = {},
)

@Composable
fun TwoStepScreen(state: TwoStepState, busy: Boolean, actions: TwoStepActions) {
    BackHandler(onBack = actions.onBack)
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state.step,
        contentKey = { it::class },
        transitionSpec = { motion.crossfade() },
        label = "twoStepStep",
    ) { step ->
        when (step) {
            TwoStepStep.Overview -> TwoStepOverview(state, busy, actions)
            is TwoStepStep.Current -> CurrentStep(step.goal, state, busy, actions)
            TwoStepStep.NewPassword -> NewPasswordStep(busy, actions)
            TwoStepStep.Hint -> HintStep(busy, actions)
            is TwoStepStep.Email -> EmailStep(step.goal, busy, actions)
            is TwoStepStep.Code -> CodeStep(
                title = R.string.twostep_code_title,
                text = R.string.twostep_code_text,
                email = step.email,
                length = step.length,
                wrongCount = state.wrongCount,
                busy = busy,
                onBack = actions.onBack,
                onSubmit = actions.onCode,
                onResend = actions.onResend,
            )
            is TwoStepStep.Recover -> CodeStep(
                title = R.string.twostep_recover_title,
                text = R.string.twostep_recover_text,
                email = step.email,
                length = step.length,
                wrongCount = state.wrongCount,
                busy = busy,
                onBack = actions.onBack,
                onSubmit = actions.onRecover,
                onResend = null,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TwoStepOverview(state: TwoStepState, busy: Boolean, actions: TwoStepActions) {
    val list = rememberLazyListState()
    val title = stringResource(R.string.twostep_title)
    val status = state.status
    Page(title, rememberTopBarState(list), onBack = actions.onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("twoStep"), state = list, contentPadding = padding) {
            largeTitle(title)
            if (status == null) {
                item(key = "loading") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator(Modifier.size(48.dp).testTag("twoStepLoading"))
                    }
                }
                return@LazyColumn
            }
            item(key = "hero") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MorphingShape(MaterialShapes.Cookie9Sided, if (status.enabled) MaterialShapes.Clover4Leaf else MaterialShapes.Pill, size = 112.dp) {
                        Icon(
                            painterResource(if (status.enabled) R.drawable.ic_shield else R.drawable.ic_lock_open),
                            null,
                            Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(if (status.enabled) R.string.twostep_hero_on else R.string.twostep_hero_off),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("twoStepHero"),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(if (status.enabled) R.string.twostep_hero_on_text else R.string.twostep_hero_off_text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    state.saved?.let { saved ->
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(savedText(saved)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .semantics { liveRegion = LiveRegionMode.Polite }
                                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.large)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("twoStepSaved"),
                        )
                    }
                }
            }
            if (status.unconfirmedEmail != null) {
                item(key = "pending") {
                    InfoCard(Modifier.padding(top = 8.dp), title = stringResource(R.string.twostep_pending_title)) {
                        InfoRow(R.drawable.ic_mail, stringResource(R.string.twostep_pending_text, status.unconfirmedEmail.orEmpty()))
                        Row(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(onClick = actions.onEnterPendingCode, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp).testTag("twoStepEnterCode")) {
                                Text(stringResource(R.string.twostep_pending_enter))
                            }
                            TextButton(onClick = actions.onCancelEmail, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp).testTag("twoStepCancelEmail")) {
                                Text(stringResource(R.string.twostep_pending_cancel))
                            }
                        }
                    }
                }
            }
            if (!status.enabled) {
                item(key = "set") {
                    Button(
                        onClick = { actions.onStart(TwoStepGoal.Create) },
                        shapes = ButtonDefaults.shapesFor(ButtonDefaults.LargeContainerHeight),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).heightIn(min = 56.dp).testTag("twoStepSet"),
                    ) {
                        Text(stringResource(R.string.twostep_set), style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                item(key = "actions") {
                    InfoCard(Modifier.padding(top = 8.dp)) {
                        status.hint?.let {
                            InfoRow(R.drawable.ic_info, stringResource(R.string.twostep_hint_value, it), stringResource(R.string.twostep_hint_title), modifier = Modifier.testTag("twoStepHint"))
                            InfoDivider()
                        }
                        InfoRow(R.drawable.ic_lock, stringResource(R.string.twostep_change), onClick = { actions.onStart(TwoStepGoal.Change) }, modifier = Modifier.testTag("twoStepChange"))
                        InfoDivider()
                        InfoRow(
                            R.drawable.ic_mail,
                            stringResource(if (status.hasRecovery) R.string.twostep_email_change else R.string.twostep_email_set),
                            stringResource(if (status.hasRecovery) R.string.twostep_email_on else R.string.twostep_email_off),
                            onClick = { actions.onStart(TwoStepGoal.Email) },
                            modifier = Modifier.testTag("twoStepEmail"),
                        )
                        InfoDivider()
                        InfoRow(
                            R.drawable.ic_delete,
                            stringResource(R.string.twostep_remove),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            container = MaterialTheme.colorScheme.errorContainer,
                            valueColor = MaterialTheme.colorScheme.error,
                            onClick = { actions.onStart(TwoStepGoal.Remove) },
                            modifier = Modifier.testTag("twoStepRemove"),
                        )
                    }
                }
            }
        }
    }
}

@StringRes
private fun savedText(saved: TwoStepSaved): Int = when (saved) {
    TwoStepSaved.Created -> R.string.twostep_saved_created
    TwoStepSaved.Changed -> R.string.twostep_saved_changed
    TwoStepSaved.Removed -> R.string.twostep_saved_removed
    TwoStepSaved.Email -> R.string.twostep_saved_email
    TwoStepSaved.Recovered -> R.string.twostep_saved_recovered
}

@Composable
private fun CurrentStep(goal: TwoStepGoal, state: TwoStepState, busy: Boolean, actions: TwoStepActions) {
    val password = rememberTextFieldState()
    var confirming by rememberSaveable { mutableStateOf(false) }
    val hint = state.status?.hint
    val submit = {
        if (password.text.isNotBlank() && !busy) {
            if (goal == TwoStepGoal.Remove) confirming = true else actions.onCurrent(password.text.toString())
        }
    }
    AuthScreenLayout(onBack = actions.onBack) {
        AuthHeader(
            title = stringResource(R.string.twostep_current_title),
            subtitle = stringResource(
                when (goal) {
                    TwoStepGoal.Remove -> R.string.twostep_current_remove
                    TwoStepGoal.Email -> R.string.twostep_current_email
                    else -> R.string.twostep_current_change
                },
            ),
        )
        SecretField(
            password,
            label = stringResource(R.string.twostep_current_label),
            wrongCount = state.wrongCount,
            wrongText = R.string.error_password_invalid,
            busy = busy,
            hint = hint,
            imeAction = ImeAction.Done,
            onDone = submit,
            tag = "twoStepPassword",
        )
        Spacer(Modifier.height(32.dp))
        ContinueButton(
            enabled = password.text.isNotBlank(),
            busy = busy,
            label = stringResource(if (goal == TwoStepGoal.Remove) R.string.twostep_remove else R.string.common_next),
            onClick = submit,
            modifier = Modifier.testTag("twoStepContinue"),
        )
        if (state.status?.hasRecovery == true) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = actions.onForgot, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("twoStepForgot")) {
                Text(stringResource(R.string.twostep_forgot))
            }
        }
    }
    if (confirming) {
        ConfirmDialog(
            title = stringResource(R.string.twostep_remove_title),
            text = stringResource(R.string.twostep_remove_text),
            confirm = stringResource(R.string.twostep_remove_confirm),
            tag = "twoStepConfirmRemove",
            onConfirm = {
                confirming = false
                actions.onCurrent(password.text.toString())
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun NewPasswordStep(busy: Boolean, actions: TwoStepActions) {
    val first = rememberTextFieldState()
    val second = rememberTextFieldState()
    val matches = first.text.isNotEmpty() && first.text.toString() == second.text.toString()
    val mismatch = second.text.isNotEmpty() && first.text.toString() != second.text.toString()
    val submit = { if (matches && !busy) actions.onNewPassword(first.text.toString()) }
    AuthScreenLayout(onBack = actions.onBack) {
        AuthHeader(title = stringResource(R.string.twostep_new_title), subtitle = stringResource(R.string.twostep_new_text))
        SecretField(first, label = stringResource(R.string.twostep_new_label), busy = busy, imeAction = ImeAction.Next, tag = "twoStepNew")
        Spacer(Modifier.height(16.dp))
        SecretField(
            second,
            label = stringResource(R.string.twostep_repeat_label),
            busy = busy,
            imeAction = ImeAction.Done,
            onDone = submit,
            mismatch = mismatch,
            focus = false,
            tag = "twoStepRepeat",
        )
        Spacer(Modifier.height(32.dp))
        ContinueButton(enabled = matches, busy = busy, label = stringResource(R.string.common_next), onClick = submit, modifier = Modifier.testTag("twoStepContinue"))
    }
}

@Composable
private fun HintStep(busy: Boolean, actions: TwoStepActions) {
    var hint by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(FOCUS_DELAY_MS)
        focus.requestFocus()
    }
    AuthScreenLayout(onBack = actions.onBack) {
        AuthHeader(title = stringResource(R.string.twostep_hint_title), subtitle = stringResource(R.string.twostep_hint_text))
        OutlinedTextField(
            value = hint,
            onValueChange = { hint = it.take(HINT_MAX) },
            label = { Text(stringResource(R.string.twostep_hint_label)) },
            singleLine = true,
            enabled = !busy,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!busy) actions.onHint(hint) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("twoStepHintField"),
        )
        Spacer(Modifier.height(32.dp))
        ContinueButton(enabled = true, busy = busy, label = stringResource(R.string.common_next), onClick = { actions.onHint(hint) }, modifier = Modifier.testTag("twoStepContinue"))
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { actions.onHint("") }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("twoStepSkipHint")) {
            Text(stringResource(R.string.twostep_skip))
        }
    }
}

@Composable
private fun EmailStep(goal: TwoStepGoal, busy: Boolean, actions: TwoStepActions) {
    var email by rememberSaveable { mutableStateOf("") }
    var skipping by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(FOCUS_DELAY_MS)
        focus.requestFocus()
    }
    val valid = '@' in email && email.trim().length >= 3
    AuthScreenLayout(onBack = actions.onBack) {
        AuthHeader(title = stringResource(R.string.twostep_email_title), subtitle = stringResource(R.string.twostep_email_text))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text(stringResource(R.string.twostep_email_label)) },
            singleLine = true,
            enabled = !busy,
            shape = MaterialTheme.shapes.medium,
            leadingIcon = { Icon(painterResource(R.drawable.ic_mail), null, Modifier.size(20.dp)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (valid && !busy) actions.onEmail(email) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("twoStepEmailField"),
        )
        Spacer(Modifier.height(32.dp))
        ContinueButton(enabled = valid, busy = busy, label = stringResource(R.string.common_next), onClick = { actions.onEmail(email) }, modifier = Modifier.testTag("twoStepContinue"))
        if (goal == TwoStepGoal.Create) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { skipping = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("twoStepSkipEmail")) {
                Text(stringResource(R.string.twostep_skip))
            }
        }
    }
    if (skipping) {
        ConfirmDialog(
            title = stringResource(R.string.twostep_skip_email_title),
            text = stringResource(R.string.twostep_skip_email_text),
            confirm = stringResource(R.string.twostep_skip),
            tag = "twoStepConfirmSkip",
            onConfirm = {
                skipping = false
                actions.onEmail(null)
            },
            onDismiss = { skipping = false },
        )
    }
}

@Composable
private fun CodeStep(
    @StringRes title: Int,
    @StringRes text: Int,
    email: String,
    length: Int,
    wrongCount: Int,
    busy: Boolean,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    onResend: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    var code by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(FOCUS_DELAY_MS)
        focus.requestFocus()
    }
    LaunchedEffect(wrongCount) {
        if (wrongCount > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            code = ""
        }
    }
    val wrong = wrongCount > 0 && code.isEmpty()
    AuthScreenLayout(onBack = onBack) {
        AuthHeader(title = stringResource(title), subtitle = stringResource(text, email))
        InlineError(if (wrong) stringResource(R.string.error_code_invalid) else null, textModifier = Modifier.testTag("twoStepWrongCode"))
        if (wrong) Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(length) },
            label = { Text(stringResource(R.string.twostep_code_label)) },
            singleLine = true,
            isError = wrong,
            enabled = !busy,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (code.isNotEmpty() && !busy) onSubmit(code) }),
            modifier = Modifier.fillMaxWidth().shake(wrongCount).focusRequester(focus).testTag("twoStepCode"),
        )
        Spacer(Modifier.height(32.dp))
        ContinueButton(enabled = code.isNotEmpty(), busy = busy, label = stringResource(R.string.common_next), onClick = { onSubmit(code) }, modifier = Modifier.testTag("twoStepContinue"))
        if (onResend != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onResend, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("twoStepResend")) {
                Text(stringResource(R.string.twostep_code_resend))
            }
        }
    }
}

@Composable
private fun ColumnScope.SecretField(
    state: TextFieldState,
    label: String,
    busy: Boolean,
    imeAction: ImeAction,
    tag: String,
    wrongCount: Int = 0,
    wrongText: Int = R.string.error_password_invalid,
    hint: String? = null,
    mismatch: Boolean = false,
    focus: Boolean = true,
    onDone: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    var visible by rememberSaveable { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!focus) return@LaunchedEffect
        delay(FOCUS_DELAY_MS)
        focusRequester.requestFocus()
    }
    LaunchedEffect(wrongCount) {
        if (wrongCount == 0) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
        state.clearText()
        wrong = true
    }
    LaunchedEffect(state.text) {
        if (state.text.isNotEmpty()) wrong = false
    }
    OutlinedSecureTextField(
        state = state,
        label = { Text(label) },
        supportingText = when {
            wrong -> {
                { Text(stringResource(wrongText), modifier = Modifier.testTag("${tag}Error")) }
            }
            mismatch -> {
                { Text(stringResource(R.string.twostep_mismatch), modifier = Modifier.testTag("${tag}Error")) }
            }
            !hint.isNullOrEmpty() -> {
                { Text(stringResource(R.string.password_hint, hint), modifier = Modifier.testTag("${tag}Hint")) }
            }
            else -> null
        },
        isError = wrong || mismatch,
        enabled = !busy,
        textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        leadingIcon = { Icon(painterResource(R.drawable.ic_lock), null, Modifier.size(20.dp)) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.testTag("${tag}Toggle"), shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    stringResource(if (visible) R.string.password_hide else R.string.password_show),
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        onKeyboardAction = { onDone() },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().shake(wrongCount).focusRequester(focusRequester).testTag(tag),
    )
}

private const val FOCUS_DELAY_MS = 200L
private const val HINT_MAX = 70
