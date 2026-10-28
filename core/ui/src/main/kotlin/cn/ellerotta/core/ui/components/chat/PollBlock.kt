package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.Poll
import cn.ellerotta.core.model.PollVotesState
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar
import kotlinx.coroutines.delay

private const val VOTER_AVATARS = 3

class ChatPollActions(
    val onVote: (messageId: Int, options: List<String>) -> Unit,
    val onShowVotes: (messageId: Int) -> Unit,
    val onMoreVotes: () -> Unit,
    val onCloseVotes: () -> Unit,
)

@Composable
internal fun PollBlock(
    message: MessageItem,
    poll: Poll,
    accent: Color,
    avatars: Map<String, ByteArray>,
    actions: TextActions,
    press: TextPress,
    emoji: InlineEmoji,
    onVote: ((List<String>) -> Unit)?,
    onShowVotes: (() -> Unit)?,
) {
    val now by produceState((System.currentTimeMillis() / 1000).toInt(), poll.closeDate) {
        while (true) {
            val end = poll.closeDate ?: break
            if (end <= value) break
            delay(1_000)
            value = (System.currentTimeMillis() / 1000).toInt()
        }
    }
    val results = poll.showsResults(now)
    val closed = poll.isClosed(now)
    var revealed by remember(message.id) { mutableStateOf(false) }
    var checked by remember(message.id, poll.voted) { mutableStateOf(emptyList<String>()) }
    val canVote = onVote != null && poll.canVote(now)
    val colors = bubbleColors()
    Column(Modifier.widthIn(min = 220.dp).testTag("poll_${message.id}")) {
        PollText(poll.question, message.id, accent, revealed, { revealed = true }, actions, press, emoji, MaterialTheme.typography.titleMedium, "pollQuestion_${message.id}")
        val kind = when {
            closed -> R.string.chat_poll_closed
            poll.quiz -> if (poll.publicVoters) R.string.chat_poll_quiz else R.string.chat_poll_anonymous_quiz
            else -> if (poll.publicVoters) R.string.chat_poll_public else R.string.chat_poll_anonymous
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(kind), style = MaterialTheme.typography.labelMedium, color = colors.secondary)
            val end = poll.closeDate
            if (end != null && !closed) {
                Text(
                    stringResource(R.string.chat_poll_ends_in, countdownText(end - now)),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.secondary,
                    modifier = Modifier.testTag("pollEndsIn_${message.id}"),
                )
            }
        }
        val percents = remember(poll) { poll.percents() }
        poll.answers.forEachIndexed { index, answer ->
            val voters = poll.votersOf(answer.option)
            val tag = "pollAnswer_${message.id}_$index"
            if (results) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp).testTag(tag)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${percents[index]}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 36.dp))
                        Box(Modifier.weight(1f)) {
                            PollText(answer.text, message.id, accent, revealed, { revealed = true }, actions, press, emoji, MaterialTheme.typography.bodyMedium, "${tag}_text")
                        }
                        when {
                            poll.quiz && voters?.correct == true -> Icon(
                                painterResource(R.drawable.ic_check_circle_filled), stringResource(R.string.chat_poll_correct),
                                Modifier.size(18.dp).testTag("pollCorrect_${message.id}_$index"), tint = colors.success,
                            )
                            poll.quiz && voters?.chosen == true -> Icon(
                                painterResource(R.drawable.ic_close), stringResource(R.string.chat_poll_wrong),
                                Modifier.size(18.dp).testTag("pollWrong_${message.id}_$index"), tint = colors.danger,
                            )
                            voters?.chosen == true -> Icon(
                                painterResource(R.drawable.ic_check_circle_filled), stringResource(R.string.chat_poll_chosen),
                                Modifier.size(18.dp).testTag("pollChosen_${message.id}_$index"), tint = accent,
                            )
                        }
                    }
                    val color = when {
                        poll.quiz && voters?.correct == true -> colors.success
                        poll.quiz && voters?.chosen == true -> colors.danger
                        else -> accent
                    }
                    LinearProgressIndicator(progress = { percents[index] / 100f }, color = color, trackColor = colors.track, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            } else {
                val selected = answer.option in checked
                val pick: () -> Unit = {
                    if (poll.multipleChoice) {
                        checked = if (selected) checked - answer.option else checked + answer.option
                    } else {
                        onVote?.invoke(listOf(answer.option))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(enabled = canVote, onClick = pick).testTag(tag),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (poll.multipleChoice) {
                        Checkbox(
                            checked = selected, onCheckedChange = { pick() }, enabled = canVote,
                            colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = colors.content, checkmarkColor = colors.onAccent),
                        )
                    } else {
                        RadioButton(selected = false, onClick = pick, enabled = canVote, colors = RadioButtonDefaults.colors(selectedColor = accent, unselectedColor = colors.content))
                    }
                    PollText(answer.text, message.id, accent, revealed, { revealed = true }, actions, press, emoji, MaterialTheme.typography.bodyMedium, "${tag}_text")
                }
            }
        }
        val solution = poll.results.solution
        if (poll.quiz && results && solution != null) {
            Row(Modifier.padding(top = 8.dp).testTag("pollSolution_${message.id}"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(R.drawable.ic_info), stringResource(R.string.chat_poll_solution), Modifier.size(18.dp), tint = accent)
                PollText(solution, message.id, accent, revealed, { revealed = true }, actions, press, emoji, MaterialTheme.typography.bodyMedium, "pollSolutionText_${message.id}")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val voters = poll.results.recentVoters
            if (poll.publicVoters && voters.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    voters.take(VOTER_AVATARS).forEach { voter -> Avatar(voter.name, voter.avatarKey?.let(avatars::get), size = 20, seed = voter.peerId) }
                }
            }
            val total = poll.results.totalVoters
            Text(
                if (total == 0) stringResource(R.string.chat_poll_no_votes) else pluralStringResource(R.plurals.chat_poll_votes, total, total),
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondary,
                modifier = Modifier.weight(1f).testTag("pollTotal_${message.id}"),
            )
        }
        if (canVote && poll.multipleChoice) {
            TextButton(onClick = { onVote?.invoke(checked) }, enabled = checked.isNotEmpty(), modifier = Modifier.align(Alignment.CenterHorizontally).testTag("pollVote_${message.id}")) {
                Text(stringResource(R.string.chat_poll_vote), color = accent)
            }
        }
        if (onVote != null && poll.canRetract(now)) {
            TextButton(onClick = { onVote(emptyList()) }, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("pollRetract_${message.id}")) {
                Text(stringResource(R.string.chat_poll_retract), color = accent)
            }
        }
        if (onShowVotes != null && poll.publicVoters && results && poll.results.totalVoters > 0) {
            TextButton(onClick = onShowVotes, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("pollVotes_${message.id}")) {
                Text(stringResource(R.string.chat_poll_view_votes), color = accent)
            }
        }
    }
}

@Composable
private fun PollText(
    text: FormattedText,
    messageId: Int,
    accent: Color,
    revealed: Boolean,
    onReveal: () -> Unit,
    actions: TextActions,
    press: TextPress,
    emoji: InlineEmoji,
    style: androidx.compose.ui.text.TextStyle,
    tag: String,
) {
    RichParagraph(text.text, text.entities, messageId, accent, revealed, onReveal, actions, press, emoji, style = style, tag = tag)
}

internal fun countdownText(seconds: Int): String {
    val left = seconds.coerceAtLeast(0)
    val h = left / 3600
    val m = left % 3600 / 60
    val s = left % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
internal fun PollVotesSheet(state: PollVotesState, avatars: Map<String, ByteArray>, onMore: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            pluralStringResource(R.plurals.chat_poll_votes, state.count, state.count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        val list = rememberLazyListState()
        LaunchedEffect(list, state.next) {
            if (state.next == null) return@LaunchedEffect
            snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
                .collect { (last, total) -> if (last != null && last >= total - 3) onMore() }
        }
        val answers = remember(state.poll) { state.poll.answers.associate { it.option to it.text.text } }
        LazyColumn(Modifier.fillMaxWidth().testTag("pollVotesList"), state = list) {
            items(state.votes) { vote ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(vote.voter.name, vote.voter.avatarKey?.let(avatars::get), size = 36, seed = vote.voter.peerId)
                    Column(Modifier.weight(1f)) {
                        Text(vote.voter.name, style = MaterialTheme.typography.bodyLarge)
                        Text(vote.options.mapNotNull(answers::get).joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
