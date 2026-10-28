package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cn.ellerotta.core.ui.components.auth.QRCodeScreen
import cn.ellerotta.core.ui.components.auth.qrModules
import cn.ellerotta.core.ui.theme.MioTheme
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.Decoder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QrTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun modulesDecodeBackToTheLink() {
        val url = "tg://login?token=AQIDBAUGBwgJCgsMDQ4PEA"
        val modules = qrModules(url)
        val size = modules.size
        assertTrue(modules.all { it.size == size })
        assertTrue((0..6).all { modules[0][it] && modules[6][it] && modules[it][0] && modules[it][6] })
        assertTrue((1..5).all { !modules[1][it] && !modules[5][it] })
        assertTrue((2..4).all { x -> (2..4).all { y -> modules[y][x] } })
        val bits = BitMatrix(size).apply { for (y in 0 until size) for (x in 0 until size) if (modules[y][x]) set(x, y) }
        assertEquals(url, Decoder().decode(bits).text)
    }

    @Test
    fun screenShowsTheCodeAndTheWayBack() {
        var url by mutableStateOf<String?>(null)
        var phone = 0
        compose.setContent { MioTheme { QRCodeScreen(url = url, onBack = {}, onSwitchToPhone = { phone++ }) } }
        compose.onNodeWithTag("qrLoading").assertIsDisplayed()
        url = "tg://login?token=AAAA"
        compose.onNodeWithContentDescription("QR code for logging in").assertIsDisplayed()
        compose.onNodeWithText("Open Telegram on your phone").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("usePhone").performScrollTo().performClick()
        assertEquals(1, phone)
    }
}
