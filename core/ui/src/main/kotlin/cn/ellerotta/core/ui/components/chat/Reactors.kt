package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerReaction
import cn.ellerotta.core.model.Reactor
import cn.ellerotta.core.model.ReactorsState
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.UnreadBadge
import cn.ellerotta.core.ui.components.common.Avatar

class ChatReactorActions(
    val onShow: (messageId: Int) -> Unit,
    val onMore: () -> Unit,
    val onClose: () -> Unit,
    val onOpenPeer: (key: String) -> Unit,
    val onNextUnread: () -> Unit,
    val onReadAll: () -> Unit,
)

internal fun reactionTotal(message: MessageItem): Int = message.reactions.sumOf { it.count }

@Composable
internal fun ReactorsLine(message: MessageItem, avatars: Map<String, ByteArray>, onClick: () -> Unit) {
    val total = reactionTotal(message)
    val faces: List<Reactor> = message.reactions.flatMap { it.recent }.distinctBy { it.peerId }.take(LINE_FACES)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LINE_HEIGHT)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("showReactors"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_favorite), null, Modifier.size(20.dp))
        Text(pluralStringResource(R.plurals.chat_reactions_total, total, total), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f, fill = false))
        if (faces.isNotEmpty()) {
            Box(Modifier.size(FACE * faces.size - FACE_OVERLAP * (faces.size - 1), FACE).clearAndSetSemantics { }) {
                faces.forEachIndexed { index, reactor ->
                    Avatar(reactor.name, reactor.avatarKey?.let(avatars::get), size = FACE.value.toInt(), seed = reactor.peerId, modifier = Modifier.offset(x = (FACE - FACE_OVERLAP) * index))
                }
            }
        }
    }
}

@Composable
internal fun ReactorsSheet(state: ReactorsState, avatars: Map<String, ByteArray>, emoji: InlineEmoji, actions: ChatReactorActions) {
    ModalBottomSheet(onDismissRequest = actions.onClose, modifier = Modifier.testTag("reactorsSheet")) {
        ReactorsContent(state, avatars, emoji, actions.onMore, actions.onOpenPeer)
    }
}

@Composable
internal fun ReactorsContent(state: ReactorsState, avatars: Map<String, ByteArray>, emoji: InlineEmoji, onMore: () -> Unit, onOpenPeer: (String) -> Unit) {
    Column {
        val total = maxOf(state.total, state.count)
        Text(
            pluralStringResource(R.plurals.chat_reactions_total, total, total),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        )
        val list = rememberLazyListState()
        LaunchedEffect(list, state.next, state.loading) {
            if (state.next == null || state.loading) return@LaunchedEffect
            snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
                .collect { (last, count) -> if (last != null && last >= count - 3) onMore() }
        }
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding().testTag("reactorsList"), state = list) {
            items(state.list) { reaction -> ReactorRow(reaction, avatars, emoji) { onOpenPeer(reaction.key) } }
            if (state.loading) {
                item(key = "loading") {
                    val label = stringResource(R.string.chat_reactors_loading)
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator(Modifier.semantics { contentDescription = label })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactorRow(reaction: PeerReaction, avatars: Map<String, ByteArray>, emoji: InlineEmoji, onClick: () -> Unit) {
    val reactor = reaction.reactor
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .testTag("reactor_${reaction.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(reactor.name, reactor.avatarKey?.let(avatars::get), size = 40, seed = reactor.peerId)
        Text(reactor.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        val label = reactionLabel(reaction.reaction, emoji)
        Box(Modifier.clearAndSetSemantics { contentDescription = label }) { ReactionGlyph(reaction.reaction, emoji, 24.dp) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UnreadReactionsButton(count: Int, onNext: () -> Unit, onReadAll: () -> Unit, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        count > 0,
        modifier = modifier,
        enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
        exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        val label = pluralStringResource(R.plurals.chat_unread_reactions, count, count)
        val clearLabel = stringResource(R.string.chat_unread_reactions_clear)
        Box(Modifier.size(BUTTON + BADGE_RISE)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(BUTTON)
                    .clip(CircleShape)
                    .combinedClickable(onClickLabel = label, onLongClickLabel = clearLabel, onLongClick = onReadAll, onClick = onNext)
                    .semantics { contentDescription = label }
                    .testTag("unreadReactions"),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_favorite), null) }
            }
            UnreadBadge(count, muted = false, modifier = Modifier.align(Alignment.TopEnd).clearAndSetSemantics { })
        }
    }
}

private val LINE_HEIGHT = 48.dp
private const val LINE_FACES = 3
private val FACE = 24.dp
private val FACE_OVERLAP = 8.dp
private val BUTTON = 48.dp
private val BADGE_RISE = 8.dp
