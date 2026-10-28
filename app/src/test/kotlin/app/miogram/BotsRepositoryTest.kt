package app.miogram

import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.InlineResultKind
import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.BotButtonStyle
import cn.ellerotta.core.model.BotButtonMark
import cn.ellerotta.core.model.mark
import cn.ellerotta.core.model.CallbackAnswer
import cn.ellerotta.core.model.KeyboardChange
import cn.ellerotta.core.model.LinkTarget
import cn.ellerotta.core.model.keyboardMessage
import cn.ellerotta.core.model.commandsFor
import cn.ellerotta.core.model.LoginAnswer
import cn.ellerotta.core.model.LoginRequest
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.core.model.OpenWebApp
import cn.ellerotta.core.model.AttachPeerType
import cn.ellerotta.core.model.BotAppInfo
import cn.ellerotta.core.model.BotMenuButton
import cn.ellerotta.core.model.InlineSwitchWebView
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.WebAppConsent
import cn.ellerotta.core.model.WebAppMode
import cn.ellerotta.ktproto.api.types.InputBotAppID
import cn.ellerotta.ktproto.api.types.InputBotAppShortName
import cn.ellerotta.ktproto.api.types.InputPeerEmpty
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import cn.ellerotta.ktproto.api.types.InputUser
import cn.ellerotta.ktproto.api.types.InputMediaContact
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.PeerUser
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class BotsRepositoryTest {
    @Test
    fun inlineBotsAnswerPageAndSend(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val profile = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile
                fake.selfId = profile.id
                val saved = repos.dialogs.dialogs().dialogs.first { it.isSaved }.key

                val bot = assertNotNull(repos.bots.inlineBot("gifbot"))
                assertEquals("u${FakeBots.GIF_BOT_ID}", bot.key)
                assertEquals("Search GIFs", bot.placeholder)
                assertNull(repos.bots.inlineBot("carol_writer"))
                assertNull(repos.bots.inlineBot("nobody_here"))

                val first = repos.bots.inlineResults(bot, saved, "kotlin")
                val call = fake.bots.inlineCalls.single()
                assertEquals(FakeBots.GIF_BOT_ID, (call.bot as InputUser).userId)
                assertEquals(InputPeerSelf, call.peer)
                assertEquals("kotlin", call.query)
                assertEquals("", call.offset)
                assertEquals(listOf("kotlin", "photo", "cat"), first.results.map { it.id })
                assertEquals(listOf(InlineResultKind.Article, InlineResultKind.Photo, InlineResultKind.Sticker), first.results.map { it.kind })
                assertEquals("a modern language", first.results[0].description)
                assertEquals("photo${FakeTelegram.PHOTO_ID}@m", first.results[1].thumb?.key)
                assertEquals("doc${FakeTelegram.CAT_ID}", first.results[2].thumb?.key)
                assertTrue(first.results.all { it.queryId == FakeBots.FIRST_QUERY })
                assertEquals(FakeBots.NEXT_OFFSET, first.nextOffset)
                assertEquals(FakeBots.START_PARAMETER, first.switchPm?.startParameter)

                assertEquals(first, repos.bots.inlineResults(bot, saved, "kotlin"))
                assertEquals(1, fake.bots.inlineCalls.size)
                val second = repos.bots.inlineResults(bot, saved, "kotlin", first.nextOffset!!)
                assertEquals(FakeBots.NEXT_OFFSET, fake.bots.inlineCalls.last().offset)
                assertEquals(listOf("compose"), second.results.map { it.id })
                assertNull(second.nextOffset)

                assertEquals(fake.photoSizes.getValue("m").size, repos.media.sticker("photo${FakeTelegram.PHOTO_ID}@m")?.size)

                repos.bots.sendInlineResult(saved, second.results.single().queryId, "compose", replyTo = 10)
                val sent = fake.bots.inlineSends.single()
                assertEquals(FakeBots.SECOND_QUERY, sent.queryId)
                assertEquals("compose", sent.id)
                assertEquals(10, (sent.replyTo as InputReplyToMessage).replyToMsgId)
                assertTrue(sent.clearDraft)
                assertEquals(InputPeerSelf, sent.peer)

                assertEquals(listOf("gifbot"), repos.bots.recentInlineBots().map { it.username })
                assertTrue(fake.bots.topPeersCalls.single().botsInline)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun messagesSentViaABotNameIt(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val profile = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile
                fake.selfId = profile.id
                val via = fake.addSaved { id -> Message(out = true, id = id, peerId = PeerUser(profile.id), viaBotId = FakeBots.GIF_BOT_ID, date = FakeTelegram.NOW, message = "a gif") }
                val plain = fake.addSaved { id -> Message(out = true, id = id, peerId = PeerUser(profile.id), date = FakeTelegram.NOW, message = "plain") }
                val saved = repos.dialogs.dialogs().dialogs.first { it.isSaved }.key

                val history = repos.messages.history(saved).associateBy { it.id }
                assertEquals("gifbot", history.getValue(via).viaBot)
                assertNull(history.getValue(plain).viaBot)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun keyboardsMapAndCallbacksAnswer(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val profile = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile
                fake.selfId = profile.id
                val key = assertIs<LinkTarget.Chat>(repos.links.open("tg://resolve?domain=helperbot")).dialog.key
                val history = repos.messages.history(key).associateBy { it.id }

                val menu = history.getValue(FakeBots.MENU_ID)
                assertEquals(listOf(2, 2, 2, 2, 3, 3, 3), menu.buttons.map { it.size })
                assertEquals(BotButton.Callback("Ping", "ping".toByteArray()), menu.buttons[0][0])
                assertEquals(BotButton.Url("Site", "https://example.com/docs"), menu.buttons[2][0])
                assertEquals(BotButton.SwitchInline("Share", "helperbot", "hi", samePeer = false), menu.buttons[3][0])
                assertEquals(BotButton.SwitchInline("Here", "helperbot", "cats", samePeer = true), menu.buttons[3][1])
                assertEquals(BotButton.Copy("Copy code", "A1B2"), menu.buttons[4][0])
                assertEquals(BotButton.Unsupported("Play"), menu.buttons[4][1])
                assertEquals(BotButton.WebApp("Open app", FakeBots.APP_URL), menu.buttons[4][2])
                assertEquals(BotButtonMark.WebApp, menu.buttons[4][2].mark)
                assertEquals(BotButton.UserProfile("Alice", fake.alice.id, BotButtonStyle.Primary, icon = FakeBots.ICON_ID), menu.buttons[5][0])
                assertEquals(BotButton.Disabled("Sold out", BotButtonStyle.Danger), menu.buttons[5][1])
                assertEquals(BotButton.Unsupported("Pay", BotButtonStyle.Success), menu.buttons[5][2])
                assertEquals(BotButton.LoginUrl("Log in", FakeBots.LOGIN_URL, FakeBots.ASKING_LOGIN), menu.buttons[6][0])
                assertEquals(BotButtonMark.Link, menu.buttons[6][0].mark)
                assertNull(menu.keyboard)
                val city = history.getValue(FakeBots.CITY_ID)
                assertEquals(listOf(listOf<BotButton>(BotButton.Url("Map", "https://example.com/map"))), city.buttons)
                assertEquals(KeyboardChange.ForceReply(), city.keyboard)

                val shown = assertIs<KeyboardChange.Show>(history.getValue(FakeBots.QUESTION_ID).keyboard).keyboard
                assertEquals(listOf(listOf<BotButton>(BotButton.Text("Yes"), BotButton.Text("No")), listOf(BotButton.WebApp("Order", FakeBots.ORDER_URL, simple = true))), shown.rows)
                assertTrue(shown.resize && shown.singleUse)
                assertEquals("Answer the helper", shown.placeholder)
                assertEquals(history.getValue(FakeBots.QUESTION_ID), keyboardMessage(history.values.sortedBy { it.id }))

                assertEquals(CallbackAnswer("Pong"), repos.bots.callback(key, FakeBots.MENU_ID, "ping".toByteArray()))
                assertEquals(CallbackAnswer("Careful, this is an alert", alert = true), repos.bots.callback(key, FakeBots.MENU_ID, "alert".toByteArray()))
                assertEquals(CallbackAnswer(url = "https://t.me/kotlinnews"), repos.bots.callback(key, FakeBots.MENU_ID, "url".toByteArray()))
                assertEquals(CallbackAnswer(), repos.bots.callback(key, FakeBots.MENU_ID, "slow".toByteArray()))
                assertEquals(FakeBots.MENU_ID, fake.bots.callbackCalls.first().msgId)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun loginButtonsAskOpenOrFallBackToTheirSite(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val profile = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile
                fake.selfId = profile.id
                val key = assertIs<LinkTarget.Chat>(repos.links.open("tg://resolve?domain=helperbot")).dialog.key
                val logins = repos.messages.history(key).single { it.id == FakeBots.MENU_ID }.buttons[6].map { it as BotButton.LoginUrl }

                assertEquals(LoginAnswer.Ask(LoginRequest("example.com", "Helper", writeAccess = true, phone = true)), repos.bots.requestLogin(key, FakeBots.MENU_ID, logins[0]))
                val asked = fake.bots.loginRequests.single()
                assertEquals(fake.helperBot.id, (asked.peer as InputPeerUser).userId)
                assertEquals(FakeBots.MENU_ID, asked.msgId)
                assertEquals(FakeBots.ASKING_LOGIN, asked.buttonId)

                assertEquals(FakeBots.LOGGED_IN_URL, repos.bots.acceptLogin(key, FakeBots.MENU_ID, logins[0], writeAllowed = true, sharePhone = false))
                val accepted = fake.bots.loginAccepts.single()
                assertTrue(accepted.writeAllowed)
                assertFalse(accepted.sharePhoneNumber)
                assertEquals(FakeBots.ASKING_LOGIN, accepted.buttonId)
                assertEquals(FakeBots.MENU_ID, accepted.msgId)

                assertEquals(LoginAnswer.Open(FakeBots.LOGGED_IN_URL), repos.bots.requestLogin(key, FakeBots.MENU_ID, logins[1]))
                assertEquals(LoginAnswer.Plain, repos.bots.requestLogin(key, FakeBots.MENU_ID, logins[2]))
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun webAppsOpenStayAliveAndSendData(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val profile = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile
                fake.selfId = profile.id
                val key = assertIs<LinkTarget.Chat>(repos.links.open("tg://resolve?domain=helperbot")).dialog.key
                val history = repos.messages.history(key).associateBy { it.id }
                val menu = history.getValue(FakeBots.MENU_ID)

                val bot = assertNotNull(repos.bots.webAppBot(key, menu.senderId, menu.viaBot))
                assertEquals("u${fake.helperBot.id}", bot)
                assertEquals("u42", repos.bots.webAppBot("ch7", 42, null))
                assertEquals("u${fake.helperBot.id}", repos.bots.webAppBot("ch7", 42, "HelperBot"))
                assertNull(repos.bots.webAppBot("ch7", 0, null))

                val app = repos.bots.openWebApp(key, bot, menu.buttons[4][2] as BotButton.WebApp, """{"bg_color":"#ffffff"}""")
                val asked = fake.bots.webAppRequests.single()
                assertEquals(fake.helperBot.id, (asked.peer as InputPeerUser).userId)
                assertEquals(FakeBots.APP_URL, asked.url)
                assertEquals("""{"bg_color":"#ffffff"}""", asked.themeParams?.data)
                assertEquals("android", asked.platform)
                assertEquals(OpenWebApp(bot, "Helper", "${FakeBots.APP_URL}#tgWebAppData=app", queryId = FakeBots.WEB_APP_QUERY), app)
                assertTrue(repos.bots.prolongWebApp(key, app))
                assertEquals(FakeBots.WEB_APP_QUERY, fake.bots.webAppProlongs.single().queryId)
                fake.bots.webAppForgotten = true
                assertFalse(repos.bots.prolongWebApp(key, app))

                val order = assertIs<KeyboardChange.Show>(history.getValue(FakeBots.QUESTION_ID).keyboard).keyboard.rows[1][0] as BotButton.WebApp
                val simple = repos.bots.openWebApp(key, bot, order, "{}")
                assertEquals(FakeBots.ORDER_URL, fake.bots.simpleWebAppRequests.single().url)
                assertEquals(OpenWebApp(bot, "Helper", "${FakeBots.ORDER_URL}#tgWebAppData=simple", sendsData = true, buttonText = "Order"), simple)
                repos.bots.sendWebAppData(simple, "two coffees")
                val sent = fake.bots.webAppData.single()
                assertEquals(fake.helperBot.id, (sent.bot as InputUser).userId)
                assertEquals("Order" to "two coffees", sent.buttonText to sent.data)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun webAppsOpenFromMenusLinksAndInlineMode(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val profile = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile
                fake.selfId = profile.id
                val alice = "u${fake.alice.id}"
                repos.dialogs.dialogs()
                val helper = assertIs<LinkTarget.Chat>(repos.links.open("tg://resolve?domain=helperbot")).dialog.key
                val gif = "u${FakeBots.GIF_BOT_ID}"
                val theme = """{"bg_color":"#ffffff"}"""

                val button = assertNotNull(repos.bots.menuButton(helper))
                assertEquals(BotMenuButton("Menu", FakeBots.MENU_APP_URL), button)
                assertNull(repos.bots.menuButton(alice))
                val menuApp = repos.bots.openMenuApp(helper, button, theme)
                val menuCall = fake.bots.webAppRequests.single()
                assertTrue(menuCall.fromBotMenu)
                assertEquals(FakeBots.MENU_APP_URL, menuCall.url)
                assertEquals(fake.helperBot.id, (menuCall.peer as InputPeerUser).userId)
                assertEquals(fake.helperBot.id, (menuCall.bot as InputUser).userId)
                assertEquals(theme to "android", menuCall.themeParams?.data to menuCall.platform)
                assertEquals(OpenWebApp(helper, "Helper", "${FakeBots.MENU_APP_URL}#tgWebAppData=menu", queryId = FakeBots.WEB_APP_QUERY), menuApp)

                val attach = repos.bots.attachMenuBots()
                assertEquals(0L, fake.bots.attachMenuCalls.single().hash)
                val helperEntry = attach.first { it.key == helper }
                assertEquals("Helper app", helperEntry.shortName)
                assertEquals(setOf(AttachPeerType.SameBot, AttachPeerType.Users, AttachPeerType.Groups), helperEntry.peerTypes)
                assertTrue(helperEntry.attachMenu && helperEntry.sideMenu && !helperEntry.inactive)
                assertTrue(helperEntry.allowedIn(alice, PeerKind.User) && helperEntry.allowedIn(helper, PeerKind.Bot) && !helperEntry.allowedIn(gif, PeerKind.Bot))
                assertNull(helperEntry.consent)
                val gifEntry = attach.first { it.key == gif }
                assertTrue(gifEntry.inactive && gifEntry.requestWriteAccess && !gifEntry.sideMenu)
                assertEquals(WebAppConsent("Gif studio", add = true, writeAccess = true), gifEntry.consent)
                assertEquals(attach, repos.bots.attachMenuBots())
                assertEquals(FakeBots.ATTACH_HASH, fake.bots.attachMenuCalls.last().hash)

                val attachApp = repos.bots.openAttachApp(alice, helperEntry, theme)
                val attachCall = fake.bots.webAppRequests.last()
                assertFalse(attachCall.fromBotMenu)
                assertNull(attachCall.url)
                assertEquals(fake.alice.id, (attachCall.peer as InputPeerUser).userId)
                assertEquals(OpenWebApp(helper, "Helper", "${FakeBots.ATTACH_URL}#tgWebAppData=attach", queryId = FakeBots.WEB_APP_QUERY), attachApp)
                repos.bots.enableAttachBot(gif, writeAllowed = true)
                val toggle = fake.bots.attachToggles.single()
                assertEquals(Triple(FakeBots.GIF_BOT_ID, true, true), Triple((toggle.bot as InputUser).userId, toggle.enabled, toggle.writeAllowed))
                assertFalse(repos.bots.attachMenuBots().first { it.key == gif }.inactive)
                assertEquals(0L, fake.bots.attachMenuCalls.last().hash)

                val side = repos.bots.openSideMenuApp(helperEntry, theme)
                val sideCall = fake.bots.simpleWebAppRequests.single()
                assertTrue(sideCall.fromSideMenu && !sideCall.fromSwitchWebview)
                assertNull(sideCall.url)
                assertEquals(OpenWebApp(helper, "Helper", "${FakeBots.SIDE_URL}#tgWebAppData=side"), side)

                val inlineBot = assertNotNull(repos.bots.inlineBot("gifbot"))
                val switch = assertNotNull(repos.bots.inlineResults(inlineBot, alice, "cats").switchWebView)
                assertEquals(InlineSwitchWebView("Open the gif studio", FakeBots.GIF_APP_URL), switch)
                val inlineApp = repos.bots.openInlineApp(inlineBot, switch, theme)
                val inlineCall = fake.bots.simpleWebAppRequests.last()
                assertTrue(inlineCall.fromSwitchWebview && !inlineCall.fromSideMenu)
                assertEquals(FakeBots.GIF_APP_URL, inlineCall.url)
                assertEquals(OpenWebApp(gif, "Gif", "${FakeBots.GIF_APP_URL}#tgWebAppData=simple", inlineBot = "gifbot"), inlineApp)

                val main = repos.bots.openMainApp(helper, alice, "level1", WebAppMode.Compact, theme)
                val mainCall = fake.bots.mainAppRequests.single()
                assertEquals(fake.alice.id, (mainCall.peer as InputPeerUser).userId)
                assertEquals("level1", mainCall.startParam)
                assertTrue(mainCall.compact && !mainCall.fullscreen)
                assertEquals(OpenWebApp(helper, "Helper", "${FakeBots.MAIN_URL}#tgWebAppData=mainlevel1", mode = WebAppMode.Compact), main)
                repos.bots.openMainApp(helper, null, null, WebAppMode.Default, theme)
                assertEquals(InputPeerEmpty, fake.bots.mainAppRequests.last().peer)
                assertNull(fake.bots.mainAppRequests.last().startParam)

                val game = repos.bots.botApp(helper, FakeBots.GAME_APP)
                val asked = fake.bots.botAppCalls.single()
                assertEquals(FakeBots.GAME_APP to 0L, (asked.app as InputBotAppShortName).shortName to asked.hash)
                assertEquals(BotAppInfo(helper, FakeBots.GAME_APP, "Game", "a little game"), game)
                assertNull(game.consent)
                assertEquals(game, repos.bots.botApp(helper, FakeBots.GAME_APP))
                assertEquals(FakeBots.GAME_HASH, fake.bots.botAppCalls.last().hash)
                val gameApp = repos.bots.openBotApp(game, alice, "lvl", WebAppMode.Fullscreen, writeAllowed = false, theme)
                val gameCall = fake.bots.appWebViewRequests.single()
                assertEquals(FakeBots.GAME_ID to FakeBots.GAME_ACCESS, (gameCall.app as InputBotAppID).let { it.id to it.accessHash })
                assertEquals(fake.alice.id, (gameCall.peer as InputPeerUser).userId)
                assertEquals("lvl", gameCall.startParam)
                assertTrue(gameCall.fullscreen && !gameCall.compact && !gameCall.writeAllowed)
                assertEquals(OpenWebApp(helper, "Game", "${FakeBots.GAME_URL}#tgWebAppData=gamelvl", mode = WebAppMode.Fullscreen), gameApp)

                val quiz = repos.bots.botApp(helper, FakeBots.QUIZ_APP)
                assertTrue(quiz.inactive && quiz.requestWriteAccess)
                assertEquals(WebAppConsent("Quiz", writeAccess = true), quiz.consent)
                repos.bots.openBotApp(quiz, null, null, WebAppMode.Default, writeAllowed = true, theme)
                val quizCall = fake.bots.appWebViewRequests.last()
                assertTrue(quizCall.writeAllowed)
                assertEquals(fake.helperBot.id, (quizCall.peer as InputPeerUser).userId)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun commandsOfABotAndOfAGroup(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            fake.groups.inDialogs = true
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val profile = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile
                fake.selfId = profile.id

                val helper = assertIs<LinkTarget.Chat>(repos.links.open("tg://resolve?domain=helperbot")).dialog.key
                val own = repos.bots.commands(helper)
                assertEquals(listOf("/start", "/help", "/weather", "/secret"), own.map { it.text })
                assertEquals("What the helper can do", own[1].description)
                assertEquals(listOf(false, false, false, true), own.map { it.ephemeral })
                assertEquals(helper, own.last().botKey)

                repos.dialogs.dialogs()
                val group = repos.bots.commands("ch${FakeGroups.DEVS_ID}")
                assertEquals(listOf("/start@helperbot", "/help@helperbot", "/weather@helperbot", "/secret@helperbot"), group.map { it.text })
                assertEquals("u${fake.helperBot.id}", group.last().botKey)
                assertTrue(repos.bots.commands("u${fake.alice.id}").isEmpty())
                assertEquals(listOf("/help@helperbot"), commandsFor("/he", repos.bots.commands("ch${FakeGroups.DEVS_ID}")).map { it.text })
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun miniAppsAskToWriteForThePhoneAndCustomMethods(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val profile = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile
                fake.selfId = profile.id
                repos.dialogs.dialogs()
                val helper = assertIs<LinkTarget.Chat>(repos.links.open("tg://resolve?domain=helperbot")).dialog.key
                val gif = "u${FakeBots.GIF_BOT_ID}"

                assertFalse(repos.bots.canWrite(helper))
                repos.bots.allowWrite(helper)
                assertTrue(repos.bots.canWrite(helper))
                assertEquals(listOf(fake.helperBot.id, fake.helperBot.id), fake.bots.canWriteCalls.map { (it.bot as InputUser).userId })
                assertEquals(fake.helperBot.id, (fake.bots.allowWriteCalls.single().bot as InputUser).userId)

                fake.bots.helperBlocked = true
                repos.bots.sharePhone(helper)
                assertEquals(fake.helperBot.id, (fake.bots.unblocks.single().id as InputPeerUser).userId)
                val contact = fake.contactSends.single()
                assertEquals(fake.helperBot.id, (contact.peer as InputPeerUser).userId)
                val media = contact.media as InputMediaContact
                assertEquals(Triple("99966", "Test", "User ${profile.id}"), Triple(media.phoneNumber, media.firstName, media.lastName))
                repos.bots.sharePhone(helper)
                assertEquals(1, fake.bots.unblocks.size)
                assertEquals(2, fake.contactSends.size)

                assertEquals("""{"a":1}""", repos.bots.invokeCustomMethod(helper, "echo", """{"a":1}""").getOrThrow())
                assertEquals("METHOD_INVALID", repos.bots.invokeCustomMethod(helper, "nope", "{}").exceptionOrNull()?.message)
                assertEquals("echo", fake.bots.customMethodCalls.first().customMethod)

                repos.bots.attachMenuBots()
                val entry = assertNotNull(repos.bots.attachMenuBot(gif))
                assertTrue(entry.inactive && entry.attachMenu)
                assertNull(repos.bots.attachMenuBot("u${fake.alice.id}"))
                repos.bots.enableAttachBot(gif, writeAllowed = false)
                repos.bots.disableAttachBot(gif)
                val removal = fake.bots.attachToggles.last()
                assertFalse(removal.enabled)
                assertEquals(FakeBots.GIF_BOT_ID, (removal.bot as InputUser).userId)
                assertFalse(fake.bots.gifAttached)
                assertTrue(repos.bots.attachMenuBots().single { it.key == gif }.inactive)

                repos.bots.openAttachApp(helper, repos.bots.attachMenuBots().first { it.key == helper }, "{}", "ref")
                assertEquals("ref", fake.bots.webAppRequests.last().startParam)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}
