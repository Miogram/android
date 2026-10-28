package cn.ellerotta.core.ui.components.profile

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.CropRect
import cn.ellerotta.core.model.SquareCrop
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.TopBarState

@Composable
fun AvatarCropScreen(photo: ByteArray?, unreadable: Boolean, busy: Boolean, onBack: () -> Unit, onApply: (CropRect) -> Unit) {
    val bitmap = remember(photo) { photo?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap() }
    var crop by remember(bitmap) { mutableStateOf(bitmap?.let { SquareCrop(it.width, it.height) }) }
    Page(stringResource(R.string.profile_crop_title), TopBarState.Shown, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val current = crop
            if (bitmap == null || current == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                    if (unreadable || photo != null) {
                        Text(stringResource(R.string.error_photo_unreadable), Modifier.testTag("cropUnreadable"), textAlign = TextAlign.Center)
                    } else {
                        CircularWavyProgressIndicator()
                    }
                }
            } else {
                var viewSize by remember { mutableStateOf(0f) }
                val hint = stringResource(R.string.profile_crop_hint)
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
                val zoomLabel = stringResource(R.string.profile_crop_zoom)
                val overlay = Color.Black.copy(alpha = OVERLAY)
                val ring = Color.White
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .aspectRatio(1f)
                        .onSizeChanged { viewSize = it.width.toFloat() }
                        .pointerInput(bitmap) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val state = crop ?: return@detectTransformGestures
                                crop = state.zoomed(state.zoom * zoom).panned(pan.x, pan.y, viewSize)
                            }
                        }
                        .semantics { contentDescription = hint }
                        .testTag("cropArea"),
                ) {
                    val rect = current.rect()
                    drawImage(bitmap, srcOffset = IntOffset(rect.left, rect.top), srcSize = IntSize(rect.side, rect.side), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                    val hole = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        addOval(Rect(Offset.Zero, Size(size.width, size.height)))
                    }
                    drawPath(hole, overlay)
                    drawCircle(ring, radius = size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                }
                Slider(
                    value = current.zoom,
                    onValueChange = { crop = current.zoomed(it) },
                    valueRange = 1f..SquareCrop.MAX_ZOOM,
                    modifier = Modifier.padding(top = 16.dp).semantics { contentDescription = zoomLabel }.testTag("cropZoom"),
                )
                Text(stringResource(R.string.profile_crop_zoom), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                Button(
                    onClick = { onApply(current.rect()) },
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).heightIn(min = 48.dp).testTag("cropApply"),
                ) { Text(stringResource(R.string.profile_crop_apply)) }
            }
        }
    }
}

private const val OVERLAY = 0.6f
