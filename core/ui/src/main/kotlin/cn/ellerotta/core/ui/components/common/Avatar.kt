package cn.ellerotta.core.ui.components.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun Avatar(title: String, bytes: ByteArray?, size: Int = 48, seed: Long = title.hashCode().toLong(), shape: Shape = CircleShape, modifier: Modifier = Modifier) {
    val bitmap = remember(bytes) { bytes?.let(DecodedPhotos::get) }
    if (bitmap != null) {
        Image(bitmap, contentDescription = title, contentScale = ContentScale.Crop, modifier = modifier.size(size.dp).clip(shape))
    } else {
        val palette = avatarPalette(seed)
        Box(
            modifier = modifier.size(size.dp).clip(shape).background(palette.brush),
            contentAlignment = Alignment.Center,
        ) {
            val glyph = with(LocalDensity.current) { (size * 0.4f).dp.toSp() }
            Text(
                initials(title),
                color = palette.content,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = glyph, lineHeight = glyph),
            )
        }
    }
}
