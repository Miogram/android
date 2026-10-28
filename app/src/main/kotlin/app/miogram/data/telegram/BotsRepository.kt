package app.miogram.data.telegram

import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.BotCommand
import cn.ellerotta.core.model.CallbackAnswer
import cn.ellerotta.ktproto.api.functions.channels.GetFullChannel
import cn.ellerotta.ktproto.api.functions.messages.GetFullChat
import cn.ellerotta.ktproto.api.functions.users.GetFullUser
import cn.ellerotta.ktproto.api.types.ChannelFull
import cn.ellerotta.ktproto.api.types.ChatFull
import cn.ellerotta.ktproto.api.types.CommunityFull
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.TypeChatFull
import cn.ellerotta.ktproto.api.types.TypeUser
import cn.ellerotta.core.model.InlineBot
import cn.ellerotta.core.model.InlineResults
import cn.ellerotta.core.model.LoginAnswer
import cn.ellerotta.core.model.LoginRequest
import cn.ellerotta.ktproto.api.functions.contacts.GetTopPeers
import cn.ellerotta.ktproto.api.functions.contacts.ResolveUsername
import cn.ellerotta.ktproto.api.functions.messages.AcceptUrlAuth
import cn.ellerotta.ktproto.api.functions.messages.GetBotCallbackAnswer
import cn.ellerotta.core.model.OpenWebApp
import cn.ellerotta.core.model.AttachMenuBot
import cn.ellerotta.core.model.BotAppInfo
import cn.ellerotta.core.model.BotMenuButton
import cn.ellerotta.core.model.InlineSwitchWebView
import cn.ellerotta.core.model.WebAppMode
import cn.ellerotta.ktproto.api.functions.messages.GetAttachMenuBots
import cn.ellerotta.ktproto.api.functions.bots.AllowSendMessage
import cn.ellerotta.ktproto.api.functions.bots.CanSendMessage
import cn.ellerotta.ktproto.api.functions.bots.InvokeWebViewCustomMethod
import cn.ellerotta.ktproto.api.functions.contacts.Unblock
import cn.ellerotta.ktproto.api.functions.messages.GetAttachMenuBot
import cn.ellerotta.ktproto.api.functions.messages.SendMedia
import cn.ellerotta.ktproto.api.functions.users.GetUsers
import cn.ellerotta.ktproto.api.types.InputMediaContact
import cn.ellerotta.ktproto.api.types.InputUserSelf
import cn.ellerotta.ktproto.api.functions.messages.GetBotApp
import cn.ellerotta.ktproto.api.functions.messages.ProlongWebView
import cn.ellerotta.ktproto.api.functions.messages.RequestAppWebView
import cn.ellerotta.ktproto.api.functions.messages.RequestMainWebView
import cn.ellerotta.ktproto.api.functions.messages.RequestSimpleWebView
import cn.ellerotta.ktproto.api.functions.messages.RequestWebView
import cn.ellerotta.ktproto.api.functions.messages.SendWebViewData
import cn.ellerotta.ktproto.api.functions.messages.ToggleBotInAttachMenu
import cn.ellerotta.ktproto.api.types.AttachMenuBots
import cn.ellerotta.ktproto.api.types.AttachMenuBotsNotModified
import cn.ellerotta.ktproto.api.types.BotApp
import cn.ellerotta.ktproto.api.types.BotAppNotModified
import cn.ellerotta.ktproto.api.types.DataJSON
import cn.ellerotta.ktproto.api.types.InputBotAppID
import cn.ellerotta.ktproto.api.types.InputBotAppShortName
import cn.ellerotta.ktproto.api.types.InputPeerEmpty
import cn.ellerotta.ktproto.api.types.WebViewResultUrl
import cn.ellerotta.ktproto.api.types.BotMenuButton as TlBotMenuButton
import cn.ellerotta.ktproto.api.functions.messages.GetInlineBotResults
import cn.ellerotta.ktproto.api.functions.messages.RequestUrlAuth
import cn.ellerotta.ktproto.api.functions.messages.SendInlineBotResult
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.TopPeerCategoryBotsInline
import cn.ellerotta.ktproto.api.types.UrlAuthResultAccepted
import cn.ellerotta.ktproto.api.types.UrlAuthResultDefault
import cn.ellerotta.ktproto.api.types.UrlAuthResultRequest
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.contacts.TopPeers
import cn.ellerotta.ktproto.session.RpcException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class BotsRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    private val cache = object : LinkedHashMap<String, Pair<Long, InlineResults>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, InlineResults>>) = size > MAX_CACHED
    }

    @Volatile private var attachBots: Pair<Long, List<AttachMenuBot>>? = null

    private val botApps = HashMap<String, BotApp>()

    suspend fun inlineBot(username: String): InlineBot? {
        val resolved = try {
            telegram.client().invoke(ResolveUsername(username))
        } catch (e: RpcException) {
            if (e.code == 400) return null
            throw e
        }
        registry.rememberUsers(resolved.users)
        val id = (resolved.peer as? PeerUser)?.userId ?: return null
        val user = resolved.users.filterIsInstance<User>().firstOrNull { it.id == id } ?: return null
        return registry.inlineBotOf(user)
    }

    suspend fun inlineResults(bot: InlineBot, dialogKey: String, query: String, offset: String = ""): InlineResults {
        val key = "${bot.key}|$dialogKey|$offset|$query"
        val now = System.currentTimeMillis()
        synchronized(cache) { cache[key] }?.let { (until, results) -> if (until > now) return results }
        val answer = telegram.client().invoke(GetInlineBotResults(bot = registry.user(bot.key), peer = registry.peer(dialogKey), query = query, offset = offset))
        registry.rememberUsers(answer.users)
        val results = registry.inlineResultsOf(answer)
        if (results.cacheTime > 0) synchronized(cache) { cache[key] = now + results.cacheTime * 1000L to results }
        return results
    }

    suspend fun sendInlineResult(dialogKey: String, queryId: Long, id: String, replyTo: Int? = null, topicId: Int? = null) {
        telegram.client().invoke(
            SendInlineBotResult(clearDraft = true, peer = registry.peer(dialogKey), replyTo = replyToOf(replyTo, topicId), randomId = Random.nextLong(), queryId = queryId, id = id),
        )
    }

    suspend fun recentInlineBots(): List<InlineBot> {
        val top = telegram.client().invoke(GetTopPeers(botsInline = true, offset = 0, limit = RECENT_BOTS, hash = 0)) as? TopPeers ?: return emptyList()
        registry.rememberUsers(top.users)
        val users = top.users.filterIsInstance<User>().associateBy { it.id }
        return top.categories.filter { it.category == TopPeerCategoryBotsInline }
            .flatMap { it.peers }
            .mapNotNull { peer -> (peer.peer as? PeerUser)?.let { users[it.userId] }?.let { registry.inlineBotOf(it) } }
    }

    suspend fun callback(dialogKey: String, messageId: Int, data: ByteArray): CallbackAnswer {
        val answer = try {
            telegram.client().invoke(GetBotCallbackAnswer(peer = registry.peer(dialogKey), msgId = messageId, data = data))
        } catch (e: RpcException) {
            if (e.errorMessage == "BOT_RESPONSE_TIMEOUT") return CallbackAnswer()
            throw e
        }
        return CallbackAnswer(answer.message?.ifEmpty { null }, answer.alert, answer.url?.ifEmpty { null })
    }

    suspend fun requestLogin(dialogKey: String, messageId: Int, button: BotButton.LoginUrl): LoginAnswer =
        when (val result = telegram.client().invoke(RequestUrlAuth(peer = registry.peer(dialogKey), msgId = messageId, buttonId = button.buttonId))) {
            is UrlAuthResultRequest -> LoginAnswer.Ask(
                LoginRequest(result.domain, (result.bot as? User)?.let(::nameOf).orEmpty(), result.requestWriteAccess, result.requestPhoneNumber),
            )
            is UrlAuthResultAccepted -> LoginAnswer.Open(result.url ?: button.url)
            UrlAuthResultDefault -> LoginAnswer.Plain
        }

    suspend fun acceptLogin(dialogKey: String, messageId: Int, button: BotButton.LoginUrl, writeAllowed: Boolean, sharePhone: Boolean): String {
        val call = AcceptUrlAuth(writeAllowed = writeAllowed, sharePhoneNumber = sharePhone, peer = registry.peer(dialogKey), msgId = messageId, buttonId = button.buttonId)
        return (telegram.client().invoke(call) as? UrlAuthResultAccepted)?.url ?: button.url
    }

    fun webAppBot(dialogKey: String, senderId: Long, viaBot: String?): String? {
        val id = viaBot?.let { name -> registry.usernames.entries.firstOrNull { it.value.equals(name, ignoreCase = true) }?.key }
            ?: senderId.takeIf { it != 0L }
            ?: dialogKey.takeIf { it.startsWith("u") }?.drop(1)?.toLongOrNull()
        return id?.let { "u$it" }
    }

    suspend fun openWebApp(dialogKey: String, botKey: String, button: BotButton.WebApp, theme: String): OpenWebApp {
        val bot = registry.user(botKey)
        val title = registry.peerNames[botKey].orEmpty()
        return if (button.simple) {
            val result = telegram.client().invoke(RequestSimpleWebView(bot = bot, url = button.url, themeParams = DataJSON(theme), platform = PLATFORM))
            OpenWebApp(botKey, title, result.url, sendsData = true, buttonText = button.text)
        } else {
            val result = telegram.client().invoke(RequestWebView(peer = registry.peer(dialogKey), bot = bot, url = button.url, themeParams = DataJSON(theme), platform = PLATFORM))
            OpenWebApp(botKey, title, result.url, queryId = result.queryId)
        }
    }

    suspend fun prolongWebApp(dialogKey: String, app: OpenWebApp): Boolean {
        val queryId = app.queryId ?: return false
        return try {
            telegram.client().invoke(ProlongWebView(peer = registry.peer(dialogKey), bot = registry.user(app.bot), queryId = queryId))
        } catch (e: RpcException) {
            if (e.errorMessage == "QUERY_ID_INVALID") return false
            throw e
        }
    }

    suspend fun sendWebAppData(app: OpenWebApp, data: String) {
        telegram.client().invoke(SendWebViewData(bot = registry.user(app.bot), randomId = Random.nextLong(), buttonText = app.buttonText, data = data))
    }

    suspend fun menuButton(dialogKey: String): BotMenuButton? {
        val full = telegram.client().invoke(GetFullUser(registry.user(dialogKey)))
        registry.rememberUsers(full.users)
        return (full.fullUser.botInfo?.menuButton as? TlBotMenuButton)?.let { BotMenuButton(it.text, it.url) }
    }

    suspend fun openMenuApp(dialogKey: String, button: BotMenuButton, theme: String): OpenWebApp {
        val result = telegram.client().invoke(
            RequestWebView(fromBotMenu = true, peer = registry.peer(dialogKey), bot = registry.user(dialogKey), url = button.url, themeParams = DataJSON(theme), platform = PLATFORM),
        )
        return opened(dialogKey, result)
    }

    suspend fun attachMenuBots(): List<AttachMenuBot> {
        val kept = attachBots
        return when (val answer = telegram.client().invoke(GetAttachMenuBots(hash = kept?.first ?: 0))) {
            AttachMenuBotsNotModified -> kept?.second.orEmpty()
            is AttachMenuBots -> {
                registry.rememberUsers(answer.users)
                answer.bots.map(::attachMenuBotOf).also { attachBots = answer.hash to it }
            }
        }
    }

    suspend fun enableAttachBot(botKey: String, writeAllowed: Boolean) {
        telegram.client().invoke(ToggleBotInAttachMenu(writeAllowed = writeAllowed, bot = registry.user(botKey), enabled = true))
        attachBots = null
    }

    suspend fun disableAttachBot(botKey: String) {
        telegram.client().invoke(ToggleBotInAttachMenu(bot = registry.user(botKey), enabled = false))
        attachBots = null
    }

    suspend fun attachMenuBot(botKey: String): AttachMenuBot? {
        val answer = try {
            telegram.client().invoke(GetAttachMenuBot(registry.user(botKey)))
        } catch (e: RpcException) {
            if (e.code == 400) return null
            throw e
        }
        registry.rememberUsers(answer.users)
        return attachMenuBotOf(answer.bot)
    }

    suspend fun openAttachApp(dialogKey: String, bot: AttachMenuBot, theme: String, startParameter: String? = null): OpenWebApp {
        val result = telegram.client().invoke(
            RequestWebView(peer = registry.peer(dialogKey), bot = registry.user(bot.key), startParam = startParameter, themeParams = DataJSON(theme), platform = PLATFORM),
        )
        return opened(bot.key, result)
    }

    suspend fun openSideMenuApp(bot: AttachMenuBot, theme: String): OpenWebApp {
        val result = telegram.client().invoke(RequestSimpleWebView(fromSideMenu = true, bot = registry.user(bot.key), themeParams = DataJSON(theme), platform = PLATFORM))
        return opened(bot.key, result)
    }

    suspend fun openInlineApp(bot: InlineBot, switch: InlineSwitchWebView, theme: String): OpenWebApp {
        val result = telegram.client().invoke(
            RequestSimpleWebView(fromSwitchWebview = true, bot = registry.user(bot.key), url = switch.url, themeParams = DataJSON(theme), platform = PLATFORM),
        )
        return opened(bot.key, result).copy(inlineBot = bot.username)
    }

    suspend fun openMainApp(botKey: String, peerKey: String?, startParameter: String?, mode: WebAppMode, theme: String): OpenWebApp {
        val result = telegram.client().invoke(
            RequestMainWebView(
                compact = mode == WebAppMode.Compact,
                fullscreen = mode == WebAppMode.Fullscreen,
                peer = peerKey?.let(registry::peer) ?: InputPeerEmpty,
                bot = registry.user(botKey),
                startParam = startParameter,
                themeParams = DataJSON(theme),
                platform = PLATFORM,
            ),
        )
        return opened(botKey, result).copy(mode = mode)
    }

    suspend fun botApp(botKey: String, shortName: String): BotAppInfo {
        val key = "$botKey|$shortName"
        val kept = synchronized(botApps) { botApps[key] }
        val answer = telegram.client().invoke(GetBotApp(InputBotAppShortName(registry.user(botKey), shortName), hash = kept?.hash ?: 0))
        val app = when (val described = answer.app) {
            BotAppNotModified -> kept ?: error("bot app $shortName not modified but not known")
            is BotApp -> described.also { synchronized(botApps) { botApps[key] = it } }
        }
        return BotAppInfo(botKey, app.shortName, app.title, app.description, inactive = answer.inactive, requestWriteAccess = answer.requestWriteAccess)
    }

    suspend fun openBotApp(app: BotAppInfo, peerKey: String?, startParameter: String?, mode: WebAppMode, writeAllowed: Boolean, theme: String): OpenWebApp {
        val described = synchronized(botApps) { botApps["${app.bot}|${app.shortName}"] } ?: error("bot app ${app.shortName} not described yet")
        val result = telegram.client().invoke(
            RequestAppWebView(
                writeAllowed = writeAllowed,
                compact = mode == WebAppMode.Compact,
                fullscreen = mode == WebAppMode.Fullscreen,
                peer = registry.peer(peerKey ?: app.bot),
                app = InputBotAppID(described.id, described.accessHash),
                startParam = startParameter,
                themeParams = DataJSON(theme),
                platform = PLATFORM,
            ),
        )
        return opened(app.bot, result).copy(title = app.title.ifEmpty { registry.peerNames[app.bot].orEmpty() }, mode = mode)
    }

    suspend fun canWrite(botKey: String): Boolean = telegram.client().invoke(CanSendMessage(registry.user(botKey)))

    suspend fun allowWrite(botKey: String) {
        telegram.client().invoke(AllowSendMessage(registry.user(botKey)))
    }

    suspend fun sharePhone(botKey: String) {
        val peer = registry.peer(botKey)
        val full = telegram.client().invoke(GetFullUser(registry.user(botKey)))
        registry.rememberUsers(full.users)
        if (full.fullUser.blocked) telegram.client().invoke(Unblock(id = peer))
        val self = telegram.client().invoke(GetUsers(listOf(InputUserSelf))).filterIsInstance<User>().firstOrNull() ?: error("no own user")
        val phone = self.phone ?: error("no own phone number")
        val contact = InputMediaContact(phoneNumber = phone, firstName = self.firstName.orEmpty(), lastName = self.lastName.orEmpty(), vcard = "")
        telegram.client().invoke(SendMedia(peer = peer, media = contact, message = "", randomId = Random.nextLong()))
    }

    suspend fun invokeCustomMethod(botKey: String, method: String, params: String): Result<String> = try {
        Result.success(telegram.client().invoke(InvokeWebViewCustomMethod(bot = registry.user(botKey), customMethod = method, params = DataJSON(params))).data)
    } catch (e: RpcException) {
        Result.failure(IllegalStateException(e.errorMessage))
    }

    private fun opened(botKey: String, result: WebViewResultUrl) = OpenWebApp(botKey, registry.peerNames[botKey].orEmpty(), result.url, queryId = result.queryId)

    suspend fun commands(dialogKey: String): List<BotCommand> = when (val peer = peerOfKey(dialogKey)) {
        is PeerUser -> telegram.client().invoke(GetFullUser(registry.user(dialogKey))).fullUser.botInfo?.commands.orEmpty().map {
            BotCommand(it.command, it.description, ephemeral = it.ephemeral, botKey = dialogKey)
        }
        is PeerChannel -> telegram.client().invoke(GetFullChannel(registry.channel(dialogKey))).let { groupCommands(it.fullChat, it.users) }
        is PeerChat -> telegram.client().invoke(GetFullChat(peer.chatId)).let { groupCommands(it.fullChat, it.users) }
        else -> emptyList()
    }

    private fun groupCommands(full: TypeChatFull, users: List<TypeUser>): List<BotCommand> {
        val infos = when (full) {
            is ChannelFull -> full.botInfo
            is ChatFull -> full.botInfo
            is CommunityFull -> null
        }
        val usernames = users.filterIsInstance<User>().associate { it.id to it.username }
        return infos.orEmpty().flatMap { info ->
            val bot = info.userId?.let(usernames::get) ?: return@flatMap emptyList()
            info.commands.orEmpty().map { BotCommand(it.command, it.description, bot, ephemeral = it.ephemeral, botKey = "u${info.userId}") }
        }
    }

    private companion object {
        const val MAX_CACHED = 50
        const val RECENT_BOTS = 20

        const val PLATFORM = "android"
    }
}
