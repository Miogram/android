package app.miogram.feature.chat

import app.miogram.data.telegram.BotsRepository
import cn.ellerotta.core.model.AttachMenuBot
import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.BotMenuButton
import cn.ellerotta.core.model.InlineBot
import cn.ellerotta.core.model.InlineSwitchWebView
import cn.ellerotta.core.model.OpenWebApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ChatWebApps(
    private val scope: CoroutineScope,
    private val dialogKey: String,
    private val bots: BotsRepository,
    private val work: (suspend () -> Unit) -> Job,
) {
    private val appState = MutableStateFlow<OpenWebApp?>(null)
    private val menuState = MutableStateFlow<BotMenuButton?>(null)
    private val attachState = MutableStateFlow<List<AttachMenuBot>>(emptyList())
    private var prolonging: Job? = null

    val app: StateFlow<OpenWebApp?> = appState.asStateFlow()

    val menuButton: StateFlow<BotMenuButton?> = menuState.asStateFlow()

    val attachBots: StateFlow<List<AttachMenuBot>> = attachState.asStateFlow()

    fun loadMenuButton() {
        scope.launch { quietly { menuState.value = bots.menuButton(dialogKey) } }
    }

    fun loadAttachBots() {
        scope.launch { quietly { attachState.value = bots.attachMenuBots().filter { it.attachMenu } } }
    }

    fun openButton(bot: String, button: BotButton.WebApp, theme: String) = open { bots.openWebApp(dialogKey, bot, button, theme) }

    fun openMenu(theme: String) {
        val button = menuState.value ?: return
        open { bots.openMenuApp(dialogKey, button, theme) }
    }

    fun openAttach(bot: AttachMenuBot, theme: String, writeAllowed: Boolean, startParameter: String? = null) = open {
        if (bot.inactive) {
            bots.enableAttachBot(bot.key, writeAllowed)
            quietly { attachState.value = bots.attachMenuBots().filter { it.attachMenu } }
        }
        bots.openAttachApp(dialogKey, bot, theme, startParameter)
    }

    fun openInline(bot: InlineBot, switch: InlineSwitchWebView, theme: String) = open { bots.openInlineApp(bot, switch, theme) }

    private fun open(request: suspend () -> OpenWebApp) {
        work {
            val app = request()
            close()
            appState.value = app
            if (app.queryId != null) prolonging = scope.launch { prolong(app) }
        }
    }

    private suspend fun prolong(app: OpenWebApp) {
        while (true) {
            delay(PROLONG_MS)
            val alive = try {
                bots.prolongWebApp(dialogKey, app)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                true
            }
            if (!alive) {
                appState.compareAndSet(app, null)
                return
            }
        }
    }

    fun sendData(data: String) {
        val app = appState.value?.takeIf { it.sendsData } ?: return
        close()
        work { bots.sendWebAppData(app, data) }
    }

    fun close() {
        prolonging?.cancel()
        prolonging = null
        appState.value = null
    }

    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    companion object {
        const val PROLONG_MS = 60_000L
    }
}
