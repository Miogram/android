package app.miogram

import app.miogram.data.telegram.JoinBotCheckException
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.entitiesOf
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.model.linkTarget
import cn.ellerotta.core.model.AttachPeerType
import cn.ellerotta.core.model.LinkTarget
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.WebAppMode
import cn.ellerotta.core.model.Membership
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.MessageEntityBold
import cn.ellerotta.ktproto.api.types.MessageEntityUrl
import cn.ellerotta.ktproto.session.RpcException
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class LinksRepositoryTest {
    @Test
    fun resolvesJoinsAndStarts(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("9996624242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "22222")).profile.id

                val carol = assertIs<LinkTarget.Chat>(repos.links.open("https://t.me/carol_writer?text=%40helperbot+hi"))
                assertEquals("u503" to "Carol Writer", carol.dialog.key to carol.dialog.title)
                assertEquals(" @helperbot hi", carol.draft)
                assertTrue(assertIs<LinkTarget.Chat>(repos.links.open("tg://resolve?domain=carol_writer&profile")).profile)
                assertEquals("u503", assertIs<LinkTarget.Chat>(repos.links.open("t.me/+15550001111")).dialog.key)
                assertEquals("PHONE_NOT_OCCUPIED", assertFailsWith<RpcException> { repos.links.open("tg://resolve?phone=1999") }.errorMessage)
                assertEquals("USERNAME_NOT_OCCUPIED", assertFailsWith<RpcException> { repos.links.open("t.me/nobody_here") }.errorMessage)

                val bot = assertIs<LinkTarget.Chat>(repos.links.open("https://t.me/helperbot?start=ref-42"))
                assertEquals(PeerKind.Bot to "ref-42", bot.dialog.kind to bot.startParameter)
                assertNull(assertIs<LinkTarget.Chat>(repos.links.open("https://t.me/carol_writer?start=ref-42")).startParameter)
                repos.links.startBot(bot.dialog.key, "ref-42")
                val started = assertNotNull(fake.lastStartBot)
                assertEquals("ref-42" to 504L, started.startParam to (started.peer as InputPeerUser).userId)

                val post = assertIs<LinkTarget.Chat>(repos.links.open("https://t.me/kotlinnews/30"))
                assertEquals("ch${FakeTelegram.NEWS_ID}" to 30, post.dialog.key to post.messageId)
                assertEquals(Membership.CanJoin, repos.channels.info(post.dialog.key)?.membership)
                val around = repos.messages.historyAround(post.dialog.key, 30, 50)
                assertEquals((6..55).toList(), around.map { it.id })
                assertEquals((56..105).toList(), repos.messages.historyAfter(post.dialog.key, 55, 50).map { it.id })
                assertEquals((106..120).toList(), repos.messages.historyAfter(post.dialog.key, 105, 50).map { it.id })
                assertEquals(Membership.Member, repos.channels.join(post.dialog.key))
                assertTrue(FakeTelegram.NEWS_ID in fake.joinedChannels)
                assertEquals(Membership.Member, repos.channels.info(post.dialog.key)?.membership)

                assertEquals(LinkTarget.PrivateChat, repos.links.open("https://t.me/c/${FakeTelegram.BOOK_CLUB_ID}/7"))
                val invite = assertIs<LinkTarget.Invite>(repos.links.open("https://t.me/+${FakeTelegram.BOOK_CLUB_INVITE}")).invite
                assertEquals("Book club" to 42, invite.title to invite.participants)
                assertEquals("we read about kotlin", invite.about)
                assertFalse(invite.channel)
                assertTrue(assertNotNull(invite.photo).isNotEmpty())
                val joined = repos.links.joinInvite(invite.hash)
                assertEquals("ch${FakeTelegram.BOOK_CLUB_ID}" to "Book club", joined.key to joined.title)
                assertEquals(joined.key, assertIs<LinkTarget.Chat>(repos.links.open("tg://join?invite=${FakeTelegram.BOOK_CLUB_INVITE}")).dialog.key)
                val private = assertIs<LinkTarget.Chat>(repos.links.open("tg://privatepost?channel=${FakeTelegram.BOOK_CLUB_ID}&post=7"))
                assertEquals(joined.key to 7, private.dialog.key to private.messageId)
                assertEquals("INVITE_HASH_EXPIRED", assertFailsWith<RpcException> { repos.links.open("t.me/joinchat/Gone") }.errorMessage)
                assertFailsWith<JoinBotCheckException> { repos.links.joinInvite(FakeTelegram.GUARDED_INVITE) }

                val dogs = assertIs<LinkTarget.StickerSet>(repos.links.open("https://t.me/addstickers/dogs")).detail
                assertEquals("Dogs" to false, dogs.info.title to dogs.info.installed)
                assertEquals(LinkTarget.Share("https://example.com\nhave a look"), repos.links.open("t.me/share/url?url=https%3A%2F%2Fexample.com&text=have+a+look"))
                assertEquals(LinkTarget.LoginCode("12345"), repos.links.open("https://t.me/login/12345"))
                assertEquals(LinkTarget.Unsupported("tg://proxy?server=1.2.3.4&port=443&secret=ee00"), repos.links.open("tg://proxy?server=1.2.3.4&port=443&secret=ee00"))
                assertEquals(LinkTarget.Unsupported("tg://need_update_for_some_feature?test=a"), repos.links.open("tg://need_update_for_some_feature?test=a"))
                assertEquals(LinkTarget.External("https://example.com/a"), repos.links.open("example.com/a"))
                assertEquals(LinkTarget.External("https://t.me/iv?url=x"), repos.links.open("https://t.me/iv?url=x"))

                val game = assertIs<LinkTarget.BotApp>(repos.links.open("https://t.me/helperbot/game?startapp=lvl1&mode=compact"))
                assertEquals(Triple("u504", "game", "lvl1"), Triple(game.bot.key, game.shortName, game.startParameter))
                assertEquals(WebAppMode.Compact, game.mode)
                assertEquals("quiz", assertIs<LinkTarget.BotApp>(repos.links.open("tg://resolve?domain=helperbot&appname=quiz")).shortName)
                val main = assertIs<LinkTarget.MainApp>(repos.links.open("tg://resolve?domain=helperbot&startapp=ref&mode=fullscreen"))
                assertEquals(Triple("u504", "ref", WebAppMode.Fullscreen), Triple(main.bot.key, main.startParameter, main.mode))
                assertNull(assertIs<LinkTarget.MainApp>(repos.links.open("t.me/helperbot?startapp")).startParameter)
                assertEquals("u503", assertIs<LinkTarget.Chat>(repos.links.open("t.me/carol_writer?startapp=x")).dialog.key)
                assertEquals("u503", assertIs<LinkTarget.Chat>(repos.links.open("t.me/carol_writer/game")).dialog.key)
                val attach = assertIs<LinkTarget.AttachApp>(repos.links.open("https://t.me/+15550001111?attach=gifbot&startattach=x"))
                assertEquals(Triple("u${FakeBots.GIF_BOT_ID}", "x", "u503"), Triple(attach.bot.key, attach.startParameter, attach.chat?.key))
                val choosing = assertIs<LinkTarget.AttachApp>(repos.links.open("tg://resolve?domain=gifbot&startattach&choose=users+groups"))
                assertEquals(setOf(AttachPeerType.Users, AttachPeerType.Groups), choosing.choose)
                assertNull(choosing.chat)
                assertEquals("u503", assertIs<LinkTarget.Chat>(repos.links.open("t.me/carol_writer?startattach")).dialog.key)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun entitiesBecomeTappableLinks(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("9996624242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "22222")).profile.id
                val id = fake.addEntityMessage()
                val message = repos.messages.history("self").single { it.id == id }
                val parts = message.entities.mapNotNull { entity -> linkTarget(message.text, entity)?.let { entity.part(message.text) to it } }
                assertEquals(
                    listOf(
                        "@carol_writer" to "https://t.me/carol_writer",
                        "this post" to "https://t.me/kotlinnews/30",
                        "example.com/docs" to "example.com/docs",
                    ),
                    parts,
                )
                assertEquals(7, message.entities.first().offset)
                assertEquals(
                    listOf(TextEntity(0, 5, EntityType.Bold), TextEntity(3, 2, EntityType.Url)),
                    entitiesOf("short", listOf(MessageEntityUrl(3, 10), MessageEntityBold(0, 5), MessageEntityUrl(7, 2))),
                )
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}
