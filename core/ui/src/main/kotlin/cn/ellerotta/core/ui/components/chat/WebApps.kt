package cn.ellerotta.core.ui.components.chat

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.ellerotta.core.model.OpenWebApp
import cn.ellerotta.core.model.WebAppConsent
import cn.ellerotta.core.ui.components.common.CheckRow
import androidx.compose.runtime.saveable.rememberSaveable
import cn.ellerotta.core.ui.R
import org.json.JSONArray
import org.json.JSONObject
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import cn.ellerotta.core.model.WebAppMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONTokener

internal const val WEB_APP_PROXY = "TelegramWebviewProxy"

internal data class WebAppMainButton(val text: String, val active: Boolean = true, val progress: Boolean = false, val color: Color? = null, val textColor: Color? = null)

internal data class WebAppPopup(val title: String?, val message: String, val buttons: List<WebAppPopupButton>)

internal data class WebAppPopupButton(val id: String, val type: String, val text: String?)

internal enum class WebAppAsk { WriteAccess, Phone, Biometry, Location }

internal data class WebAppQuestion(val ask: WebAppAsk, val reason: String? = null)

internal data class WebAppScanner(val text: String?)

internal data class WebAppInsets(val top: Int = 0, val bottom: Int = 0, val left: Int = 0, val right: Int = 0) {
    fun json(): JSONObject = JSONObject().put("top", top).put("bottom", bottom).put("left", left).put("right", right)
}

@Stable
internal class WebAppSession(
    private val sendsData: Boolean,
    private val onClose: () -> Unit,
    private val onData: (String) -> Unit,
    private val onOpenLink: (String) -> Unit,
    private val onHaptic: (HapticFeedbackType) -> Unit = {},
    private val onSwitchInline: ((query: String, chatTypes: List<String>) -> Unit)? = null,
    private val bot: String = "",
    private val title: String = "",
    mode: WebAppMode = WebAppMode.Default,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    var mainButton by mutableStateOf<WebAppMainButton?>(null)
        private set
    var backVisible by mutableStateOf(false)
        private set
    var confirmsClosing by mutableStateOf(false)
        private set
    var popup by mutableStateOf<WebAppPopup?>(null)
        private set
    var headerColor by mutableStateOf<Color?>(null)
        private set
    var backgroundColor by mutableStateOf<Color?>(null)
        private set

    var fullscreen by mutableStateOf(mode == WebAppMode.Fullscreen)
        private set

    var expanded by mutableStateOf(mode != WebAppMode.Compact)
        private set

    var question by mutableStateOf<WebAppQuestion?>(null)
        private set

    var scanner by mutableStateOf<WebAppScanner?>(null)
        private set

    var theme: JSONObject = JSONObject()

    var viewportHeight: Int = 0

    var send: (event: String, params: JSONObject?) -> Unit = { _, _ -> }

    var device: WebAppDevice = WebAppDevice.None

    private var dataSent = false
    private var safeArea = WebAppInsets()
    private var contentSafeArea = WebAppInsets()
    private var lastInteraction: Long? = null
    private var lastSettings: Long? = null
    private var biometryInitialized = false
    private var answering: CompletableDeferred<Boolean>? = null
    private val asking = Mutex()
    private val storing = Mutex()

    private val handling = HashSet<String>()

    fun handle(type: String, data: String?) {
        val params = data?.takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { JSONObject(it) }.getOrNull() }
        when (type) {
            "web_app_close" -> onClose()
            "web_app_request_theme" -> send("theme_changed", JSONObject().put("theme_params", theme))
            "web_app_request_viewport" -> sendViewport()
            "web_app_request_safe_area" -> send("safe_area_changed", safeArea.json())
            "web_app_request_content_safe_area" -> send("content_safe_area_changed", contentSafeArea.json())
            "web_app_expand" -> if (!expanded) {
                expanded = true
                sendViewport()
            }
            "web_app_request_fullscreen" -> if (fullscreen) {
                send("fullscreen_failed", JSONObject().put("error", "ALREADY_FULLSCREEN"))
            } else {
                fullscreen = true
                expanded = true
                send("fullscreen_changed", JSONObject().put("is_fullscreen", true).put("blur_enabled", false))
            }
            "web_app_exit_fullscreen" -> {
                fullscreen = false
                send("fullscreen_changed", JSONObject().put("is_fullscreen", false))
            }
            "web_app_setup_main_button" -> mainButton = params?.let(::mainButtonOf)
            "web_app_setup_back_button" -> backVisible = params?.optBoolean("is_visible") == true
            "web_app_setup_closing_behavior" -> confirmsClosing = params?.optBoolean("need_confirmation") == true
            "web_app_set_header_color" -> headerColor = params?.optString("color")?.let(::colorOf)
            "web_app_set_background_color" -> backgroundColor = params?.optString("color")?.let(::colorOf)
            "web_app_open_popup" -> if (popup == null) popup = params?.let(::popupOf)
            "web_app_open_link" -> params?.optString("url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let(onOpenLink)
            "web_app_open_tg_link" -> params?.optString("path_full")?.takeIf { it.startsWith("/") }?.let {
                onClose()
                onOpenLink("https://t.me$it")
            }
            "web_app_data_send" -> if (sendsData && !dataSent) params?.optString("data")?.takeIf { it.isNotEmpty() }?.let {
                dataSent = true
                onData(it)
            }
            "web_app_request_write_access" -> once(type) {
                val allowed = safely(false) { device.canWrite(bot) } || ask(WebAppAsk.WriteAccess) && safely(false) { device.allowWrite(bot) }
                send("write_access_requested", JSONObject().put("status", if (allowed) "allowed" else "cancelled"))
            }
            "web_app_request_phone" -> once(type) {
                val sent = ask(WebAppAsk.Phone) && safely(false) { device.sharePhone(bot) }
                send("phone_requested", JSONObject().put("status", if (sent) "sent" else "cancelled"))
            }
            "web_app_invoke_custom_method" -> invokeCustomMethod(params)
            "web_app_trigger_haptic_feedback" -> params?.let(::hapticOf)?.let(onHaptic)
            "web_app_switch_inline_query" -> onSwitchInline?.let { switch ->
                val types = params?.optJSONArray("chat_types") ?: JSONArray()
                switch(params?.optString("query").orEmpty(), (0 until types.length()).map { types.optString(it) }.filter { it in CHAT_TYPES })
            }
            "web_app_read_text_from_clipboard" -> readClipboard(params)
            "web_app_open_scan_qr_popup" -> if (scanner == null) once(type) {
                if (safely(false) { device.cameraPermission() }) {
                    scanner = WebAppScanner(params?.optString("text")?.takeIf { it.isNotEmpty() }?.take(SCANNER_TEXT))
                } else {
                    send("scan_qr_popup_closed", JSONObject())
                }
            }
            "web_app_close_scan_qr_popup" -> scanner = null
            "web_app_device_storage_save_key", "web_app_device_storage_get_key", "web_app_device_storage_clear",
            "web_app_secure_storage_save_key", "web_app_secure_storage_get_key", "web_app_secure_storage_clear",
            "web_app_secure_storage_restore_key" -> storage(type, params)
            "web_app_biometry_get_info" -> scope.launch { sendBiometryInfo() }
            "web_app_biometry_request_access" -> once(type) {
                if (device.biometryType() != null && safely(null) { device.permission(bot, WebAppPermission.Biometry) } == null) {
                    val granted = ask(WebAppAsk.Biometry, reasonOf(params))
                    safely(Unit) { device.setPermission(bot, WebAppPermission.Biometry, granted) }
                }
                sendBiometryInfo()
            }
            "web_app_biometry_request_auth" -> once(type) {
                val token = if (biometryAllowed() && authenticate(params)) safely(null) { device.biometryToken(bot).orEmpty() } else null
                if (token == null) {
                    send("biometry_auth_requested", JSONObject().put("status", "failed"))
                    if (!biometryInitialized) sendBiometryInfo()
                } else {
                    send("biometry_auth_requested", JSONObject().put("status", "authorized").put("token", token))
                }
            }
            "web_app_biometry_update_token" -> once(type) {
                val token = params?.optString("token").orEmpty()
                val saved = token.length <= TOKEN_LENGTH && biometryAllowed() && authenticate(params) &&
                    safely(false) { device.saveBiometryToken(bot, token.ifEmpty { null }).let { true } }
                send("biometry_token_updated", JSONObject().put("status", if (!saved) "failed" else if (token.isEmpty()) "removed" else "updated"))
                if (!saved && !biometryInitialized) sendBiometryInfo()
            }
            "web_app_biometry_open_settings" -> settings(type) {
                if (device.biometryType() != null) {
                    val granted = ask(WebAppAsk.Biometry)
                    safely(Unit) { device.setPermission(bot, WebAppPermission.Biometry, granted) }
                }
                sendBiometryInfo()
            }
            "web_app_check_location" -> scope.launch { send("location_checked", locationChecked()) }
            "web_app_request_location" -> once(type) {
                val known = if (device.locationAvailable()) safely(null) { device.permission(bot, WebAppPermission.Location) } else false
                val granted = known ?: ask(WebAppAsk.Location).also { answer -> safely(Unit) { device.setPermission(bot, WebAppPermission.Location, answer) } }
                val location = if (granted && safely(false) { device.locationPermission() }) safely(null) { device.location() } else null
                send("location_requested", location?.let(::locationJson) ?: JSONObject().put("available", false))
            }
            "web_app_open_location_settings" -> settings(type) {
                if (device.locationAvailable()) {
                    val granted = ask(WebAppAsk.Location)
                    safely(Unit) { device.setPermission(bot, WebAppPermission.Location, granted) }
                }
                send("location_checked", locationChecked())
            }
        }
    }

    fun interacted() {
        lastInteraction = clock()
    }

    fun answer(yes: Boolean) {
        answering?.complete(yes)
    }

    fun closePopup(id: String?) {
        popup = null
        send("popup_closed", JSONObject().apply { id?.let { put("button_id", it) } })
    }

    fun scanned(text: String) {
        if (scanner != null) send("qr_text_received", JSONObject().put("data", text))
    }

    fun closeScanner() {
        if (scanner == null) return
        scanner = null
        send("scan_qr_popup_closed", JSONObject())
    }

    fun updateSafeAreas(safe: WebAppInsets, content: WebAppInsets) {
        if (safe != safeArea) {
            safeArea = safe
            send("safe_area_changed", safe.json())
        }
        if (content != contentSafeArea) {
            contentSafeArea = content
            send("content_safe_area_changed", content.json())
        }
    }

    fun pressMainButton() {
        interacted()
        send("main_button_pressed", null)
    }

    fun pressBack() = send("back_button_pressed", null)

    fun sendViewport() = send("viewport_changed", JSONObject().put("height", viewportHeight).put("is_expanded", expanded).put("is_state_stable", true))

    private fun recentlyInteracted() = lastInteraction?.let { clock() - it <= INTERACTION_TTL } == true

    private fun once(type: String, block: suspend () -> Unit) {
        if (!handling.add(type)) return
        scope.launch {
            try {
                block()
            } finally {
                handling.remove(type)
            }
        }
    }

    private fun settings(type: String, block: suspend () -> Unit) {
        val now = clock()
        if (!recentlyInteracted() || lastSettings?.let { now - it < SETTINGS_INTERVAL } == true) return
        lastSettings = now
        once(type, block)
    }

    private suspend fun ask(ask: WebAppAsk, reason: String? = null): Boolean = asking.withLock {
        val answer = CompletableDeferred<Boolean>()
        answering = answer
        question = WebAppQuestion(ask, reason)
        try {
            answer.await()
        } finally {
            question = null
            answering = null
        }
    }

    private suspend fun <T> safely(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        fallback
    }

    private fun invokeCustomMethod(params: JSONObject?) {
        val id = params?.optString("req_id")?.takeIf { it.isNotEmpty() } ?: return
        val method = params.optString("method").takeIf { it.isNotEmpty() } ?: return
        val arguments = params.opt("params")?.takeIf { it != JSONObject.NULL }?.toString() ?: "{}"
        scope.launch {
            val result = safely(Result.failure(IllegalStateException())) { device.invokeCustomMethod(bot, method, arguments) }
            val answer = JSONObject().put("req_id", id)
            result.fold(
                onSuccess = { json -> answer.put("result", runCatching { JSONTokener(json).nextValue() }.getOrDefault(json)) },
                onFailure = { answer.put("error", it.message ?: "UNKNOWN_ERROR") },
            )
            send("custom_method_invoked", answer)
        }
    }

    private fun readClipboard(params: JSONObject?) {
        val id = params?.optString("req_id")?.takeIf { it.isNotEmpty() } ?: return
        val recent = recentlyInteracted()
        scope.launch {
            val text = if (recent && safely(false) { device.clipboardAllowed(bot) }) safely(null) { device.clipboardText() } else null
            send("clipboard_text_received", JSONObject().put("req_id", id).apply { text?.let { put("data", it) } })
        }
    }

    private fun storage(type: String, params: JSONObject?) {
        val id = params?.optString("req_id")?.takeIf { it.isNotEmpty() } ?: return
        val secure = type.startsWith("web_app_secure_storage_")
        val kind = if (secure) "secure_storage" else "device_storage"
        val action = type.substringAfter("_storage_")
        scope.launch {
            val (event, answer) = try {
                storing.withLock { storageAnswer(kind, secure, action, params) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "${kind}_failed" to JSONObject().put("error", if (e is UnsupportedOperationException) WebAppDevice.UNSUPPORTED else "UNKNOWN_ERROR")
            }
            send(event, answer.put("req_id", id))
        }
    }

    private suspend fun storageAnswer(kind: String, secure: Boolean, action: String, params: JSONObject): Pair<String, JSONObject> {
        val failed = "${kind}_failed"
        val key = params.optString("key")
        return when (action) {
            "save_key" -> {
                if (key.isEmpty()) return failed to JSONObject().put("error", "KEY_INVALID")
                val values = device.storage(bot, secure).toMutableMap()
                if (params.isNull("value")) values.remove(key) else values[key] = params.optString("value")
                val full = if (secure) values.size > SECURE_ITEMS else values.entries.sumOf { it.key.encodeToByteArray().size + it.value.encodeToByteArray().size } > DEVICE_BYTES
                if (full) return failed to JSONObject().put("error", "QUOTA_EXCEEDED")
                device.saveStorage(bot, secure, values)
                "${kind}_key_saved" to JSONObject()
            }
            "get_key" -> {
                if (key.isEmpty()) return failed to JSONObject().put("error", "KEY_INVALID")
                val value = device.storage(bot, secure)[key]
                "${kind}_key_received" to JSONObject().put("value", value ?: JSONObject.NULL).apply { if (secure) put("can_restore", false) }
            }
            "clear" -> {
                device.saveStorage(bot, secure, emptyMap())
                "${kind}_cleared" to JSONObject()
            }
            else -> failed to JSONObject().put("error", "RESTORE_UNAVAILABLE")
        }
    }

    private suspend fun biometryAllowed() = device.biometryType() != null && safely(null) { device.permission(bot, WebAppPermission.Biometry) } == true

    private suspend fun authenticate(params: JSONObject?) = safely(false) { device.authenticate(title, reasonOf(params)) }

    private suspend fun sendBiometryInfo() {
        biometryInitialized = true
        val type = device.biometryType()
        val granted = safely(null) { device.permission(bot, WebAppPermission.Biometry) }
        val saved = type != null && safely(null) { device.biometryToken(bot) } != null
        val info = JSONObject().put("available", type != null)
        type?.let { info.put("type", it) }
        send("biometry_info_received", info.put("access_requested", granted != null).put("access_granted", granted == true).put("token_saved", saved).put("device_id", safely("") { device.deviceId() }))
    }

    private suspend fun locationChecked(): JSONObject {
        val available = device.locationAvailable()
        val answer = JSONObject().put("available", available)
        if (available) {
            val granted = safely(null) { device.permission(bot, WebAppPermission.Location) }
            answer.put("access_requested", granted != null).put("access_granted", granted == true)
        }
        return answer
    }

    private fun locationJson(location: WebAppLocation): JSONObject = JSONObject()
        .put("available", true)
        .put("latitude", location.latitude)
        .put("longitude", location.longitude)
        .put("altitude", location.altitude ?: JSONObject.NULL)
        .put("course", location.course ?: JSONObject.NULL)
        .put("speed", location.speed ?: JSONObject.NULL)
        .put("horizontal_accuracy", location.horizontalAccuracy ?: JSONObject.NULL)
        .put("vertical_accuracy", location.verticalAccuracy ?: JSONObject.NULL)
        .put("course_accuracy", location.courseAccuracy ?: JSONObject.NULL)
        .put("speed_accuracy", location.speedAccuracy ?: JSONObject.NULL)

    private fun reasonOf(params: JSONObject?): String? = params?.optString("reason")?.trim()?.takeIf { it.isNotEmpty() }?.take(REASON_LENGTH)

    private fun mainButtonOf(params: JSONObject): WebAppMainButton? {
        val text = params.optString("text").trim()
        if (!params.optBoolean("is_visible") || text.isEmpty()) return null
        return WebAppMainButton(
            text = text.take(MAIN_BUTTON_TEXT),
            active = params.optBoolean("is_active", true),
            progress = params.optBoolean("is_progress_visible"),
            color = colorOf(params.optString("color")),
            textColor = colorOf(params.optString("text_color")),
        )
    }

    private fun popupOf(params: JSONObject): WebAppPopup? {
        val message = params.optString("message").takeIf { it.isNotEmpty() && it.length <= POPUP_MESSAGE } ?: return null
        val buttons = params.optJSONArray("buttons") ?: JSONArray()
        if (buttons.length() !in 1..3) return null
        val parsed = (0 until buttons.length()).mapNotNull { i ->
            val button = buttons.optJSONObject(i) ?: return@mapNotNull null
            val type = button.optString("type", "default")
            val text = button.optString("text").takeIf { it.isNotEmpty() }
            if (text == null && type !in setOf("ok", "close", "cancel")) return@mapNotNull null
            WebAppPopupButton(button.optString("id"), type, text)
        }
        if (parsed.size != buttons.length()) return null
        return WebAppPopup(params.optString("title").takeIf { it.isNotEmpty() }?.take(POPUP_TITLE), message, parsed)
    }

    private fun hapticOf(params: JSONObject): HapticFeedbackType? = when (params.optString("type")) {
        "impact" -> HapticFeedbackType.ContextClick
        "notification" -> if (params.optString("notification_type") == "success") HapticFeedbackType.Confirm else HapticFeedbackType.Reject
        "selection_change" -> HapticFeedbackType.SegmentTick
        else -> null
    }

    companion object {
        private const val MAIN_BUTTON_TEXT = 64
        private const val POPUP_TITLE = 64
        private const val POPUP_MESSAGE = 256
        private const val SCANNER_TEXT = 64
        private const val REASON_LENGTH = 128
        private const val TOKEN_LENGTH = 1024
        private val CHAT_TYPES = setOf("users", "bots", "groups", "channels")

        const val INTERACTION_TTL = 10_000L

        private const val SETTINGS_INTERVAL = 1_000L

        const val DEVICE_BYTES = 5 * 1024 * 1024
        const val SECURE_ITEMS = 10
    }
}

internal fun colorOf(hex: String?): Color? {
    val digits = hex?.removePrefix("#")?.takeIf { hex.startsWith("#") } ?: return null
    val full = when (digits.length) {
        3 -> digits.map { "$it$it" }.joinToString("")
        6 -> digits
        else -> return null
    }
    return full.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}

private fun Color.hex() = "#%06x".format(toArgb() and 0xFFFFFF)

internal fun webAppTheme(colors: ColorScheme): JSONObject = JSONObject()
    .put("bg_color", colors.surface.hex())
    .put("secondary_bg_color", colors.surfaceContainer.hex())
    .put("section_bg_color", colors.surfaceContainerLow.hex())
    .put("header_bg_color", colors.surfaceContainer.hex())
    .put("bottom_bar_bg_color", colors.surfaceContainer.hex())
    .put("text_color", colors.onSurface.hex())
    .put("hint_color", colors.onSurfaceVariant.hex())
    .put("subtitle_text_color", colors.onSurfaceVariant.hex())
    .put("section_header_text_color", colors.primary.hex())
    .put("section_separator_color", colors.outlineVariant.hex())
    .put("link_color", colors.primary.hex())
    .put("accent_text_color", colors.primary.hex())
    .put("button_color", colors.primary.hex())
    .put("button_text_color", colors.onPrimary.hex())
    .put("destructive_text_color", colors.error.hex())

@Composable
fun webAppThemeJson(): String {
    val colors = MaterialTheme.colorScheme
    return remember(colors) { webAppTheme(colors).toString() }
}

private class WebAppProxy(private val session: WebAppSession) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postEvent(eventType: String, eventData: String?) {
        main.post { session.handle(eventType, eventData) }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun WebAppScreen(
    app: OpenWebApp,
    onClose: () -> Unit,
    onData: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onSwitchInline: ((query: String, chatTypes: List<String>) -> Unit)? = null,
    device: WebAppDevice = WebAppDevice.None,
) {
    val haptics = LocalHapticFeedback.current
    val close by rememberUpdatedState(onClose)
    val data by rememberUpdatedState(onData)
    val link by rememberUpdatedState(onOpenLink)
    val switchInline by rememberUpdatedState(onSwitchInline)
    val scope = rememberCoroutineScope()
    val session = remember(app) {
        WebAppSession(
            app.sendsData, { close() }, { data(it) }, { link(it) }, { haptics.performHapticFeedback(it) },
            onSwitchInline = if (app.inlineBot != null) ({ query, types -> switchInline?.invoke(query, types) }) else null,
            bot = app.bot,
            title = app.title,
            mode = app.mode,
            scope = scope,
        )
    }
    session.device = device
    val colors = MaterialTheme.colorScheme
    session.theme = remember(colors) { webAppTheme(colors) }
    LaunchedEffect(session.theme) { session.send("theme_changed", JSONObject().put("theme_params", session.theme)) }
    var confirmingClose by remember(app) { mutableStateOf(false) }
    fun requestClose() = if (session.confirmsClosing) confirmingClose = true else onClose()
    Dialog(
        onDismissRequest = { if (session.backVisible) session.pressBack() else requestClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler { if (session.backVisible) session.pressBack() else requestClose() }
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val safe = with(density) {
            WebAppInsets(
                bars.getTop(this).toDp().value.toInt(),
                bars.getBottom(this).toDp().value.toInt(),
                bars.getLeft(this, direction).toDp().value.toInt(),
                bars.getRight(this, direction).toDp().value.toInt(),
            )
        }
        LaunchedEffect(session.fullscreen, safe) {
            if (session.fullscreen) session.updateSafeAreas(safe, WebAppInsets(top = FULLSCREEN_CONTROLS)) else session.updateSafeAreas(WebAppInsets(), WebAppInsets())
        }
        val background = session.backgroundColor ?: colors.surface
        val header = session.headerColor ?: colors.surfaceContainer
        Box(Modifier.fillMaxSize()) {
            if (!session.expanded) {
                Box(Modifier.fillMaxSize().background(colors.scrim.copy(alpha = SCRIM_ALPHA)).pointerInput(Unit) { detectTapGestures { requestClose() } })
            }
            val sheet = if (session.expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth().fillMaxHeight(COMPACT_HEIGHT).align(Alignment.BottomCenter)
            Column(sheet.background(background).testTag("webApp")) {
                if (!session.fullscreen) {
                    Row(
                        Modifier.fillMaxWidth().background(header).then(if (session.expanded) Modifier.statusBarsPadding() else Modifier).padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { if (session.backVisible) session.pressBack() else requestClose() }, modifier = Modifier.testTag("webAppClose"), shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(if (session.backVisible) R.drawable.ic_arrow_back else R.drawable.ic_close), stringResource(R.string.web_app_close))
                        }
                        Text(app.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("webAppTitle"))
                    }
                }
                val page = if (!session.fullscreen && session.mainButton == null) Modifier.navigationBarsPadding() else Modifier
                Box(Modifier.weight(1f).fillMaxWidth().then(page)) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                addJavascriptInterface(WebAppProxy(session), WEB_APP_PROXY)
                                session.send = { event, params ->
                                    evaluateJavascript("window.Telegram && window.Telegram.WebView && window.Telegram.WebView.receiveEvent(${JSONObject.quote(event)}, ${params ?: "null"})", null)
                                }
                                setOnTouchListener { _, event ->
                                    if (event.actionMasked == MotionEvent.ACTION_DOWN) session.interacted()
                                    false
                                }
                                addOnLayoutChangeListener { view, _, top, _, bottom, _, oldTop, _, oldBottom ->
                                    if (bottom - top != oldBottom - oldTop) {
                                        session.viewportHeight = ((bottom - top) / view.resources.displayMetrics.density).toInt()
                                        session.sendViewport()
                                    }
                                }
                                loadUrl(app.url)
                            }
                        },
                        onRelease = { it.destroy() },
                        modifier = Modifier.fillMaxSize().testTag("webAppPage"),
                    )
                    if (session.fullscreen) {
                        IconButton(
                            onClick = { if (session.backVisible) session.pressBack() else requestClose() },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = header.copy(alpha = CONTROLS_ALPHA), contentColor = colors.onSurface),
                            modifier = Modifier.statusBarsPadding().padding(8.dp).testTag("webAppClose"),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(painterResource(if (session.backVisible) R.drawable.ic_arrow_back else R.drawable.ic_close), stringResource(R.string.web_app_close))
                        }
                    }
                }
                session.mainButton?.let { button ->
                    Button(
                        onClick = session::pressMainButton,
                        enabled = button.active && !button.progress,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = button.color ?: colors.primary,
                            contentColor = button.textColor ?: colors.onPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth().background(background).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).testTag("webAppMainButton"),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        if (button.progress) LoadingIndicator(Modifier.size(24.dp), color = LocalContentColor.current) else Text(button.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        session.popup?.let { popup -> WebAppPopupDialog(popup, session::closePopup) }
        session.question?.let { question -> WebAppQuestionDialog(app.title, question, session::answer) }
        session.scanner?.let { scanner -> WebAppScannerDialog(scanner, onText = session::scanned, onClose = session::closeScanner) }
        if (confirmingClose) {
            AlertDialog(
                onDismissRequest = { confirmingClose = false },
                text = { Text(stringResource(R.string.web_app_close_text)) },
                confirmButton = {
                    TextButton(onClick = { confirmingClose = false; onClose() }, modifier = Modifier.testTag("webAppCloseAnyway")) {
                        Text(stringResource(R.string.web_app_close_anyway), color = colors.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmingClose = false }) { Text(stringResource(R.string.common_cancel)) } },
                modifier = Modifier.testTag("webAppConfirmClose"),
            )
        }
    }
}

@Composable
private fun WebAppQuestionDialog(name: String, question: WebAppQuestion, onAnswer: (Boolean) -> Unit) {
    val text = when (question.ask) {
        WebAppAsk.WriteAccess -> R.string.web_app_ask_write
        WebAppAsk.Phone -> R.string.web_app_ask_phone
        WebAppAsk.Biometry -> R.string.web_app_ask_biometry
        WebAppAsk.Location -> R.string.web_app_ask_location
    }
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text(name) },
        text = {
            Column {
                Text(stringResource(text, name))
                question.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(true) }, modifier = Modifier.testTag("webAppAllow")) {
                Text(stringResource(if (question.ask == WebAppAsk.Phone) R.string.web_app_share_phone else R.string.web_app_allow))
            }
        },
        dismissButton = { TextButton(onClick = { onAnswer(false) }, modifier = Modifier.testTag("webAppDeny")) { Text(stringResource(R.string.web_app_deny)) } },
        modifier = Modifier.testTag("webAppQuestion"),
    )
}

@Composable
fun WebAppConsentDialog(consent: WebAppConsent, onAnswer: (writeAllowed: Boolean) -> Unit, onDismiss: () -> Unit) {
    var write by rememberSaveable(consent) { mutableStateOf(true) }
    var terms by rememberSaveable(consent) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(consent.name) },
        text = {
            Column {
                Text(stringResource(if (consent.add) R.string.web_app_consent_add else R.string.web_app_consent_open, consent.name))
                if (consent.writeAccess) CheckRow(write, { write = it }, stringResource(R.string.chat_login_write, consent.name), "webAppConsentWrite")
                if (consent.terms) CheckRow(terms, { terms = it }, stringResource(R.string.web_app_consent_terms), "webAppConsentTerms")
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(consent.writeAllowed(write)) }, enabled = !consent.terms || terms, modifier = Modifier.testTag("webAppConsentOk")) {
                Text(stringResource(if (consent.add) R.string.web_app_consent_add_button else R.string.web_app_consent_open_button))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        modifier = Modifier.testTag("webAppConsent"),
    )
}

@Composable
private fun WebAppPopupDialog(popup: WebAppPopup, onClose: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onClose(null) },
        title = popup.title?.let { { Text(it) } },
        text = { Text(popup.message) },
        confirmButton = {
            val stacked = popup.buttons.size == 3 || popup.buttons.sumOf { (it.text ?: it.type).length } > POPUP_ROW
            val buttons = @Composable {
                popup.buttons.forEach { button ->
                    val text = button.text ?: stringResource(if (button.type == "cancel") R.string.common_cancel else if (button.type == "close") R.string.web_app_close else R.string.common_ok)
                    TextButton(onClick = { onClose(button.id) }, modifier = Modifier.testTag("webAppPopup_${button.id}")) {
                        Text(text, color = if (button.type == "destructive") MaterialTheme.colorScheme.error else Color.Unspecified)
                    }
                }
            }
            if (stacked) Column(horizontalAlignment = Alignment.End) { buttons() } else Row(horizontalArrangement = Arrangement.End) { buttons() }
        },
        modifier = Modifier.testTag("webAppPopup"),
    )
}

private const val POPUP_ROW = 24

private const val COMPACT_HEIGHT = 0.5f

private const val FULLSCREEN_CONTROLS = 64

private const val SCRIM_ALPHA = 0.32f
private const val CONTROLS_ALPHA = 0.8f
