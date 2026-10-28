package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ArticleBlock
import cn.ellerotta.core.model.ArticleMediaKind
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.ArticleRow
import cn.ellerotta.core.model.ArticleUpload
import cn.ellerotta.core.ui.R

private val ArticleBlock.mediaKind: ArticleMediaKind?
    get() = when (this) {
        is ArticleBlock.Photo -> ArticleMediaKind.Photo
        is ArticleBlock.Video -> ArticleMediaKind.Video
        is ArticleBlock.Audio -> ArticleMediaKind.Audio
        is ArticleBlock.Document -> ArticleMediaKind.File
        else -> null
    }

internal fun mediaIcon(kind: ArticleMediaKind?): Int = when (kind) {
    ArticleMediaKind.Photo -> R.drawable.ic_image
    ArticleMediaKind.Video -> R.drawable.ic_movie
    ArticleMediaKind.Audio -> R.drawable.ic_music_note
    ArticleMediaKind.File, null -> R.drawable.ic_description
}

internal fun mediaLabel(kind: ArticleMediaKind): Int = when (kind) {
    ArticleMediaKind.Photo -> R.string.chat_article_media_photo
    ArticleMediaKind.Video -> R.string.chat_article_media_video
    ArticleMediaKind.Audio -> R.string.chat_article_media_audio
    ArticleMediaKind.File -> R.string.chat_article_media_file
}

@Composable
internal fun EditorMedia(row: ArticleRow, upload: ArticleUpload?, onRetry: () -> Unit, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainerHigh, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().testTag("articleMedia_${row.id}")) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(mediaIcon(upload?.kind ?: row.block?.mediaKind)), null, tint = colors.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                val name = upload?.name ?: row.block?.let { articlePreview(ArticleMessage(listOf(it))) }.orEmpty()
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when {
                    upload == null || upload.done -> Unit
                    upload.failed -> Text(
                        stringResource(R.string.chat_article_upload_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                        modifier = Modifier.testTag("articleUploadFailed_${row.id}"),
                    )
                    else -> {
                        val fraction = if (upload.total > 0) (upload.uploaded.toFloat() / upload.total).coerceIn(0f, 1f) else 0f
                        LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("articleUploadProgress_${row.id}"))
                    }
                }
            }
            if (upload?.failed == true) {
                IconButton(onClick = onRetry, modifier = Modifier.testTag("articleUploadRetry_${row.id}"), shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.chat_article_upload_retry))
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.testTag("articleMediaRemove_${row.id}"), shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.chat_article_remove))
            }
        }
    }
}
