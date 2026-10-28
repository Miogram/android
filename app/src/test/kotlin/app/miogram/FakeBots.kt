package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.contacts.GetTopPeers
import cn.ellerotta.ktproto.api.functions.messages.GetInlineBotResults
import cn.ellerotta.ktproto.api.functions.messages.SendInlineBotResult
import cn.ellerotta.ktproto.api.types.BotInlineMediaResult
import cn.ellerotta.ktproto.api.types.BotInlineMessageMediaAuto
import cn.ellerotta.ktproto.api.types.BotInlineMessageText
import cn.ellerotta.ktproto.api.types.BotInlineResult
import cn.ellerotta.ktproto.api.types.InlineBotSwitchPM
import cn.ellerotta.ktproto.api.types.InputPeerEmpty
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import cn.ellerotta.ktproto.api.types.InputUser
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.api.types.MessageMediaPhoto
import cn.ellerotta.ktproto.api.types.MessageReplyHeader
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.TypePeer
import cn.ellerotta.ktproto.api.types.TopPeer
import cn.ellerotta.ktproto.api.types.TopPeerCategoryBotsInline
import cn.ellerotta.ktproto.api.types.TopPeerCategoryCorrespondents
import cn.ellerotta.ktproto.api.types.TopPeerCategoryPeers
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.messages.BotCallbackAnswer
import cn.ellerotta.ktproto.api.types.BotCommand
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.ButtonTypeDefault
import cn.ellerotta.ktproto.api.types.InlineButtonTypeCallback
import cn.ellerotta.ktproto.api.types.InlineButtonTypeCopy
import cn.ellerotta.ktproto.api.types.InlineButtonTypeDisabled
import cn.ellerotta.ktproto.api.types.InlineButtonTypeGame
import cn.ellerotta.ktproto.api.types.InlineButtonTypeSwitchInline
import cn.ellerotta.ktproto.api.types.InlineButtonTypeUrl
import cn.ellerotta.ktproto.api.types.InlineButtonTypeUserProfile
import cn.ellerotta.ktproto.api.types.InlineButtonTypeWebView
import cn.ellerotta.ktproto.api.types.KeyboardButton
import cn.ellerotta.ktproto.api.types.KeyboardButtonRow
import cn.ellerotta.ktproto.api.types.KeyboardButtonStyle
import cn.ellerotta.ktproto.api.types.KeyboardInlineButton
import cn.ellerotta.ktproto.api.types.KeyboardInlineButtonRow
import cn.ellerotta.ktproto.api.types.ReplyInlineMarkup
import cn.ellerotta.ktproto.api.types.ReplyKeyboardMarkup
import cn.ellerotta.ktproto.api.types.TypeInlineButtonType
import cn.ellerotta.ktproto.api.functions.messages.GetBotCallbackAnswer
import cn.ellerotta.ktproto.api.functions.messages.AcceptUrlAuth
import cn.ellerotta.ktproto.api.functions.messages.RequestUrlAuth
import cn.ellerotta.ktproto.api.types.InlineButtonTypeUrlAuth
import cn.ellerotta.ktproto.api.types.UrlAuthResultAccepted
import cn.ellerotta.ktproto.api.types.UrlAuthResultDefault
import cn.ellerotta.ktproto.api.types.UrlAuthResultRequest
import cn.ellerotta.ktproto.api.functions.messages.ProlongWebView
import cn.ellerotta.ktproto.api.functions.messages.RequestSimpleWebView
import cn.ellerotta.ktproto.api.functions.messages.RequestWebView
import cn.ellerotta.ktproto.api.functions.messages.SendWebViewData
import cn.ellerotta.ktproto.api.types.ButtonTypeSimpleWebView
import cn.ellerotta.ktproto.api.types.WebViewResultUrl
import cn.ellerotta.ktproto.api.functions.messages.GetAttachMenuBots
import cn.ellerotta.ktproto.api.functions.messages.GetAttachMenuBot
import cn.ellerotta.ktproto.api.functions.bots.AllowSendMessage
import cn.ellerotta.ktproto.api.functions.bots.CanSendMessage
import cn.ellerotta.ktproto.api.functions.bots.InvokeWebViewCustomMethod
import cn.ellerotta.ktproto.api.functions.contacts.Unblock
import cn.ellerotta.ktproto.api.types.AttachMenuBotsBot
import cn.ellerotta.ktproto.api.types.DataJSON
import cn.ellerotta.ktproto.api.functions.messages.GetBotApp
import cn.ellerotta.ktproto.api.functions.messages.RequestAppWebView
import cn.ellerotta.ktproto.api.functions.messages.RequestMainWebView
import cn.ellerotta.ktproto.api.functions.messages.ToggleBotInAttachMenu
import cn.ellerotta.ktproto.api.types.AttachMenuBot
import cn.ellerotta.ktproto.api.types.AttachMenuBots
import cn.ellerotta.ktproto.api.types.AttachMenuBotsNotModified
import cn.ellerotta.ktproto.api.types.AttachMenuPeerTypeBotPM
import cn.ellerotta.ktproto.api.types.AttachMenuPeerTypeChat
import cn.ellerotta.ktproto.api.types.AttachMenuPeerTypePM
import cn.ellerotta.ktproto.api.types.AttachMenuPeerTypeSameBotPM
import cn.ellerotta.ktproto.api.types.BotApp
import cn.ellerotta.ktproto.api.types.BotAppNotModified
import cn.ellerotta.ktproto.api.types.InlineBotWebView
import cn.ellerotta.ktproto.api.types.InputBotAppID
import cn.ellerotta.ktproto.api.types.InputBotAppShortName
import cn.ellerotta.ktproto.api.types.PhotoEmpty
import cn.ellerotta.ktproto.api.types.messages.BotApp as MessagesBotApp
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.contacts.TopPeers
import cn.ellerotta.ktproto.api.types.messages.BotResults
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.CopyOnWriteArrayList

class FakeBots(private val fake: FakeTelegram) {
    val gifBot = User(bot = true, botInfoVersion = 1, id = GIF_BOT_ID, accessHash = 15, firstName = "Gif", username = "gifbot", botInlinePlaceholder = "Search GIFs")

    val inlineCalls: MutableList<GetInlineBotResults> = CopyOnWriteArrayList()

    val inlineSends: MutableList<SendInlineBotResult> = CopyOnWriteArrayList()

    val topPeersCalls: MutableList<GetTopPeers> = CopyOnWriteArrayList()

    fun resolve(username: String): User? = gifBot.takeIf { it.username == username || username == "gif" }

    val helperHistory: MutableList<Message> = CopyOnWriteArrayList(
        listOf(
            Message(
                id = CITY_ID,
                peerId = PeerUser(fake.helperBot.id),
                date = NOW - 120,
                message = "Which city?",
                replyMarkup = ReplyInlineMarkup(forceReply = true, rows = listOf(KeyboardInlineButtonRow(listOf(button("Map", InlineButtonTypeUrl(url = "https://example.com/map")))))),
            ),
            Message(id = MENU_ID, peerId = PeerUser(fake.helperBot.id), date = NOW - 60, message = "Pick an action", replyMarkup = menu("Ping")),
            Message(
                id = QUESTION_ID,
                peerId = PeerUser(fake.helperBot.id),
                date = NOW,
                message = "Do you like Kotlin?",
                replyMarkup = ReplyKeyboardMarkup(
                    resize = true,
                    singleUse = true,
                    rows = listOf(
                        KeyboardButtonRow(listOf(KeyboardButton(text = "Yes", type = ButtonTypeDefault), KeyboardButton(text = "No", type = ButtonTypeDefault))),
                        KeyboardButtonRow(listOf(KeyboardButton(text = "Order", type = ButtonTypeSimpleWebView(url = ORDER_URL)))),
                    ),
                    placeholder = "Answer the helper",
                ),
            ),
        ),
    )

    val callbackCalls: MutableList<GetBotCallbackAnswer> = CopyOnWriteArrayList()

    val loginRequests: MutableList<RequestUrlAuth> = CopyOnWriteArrayList()

    val loginAccepts: MutableList<AcceptUrlAuth> = CopyOnWriteArrayList()

    val webAppRequests: MutableList<RequestWebView> = CopyOnWriteArrayList()
    val simpleWebAppRequests: MutableList<RequestSimpleWebView> = CopyOnWriteArrayList()

    val webAppProlongs: MutableList<ProlongWebView> = CopyOnWriteArrayList()

    @Volatile var webAppForgotten = false

    val webAppData: MutableList<SendWebViewData> = CopyOnWriteArrayList()

    val mainAppRequests: MutableList<RequestMainWebView> = CopyOnWriteArrayList()
    val botAppCalls: MutableList<GetBotApp> = CopyOnWriteArrayList()
    val appWebViewRequests: MutableList<RequestAppWebView> = CopyOnWriteArrayList()

    val attachMenuCalls: MutableList<GetAttachMenuBots> = CopyOnWriteArrayList()
    val attachToggles: MutableList<ToggleBotInAttachMenu> = CopyOnWriteArrayList()

    @Volatile var gifAttached = false

    @Volatile var helperAttached = true

    val attachBotCalls: MutableList<GetAttachMenuBot> = CopyOnWriteArrayList()

    val canWriteCalls: MutableList<CanSendMessage> = CopyOnWriteArrayList()
    val allowWriteCalls: MutableList<AllowSendMessage> = CopyOnWriteArrayList()

    @Volatile var helperWriteAllowed = false

    var helperBlocked: Boolean
        get() = fake.helperBot.id in fake.safety.blocked
        set(value) {
            if (value) fake.safety.blocked[fake.helperBot.id] = NOW else fake.safety.blocked.remove(fake.helperBot.id)
        }
    val unblocks: MutableList<Unblock> get() = fake.safety.unblocks

    val customMethodCalls: MutableList<InvokeWebViewCustomMethod> = CopyOnWriteArrayList()

    val helperCommands = listOf(
        BotCommand(command = "start", description = "Start the helper"),
        BotCommand(command = "help", description = "What the helper can do"),
        BotCommand(command = "weather", description = "Today's weather"),
        BotCommand(ephemeral = true, command = "secret", description = "Only you see the answer"),
    )

    private fun button(text: String, type: TypeInlineButtonType, style: KeyboardButtonStyle? = null) = KeyboardInlineButton(style = style, text = text, type = type)

    private fun menu(ping: String) = ReplyInlineMarkup(
        rows = listOf(
            KeyboardInlineButtonRow(listOf(button(ping, InlineButtonTypeCallback(data = "ping".toByteArray())), button("Alert", InlineButtonTypeCallback(data = "alert".toByteArray())))),
            KeyboardInlineButtonRow(listOf(button("Slow", InlineButtonTypeCallback(data = "slow".toByteArray())), button("Docs link", InlineButtonTypeCallback(data = "url".toByteArray())))),
            KeyboardInlineButtonRow(listOf(button("Site", InlineButtonTypeUrl(url = "https://example.com/docs")), button("News", InlineButtonTypeUrl(url = "https://t.me/kotlinnews")))),
            KeyboardInlineButtonRow(listOf(button("Share", InlineButtonTypeSwitchInline(query = "hi")), button("Here", InlineButtonTypeSwitchInline(samePeer = true, query = "cats")))),
            KeyboardInlineButtonRow(listOf(button("Copy code", InlineButtonTypeCopy(copyText = "A1B2")), button("Play", InlineButtonTypeGame), button("Open app", InlineButtonTypeWebView(url = APP_URL)))),
            KeyboardInlineButtonRow(
                listOf(
                    button("Alice", InlineButtonTypeUserProfile(userId = fake.alice.id), KeyboardButtonStyle(bgPrimary = true, icon = ICON_ID)),
                    button("Sold out", InlineButtonTypeDisabled, KeyboardButtonStyle(bgDanger = true)),
                    button("Pay", InlineButtonTypeCallback(requiresPassword = true, data = "pay".toByteArray()), KeyboardButtonStyle(bgSuccess = true)),
                ),
            ),
            KeyboardInlineButtonRow(
                listOf(
                    button("Log in", InlineButtonTypeUrlAuth(url = LOGIN_URL, buttonId = ASKING_LOGIN)),
                    button("Known site", InlineButtonTypeUrlAuth(url = LOGIN_URL, buttonId = KNOWN_LOGIN)),
                    button("Plain site", InlineButtonTypeUrlAuth(url = PLAIN_URL, buttonId = PLAIN_LOGIN)),
                ),
            ),
        ),
    )

    private fun page(offset: String): BotResults = if (offset.isEmpty()) {
        BotResults(
            queryId = FIRST_QUERY,
            nextOffset = NEXT_OFFSET,
            switchPm = InlineBotSwitchPM("Set up the gif bot", START_PARAMETER),
            results = listOf(
                BotInlineResult(id = "kotlin", type = "article", title = "Kotlin", description = "a modern language", sendMessage = BotInlineMessageText(message = "Kotlin is a modern language")),
                BotInlineMediaResult(id = "photo", type = "photo", photo = fake.photo, title = "a photo", sendMessage = BotInlineMessageMediaAuto(message = "")),
                BotInlineMediaResult(id = "cat", type = "sticker", document = fake.stickerDocuments().first(), sendMessage = BotInlineMessageMediaAuto(message = "")),
            ),
            cacheTime = CACHE_TIME,
            users = listOf(gifBot),
            switchWebview = InlineBotWebView("Open the gif studio", GIF_APP_URL),
        )
    } else {
        BotResults(
            queryId = SECOND_QUERY,
            results = listOf(BotInlineResult(id = "compose", type = "article", title = "Compose", description = "a ui toolkit", sendMessage = BotInlineMessageText(message = "Compose is a ui toolkit"))),
            cacheTime = CACHE_TIME,
            users = listOf(gifBot),
        )
    }

    private fun gifPage(offset: String) = BotResults(
        gallery = true,
        queryId = FIRST_QUERY,
        nextOffset = NEXT_OFFSET.takeIf { offset.isEmpty() },
        results = listOf(BotInlineMediaResult(id = "gif$offset", type = "gif", document = fake.gifDocuments().last(), sendMessage = BotInlineMessageMediaAuto(message = ""))),
        cacheTime = 0,
        users = listOf(gifBot),
    )

    fun register(server: FakeTelegramServer) {
        server.rpc.on(GetBotCallbackAnswer.CONSTRUCTOR_ID) { request ->
            val call = GetBotCallbackAnswer.decode(request)
            callbackCalls += call
            if ((call.peer as? InputPeerUser)?.userId != fake.helperBot.id || call.msgId != MENU_ID) return@on FakeAnswer.Error(400, "MESSAGE_ID_INVALID")
            when (call.data?.decodeToString()) {
                "ping" -> {
                    val edited = helperHistory.first { it.id == MENU_ID }.let { Message(id = it.id, peerId = it.peerId, date = it.date, editDate = NOW, message = "Pinged", replyMarkup = menu("Ping again")) }
                    helperHistory[helperHistory.indexOfFirst { it.id == MENU_ID }] = edited
                    fake.cluster.updates.editMessage(edited, listOf(fake.helperBot))
                    FakeAnswer.Result(BotCallbackAnswer(message = "Pong", cacheTime = 0))
                }
                "alert" -> FakeAnswer.Result(BotCallbackAnswer(alert = true, message = "Careful, this is an alert", cacheTime = 0))
                "url" -> FakeAnswer.Result(BotCallbackAnswer(hasUrl = true, url = "https://t.me/kotlinnews", cacheTime = 0))
                else -> FakeAnswer.Error(400, "BOT_RESPONSE_TIMEOUT")
            }
        }
        server.rpc.on(GetInlineBotResults.CONSTRUCTOR_ID) { request ->
            val call = GetInlineBotResults.decode(request)
            if ((call.bot as? InputUser)?.userId != gifBot.id) return@on FakeAnswer.Error(400, "BOT_INVALID")
            inlineCalls += call
            if (call.peer == InputPeerEmpty) return@on FakeAnswer.Result(gifPage(call.offset))
            FakeAnswer.Result(page(call.offset))
        }
        server.rpc.on(SendInlineBotResult.CONSTRUCTOR_ID) { request ->
            val call = SendInlineBotResult.decode(request)
            inlineSends += call
            if (call.queryId != FIRST_QUERY && call.queryId != SECOND_QUERY) return@on FakeAnswer.Error(400, "QUERY_ID_INVALID")
            val reply = (call.replyTo as? InputReplyToMessage)?.let { MessageReplyHeader(replyToMsgId = it.replyToMsgId) }
            fun made(id: Int, peer: TypePeer, from: TypePeer?, date: Int) = when (call.id) {
                "photo" -> Message(out = true, id = id, fromId = from, peerId = peer, viaBotId = gifBot.id, replyTo = reply, date = date, message = "", media = MessageMediaPhoto(photo = fake.photo))
                "cat" -> Message(out = true, id = id, fromId = from, peerId = peer, viaBotId = gifBot.id, replyTo = reply, date = date, message = "", media = MessageMediaDocument(document = fake.stickerDocuments().first()))
                "compose" -> Message(out = true, id = id, fromId = from, peerId = peer, viaBotId = gifBot.id, replyTo = reply, date = date, message = "Compose is a ui toolkit")
                else -> Message(out = true, id = id, fromId = from, peerId = peer, viaBotId = gifBot.id, replyTo = reply, date = date, message = "Kotlin is a modern language")
            }
            val id = if (call.peer == InputPeerSelf) fake.addSaved { id -> made(id, PeerUser(fake.selfId), null, NOW) } else fake.newMessageId()
            FakeAnswer.Result(fake.sentUpdates(listOf(made(id, fake.peerOf(call.peer), PeerUser(fake.selfId), fake.sentDate()) to call.randomId)))
        }
        server.rpc.on(GetTopPeers.CONSTRUCTOR_ID) { request ->
            val call = GetTopPeers.decode(request)
            topPeersCalls += call
            val categories = buildList {
                if (call.botsInline) add(TopPeerCategoryPeers(TopPeerCategoryBotsInline, 1, listOf(TopPeer(PeerUser(gifBot.id), 1.0))))
                if (call.correspondents) add(TopPeerCategoryPeers(TopPeerCategoryCorrespondents, 3, listOf(TopPeer(PeerUser(fake.bob.id), 2.0), TopPeer(PeerUser(fake.alice.id), 1.5), TopPeer(PeerUser(fake.selfId), 1.0))))
            }
            FakeAnswer.Result(TopPeers(categories, emptyList(), listOf(gifBot, fake.alice, fake.bob, User(self = true, id = fake.selfId, accessHash = 1, firstName = "Test", lastName = "User"))))
        }
        server.rpc.on(RequestUrlAuth.CONSTRUCTOR_ID) { request ->
            val call = RequestUrlAuth.decode(request)
            loginRequests += call
            if ((call.peer as? InputPeerUser)?.userId != fake.helperBot.id || call.msgId != MENU_ID) return@on FakeAnswer.Error(400, "MESSAGE_ID_INVALID")
            when (call.buttonId) {
                ASKING_LOGIN -> FakeAnswer.Result(UrlAuthResultRequest(requestWriteAccess = true, requestPhoneNumber = true, bot = fake.helperBot, domain = "example.com"))
                KNOWN_LOGIN -> FakeAnswer.Result(UrlAuthResultAccepted(url = LOGGED_IN_URL))
                else -> FakeAnswer.Result(UrlAuthResultDefault)
            }
        }
        server.rpc.on(AcceptUrlAuth.CONSTRUCTOR_ID) { request ->
            val call = AcceptUrlAuth.decode(request)
            loginAccepts += call
            if (call.buttonId != ASKING_LOGIN) return@on FakeAnswer.Error(400, "URL_INVALID")
            FakeAnswer.Result(UrlAuthResultAccepted(url = LOGGED_IN_URL))
        }
        server.rpc.on(RequestWebView.CONSTRUCTOR_ID) { request ->
            val call = RequestWebView.decode(request)
            webAppRequests += call
            val bot = (call.bot as? InputUser)?.userId
            if (bot != fake.helperBot.id && !(bot == gifBot.id && call.url == null)) return@on FakeAnswer.Error(400, "BOT_INVALID")
            val url = when {
                call.fromBotMenu -> "${call.url}#tgWebAppData=menu"
                call.url == null -> "$ATTACH_URL#tgWebAppData=attach"
                else -> "${call.url}#tgWebAppData=app"
            }
            FakeAnswer.Result(WebViewResultUrl(queryId = WEB_APP_QUERY, url = url))
        }
        server.rpc.on(RequestSimpleWebView.CONSTRUCTOR_ID) { request ->
            val call = RequestSimpleWebView.decode(request)
            simpleWebAppRequests += call
            val bot = (call.bot as? InputUser)?.userId
            if (bot != fake.helperBot.id && bot != gifBot.id) return@on FakeAnswer.Error(400, "BOT_INVALID")
            FakeAnswer.Result(WebViewResultUrl(url = if (call.fromSideMenu) "$SIDE_URL#tgWebAppData=side" else "${call.url}#tgWebAppData=simple"))
        }
        server.rpc.on(RequestMainWebView.CONSTRUCTOR_ID) { request ->
            val call = RequestMainWebView.decode(request)
            mainAppRequests += call
            if ((call.bot as? InputUser)?.userId != fake.helperBot.id) return@on FakeAnswer.Error(400, "BOT_INVALID")
            FakeAnswer.Result(WebViewResultUrl(url = "$MAIN_URL#tgWebAppData=main${call.startParam.orEmpty()}"))
        }
        server.rpc.on(GetAttachMenuBots.CONSTRUCTOR_ID) { request ->
            val call = GetAttachMenuBots.decode(request)
            attachMenuCalls += call
            val hash = attachHash()
            if (call.hash == hash) return@on FakeAnswer.Result(AttachMenuBotsNotModified)
            FakeAnswer.Result(AttachMenuBots(hash, attachMenuBots(), listOf(fake.helperBot, gifBot)))
        }
        server.rpc.on(ToggleBotInAttachMenu.CONSTRUCTOR_ID) { request ->
            val call = ToggleBotInAttachMenu.decode(request)
            attachToggles += call
            when ((call.bot as? InputUser)?.userId) {
                gifBot.id -> gifAttached = call.enabled
                fake.helperBot.id -> helperAttached = call.enabled
            }
            FakeTelegram.BOOL_TRUE
        }
        server.rpc.on(GetAttachMenuBot.CONSTRUCTOR_ID) { request ->
            val call = GetAttachMenuBot.decode(request)
            attachBotCalls += call
            val id = (call.bot as? InputUser)?.userId
            val bot = attachEntries().firstOrNull { it.botId == id } ?: return@on FakeAnswer.Error(400, "BOT_INVALID")
            FakeAnswer.Result(AttachMenuBotsBot(bot, listOf(fake.helperBot, gifBot)))
        }
        server.rpc.on(CanSendMessage.CONSTRUCTOR_ID) { request ->
            val call = CanSendMessage.decode(request)
            canWriteCalls += call
            if ((call.bot as? InputUser)?.userId == fake.helperBot.id && helperWriteAllowed) FakeTelegram.BOOL_TRUE else FakeTelegram.BOOL_FALSE
        }
        server.rpc.on(AllowSendMessage.CONSTRUCTOR_ID) { request ->
            val call = AllowSendMessage.decode(request)
            allowWriteCalls += call
            if ((call.bot as? InputUser)?.userId != fake.helperBot.id) return@on FakeAnswer.Error(400, "BOT_INVALID")
            helperWriteAllowed = true
            FakeAnswer.Result(Updates(emptyList(), listOf(fake.helperBot), emptyList(), NOW, 0))
        }
        server.rpc.on(InvokeWebViewCustomMethod.CONSTRUCTOR_ID) { request ->
            val call = InvokeWebViewCustomMethod.decode(request)
            customMethodCalls += call
            if (call.customMethod == "echo") FakeAnswer.Result(DataJSON(call.params.data)) else FakeAnswer.Error(400, "METHOD_INVALID")
        }
        server.rpc.on(GetBotApp.CONSTRUCTOR_ID) { request ->
            val call = GetBotApp.decode(request)
            botAppCalls += call
            val asked = call.app as? InputBotAppShortName ?: return@on FakeAnswer.Error(400, "BOT_APP_INVALID")
            if ((asked.botId as? InputUser)?.userId != fake.helperBot.id) return@on FakeAnswer.Error(400, "BOT_APP_INVALID")
            when (asked.shortName) {
                GAME_APP -> if (call.hash == GAME_HASH) {
                    FakeAnswer.Result(MessagesBotApp(app = BotAppNotModified))
                } else {
                    FakeAnswer.Result(MessagesBotApp(app = BotApp(id = GAME_ID, accessHash = GAME_ACCESS, shortName = GAME_APP, title = "Game", description = "a little game", photo = PhotoEmpty(0), hash = GAME_HASH)))
                }
                QUIZ_APP -> FakeAnswer.Result(
                    MessagesBotApp(
                        inactive = true,
                        requestWriteAccess = true,
                        app = BotApp(id = QUIZ_ID, accessHash = QUIZ_ACCESS, shortName = QUIZ_APP, title = "Quiz", description = "questions", photo = PhotoEmpty(0), hash = 1),
                    ),
                )
                else -> FakeAnswer.Error(400, "BOT_APP_SHORTNAME_INVALID")
            }
        }
        server.rpc.on(RequestAppWebView.CONSTRUCTOR_ID) { request ->
            val call = RequestAppWebView.decode(request)
            appWebViewRequests += call
            val app = call.app as? InputBotAppID ?: return@on FakeAnswer.Error(400, "BOT_APP_INVALID")
            when (app.id to app.accessHash) {
                GAME_ID to GAME_ACCESS -> FakeAnswer.Result(WebViewResultUrl(url = "$GAME_URL#tgWebAppData=game${call.startParam.orEmpty()}"))
                QUIZ_ID to QUIZ_ACCESS -> FakeAnswer.Result(WebViewResultUrl(url = "$QUIZ_URL#tgWebAppData=quiz"))
                else -> FakeAnswer.Error(400, "BOT_APP_INVALID")
            }
        }
        server.rpc.on(ProlongWebView.CONSTRUCTOR_ID) { request ->
            val call = ProlongWebView.decode(request)
            webAppProlongs += call
            if (webAppForgotten || call.queryId != WEB_APP_QUERY) FakeAnswer.Error(400, "QUERY_ID_INVALID") else FakeTelegram.BOOL_TRUE
        }
        server.rpc.on(SendWebViewData.CONSTRUCTOR_ID) { request ->
            webAppData += SendWebViewData.decode(request)
            FakeAnswer.Result(Updates(emptyList(), emptyList(), emptyList(), NOW, 0))
        }
    }

    private fun attachMenuBots() = attachEntries().filter { !it.inactive || it.botId != fake.helperBot.id }

    private fun attachEntries() = listOf(
        AttachMenuBot(
            inactive = !helperAttached,
            showInAttachMenu = true,
            showInSideMenu = true,
            botId = fake.helperBot.id,
            shortName = "Helper app",
            peerTypes = listOf(AttachMenuPeerTypeSameBotPM, AttachMenuPeerTypePM, AttachMenuPeerTypeChat),
            icons = emptyList(),
        ),
        AttachMenuBot(
            inactive = !gifAttached,
            requestWriteAccess = true,
            showInAttachMenu = true,
            botId = gifBot.id,
            shortName = "Gif studio",
            peerTypes = listOf(AttachMenuPeerTypePM, AttachMenuPeerTypeBotPM),
            icons = emptyList(),
        ),
    )

    private fun attachHash() = ATTACH_HASH + (if (gifAttached) 1 else 0) + (if (helperAttached) 0 else 2)

    companion object {
        const val GIF_BOT_ID = 506L
        const val CITY_ID = 9_000
        const val MENU_ID = 9_001
        const val QUESTION_ID = 9_002
        const val FIRST_QUERY = 7_001L
        const val SECOND_QUERY = 7_002L
        const val NEXT_OFFSET = "page2"
        const val START_PARAMETER = "inline-setup"
        const val CACHE_TIME = 300

        const val ICON_ID = 5_555L

        const val LOGIN_URL = "https://example.com/login"
        const val PLAIN_URL = "https://example.com/plain"
        const val LOGGED_IN_URL = "https://example.com/login?id=1&hash=abc"

        const val ASKING_LOGIN = 1
        const val KNOWN_LOGIN = 2
        const val PLAIN_LOGIN = 3

        const val APP_URL = "https://example.com/app"
        const val ORDER_URL = "https://example.com/order"
        const val WEB_APP_QUERY = 8_001L

        const val MENU_APP_URL = "https://example.com/menu"
        const val ATTACH_URL = "https://example.com/attach"
        const val SIDE_URL = "https://example.com/side"
        const val MAIN_URL = "https://example.com/main"
        const val GIF_APP_URL = "https://example.com/gifs"

        const val ATTACH_HASH = 4_242L

        const val GAME_APP = "game"
        const val GAME_ID = 9_100L
        const val GAME_ACCESS = 9_101L
        const val GAME_HASH = 9_102L
        const val GAME_URL = "https://example.com/game"
        const val QUIZ_APP = "quiz"
        const val QUIZ_ID = 9_200L
        const val QUIZ_ACCESS = 9_201L
        const val QUIZ_URL = "https://example.com/quiz"
    }
}
