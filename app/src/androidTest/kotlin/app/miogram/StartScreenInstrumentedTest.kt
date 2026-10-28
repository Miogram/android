package app.miogram

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.ellerotta.core.ui.components.auth.StartScreen
import cn.ellerotta.core.ui.theme.MioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartScreenInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun startButtonCallsBack() {
        var clicks = 0
        compose.setContent { MioTheme { StartScreen(testMode = true, busy = false, onStartAuth = { clicks++ }, serverSheet = {}) } }
        compose.onNodeWithTag("start").assertIsDisplayed().performClick()
        assertEquals(1, clicks)
    }
}
