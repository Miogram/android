package app.miogram

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.miogram.data.DialogsStore
import app.miogram.data.maps.MapsConfig
import app.miogram.data.maps.MapsRepository
import app.miogram.maps.GoogleMapRenderer
import app.miogram.data.SettingsStore
import app.miogram.data.telegram.GeoMapsRepository
import app.miogram.links.LinkRouter
import app.miogram.location.LiveLocationService
import app.miogram.lock.AppLock
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import app.miogram.push.ChatOnScreen
import app.miogram.push.PushNotifier
import app.miogram.ui.AppNavHost
import cn.ellerotta.core.model.ThemeMode
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.LockScreen
import cn.ellerotta.core.ui.components.maps.LocalGeoMapImages
import cn.ellerotta.core.ui.components.maps.LocalMapImages
import cn.ellerotta.core.ui.components.maps.LocalMapRenderer
import cn.ellerotta.core.ui.theme.MioTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var settings: SettingsStore

    @Inject
    lateinit var dialogs: DialogsStore

    @Inject
    lateinit var appLock: AppLock

    @Inject
    lateinit var links: LinkRouter

    @Inject
    lateinit var chatOnScreen: ChatOnScreen

    @Inject
    lateinit var maps: MapsRepository

    @Inject
    lateinit var mapsConfig: MapsConfig

    @Inject
    lateinit var geoMaps: GeoMapsRepository

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        keepSplashWhileStarting()
        setContent {
            val appearance by settings.appearance.collectAsStateWithLifecycle()
            val privacy by settings.appPrivacy.collectAsStateWithLifecycle()
            val locked by appLock.locked.collectAsStateWithLifecycle()
            val dark = when (appearance.mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            MioTheme(darkTheme = dark, useDynamicColor = appearance.dynamicColor) {
                val secure = privacy.secureWindow || navigator.current.let { it == Route.Code || it == Route.Password }
                LaunchedEffect(secure) {
                    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                val chats = navigator.current == Route.Chats
                LaunchedEffect(chats) { if (chats) askForNotifications() }
                Box(if (locked) Modifier.fillMaxSize().alpha(0f).clearAndSetSemantics {} else Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalGeoMapImages provides geoMaps,
                        LocalMapImages provides maps.takeIf { it.enabled },
                        LocalMapRenderer provides GoogleMapRenderer.takeIf { mapsConfig.sdk },
                    ) {
                        AppNavHost(navigator, APP_VERSION, onExit = ::finish)
                    }
                }
                if (locked) {
                    LaunchedEffect(Unit) { unlock() }
                    BackHandler(onBack = ::finish)
                    LockScreen(onUnlock = ::unlock)
                }
            }
        }
        openChat(intent)
        if (savedInstanceState == null) openLink(intent)
    }

    override fun onStart() {
        super.onStart()
        appLock.onForeground()
        chatOnScreen.started(true)
    }

    override fun onStop() {
        super.onStop()
        chatOnScreen.started(false)
        if (!isChangingConfigurations) appLock.onBackground()
    }

    private fun unlock() = appLock.unlock(this, getString(R.string.lock_prompt_title))

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openChat(intent)
        openLink(intent)
    }

    private fun openLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.dataString?.let(links::submit)
    }

    private fun openChat(intent: Intent?) {
        if (intent?.component?.className != PushNotifier.OPEN_CHAT || intent.component?.packageName != packageName) return
        val key = intent.getStringExtra(PushNotifier.EXTRA_DIALOG) ?: return
        val messageId = intent.getIntExtra(PushNotifier.EXTRA_MESSAGE, 0).takeIf { it != 0 }
        val proximity = intent.getBooleanExtra(LiveLocationService.EXTRA_PROXIMITY, false)
        intent.removeExtra(PushNotifier.EXTRA_DIALOG)
        intent.removeExtra(LiveLocationService.EXTRA_PROXIMITY)
        lifecycleScope.launch {
            snapshotFlow { navigator.current }.first { it != Route.Splash }
            if (Route.Chats !in navigator.backStack) return@launch
            val loaded = dialogs.state.first { it.loaded }
            val dialog = (loaded.dialogs + loaded.archived).firstOrNull { it.key == key } ?: return@launch
            val title = if (dialog.isSaved) getString(R.string.saved_messages) else dialog.title
            val shown = (navigator.current as? Route.Chat)?.dialogKey == key
            if (shown && proximity) navigator.back()
            if (!shown || proximity) navigator.go(Route.Chat(key, title, messageId = messageId, proximity = proximity))
        }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("notifications", MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        prefs.edit().putBoolean("asked", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun keepSplashWhileStarting() {
        val content = findViewById<View>(android.R.id.content)
        val since = SystemClock.uptimeMillis()
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = appLock.locked.value || navigator.current != Route.Splash || SystemClock.uptimeMillis() - since > 1500
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return ready
                }
            },
        )
    }
}

val APP_VERSION: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
