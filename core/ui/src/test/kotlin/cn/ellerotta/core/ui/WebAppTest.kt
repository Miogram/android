package cn.ellerotta.core.ui

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cn.ellerotta.core.model.OpenWebApp
import cn.ellerotta.core.ui.components.chat.WEB_APP_PROXY
import cn.ellerotta.core.ui.components.chat.WebAppMainButton
import cn.ellerotta.core.ui.components.chat.WebAppPopup
import cn.ellerotta.core.ui.components.chat.WebAppPopupButton
import cn.ellerotta.core.ui.components.chat.WebAppScreen
import cn.ellerotta.core.ui.components.chat.WebAppSession
import cn.ellerotta.core.ui.components.chat.colorOf
import cn.ellerotta.core.ui.components.chat.WebAppAsk
import cn.ellerotta.core.ui.components.chat.WebAppDevice
import cn.ellerotta.core.ui.components.chat.WebAppInsets
import cn.ellerotta.core.ui.components.chat.WebAppLocation
import cn.ellerotta.core.ui.components.chat.WebAppPermission
import cn.ellerotta.core.ui.components.chat.WebAppQuestion
import cn.ellerotta.core.ui.components.chat.WebAppScanner
import cn.ellerotta.core.ui.components.chat.decodeQr
import cn.ellerotta.core.ui.components.auth.qrModules
import cn.ellerotta.core.model.WebAppMode
import kotlin.test.assertFalse
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp")
class WebAppTest {
    @get:Rule
    val compose = createComposeRule()

    private class Calls {
        var closed = 0
        val data = mutableListOf<String>()
        val links = mutableListOf<String>()
        val sent = mutableListOf<Pair<String, String?>>()
    }

    private fun session(sendsData: Boolean = false, calls: Calls = Calls()) =
        WebAppSession(sendsData, { calls.closed++ }, { calls.data += it }, { calls.links += it }).apply { send = { event, params -> calls.sent += event to params?.toString() } }

    @Test
    fun eventsSetUpTheScreenAndAreAnswered() {
        val calls = Calls()
        val session = session(calls = calls)
        session.theme = JSONObject().put("bg_color", "#ffffff")
        session.viewportHeight = 700

        session.handle("web_app_request_theme", null)
        assertEquals("theme_changed" to """{"theme_params":{"bg_color":"#ffffff"}}""", calls.sent.last())
        session.handle("web_app_request_viewport", "")
        assertEquals("viewport_changed" to """{"height":700,"is_expanded":true,"is_state_stable":true}""", calls.sent.last())

        session.handle("web_app_setup_main_button", """{"is_visible":true,"is_active":false,"text":" Pay ","color":"#ff0000","text_color":"#fff"}""")
        assertEquals(WebAppMainButton("Pay", active = false, color = Color(0xFFFF0000), textColor = Color.White), session.mainButton)
        session.handle("web_app_setup_main_button", """{"is_visible":false,"text":"Pay"}""")
        assertNull(session.mainButton)
        session.handle("web_app_setup_back_button", """{"is_visible":true}""")
        assertTrue(session.backVisible)
        session.handle("web_app_setup_closing_behavior", """{"need_confirmation":true}""")
        assertTrue(session.confirmsClosing)
        session.handle("web_app_set_header_color", """{"color":"#00ff00"}""")
        assertEquals(Color(0xFF00FF00), session.headerColor)

        session.handle("web_app_open_popup", """{"message":"Sure?","buttons":[{"id":"y","type":"ok"},{"id":"n","type":"destructive","text":"No"}]}""")
        assertEquals(WebAppPopup(null, "Sure?", listOf(WebAppPopupButton("y", "ok", null), WebAppPopupButton("n", "destructive", "No"))), session.popup)
        session.handle("web_app_open_popup", """{"message":"Again","buttons":[{"id":"a","type":"ok"}]}""")
        assertEquals("Sure?", session.popup?.message)
        session.closePopup("n")
        assertNull(session.popup)
        assertEquals("popup_closed" to """{"button_id":"n"}""", calls.sent.last())
        session.handle("web_app_open_popup", """{"message":"Broken","buttons":[{"id":"x","type":"default"}]}""")
        assertNull(session.popup)

        session.handle("web_app_open_link", """{"url":"https://core.telegram.org"}""")
        session.handle("web_app_open_link", """{"url":"javascript:alert(1)"}""")
        session.handle("web_app_open_tg_link", """{"path_full":"/kotlinnews"}""")
        assertEquals(listOf("https://core.telegram.org", "https://t.me/kotlinnews"), calls.links)
        assertEquals(1, calls.closed)

        session.handle("web_app_request_write_access", null)
        session.answer(true)
        assertEquals("write_access_requested" to """{"status":"cancelled"}""", calls.sent.last())
        session.handle("web_app_data_send", """{"data":"x"}""")
        assertTrue(calls.data.isEmpty())
        session.handle("web_app_close", null)
        assertEquals(2, calls.closed)
        session.handle("web_app_unknown", "{broken")
    }

    @Test
    fun aReplyKeyboardAppSendsItsDataOnce() {
        val calls = Calls()
        val session = session(sendsData = true, calls = calls)
        session.handle("web_app_data_send", """{"data":""}""")
        session.handle("web_app_data_send", """{"data":"first"}""")
        session.handle("web_app_data_send", """{"data":"second"}""")
        assertEquals(listOf("first"), calls.data)
    }

    @Test
    fun anInlineModeAppSwitchesToAnInlineQuery() {
        val switches = mutableListOf<Pair<String, List<String>>>()
        val inline = WebAppSession(false, {}, {}, {}, onSwitchInline = { query, types -> switches += query to types })
        inline.handle("web_app_switch_inline_query", """{"query":"dogs","chat_types":[]}""")
        inline.handle("web_app_switch_inline_query", """{"query":"","chat_types":["users","groups","planets"]}""")
        assertEquals(listOf("dogs" to emptyList(), "" to listOf("users", "groups")), switches)
        session().handle("web_app_switch_inline_query", """{"query":"dogs","chat_types":[]}""")
        assertEquals(2, switches.size)
    }

    @Test
    fun colorsAreHexOnly() {
        assertEquals(Color(0xFF112233), colorOf("#112233"))
        assertEquals(Color(0xFFAABBCC), colorOf("#abc"))
        assertNull(colorOf("112233"))
        assertNull(colorOf("#12345"))
        assertNull(colorOf(null))
    }

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
        else -> null
    }

    @Test
    fun theScreenLoadsThePageAndClosesAfterAsking() {
        var closed = 0
        val links = mutableListOf<String>()
        compose.setContent {
            MioTheme {
                WebAppScreen(OpenWebApp("u504", "Helper", "https://example.com/app#tgWebAppData=x", queryId = 1), onClose = { closed++ }, onData = {}, onOpenLink = { links += it })
            }
        }
        compose.onNodeWithTag("webAppTitle").assertExists()
        compose.onNodeWithText("Helper").assertExists()
        val page = findWebView(ShadowDialog.getLatestDialog().window!!.decorView)!!
        assertEquals("https://example.com/app#tgWebAppData=x", shadowOf(page).lastLoadedUrl)
        assertTrue(page.settings.javaScriptEnabled)

        val proxy = shadowOf(page).getJavascriptInterface(WEB_APP_PROXY)
        val post = proxy.javaClass.getMethod("postEvent", String::class.java, String::class.java).apply { isAccessible = true }
        fun post(event: String, data: String) {
            compose.runOnUiThread { post.invoke(proxy, event, data) }
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
        }
        post("web_app_setup_back_button", """{"is_visible":true}""")
        compose.onNodeWithTag("webAppClose").performClick()
        compose.waitForIdle()
        assertTrue(shadowOf(page).lastEvaluatedJavascript.contains("\"back_button_pressed\""))
        assertEquals(0, closed)

        post("web_app_setup_back_button", """{"is_visible":false}""")
        post("web_app_setup_closing_behavior", """{"need_confirmation":true}""")
        compose.onNodeWithTag("webAppClose").performClick()
        compose.onNodeWithTag("webAppConfirmClose").assertExists()
        compose.onNodeWithTag("webAppCloseAnyway").performClick()
        assertEquals(1, closed)

        post("web_app_open_popup", """{"title":"Order","message":"Pay now?","buttons":[{"id":"pay","type":"default","text":"Pay"},{"id":"no","type":"cancel"}]}""")
        compose.onNodeWithText("Pay now?").assertExists()
        compose.onNodeWithTag("webAppPopup_pay").performClick()
        compose.waitForIdle()
        assertTrue(shadowOf(page).lastEvaluatedJavascript.contains("{\"button_id\":\"pay\"}"))
        assertTrue(links.isEmpty())
    }

    private class Device : WebAppDevice {
        var canWrite = false
        var allowed = 0
        var phones = 0
        var camera = true
        var clipboardAllowed = true
        var clipboard: String? = "copied"
        var biometry: String? = "finger"
        var confirms = true
        var locationOn = true
        var systemLocation = true
        val permissions = HashMap<Pair<String, WebAppPermission>, Boolean>()
        val stores = HashMap<Pair<String, Boolean>, Map<String, String>>()
        var token: String? = null
        var secureWorks = true

        override suspend fun canWrite(bot: String) = canWrite
        override suspend fun allowWrite(bot: String) = true.also { allowed++ }
        override suspend fun sharePhone(bot: String) = true.also { phones++ }
        override suspend fun invokeCustomMethod(bot: String, method: String, params: String) =
            if (method == "echo") Result.success(params) else Result.failure(IllegalStateException("METHOD_INVALID"))
        override suspend fun clipboardAllowed(bot: String) = clipboardAllowed
        override suspend fun clipboardText() = clipboard
        override suspend fun storage(bot: String, secure: Boolean): Map<String, String> {
            if (secure && !secureWorks) throw UnsupportedOperationException()
            return stores[bot to secure].orEmpty()
        }
        override suspend fun saveStorage(bot: String, secure: Boolean, values: Map<String, String>) {
            stores[bot to secure] = values
        }
        override suspend fun permission(bot: String, kind: WebAppPermission) = permissions[bot to kind]
        override suspend fun setPermission(bot: String, kind: WebAppPermission, granted: Boolean) {
            permissions[bot to kind] = granted
        }
        override fun biometryType() = biometry
        override suspend fun deviceId() = "device-1"
        override suspend fun biometryToken(bot: String) = token
        override suspend fun saveBiometryToken(bot: String, token: String?) {
            this.token = token
        }
        override suspend fun authenticate(title: String, reason: String?) = confirms
        override fun locationAvailable() = locationOn
        override suspend fun locationPermission() = systemLocation
        override suspend fun location() = WebAppLocation(55.75, 37.62, altitude = 150.0, horizontalAccuracy = 12.5)
        override suspend fun cameraPermission() = camera
    }

    private fun deviceSession(device: Device, calls: Calls, mode: WebAppMode = WebAppMode.Default, clock: () -> Long = { 0L }) =
        WebAppSession(false, { calls.closed++ }, {}, {}, bot = "u504", title = "Helper", mode = mode, clock = clock).apply {
            this.device = device
            send = { event, params -> calls.sent += event to params?.toString() }
        }

    private fun Calls.last(event: String): JSONObject = JSONObject(sent.last { it.first == event }.second ?: "{}")

    @Test
    fun writeAccessAndThePhoneAreAskedForAndAnswered() {
        val calls = Calls()
        val device = Device()
        val session = deviceSession(device, calls)

        device.canWrite = true
        session.handle("web_app_request_write_access", null)
        assertEquals("allowed", calls.last("write_access_requested").getString("status"))
        assertNull(session.question)

        device.canWrite = false
        session.handle("web_app_request_write_access", null)
        assertEquals(WebAppQuestion(WebAppAsk.WriteAccess), session.question)
        session.handle("web_app_request_write_access", null)
        session.answer(true)
        assertNull(session.question)
        assertEquals(1, device.allowed)
        assertEquals("allowed", calls.last("write_access_requested").getString("status"))
        session.handle("web_app_request_write_access", null)
        session.answer(false)
        assertEquals(1, device.allowed)
        assertEquals("cancelled", calls.last("write_access_requested").getString("status"))
        assertEquals(3, calls.sent.count { it.first == "write_access_requested" })

        session.handle("web_app_request_phone", null)
        assertEquals(WebAppAsk.Phone, session.question?.ask)
        session.answer(true)
        assertEquals(1, device.phones)
        assertEquals("sent", calls.last("phone_requested").getString("status"))
        session.handle("web_app_request_phone", null)
        session.answer(false)
        assertEquals("cancelled", calls.last("phone_requested").getString("status"))
        assertEquals(1, device.phones)

        session.handle("web_app_invoke_custom_method", """{"req_id":"r1","method":"echo","params":{"a":1}}""")
        assertEquals("""{"req_id":"r1","result":{"a":1}}""", calls.sent.last().second)
        session.handle("web_app_invoke_custom_method", """{"req_id":"r2","method":"nope","params":{}}""")
        assertEquals("""{"req_id":"r2","error":"METHOD_INVALID"}""", calls.sent.last().second)
        val bare = session()
        val bareCalls = Calls()
        bare.send = { event, params -> bareCalls.sent += event to params?.toString() }
        bare.handle("web_app_request_phone", null)
        bare.answer(true)
        assertEquals("phone_requested" to """{"status":"cancelled"}""", bareCalls.sent.last())
    }

    @Test
    fun theClipboardNeedsATouchAndAnAllowedBot() {
        val calls = Calls()
        val device = Device()
        var now = 0L
        val session = deviceSession(device, calls) { now }

        session.handle("web_app_read_text_from_clipboard", """{"req_id":"c1"}""")
        assertEquals("""{"req_id":"c1"}""", calls.sent.last().second)
        session.interacted()
        now = 5_000
        session.handle("web_app_read_text_from_clipboard", """{"req_id":"c2"}""")
        assertEquals("""{"req_id":"c2","data":"copied"}""", calls.sent.last().second)
        now = 10_001
        session.handle("web_app_read_text_from_clipboard", """{"req_id":"c3"}""")
        assertEquals("""{"req_id":"c3"}""", calls.sent.last().second)
        session.interacted()
        device.clipboardAllowed = false
        session.handle("web_app_read_text_from_clipboard", """{"req_id":"c4"}""")
        assertEquals("clipboard_text_received" to """{"req_id":"c4"}""", calls.sent.last())
        val sent = calls.sent.size
        session.handle("web_app_read_text_from_clipboard", "{}")
        assertEquals(sent, calls.sent.size)
    }

    @Test
    fun deviceAndSecureStorageKeepKeysWithinTheirLimits() {
        val calls = Calls()
        val device = Device()
        val session = deviceSession(device, calls)

        session.handle("web_app_device_storage_save_key", """{"req_id":"1","key":"level","value":"7"}""")
        assertEquals("device_storage_key_saved" to """{"req_id":"1"}""", calls.sent.last())
        session.handle("web_app_device_storage_get_key", """{"req_id":"2","key":"level"}""")
        assertEquals("device_storage_key_received" to """{"value":"7","req_id":"2"}""", calls.sent.last())
        session.handle("web_app_device_storage_get_key", """{"req_id":"3","key":"missing"}""")
        assertEquals("device_storage_key_received" to """{"value":null,"req_id":"3"}""", calls.sent.last())
        session.handle("web_app_device_storage_save_key", """{"req_id":"4","key":"level","value":null}""")
        assertEquals(emptyMap(), device.stores["u504" to false])
        session.handle("web_app_device_storage_save_key", """{"req_id":"5","key":"","value":"x"}""")
        assertEquals("device_storage_failed" to """{"error":"KEY_INVALID","req_id":"5"}""", calls.sent.last())
        session.handle("web_app_device_storage_save_key", """{"req_id":"6","key":"big","value":"${"x".repeat(WebAppSession.DEVICE_BYTES)}"}""")
        assertEquals("device_storage_failed" to """{"error":"QUOTA_EXCEEDED","req_id":"6"}""", calls.sent.last())
        session.handle("web_app_device_storage_save_key", """{"req_id":"7","key":"a","value":"1"}""")
        session.handle("web_app_device_storage_clear", """{"req_id":"8"}""")
        assertEquals("device_storage_cleared" to """{"req_id":"8"}""", calls.sent.last())
        assertEquals(emptyMap(), device.stores["u504" to false])

        repeat(WebAppSession.SECURE_ITEMS) { i -> session.handle("web_app_secure_storage_save_key", """{"req_id":"s$i","key":"k$i","value":"v$i"}""") }
        assertEquals("secure_storage_key_saved", calls.sent.last().first)
        session.handle("web_app_secure_storage_save_key", """{"req_id":"s10","key":"k10","value":"v"}""")
        assertEquals("secure_storage_failed" to """{"error":"QUOTA_EXCEEDED","req_id":"s10"}""", calls.sent.last())
        session.handle("web_app_secure_storage_get_key", """{"req_id":"g","key":"k3"}""")
        assertEquals("secure_storage_key_received" to """{"value":"v3","can_restore":false,"req_id":"g"}""", calls.sent.last())
        session.handle("web_app_secure_storage_restore_key", """{"req_id":"r","key":"k3"}""")
        assertEquals("secure_storage_failed" to """{"error":"RESTORE_UNAVAILABLE","req_id":"r"}""", calls.sent.last())
        session.handle("web_app_secure_storage_clear", """{"req_id":"c"}""")
        assertEquals("secure_storage_cleared" to """{"req_id":"c"}""", calls.sent.last())
        device.secureWorks = false
        session.handle("web_app_secure_storage_get_key", """{"req_id":"u","key":"k3"}""")
        assertEquals("secure_storage_failed" to """{"error":"UNSUPPORTED","req_id":"u"}""", calls.sent.last())
        assertEquals(2, device.stores.size)
    }

    @Test
    fun biometricsAreAllowedOnceThenGuardTheToken() {
        val calls = Calls()
        val device = Device()
        var now = 0L
        val session = deviceSession(device, calls) { now }

        session.handle("web_app_biometry_get_info", null)
        assertEquals(
            """{"available":true,"type":"finger","access_requested":false,"access_granted":false,"token_saved":false,"device_id":"device-1"}""",
            calls.sent.last().second,
        )
        session.handle("web_app_biometry_update_token", """{"token":"secret"}""")
        assertEquals("failed", calls.last("biometry_token_updated").getString("status"))

        session.handle("web_app_biometry_request_access", """{"reason":"to sign in"}""")
        assertEquals(WebAppQuestion(WebAppAsk.Biometry, "to sign in"), session.question)
        session.answer(true)
        assertTrue(calls.last("biometry_info_received").getBoolean("access_granted"))
        session.handle("web_app_biometry_request_access", null)
        assertNull(session.question)

        session.handle("web_app_biometry_request_auth", null)
        assertEquals("""{"status":"authorized","token":""}""", calls.sent.last().second)
        session.handle("web_app_biometry_update_token", """{"token":"secret","reason":"keep it"}""")
        assertEquals("updated", calls.last("biometry_token_updated").getString("status"))
        assertEquals("secret", device.token)
        session.handle("web_app_biometry_request_auth", null)
        assertEquals("""{"status":"authorized","token":"secret"}""", calls.sent.last().second)
        device.confirms = false
        session.handle("web_app_biometry_request_auth", null)
        assertEquals("""{"status":"failed"}""", calls.sent.last().second)
        device.confirms = true
        session.handle("web_app_biometry_update_token", """{"token":""}""")
        assertEquals("removed", calls.last("biometry_token_updated").getString("status"))
        assertNull(device.token)

        session.handle("web_app_biometry_open_settings", null)
        assertNull(session.question)
        session.interacted()
        session.handle("web_app_biometry_open_settings", null)
        session.answer(false)
        assertFalse(calls.last("biometry_info_received").getBoolean("access_granted"))
        session.handle("web_app_biometry_request_auth", null)
        assertEquals("""{"status":"failed"}""", calls.sent.last().second)

        device.biometry = null
        session.handle("web_app_biometry_get_info", null)
        assertFalse(calls.last("biometry_info_received").getBoolean("available"))
    }

    @Test
    fun fullscreenSafeAreasAndACompactAppExpanding() {
        val calls = Calls()
        val session = deviceSession(Device(), calls, mode = WebAppMode.Compact)
        assertFalse(session.expanded)
        session.handle("web_app_request_viewport", null)
        assertFalse(calls.last("viewport_changed").getBoolean("is_expanded"))
        session.handle("web_app_expand", null)
        assertTrue(session.expanded)
        assertTrue(calls.last("viewport_changed").getBoolean("is_expanded"))

        session.handle("web_app_request_fullscreen", null)
        assertTrue(session.fullscreen)
        assertEquals("fullscreen_changed" to """{"is_fullscreen":true,"blur_enabled":false}""", calls.sent.last())
        session.handle("web_app_request_fullscreen", null)
        assertEquals("fullscreen_failed" to """{"error":"ALREADY_FULLSCREEN"}""", calls.sent.last())

        session.updateSafeAreas(WebAppInsets(top = 24, bottom = 48), WebAppInsets(top = 64))
        assertEquals("safe_area_changed" to """{"top":24,"bottom":48,"left":0,"right":0}""", calls.sent[calls.sent.size - 2])
        assertEquals("content_safe_area_changed" to """{"top":64,"bottom":0,"left":0,"right":0}""", calls.sent.last())
        val sent = calls.sent.size
        session.updateSafeAreas(WebAppInsets(top = 24, bottom = 48), WebAppInsets(top = 64))
        assertEquals(sent, calls.sent.size)
        session.handle("web_app_request_content_safe_area", null)
        assertEquals("content_safe_area_changed" to """{"top":64,"bottom":0,"left":0,"right":0}""", calls.sent.last())

        session.handle("web_app_exit_fullscreen", null)
        assertFalse(session.fullscreen)
        assertEquals("fullscreen_changed" to """{"is_fullscreen":false}""", calls.sent.last())
        assertTrue(deviceSession(Device(), Calls(), mode = WebAppMode.Fullscreen).fullscreen)
    }

    @Test
    fun theQrScannerOpensWithTheCameraAndReportsCodes() {
        val calls = Calls()
        val device = Device()
        val session = deviceSession(device, calls)

        device.camera = false
        session.handle("web_app_open_scan_qr_popup", """{"text":"Scan the ticket"}""")
        assertNull(session.scanner)
        assertEquals("scan_qr_popup_closed" to "{}", calls.sent.last())

        device.camera = true
        session.handle("web_app_open_scan_qr_popup", """{"text":"Scan the ticket"}""")
        assertEquals(WebAppScanner("Scan the ticket"), session.scanner)
        session.scanned("https://example.com/ticket")
        assertEquals("qr_text_received" to """{"data":"https:\/\/example.com\/ticket"}""", calls.sent.last())
        session.handle("web_app_close_scan_qr_popup", null)
        assertNull(session.scanner)
        assertEquals("qr_text_received", calls.sent.last().first)
        session.handle("web_app_open_scan_qr_popup", null)
        assertEquals(WebAppScanner(null), session.scanner)
        session.closeScanner()
        assertEquals("scan_qr_popup_closed" to "{}", calls.sent.last())

        val modules = qrModules("tg://resolve?domain=helperbot")
        val scale = 4
        val side = (modules.size + 8) * scale
        val plane = ByteArray(side * side) { 0xff.toByte() }
        for (y in modules.indices) for (x in modules.indices) if (modules[y][x]) {
            for (dy in 0 until scale) for (dx in 0 until scale) plane[((y + 4) * scale + dy) * side + (x + 4) * scale + dx] = 0
        }
        assertEquals("tg://resolve?domain=helperbot", decodeQr(plane, side, side))
        assertNull(decodeQr(ByteArray(side * side) { 0xff.toByte() }, side, side))
    }

    @Test
    fun theLocationIsAskedForOncePerBot() {
        val calls = Calls()
        val device = Device()
        val session = deviceSession(device, calls)

        session.handle("web_app_check_location", null)
        assertEquals("location_checked" to """{"available":true,"access_requested":false,"access_granted":false}""", calls.sent.last())
        session.handle("web_app_request_location", null)
        assertEquals(WebAppAsk.Location, session.question?.ask)
        session.answer(true)
        val location = calls.last("location_requested")
        assertTrue(location.getBoolean("available"))
        assertEquals(55.75 to 37.62, location.getDouble("latitude") to location.getDouble("longitude"))
        assertEquals(150.0, location.getDouble("altitude"))
        assertEquals(12.5, location.getDouble("horizontal_accuracy"))
        assertTrue(location.isNull("speed"))
        session.handle("web_app_request_location", null)
        assertNull(session.question)
        assertTrue(calls.last("location_requested").getBoolean("available"))
        session.handle("web_app_check_location", null)
        assertEquals("""{"available":true,"access_requested":true,"access_granted":true}""", calls.sent.last().second)

        device.systemLocation = false
        session.handle("web_app_request_location", null)
        assertEquals("""{"available":false}""", calls.sent.last().second)
        device.locationOn = false
        session.handle("web_app_check_location", null)
        assertEquals("""{"available":false}""", calls.sent.last().second)

        device.locationOn = true
        session.interacted()
        session.handle("web_app_open_location_settings", null)
        session.answer(false)
        assertEquals("""{"available":true,"access_requested":true,"access_granted":false}""", calls.sent.last().second)
    }

    @Test
    fun theScreenAsksTheUserForThePage() {
        val device = Device()
        compose.setContent {
            MioTheme {
                WebAppScreen(OpenWebApp("u504", "Helper", "https://example.com/app"), onClose = {}, onData = {}, onOpenLink = {}, device = device)
            }
        }
        val page = findWebView(ShadowDialog.getLatestDialog().window!!.decorView)!!
        val proxy = shadowOf(page).getJavascriptInterface(WEB_APP_PROXY)
        val post = proxy.javaClass.getMethod("postEvent", String::class.java, String::class.java).apply { isAccessible = true }
        compose.runOnUiThread { post.invoke(proxy, "web_app_request_write_access", "") }
        shadowOf(Looper.getMainLooper()).idle()
        compose.onNodeWithTag("webAppQuestion").assertExists()
        compose.onNodeWithText("Helper would like to send you messages.").assertExists()
        compose.onNodeWithTag("webAppAllow").performClick()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        assertEquals(1, device.allowed)
        assertTrue(shadowOf(page).lastEvaluatedJavascript.contains("{\"status\":\"allowed\"}"))
        compose.onNodeWithTag("webAppQuestion").assertDoesNotExist()

        compose.runOnUiThread { post.invoke(proxy, "web_app_request_fullscreen", "") }
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        compose.onNodeWithTag("webAppTitle").assertDoesNotExist()
        compose.onNodeWithTag("webAppClose").assertExists()
    }
}
