package cn.ellerotta.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import cn.ellerotta.core.ui.theme.MioTheme
import cn.ellerotta.core.ui.theme.brandScheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dynamicColourIsTheSystemsSchemeAndOffIsTheBrands() {
        val drawn = mutableMapOf<Pair<Boolean, Boolean>, String>()
        val system = mutableMapOf<Boolean, String>()
        compose.setContent {
            val context = LocalContext.current
            for (dark in listOf(false, true)) {
                system[dark] = "${if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)}"
                for (dynamic in listOf(false, true)) {
                    MioTheme(darkTheme = dark, useDynamicColor = dynamic) { drawn[dark to dynamic] = "${MaterialTheme.colorScheme}" }
                }
            }
        }
        compose.waitForIdle()
        for (dark in listOf(false, true)) {
            assertEquals(system.getValue(dark), drawn.getValue(dark to true), "dark=$dark dynamic")
            assertEquals("${brandScheme(dark)}", drawn.getValue(dark to false), "dark=$dark brand")
        }
    }
}
