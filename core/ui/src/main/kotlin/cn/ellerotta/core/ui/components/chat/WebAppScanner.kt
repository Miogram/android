package cn.ellerotta.core.ui.components.chat

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.ellerotta.core.ui.R
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executor
import kotlin.math.max

@Composable
internal fun WebAppScannerDialog(scanner: WebAppScanner, onText: (String) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("webAppScanner")) {
            QrCameraPreview(onText, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).size(SCANNER_FRAME.dp).border(2.dp, Color.White, RoundedCornerShape(24.dp)))
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("webAppScannerClose"), shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.web_app_close), tint = Color.White)
                    }
                    Text(stringResource(R.string.web_app_scan_qr), color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
                scanner.text?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
        }
    }
}

@Composable
private fun QrCameraPreview(onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val text by rememberUpdatedState(onText)
    AndroidView(
        factory = { context -> TextureView(context).apply { tag = QrCamera(context, this) { text(it) }.also(QrCamera::start) } },
        onRelease = { (it.tag as? QrCamera)?.stop() },
        modifier = modifier,
    )
}

private class QrCamera(private val context: Context, private val view: TextureView, private val onText: (String) -> Unit) : TextureView.SurfaceTextureListener {
    private val thread = HandlerThread("qr-scanner").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executor { handler.post(it) }
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var size: Size? = null
    private var sensor = SENSOR_DEFAULT
    private var luminance = ByteArray(0)
    private var last: String? = null
    private var lastAt = 0L

    @Volatile private var stopped = false

    fun start() {
        view.surfaceTextureListener = this
        view.surfaceTexture?.takeIf { view.isAvailable }?.let { open(it) }
    }

    fun stop() {
        stopped = true
        view.surfaceTextureListener = null
        handler.post {
            session?.close()
            camera?.close()
            reader?.close()
            thread.quitSafely()
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = open(surface)

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        size?.let(::transform)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    @SuppressLint("MissingPermission")
    private fun open(texture: SurfaceTexture) {
        if (stopped || camera != null) return
        val manager = context.getSystemService(CameraManager::class.java) ?: return
        try {
            val ids = manager.cameraIdList
            val id = ids.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } ?: ids.firstOrNull() ?: return
            val characteristics = manager.getCameraCharacteristics(id)
            val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.YUV_420_888) ?: return
            val chosen = sizes.filter { it.width <= MAX_WIDTH && it.height <= MAX_HEIGHT }.maxByOrNull { it.width * it.height } ?: sizes.minByOrNull { it.width * it.height } ?: return
            size = chosen
            sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: SENSOR_DEFAULT
            texture.setDefaultBufferSize(chosen.width, chosen.height)
            transform(chosen)
            val frames = ImageReader.newInstance(chosen.width, chosen.height, ImageFormat.YUV_420_888, 2).also { reader = it }
            frames.setOnImageAvailableListener(::decode, handler)
            manager.openCamera(id, executor, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    camera = device
                    if (stopped) device.close() else configure(device, Surface(texture), frames.surface)
                }

                override fun onDisconnected(device: CameraDevice) = device.close()

                override fun onError(device: CameraDevice, error: Int) = device.close()
            })
        } catch (_: Exception) {
        }
    }

    private fun configure(device: CameraDevice, preview: Surface, frames: Surface) {
        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(preview)
            addTarget(frames)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }.build()
        val outputs = listOf(OutputConfiguration(preview), OutputConfiguration(frames))
        device.createCaptureSession(
            SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, executor, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    session = configured
                    if (stopped) configured.close() else runCatching { configured.setRepeatingRequest(request, null, handler) }
                }

                override fun onConfigureFailed(configured: CameraCaptureSession) = Unit
            }),
        )
    }

    private fun decode(frames: ImageReader) {
        val image = frames.acquireLatestImage() ?: return
        val text = try {
            val plane = image.planes[0]
            val width = image.width
            val height = image.height
            if (luminance.size != width * height) luminance = ByteArray(width * height)
            val buffer = plane.buffer
            if (plane.rowStride == width) {
                buffer.get(luminance, 0, width * height)
            } else {
                for (row in 0 until height) {
                    buffer.position(row * plane.rowStride)
                    buffer.get(luminance, row * width, width)
                }
            }
            decodeQr(luminance, width, height)
        } catch (_: Exception) {
            null
        } finally {
            image.close()
        }
        val now = SystemClock.uptimeMillis()
        if (text == null || (text == last && now - lastAt < REPEAT_MS)) return
        last = text
        lastAt = now
        main.post { if (!stopped) onText(text) }
    }

    private fun transform(size: Size) {
        val width = view.width.toFloat()
        val height = view.height.toFloat()
        if (width == 0f || height == 0f) return
        val rotation = view.display?.rotation ?: Surface.ROTATION_0
        val matrix = Matrix()
        val cx = width / 2
        val cy = height / 2
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            val viewRect = RectF(0f, 0f, width, height)
            val bufferRect = RectF(0f, 0f, size.height.toFloat(), size.width.toFloat()).apply { offset(cx - centerX(), cy - centerY()) }
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(height / size.height, width / size.width)
            matrix.postScale(scale, scale, cx, cy)
            matrix.postRotate(90f * (rotation - 2), cx, cy)
        } else {
            val upright = if (sensor % 180 != 0) Size(size.height, size.width) else size
            val scale = max(width / upright.width, height / upright.height)
            matrix.setScale(upright.width * scale / width, upright.height * scale / height, cx, cy)
            if (rotation == Surface.ROTATION_180) matrix.postRotate(180f, cx, cy)
        }
        main.post { if (!stopped) view.setTransform(matrix) }
    }

    private companion object {
        const val MAX_WIDTH = 1280
        const val MAX_HEIGHT = 720
        const val REPEAT_MS = 2_000L
        const val SENSOR_DEFAULT = 90
    }
}

internal fun decodeQr(luminance: ByteArray, width: Int, height: Int): String? {
    val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
    return try {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), DECODE_HINTS).text
    } catch (_: ReaderException) {
        null
    }
}

private val DECODE_HINTS = mapOf(DecodeHintType.CHARACTER_SET to "UTF-8")

private const val SCANNER_FRAME = 240
