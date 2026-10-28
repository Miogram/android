package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.ConfirmDialog
import cn.ellerotta.core.ui.components.common.TopBar
import cn.ellerotta.core.ui.components.common.TopBarColors
import cn.ellerotta.core.ui.components.common.TopBarPill
import cn.ellerotta.core.ui.components.common.TopBarState

class OwnPhotoActions(
    val onSetMain: (MediaItem) -> Unit,
    val onDelete: (MediaItem) -> Unit,
    val onGallery: () -> Unit,
    val onCamera: () -> Unit,
)

@Composable
fun MediaViewer(
    items: List<MessageItem>,
    initial: Int,
    downloads: Map<String, DownloadState>,
    onBack: () -> Unit,
    onPage: (Int) -> Unit,
    onSave: (MediaItem) -> Unit,
    onShare: (MediaItem) -> Unit,
    video: @Composable (media: MediaItem, active: Boolean, modifier: Modifier) -> Unit = { _, _, _ -> },
    onSaveToDownloads: ((MediaItem) -> Unit)? = null,
    own: OwnPhotoActions? = null,
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("viewer"), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(color = Color.White)
        }
        return
    }
    val pager = rememberPagerState(initialPage = initial.coerceIn(0, items.lastIndex)) { items.size }
    var deleting by remember { mutableStateOf<MediaItem?>(null) }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect(onPage) }
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("viewer")) {
        HorizontalPager(
            state = pager,
            key = { items[it].id },
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val media = items[page].media ?: return@HorizontalPager
            val active = page == pager.currentPage
            Box(Modifier.fillMaxSize().testTag("page_${items[page].id}"), contentAlignment = Alignment.Center) {
                when (media.kind) {
                    MediaKind.Video, MediaKind.Gif -> {
                        video(media, active, Modifier.fillMaxSize())
                        val saving = downloads[media.key] as? DownloadState.Progress
                        val total = saving?.total
                        if (saving != null && total != null && total > 0) {
                            LinearWavyProgressIndicator(
                                progress = { saving.downloaded.toFloat() / total },
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp).testTag("viewerDownload"),
                            )
                        }
                    }
                    else -> ZoomablePhoto(media, downloads[media.key], onTap = { chrome = !chrome }, onZoom = { if (active) zoomed = it })
                }
            }
        }
        AnimatedVisibility(chrome, enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()), exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()), modifier = Modifier.align(Alignment.TopCenter)) {
            val media = items.getOrNull(pager.currentPage)?.media
            TopBar(
                stringResource(R.string.viewer_position, pager.currentPage + 1, items.size),
                TopBarState.Shown,
                onBack,
                colors = TopBarColors.Media,
                backTag = "viewerBack",
                titleTag = "viewerPosition",
            ) {
                if (media != null) {
                    TopBarPill(R.drawable.ic_download, stringResource(R.string.viewer_save), { onSave(media) }, Modifier.testTag("viewerSave"), colors = TopBarColors.Media)
                    TopBarPill(R.drawable.ic_share, stringResource(R.string.viewer_share), { onShare(media) }, Modifier.testTag("viewerShare"), colors = TopBarColors.Media)
                    if (onSaveToDownloads != null || own != null) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            TopBarPill(R.drawable.ic_more_vert, stringResource(R.string.chat_menu), { menu = true }, Modifier.testTag("viewerMenu"), colors = TopBarColors.Media)
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (onSaveToDownloads != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_save_downloads)) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_download), null) },
                                        onClick = {
                                            menu = false
                                            onSaveToDownloads(media)
                                        },
                                        modifier = Modifier.testTag("viewerSaveDownloads"),
                                    )
                                }
                                if (own != null) {
                                    if (pager.currentPage > 0) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.viewer_set_main)) },
                                            leadingIcon = { Icon(painterResource(R.drawable.ic_account_circle), null) },
                                            onClick = {
                                                menu = false
                                                own.onSetMain(media)
                                            },
                                            modifier = Modifier.testTag("viewerSetMain"),
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.profile_edit_photo_gallery)) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_image), null) },
                                        onClick = {
                                            menu = false
                                            own.onGallery()
                                        },
                                        modifier = Modifier.testTag("viewerAddGallery"),
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.profile_edit_photo_camera)) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_videocam), null) },
                                        onClick = {
                                            menu = false
                                            own.onCamera()
                                        },
                                        modifier = Modifier.testTag("viewerAddCamera"),
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.viewer_delete), color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menu = false
                                            deleting = media
                                        },
                                        modifier = Modifier.testTag("viewerDelete"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val doomed = deleting
    if (own != null && doomed != null) {
        ConfirmDialog(
            title = stringResource(R.string.viewer_delete_title),
            text = stringResource(R.string.viewer_delete_text),
            confirm = stringResource(R.string.viewer_delete),
            tag = "confirmDeletePhoto",
            onConfirm = {
                deleting = null
                own.onDelete(doomed)
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ZoomablePhoto(media: MediaItem, state: DownloadState?, onTap: () -> Unit, onZoom: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun bounded(value: Offset, at: Float): Offset {
        val maxX = size.width * (at - 1) / 2
        val maxY = size.height * (at - 1) / 2
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }
    fun zoomTo(value: Float) {
        scale = value
        offset = bounded(offset, value)
        onZoom(value > 1f)
    }
    val zoomText = stringResource(R.string.viewer_zoom, (scale * 100).toInt())
    Box(
        Modifier
            .fillMaxSize()
            .semantics { stateDescription = zoomText }
            .pointerInput(media.key) { size = this.size }
            .pointerInput(media.key) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { zoomTo(if (scale > 1f) 1f else DOUBLE_TAP_ZOOM) },
                )
            }
            .pointerInput(media.key) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            val next = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            offset = bounded(offset + event.calculatePan(), next)
                            if (next != scale) zoomTo(next)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .testTag("zoomable_${media.key}"),
        contentAlignment = Alignment.Center,
    ) {
        StillImage(media, state, contentScale = ContentScale.Fit)
        if (state is DownloadState.Progress) {
            val total = state.total
            if (total != null && total > 0) {
                CircularWavyProgressIndicator(progress = { state.downloaded.toFloat() / total }, modifier = Modifier.padding(6.dp).size(48.dp), color = Color.White)
            } else {
                CircularWavyProgressIndicator(Modifier.padding(6.dp).size(48.dp), color = Color.White)
            }
        }
    }
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
