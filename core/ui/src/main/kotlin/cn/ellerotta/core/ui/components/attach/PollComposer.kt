package cn.ellerotta.core.ui.components.attach

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.PollDraft
import cn.ellerotta.core.model.PollLimits
import cn.ellerotta.core.model.PollOptionDraft
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.CheckedSwitch
import cn.ellerotta.core.ui.components.common.ConfirmDialog
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut

@Composable
fun PollComposerScreen(
    draft: PollDraft,
    busy: Boolean,
    onChange: (PollDraft) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
    val list = rememberLazyListState()
    var confirmDiscard by remember { mutableStateOf(false) }
    val leave = { if (draft.started) confirmDiscard = true else onBack() }
    BackHandler(onBack = leave)
    val focus = remember { FocusRequester() }
    var count by remember { mutableIntStateOf(draft.options.size) }
    var focusNew by remember { mutableStateOf(false) }
    LaunchedEffect(draft.options.size) {
        if (draft.options.size > count) focusNew = true
        count = draft.options.size
    }
    val title = stringResource(R.string.poll_new_title)
    Page(
        title,
        rememberTopBarState(list),
        onBack = leave,
        floating = {
            AnimatedVisibility(draft.canSend, enter = MaterialTheme.motionScheme.expandFadeIn(), exit = MaterialTheme.motionScheme.shrinkFadeOut()) {
                MediumExtendedFloatingActionButton(
                    onClick = onSend,
                    icon = { Icon(painterResource(R.drawable.ic_bar_chart), null) },
                    text = { Text(stringResource(R.string.poll_send)) },
                    modifier = Modifier.testTag("pollSend"),
                    expanded = true,
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("pollList"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "busy") { if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            item(key = "question") {
                OutlinedTextField(
                    value = draft.question,
                    onValueChange = { onChange(draft.withQuestion(it)) },
                    label = { Text(stringResource(R.string.poll_question)) },
                    supportingText = { Text(stringResource(R.string.poll_counter, draft.question.length, PollLimits.QUESTION)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("pollQuestion"),
                )
            }
            item(key = "optionsTitle") {
                Text(
                    stringResource(R.string.poll_options),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
                )
            }
            item(key = "options") {
                Column(Modifier.selectableGroup()) {
                    draft.options.forEachIndexed { index, option ->
                        OptionRow(
                            draft = draft,
                            option = option,
                            index = index,
                            focus = if (focusNew && index == draft.options.lastIndex) focus else null,
                            onChange = onChange,
                        )
                    }
                }
            }
            item(key = "add") {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(
                        onClick = { onChange(draft.addOption()) },
                        enabled = draft.canAddOption,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("pollAdd"),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(painterResource(R.drawable.ic_add), null, Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.poll_add_option))
                    }
                    Text(
                        if (draft.canAddOption) pluralStringResource(R.plurals.poll_options_left, PollLimits.MAX_OPTIONS - draft.options.size, PollLimits.MAX_OPTIONS - draft.options.size) else stringResource(R.string.poll_options_full),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
            item(key = "spacer") { Spacer(Modifier.height(16.dp)) }
            if (draft.canChooseAnonymity) {
                item(key = "anonymous") { SwitchRow(stringResource(R.string.poll_anonymous), stringResource(R.string.poll_anonymous_hint), draft.anonymous, "pollAnonymous") { onChange(draft.withAnonymous(it)) } }
            }
            item(key = "multiple") { SwitchRow(stringResource(R.string.poll_multiple), stringResource(R.string.poll_multiple_hint), draft.multiple, "pollMultiple") { onChange(draft.withMultiple(it)) } }
            item(key = "quiz") { SwitchRow(stringResource(R.string.poll_quiz), stringResource(R.string.poll_quiz_hint), draft.quiz, "pollQuiz") { onChange(draft.withQuiz(it)) } }
            if (draft.quiz) {
                item(key = "explanation") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (draft.correct == null) {
                            Text(stringResource(R.string.poll_correct_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp).testTag("pollCorrectHint"))
                        }
                        OutlinedTextField(
                            value = draft.explanation,
                            onValueChange = { onChange(draft.withExplanation(it)) },
                            label = { Text(stringResource(R.string.poll_explanation)) },
                            supportingText = { Text(stringResource(R.string.poll_explanation_hint) + " · " + stringResource(R.string.poll_counter, draft.explanation.length, PollLimits.EXPLANATION)) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth().testTag("pollExplanation"),
                        )
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.heightIn(min = 72.dp)) }
        }
    }
    if (focusNew) LaunchedEffect(draft.options.size) {
        runCatching { focus.requestFocus() }
        focusNew = false
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.poll_discard_title),
            text = stringResource(R.string.poll_discard_text),
            confirm = stringResource(R.string.poll_discard),
            tag = "pollDiscard",
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun OptionRow(draft: PollDraft, option: PollOptionDraft, index: Int, focus: FocusRequester?, onChange: (PollDraft) -> Unit) {
    val number = index + 1
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp).testTag("pollOption_${option.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (draft.quiz) {
            RadioButton(
                selected = draft.correct == option.id,
                onClick = null,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .selectable(selected = draft.correct == option.id, role = Role.RadioButton, onClick = { onChange(draft.withCorrect(option.id)) })
                    .padding(horizontal = 12.dp)
                    .testTag("pollCorrect_${option.id}"),
            )
        } else {
            Spacer(Modifier.width(16.dp))
        }
        OutlinedTextField(
            value = option.text,
            onValueChange = { onChange(draft.withOption(option.id, it)) },
            placeholder = { Text(stringResource(R.string.poll_option_hint, number)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = if (index == draft.options.lastIndex) ImeAction.Done else ImeAction.Next),
            modifier = Modifier.weight(1f).then(if (focus != null) Modifier.focusRequester(focus) else Modifier).testTag("pollText_${option.id}"),
        )
        IconButton(onClick = { onChange(draft.move(option.id, index - 1)) }, enabled = index > 0, modifier = Modifier.testTag("pollUp_${option.id}"), shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_keyboard_arrow_up), stringResource(R.string.poll_option_up, number))
        }
        IconButton(onClick = { onChange(draft.move(option.id, index + 1)) }, enabled = index < draft.options.lastIndex, modifier = Modifier.testTag("pollDown_${option.id}"), shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_keyboard_arrow_down), stringResource(R.string.poll_option_down, number))
        }
        IconButton(onClick = { onChange(draft.removeOption(option.id)) }, enabled = draft.canRemoveOption, modifier = Modifier.testTag("pollRemove_${option.id}"), shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.poll_option_remove, number))
        }
    }
}

@Composable
internal fun SwitchRow(title: String, hint: String, checked: Boolean, tag: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CheckedSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
