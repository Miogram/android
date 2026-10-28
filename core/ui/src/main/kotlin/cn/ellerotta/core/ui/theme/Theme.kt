package cn.ellerotta.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> remember(darkTheme) { brandScheme(darkTheme) }
    }

    MaterialExpressiveTheme(
        motionScheme = MotionScheme.expressive(),
        colorScheme  = colorScheme,
        typography   = Typography,
        shapes       = Shapes,
        content      = content,
    )
}

fun brandScheme(dark: Boolean): ColorScheme = keyColourScheme(BrandKeys, dark)

val BrandKeys = KeyColours(
    primary = lchOf(0xFF1E3A6B.toInt()),
    secondary = lchOf(0xFF5B6680.toInt()),
    tertiary = lchOf(0xFFEC9AB4.toInt()),
    neutral = lchOf(0xFF76777E.toInt()),
    neutralVariant = lchOf(0xFF747683.toInt()),
)
