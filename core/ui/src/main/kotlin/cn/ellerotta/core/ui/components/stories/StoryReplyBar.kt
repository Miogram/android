package cn.ellerotta.core.ui.components.stories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.InlineEmoji
import cn.ellerotta.core.ui.components.chat.MEDIA_SCRIM
import cn.ellerotta.core.ui.components.chat.PILL_ALPHA
import cn.ellerotta.core.ui.components.chat.ReactionGlyph
import cn.ellerotta.core.ui.components.chat.reactionLabel

val StoryHeart: Reaction = Reaction.Emoji("\u2764")

val StoryQuickReactions: List<Reaction> =
    listOf("\u2764", "\uD83D\uDC4D", "\uD83D\uDD25", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDC4F").map(Reaction::Emoji)

internal val StoryHeartFill = Color(0xFFD70015)

private val TARGET = 48.dp

@Composable
fun StoryReplyBar(
    story: StoryItem,
    peerName: String,
    onReply: (String) -> Unit,
    onReact: (Reaction?) -> Unit,
    pause: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    reactions: List<Reaction> = StoryQuickReactions,
    customEmoji: Map<Long, MediaItem> = emptyMap(),
    emojiFiles: Map<String, ByteArray> = emptyMap(),
) {
    var text by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val emoji = remember(customEmoji, emojiFiles) { InlineEmoji(customEmoji, emojiFiles) }
    val motion = MaterialTheme.motionScheme
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(
            focused && reactions.isNotEmpty(),
            enter = expandVertically(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = PILL_ALPHA))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp)
                    .testTag("storyQuickReactions"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                reactions.forEachIndexed { index, reaction ->
                    val label = stringResource(R.string.stories_reply_react, reactionLabel(reaction, emoji))
                    Box(
                        Modifier
                            .size(TARGET)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) {
                                onReact(reaction)
                                focus.clearFocus()
                            }
                            .semantics { contentDescription = label }
                            .testTag("storyQuickReaction_$index"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.clearAndSetSemantics {}) { ReactionGlyph(reaction, emoji, 32.dp) }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = TARGET)
                    .clip(RoundedCornerShape(TARGET / 2))
                    .background(Color.Black.copy(alpha = PILL_ALPHA))
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val hint = stringResource(R.string.stories_reply_hint, peerName)
                val style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = style,
                    cursorBrush = SolidColor(Color.White),
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)
                        .onFocusChanged {
                            if (it.isFocused != focused) {
                                focused = it.isFocused
                                pause(it.isFocused)
                            }
                        }
                        .semantics { contentDescription = hint }
                        .testTag("storyReplyField"),
                    decorationBox = { field ->
                        Box {
                            if (text.isEmpty()) Text(hint, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clearAndSetSemantics {})
                            field()
                        }
                    },
                )
                AnimatedVisibility(
                    text.isNotBlank(),
                    enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                    exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                ) {
                    IconButton(
                        onClick = {
                            onReply(text.trim())
                            text = ""
                            focus.clearFocus()
                        },
                        modifier = Modifier.size(TARGET).testTag("storyReplySend"),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(painterResource(R.drawable.ic_send), stringResource(R.string.stories_reply_send), tint = Color.White)
                    }
                }
            }
            HeartButton(story.reaction, emoji, onReact)
        }
    }
}

@Composable
private fun HeartButton(own: Reaction?, emoji: InlineEmoji, onReact: (Reaction?) -> Unit) {
    val liked = own != null
    val label = stringResource(if (liked) R.string.stories_reply_unlike else R.string.stories_reply_like)
    val pop = remember { Animatable(1f) }
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    var wasLiked by remember { mutableStateOf(liked) }
    LaunchedEffect(liked) {
        if (liked && !wasLiked) {
            pop.snapTo(HEART_POP)
            pop.animateTo(1f, spatial)
        }
        wasLiked = liked
    }
    val scrim = Color.Black.copy(alpha = MEDIA_SCRIM)
    FilledIconToggleButton(
        checked = liked,
        onCheckedChange = { onReact(if (it) StoryHeart else null) },
        shapes = IconButtonDefaults.toggleableShapes(),
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = scrim,
            contentColor = Color.White,
            checkedContainerColor = if (own == StoryHeart) StoryHeartFill else scrim,
            checkedContentColor = Color.White,
        ),
        modifier = Modifier
            .size(TARGET)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .semantics { contentDescription = label }
            .testTag("storyHeart"),
    ) {
        if (own == null || own == StoryHeart) {
            Icon(painterResource(if (own == StoryHeart) R.drawable.ic_favorite_filled else R.drawable.ic_favorite), null)
        } else {
            Box(Modifier.clearAndSetSemantics {}) { ReactionGlyph(own, emoji, 28.dp) }
        }
    }
}

private const val HEART_POP = 0.8f
