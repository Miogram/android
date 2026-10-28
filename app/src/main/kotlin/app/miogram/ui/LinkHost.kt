package app.miogram.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miogram.feature.links.LinksViewModel
import cn.ellerotta.core.model.LinkTarget
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.StickerSetSheet
import cn.ellerotta.core.ui.components.chat.WebAppConsentDialog
import cn.ellerotta.core.ui.components.chat.WebAppScreen
import cn.ellerotta.core.ui.components.chat.webAppThemeJson
import cn.ellerotta.core.ui.components.common.ErrorSnackbar
import cn.ellerotta.core.ui.components.links.InviteSheet
import cn.ellerotta.core.ui.components.links.LoginCodeSheet
import cn.ellerotta.core.ui.components.links.UnsupportedLinkSheet
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut
import kotlinx.coroutines.launch

@Composable
internal fun LinkHost(model: LinksViewModel) {
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val sheet by model.sheet.collectAsStateWithLifecycle()
    val stickers by model.stickers.collectAsStateWithLifecycle()
    val webApp by model.webApp.collectAsStateWithLifecycle()
    val consent by model.consent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.chat_copied)
    val theme = webAppThemeJson()
    SideEffect { model.theme = theme }
    LaunchedEffect(model) { model.external.collect { openInBrowser(context, it) } }
    fun copy(label: String, text: String) {
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text))) }
        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            busy > 0,
            Modifier.statusBarsPadding(),
            enter = MaterialTheme.motionScheme.expandFadeIn(),
            exit = MaterialTheme.motionScheme.shrinkFadeOut(),
        ) { LinearWavyProgressIndicator(Modifier.fillMaxWidth().testTag("linkBusy")) }
        error?.let { ErrorSnackbar(it, model::dismissError, Modifier.align(Alignment.BottomCenter)) }
    }
    when (val open = sheet) {
        is LinkTarget.Invite -> InviteSheet(open.invite, onJoin = model::joinInvite, onDismiss = model::dismiss)
        is LinkTarget.StickerSet -> StickerSetSheet(
            open.detail,
            stickers,
            onLoadSticker = model::loadSticker,
            onSend = null,
            onInstall = model::installSet,
            onDismiss = model::dismiss,
        )
        is LinkTarget.LoginCode -> LoginCodeSheet(open.code, onCopy = { copy("code", open.code) }, onDismiss = model::dismiss)
        is LinkTarget.Unsupported -> UnsupportedLinkSheet(open.url, onCopy = { copy("link", open.url) }, onDismiss = model::dismiss)
        else -> Unit
    }
    consent?.let { question -> WebAppConsentDialog(question, onAnswer = model::acceptConsent, onDismiss = model::dismissConsent) }
    val device = rememberWebAppDevice(model.webAppServices)
    webApp?.let { app -> WebAppScreen(app, onClose = model::closeWebApp, onData = {}, onOpenLink = model::open, device = device) }
}

internal fun openInBrowser(context: Context, url: String) {
    val uri = url.toUri()
    try {
        val provider = CustomTabsClient.getPackageName(context, emptyList())
        val browser = provider ?: browserPackage(context)
        when {
            provider != null -> CustomTabsIntent.Builder().setShowTitle(true).build().apply { intent.setPackage(provider) }.launchUrl(context, uri)
            browser != null -> context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(browser))
            else -> throw ActivityNotFoundException(url)
        }
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.links_no_browser), Toast.LENGTH_SHORT).show()
    }
}

private fun browserPackage(context: Context): String? {
    val probe = Intent(Intent.ACTION_VIEW, "https://example.com".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    val packages = context.packageManager
    val default = packages.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    if (default != null && default != "android" && default != context.packageName) return default
    return packages.queryIntentActivities(probe, 0).map { it.activityInfo.packageName }.firstOrNull { it != context.packageName }
}
