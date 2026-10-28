package cn.ellerotta.core.ui.components.chat

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import cn.ellerotta.core.model.ARTICLE_PREVIEW_MAX
import cn.ellerotta.core.model.ArticleLabel
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.preview
import cn.ellerotta.core.ui.R

@Composable
fun articlePreview(article: ArticleMessage, max: Int = ARTICLE_PREVIEW_MAX): String {
    val labels = ArticleLabel.entries.associateWith { stringResource(articleLabel(it)) }
    return remember(article, max, labels) { article.preview(labels::getValue, max) }
}

@Composable
internal fun messagePreview(message: MessageItem): String = message.article?.let { articlePreview(it) } ?: message.text.ifEmpty { message.poll?.question?.text.orEmpty() }

@StringRes
fun articleLabel(label: ArticleLabel): Int = when (label) {
    ArticleLabel.Photo -> R.string.media_photo
    ArticleLabel.Video -> R.string.media_video
    ArticleLabel.Collage -> R.string.chat_article_collage
    ArticleLabel.Slideshow -> R.string.chat_article_slideshow
    ArticleLabel.Formula -> R.string.chat_article_formula
    ArticleLabel.Table -> R.string.chat_article_table
    ArticleLabel.Map -> R.string.chat_article_map
    ArticleLabel.Audio -> R.string.chat_article_audio
    ArticleLabel.File -> R.string.media_file
    ArticleLabel.Unsupported -> R.string.chat_article_unsupported
}
