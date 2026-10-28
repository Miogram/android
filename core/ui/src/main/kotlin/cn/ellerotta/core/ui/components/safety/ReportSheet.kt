package cn.ellerotta.core.ui.components.safety

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.PeerReportReason
import cn.ellerotta.core.model.ReportOption
import cn.ellerotta.core.model.ReportState
import cn.ellerotta.core.model.ReportStep
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.theme.crossfade

class ReportActions(val onChoose: (String) -> Unit, val onComment: (String) -> Unit, val onClose: () -> Unit)

@Composable
fun ReportSheet(state: ReportState, actions: ReportActions) {
    ModalBottomSheet(onDismissRequest = actions.onClose, modifier = Modifier.testTag("reportSheet")) {
        ReportContent(state, actions)
    }
}

@Composable
internal fun ReportContent(state: ReportState, actions: ReportActions) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(state.step, transitionSpec = { motion.crossfade() }, label = "reportStep", contentKey = { it?.let { step -> step::class } }) { step ->
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            when (step) {
                null -> Loading(R.string.report_loading)
                is ReportStep.Choose -> ChooseStep(step, state.sending, actions.onChoose)
                is ReportStep.Comment -> CommentStep(step, state.sending, actions.onComment)
                ReportStep.Done -> DoneStep(actions.onClose)
            }
        }
    }
}

@Composable
private fun Loading(@StringRes label: Int) {
    val description = stringResource(label)
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.semantics { contentDescription = description }.testTag("reportLoading"))
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
    )
}

@Composable
private fun ChooseStep(step: ReportStep.Choose, sending: Boolean, onChoose: (String) -> Unit) {
    StepTitle(step.title ?: stringResource(R.string.report_title_peer))
    step.options.forEach { option ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = !sending, role = Role.Button) { onChoose(option.id) }
                .padding(horizontal = 24.dp)
                .testTag("reportOption_${option.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(optionText(option), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }
    }
    if (sending) Loading(R.string.report_sending)
}

@Composable
private fun CommentStep(step: ReportStep.Comment, sending: Boolean, onComment: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    StepTitle(stringResource(R.string.report_comment_label))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(if (step.optional) R.string.report_comment_optional else R.string.report_comment_required)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        minLines = 3,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("reportComment"),
    )
    Button(
        onClick = { onComment(text) },
        enabled = !sending && (step.optional || text.isNotBlank()),
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).heightIn(min = 48.dp).testTag("reportSend"),
    ) {
        Text(stringResource(if (step.optional && text.isBlank()) R.string.report_skip else R.string.report_send))
    }
}

@Composable
private fun DoneStep(onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(R.drawable.ic_verified), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.report_done_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.report_done_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onClose, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("reportClose")) {
            Text(stringResource(R.string.report_close))
        }
    }
}

@Composable
private fun optionText(option: ReportOption): String = option.text ?: option.reason?.let { stringResource(it.label) }.orEmpty()

@get:StringRes
private val PeerReportReason.label: Int
    get() = when (this) {
        PeerReportReason.Spam -> R.string.report_reason_spam
        PeerReportReason.Violence -> R.string.report_reason_violence
        PeerReportReason.Pornography -> R.string.report_reason_pornography
        PeerReportReason.ChildAbuse -> R.string.report_reason_child_abuse
        PeerReportReason.IllegalDrugs -> R.string.report_reason_illegal_drugs
        PeerReportReason.PersonalDetails -> R.string.report_reason_personal_details
        PeerReportReason.Fake -> R.string.report_reason_fake
        PeerReportReason.Other -> R.string.report_reason_other
    }
