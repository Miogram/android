package cn.ellerotta.core.ui.components.stories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.StoryAudience
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.MEDIA_SCRIM
import cn.ellerotta.core.ui.components.chat.StillImage
import cn.ellerotta.core.ui.components.common.ConnectedChoice
import cn.ellerotta.core.ui.components.common.DecodedPhotos
import cn.ellerotta.core.ui.components.common.TopBarColors
import cn.ellerotta.core.ui.components.common.TopBarPill
import cn.ellerotta.core.ui.components.common.TopBarTokens
import cn.ellerotta.core.ui.theme.TEXT_CONTRAST
import cn.ellerotta.core.ui.theme.fadedOver

private val FieldFill = Color.White.copy(alpha = 0.12f).compositeOver(Color.Black)

private val FrameFill = Color(0xFF1F1F1F)

private const val DISABLED_ALPHA = 0.5f

@Composable
fun StoryComposer(
    preview: ByteArray?,
    isVideo: Boolean,
    sending: Boolean,
    progress: Float?,
    onPick: () -> Unit,
    onSend: (caption: String, audience: StoryAudience?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    editing: StoryItem? = null,
    editingState: DownloadState? = null,
) {
    var caption by rememberSaveable(editing?.id) { mutableStateOf(editing?.caption.orEmpty()) }
    var audience by rememberSaveable(editing?.id) { mutableStateOf(if (editing == null) StoryAudience.Everybody else editing.audience) }
    val picked = preview != null || isVideo
    val current = editing?.media?.takeIf { !picked }
    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("storyComposer"),
    ) {
        Box(Modifier.fillMaxWidth().padding(TopBarTokens.EdgeInset).heightIn(min = TopBarTokens.PillSize)) {
            TopBarPill(
                R.drawable.ic_close,
                stringResource(R.string.stories_post_close),
                onClose,
                Modifier.align(Alignment.CenterStart).testTag("storyComposerClose"),
                colors = TopBarColors.Media,
            )
            Text(
                stringResource(if (editing != null) R.string.stories_edit_title else R.string.stories_post_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = TopBarTokens.TitleInset).semantics { heading() },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            if (picked || current != null) {
                Frame(preview, if (current != null) editing?.isVideo == true else isVideo, sending, onPick, current, editingState)
            } else {
                Button(
                    onClick = onPick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    modifier = Modifier.heightIn(min = 56.dp).testTag("storyPick"),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(painterResource(R.drawable.ic_image), null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.stories_post_pick), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CaptionField(caption, { caption = it }, enabled = !sending)
            Text(stringResource(R.string.stories_post_audience), style = MaterialTheme.typography.labelLarge, color = Color.White)
            AudienceChoice(audience, { audience = it }, enabled = !sending)
            SendButton(enabled = (picked || editing != null) && !sending, sending = sending, progress = progress, editing = editing != null) { onSend(caption.trim(), audience) }
        }
    }
}

@Composable
private fun Frame(preview: ByteArray?, isVideo: Boolean, sending: Boolean, onPick: () -> Unit, current: MediaItem? = null, currentState: DownloadState? = null) {
    val bitmap = remember(preview) { preview?.let(DecodedPhotos::get) }
    Box(
        Modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(16.dp)).background(FrameFill).testTag("storyPreview"),
        contentAlignment = Alignment.Center,
    ) {
        val label = stringResource(if (isVideo) R.string.stories_post_video else R.string.stories_post_photo)
        if (bitmap != null) {
            Image(bitmap, label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (current != null) {
            StillImage(current, currentState, modifier = Modifier.fillMaxSize().semantics { contentDescription = label }.testTag("storyCurrentMedia"))
        }
        if (isVideo) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = MEDIA_SCRIM)).semantics { if (bitmap == null && current == null) contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_play_arrow), null, Modifier.size(32.dp), tint = Color.White)
            }
        }
        TopBarPill(
            R.drawable.ic_image,
            stringResource(R.string.stories_post_change),
            onPick,
            Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("storyPickAgain"),
            enabled = !sending,
            colors = TopBarColors(Color.Black.copy(alpha = MEDIA_SCRIM), Color.White, Color.Transparent),
        )
    }
}

@Composable
private fun CaptionField(value: String, onValue: (String) -> Unit, enabled: Boolean) {
    val hint = stringResource(R.string.stories_post_caption)
    val style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
    BasicTextField(
        value = value,
        onValueChange = onValue,
        enabled = enabled,
        textStyle = style,
        cursorBrush = SolidColor(Color.White),
        maxLines = 4,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.large)
            .background(FieldFill)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = hint }
            .testTag("storyCaption"),
        decorationBox = { field ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        hint,
                        style = style.copy(color = Color.White.fadedOver(FieldFill, 0.7f, TEXT_CONTRAST)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
                field()
            }
        },
    )
}

@Composable
private fun AudienceChoice(audience: StoryAudience?, onAudience: (StoryAudience?) -> Unit, enabled: Boolean) {
    ConnectedChoice<StoryAudience?>(
        options = StoryAudience.entries,
        chosen = audience,
        onChoose = { if (enabled) onAudience(it) },
        label = { option ->
            stringResource(
                when (option) {
                    StoryAudience.Everybody, null -> R.string.stories_post_everybody
                    StoryAudience.Contacts -> R.string.stories_post_contacts
                    StoryAudience.CloseFriends -> R.string.stories_post_close_friends
                },
            )
        },
        tag = { "storyAudience_${it?.name}" },
        enabled = true,
        colors = ToggleButtonDefaults.colors(
            containerColor = Color.Black,
            contentColor = Color.White,
            checkedContainerColor = Color.White,
            checkedContentColor = Color.Black,
        ),
        border = BorderStroke(1.dp, Color.White),
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else DISABLED_ALPHA).semantics { if (!enabled) disabled() },
    )
}

@Composable
private fun SendButton(enabled: Boolean, sending: Boolean, progress: Float?, editing: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
            disabledContainerColor = if (sending) Color.White else Color.White.copy(alpha = 0.38f).compositeOver(Color.Black),
            disabledContentColor = Color.Black,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("storySend"),
        shapes = ButtonDefaults.shapes(),
    ) {
        if (sending) {
            val share = progress?.coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (share != null) {
                    CircularWavyProgressIndicator(progress = { share }, modifier = Modifier.size(24.dp).testTag("storySendProgress"), color = Color.Black)
                } else {
                    LoadingIndicator(Modifier.size(24.dp).testTag("storySendProgress"), color = Color.Black)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        share != null -> stringResource(if (editing) R.string.stories_edit_saving_progress else R.string.stories_post_sending_progress, (share * 100).toInt())
                        else -> stringResource(if (editing) R.string.stories_edit_saving else R.string.stories_post_sending)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            Icon(painterResource(if (editing) R.drawable.ic_edit else R.drawable.ic_send), null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (editing) R.string.stories_edit_save else R.string.stories_post_send), style = MaterialTheme.typography.titleMedium)
        }
    }
}
