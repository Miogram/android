package cn.ellerotta.core.ui.components.appbar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.PeerStories
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.DecodedPhotos
import cn.ellerotta.core.ui.components.common.storyRingColor

@Composable
fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    leading: (@Composable () -> Unit)? = null,
    fieldTag: String = "search",
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
            } else {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .then(if (onFocusChange != null) Modifier.onFocusChanged { onFocusChange(it.isFocused) } else Modifier)
                    .testTag(fieldTag),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        innerTextField()
                    }
                },
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.chats_clear_search))
                }
            } else {
                Spacer(Modifier.width(16.dp))
            }
        }
    }
}

@Composable
internal fun OverflowCircle(
    count: Int,
    size: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(size.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun AddStoryCard(
    self: PeerStories?,
    avatars: Map<String, ByteArray>,
    onAddStory: (() -> Unit)?,
    onOpenStories: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val own = self?.takeIf { it.stories.isNotEmpty() }
    val hasActiveStory = own != null
    val preview = remember(own) { own?.let(::storyThumb) }
    val bitmap = remember(preview) { preview?.let(DecodedPhotos::get) }
    val photo = self?.avatarKey?.let { avatars[it] }
    val photoBitmap = remember(photo) { photo?.let(DecodedPhotos::get) }
    val addLabel = stringResource(R.string.stories_bar_add)
    val myStoryLabel = stringResource(R.string.stories_bar_my_story)

    Column(
        modifier = modifier.width(STORY_CARD_WIDTH.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(width = STORY_CARD_WIDTH.dp, height = STORY_CARD_HEIGHT.dp)) {
            Surface(
                onClick = { if (own != null) onOpenStories(own.key) else onAddStory?.invoke() },
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = if (hasActiveStory) myStoryLabel else addLabel }
                    .testTag("addStory"),
                shape = MaterialTheme.shapes.extraLarge,
                color = colors.surfaceContainerHigh,
                border = BorderStroke(
                    width = if (hasActiveStory) 3.dp else 1.5.dp,
                    color = own?.let { storyRingColor(it.ring, colors.surface) } ?: colors.outline,
                ),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        hasActiveStory && bitmap != null -> Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.extraLarge)
                                .blur(6.dp),
                        )
                        hasActiveStory && photoBitmap != null -> Image(
                            bitmap = photoBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.extraLarge),
                        )
                        !hasActiveStory -> Surface(
                            shape = CircleShape,
                            color = colors.primary,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
            if (hasActiveStory && onAddStory != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onAddStory)
                        .semantics { contentDescription = addLabel }
                        .testTag("addStoryPlus"),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(shape = CircleShape, color = colors.primary, modifier = Modifier.size(28.dp)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.stories_bar_my_story),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
