package cn.ellerotta.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamicColor && supportsDynamic && darkTheme ->
            dynamicDarkColorScheme(context)

        useDynamicColor && supportsDynamic && !darkTheme ->
            dynamicLightColorScheme(context)

        darkTheme  -> darkColorScheme()
        else       -> expressiveLightColorScheme()
    }

    MaterialExpressiveTheme(
        motionScheme = MotionScheme.expressive(),
        colorScheme  = colorScheme,
        typography   = Typography,
        shapes       = Shapes,
        content      = content,
    )
}
