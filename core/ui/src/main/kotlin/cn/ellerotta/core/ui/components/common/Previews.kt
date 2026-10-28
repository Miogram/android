package cn.ellerotta.core.ui.components.common

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.tooling.preview.Preview
import cn.ellerotta.core.ui.theme.MioTheme
import java.io.ByteArrayOutputStream

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@Preview(name = "light", group = "light")
@Preview(name = "dark", group = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "large font", group = "large font", fontScale = 2f)
@Preview(name = "320 dp", group = "320 dp", widthDp = 320)
internal annotation class MioPreviews

internal val LocalPreviewScheme = staticCompositionLocalOf<((dark: Boolean) -> ColorScheme)?> { null }

@Composable
internal fun PreviewTheme(content: @Composable () -> Unit) {
    val override = LocalPreviewScheme.current
    val dark = isSystemInDarkTheme()
    MioTheme(useDynamicColor = false) {
        val surface = @Composable { Surface(color = MaterialTheme.colorScheme.surface, content = content) }
        if (override == null) surface() else MaterialTheme(colorScheme = override(dark), content = surface)
    }
}

@Composable
internal fun PreviewSheetTheme(content: @Composable () -> Unit) {
    PreviewTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, content = content)
    }
}

internal fun previewPhoto(
    width: Int = 64,
    height: Int = width,
    start: Int = 0xFF5B8DEF.toInt(),
    end: Int = 0xFFE86A9A.toInt(),
): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val paint = Paint().apply {
        shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), start, end, Shader.TileMode.CLAMP)
    }
    Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
}
