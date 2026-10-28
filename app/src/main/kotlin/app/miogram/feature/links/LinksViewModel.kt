package app.miogram.feature.links

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import app.miogram.data.telegram.BotsRepository
import app.miogram.data.telegram.JoinBotCheckException
import app.miogram.data.telegram.LinksRepository
import app.miogram.data.telegram.MediaRepository
import app.miogram.data.telegram.StickersRepository
import app.miogram.feature.common.WorkViewModel
import app.miogram.links.LinkRouter
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import cn.ellerotta.core.model.AttachMenuBot
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.PeerKind
import app.miogram.data.DialogsStore
import app.miogram.data.WebAppServices
import app.miogram.nav.AttachLaunch
import cn.ellerotta.core.model.LinkTarget
import cn.ellerotta.core.model.OpenWebApp
import cn.ellerotta.core.model.WebAppConsent
import cn.ellerotta.core.model.WebAppMode
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.UiError
import cn.ellerotta.core.model.stickerPreview
import cn.ellerotta.ktproto.session.RpcException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LinksViewModel @Inject constructor(
    private val router: LinkRouter,
    private val links: LinksRepository,
    private val bots: BotsRepository,
    private val stickersRepository: StickersRepository,
    private val media: MediaRepository,
    private val navigator: Navigator,
    private val dialogs: DialogsStore,
    val webAppServices: WebAppServices,
) : WorkViewModel() {
    private val sheetState = MutableStateFlow<LinkTarget?>(null)

    val sheet: StateFlow<LinkTarget?> = sheetState.asStateFlow()

    private val stickerFiles = MutableStateFlow<Map<String, ByteArray>>(emptyMap())

    val stickers: StateFlow<Map<String, ByteArray>> = stickerFiles.asStateFlow()

    private val externalLinks = Channel<String>(Channel.BUFFERED)

    val external: Flow<String> = externalLinks.receiveAsFlow()

    init {
        viewModelScope.launch {
            val loggedIn = snapshotFlow { Route.Chats in navigator.backStack }
            combine(router.pending, loggedIn) { url, ready -> url.takeIf { ready } }
                .filterNotNull()
                .collect { url -> if (router.take(url)) handle(url) }
        }
    }

    fun open(url: String) = router.submit(url)

    private fun handle(url: String) = work {
        val target = links.open(url)
        sheetState.value = null
        when (target) {
            is LinkTarget.Chat -> {
                val dialog = target.dialog
                val topic = target.topicId
                when {
                    target.profile -> navigator.go(Route.PeerProfile(dialog.key, dialog.title))
                    topic != null -> navigator.go(Route.Chat(dialog.key, target.topicTitle ?: dialog.title, target.messageId, target.draft, topicId = topic))
                    dialog.forum -> navigator.go(Route.Topics(dialog.key, dialog.title))
                    else -> navigator.go(Route.Chat(dialog.key, dialog.title, target.messageId, target.draft, target.startParameter))
                }
            }
            is LinkTarget.Share -> navigator.go(Route.ShareTarget(target.text))
            is LinkTarget.External -> externalLinks.send(target.url)
            is LinkTarget.BotApp -> {
                val app = bots.botApp(target.bot.key, target.shortName)
                val peer = shownChat()
                ask(app.consent) { writeAllowed -> show(bots.openBotApp(app, peer, target.startParameter, target.mode, writeAllowed, theme)) }
            }
            is LinkTarget.MainApp -> show(bots.openMainApp(target.bot.key, shownChat(), target.startParameter, target.mode, theme))
            is LinkTarget.AttachApp -> openAttachLink(target)
            LinkTarget.PrivateChat -> fail(UiError.PrivateChat)
            else -> sheetState.value = target
        }
    }

    fun joinInvite() {
        val invite = (sheetState.value as? LinkTarget.Invite)?.invite ?: return
        work {
            val dialog = try {
                links.joinInvite(invite.hash)
            } catch (e: RpcException) {
                sheetState.value = null
                throw e
            } catch (e: JoinBotCheckException) {
                sheetState.value = null
                throw e
            }
            sheetState.value = null
            navigator.go(Route.Chat(dialog.key, dialog.title))
        }
    }

    fun loadSticker(sticker: MediaItem) {
        if (sticker.key in stickerFiles.value) return
        viewModelScope.launch {
            sticker.stickerPreview()?.takeIf { !media.hasSticker(sticker.key) }?.let(::loadSticker)
            runCatching { media.sticker(sticker.key) }.getOrNull()?.let { bytes -> stickerFiles.update { it + (sticker.key to bytes) } }
        }
    }

    fun installSet(install: Boolean) {
        val set = (sheetState.value as? LinkTarget.StickerSet)?.detail ?: return
        work {
            if (install) stickersRepository.install(set.info.key) else stickersRepository.uninstall(set.info.key)
            sheetState.update { current ->
                if (current is LinkTarget.StickerSet && current.detail.info.key == set.info.key) {
                    LinkTarget.StickerSet(current.detail.copy(info = current.detail.info.copy(installed = install)))
                } else {
                    current
                }
            }
        }
    }

    fun dismiss() {
        sheetState.value = null
    }

    private val webAppState = MutableStateFlow<OpenWebApp?>(null)
    private val consentState = MutableStateFlow<WebAppConsent?>(null)
    private var consented: (suspend (writeAllowed: Boolean) -> Unit)? = null

    val webApp: StateFlow<OpenWebApp?> = webAppState.asStateFlow()

    val consent: StateFlow<WebAppConsent?> = consentState.asStateFlow()

    @Volatile var theme: String = "{}"

    private fun shownChat(): String? = (navigator.backStack.lastOrNull() as? Route.Chat)?.dialogKey

    fun openMainApp(botKey: String) {
        work { show(bots.openMainApp(botKey, shownChat(), null, WebAppMode.Default, theme)) }
    }

    fun openSideMenuApp(bot: AttachMenuBot) {
        work {
            ask(bot.consent) { writeAllowed ->
                if (bot.inactive) bots.enableAttachBot(bot.key, writeAllowed)
                show(bots.openSideMenuApp(bot, theme))
            }
        }
    }

    fun acceptConsent(writeAllowed: Boolean) {
        val open = consented ?: return
        dismissConsent()
        work { open(writeAllowed) }
    }

    fun dismissConsent() {
        consented = null
        consentState.value = null
    }

    fun closeWebApp() {
        webAppState.value = null
    }

    private suspend fun ask(consent: WebAppConsent?, open: suspend (writeAllowed: Boolean) -> Unit) {
        if (consent == null) {
            open(false)
        } else {
            consented = open
            consentState.value = consent
        }
    }

    private fun show(app: OpenWebApp) {
        webAppState.value = app
    }

    private suspend fun openAttachLink(target: LinkTarget.AttachApp) {
        val bot = bots.attachMenuBot(target.bot.key) ?: return navigator.go(Route.Chat(target.bot.key, target.bot.title))
        ask(bot.consent) { writeAllowed ->
            if (bot.inactive) bots.enableAttachBot(bot.key, writeAllowed)
            val launch = AttachLaunch(bot.copy(inactive = false), target.startParameter)
            val shown = shownChat()
            val chat = target.chat ?: if (target.choose.isNotEmpty()) null else shown?.let(::dialogOf) ?: target.bot
            when {
                chat == null -> navigator.go(Route.AttachTarget(launch, target.choose))
                !bot.allowedIn(chat.key, chat.kind) -> fail(UiError.AttachAppNotHere(bot.shortName, added = bot.inactive))
                else -> {
                    if (chat.key == shown) navigator.back()
                    navigator.go(Route.Chat(chat.key, chat.title, attach = launch))
                }
            }
        }
    }

    private fun dialogOf(key: String): DialogItem {
        dialogs.state.value.dialogs.firstOrNull { it.key == key }?.let { return it }
        val title = (navigator.backStack.lastOrNull() as? Route.Chat)?.title.orEmpty()
        val kind = when {
            key.startsWith("ch") -> PeerKind.Channel
            key.startsWith("c") -> PeerKind.Group
            else -> PeerKind.User
        }
        return DialogItem(key = key, title = title, kind = kind)
    }
}
