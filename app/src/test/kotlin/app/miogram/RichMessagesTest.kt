package app.miogram

import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.LinkTarget
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.ktproto.api.types.InputMessageEntityMentionName
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputUser
import cn.ellerotta.ktproto.api.types.MessageEntityBold
import cn.ellerotta.ktproto.api.types.MessageEntityTextUrl
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class RichMessagesTest {
    @Test
    fun entitiesMapSendEditAndOpen(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("9996624242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "22222")).profile.id
                repos.dialogs.dialogs()

                val id = fake.addRichMessage()
                val message = repos.messages.history("self").single { it.id == id }
                fun part(entity: TextEntity) = entity.part(message.text)
                assertEquals(2 + 1, message.entities.first { it.type == EntityType.Bold }.offset)
                assertEquals(
                    listOf(
                        "bold" to EntityType.Bold,
                        "italic" to EntityType.Italic,
                        "under" to EntityType.Underline,
                        "strike" to EntityType.Strike,
                        "spoiler" to EntityType.Spoiler,
                        "code" to EntityType.Code,
                        "fun main() {}" to EntityType.Pre("kotlin"),
                        "quoted words" to EntityType.Blockquote(collapsed = true),
                        "#kotlin" to EntityType.Hashtag,
                        "\$TON" to EntityType.Cashtag,
                        "/start" to EntityType.BotCommand,
                        "a@b.co" to EntityType.Email,
                        "+16502530000" to EntityType.Phone,
                        "4111111111111111" to EntityType.BankCard,
                        "Alice" to EntityType.MentionName(501),
                        "tomorrow" to EntityType.FormattedDate(FakeTelegram.NOW + 86_400, relative = true),
                    ),
                    message.entities.map { part(it) to it.type },
                )

                val text = "bold link Alice"
                val sent = listOf(TextEntity(0, 4, EntityType.Bold), TextEntity(5, 4, EntityType.TextUrl("https://example.com")), TextEntity(10, 5, EntityType.MentionName(501)))
                repos.messages.send("self", text, entities = sent)
                val call = assertNotNull(fake.lastSendMessage)
                val entities = assertNotNull(call.entities)
                assertEquals(listOf(0 to 4, 5 to 4, 10 to 5), entities.map { e -> when (e) {
                    is MessageEntityBold -> e.offset to e.length
                    is MessageEntityTextUrl -> e.offset to e.length
                    is InputMessageEntityMentionName -> e.offset to e.length
                    else -> -1 to -1
                } })
                assertEquals(9L, assertIs<InputUser>(assertIs<InputMessageEntityMentionName>(entities[2]).userId).accessHash)
                val echoed = repos.messages.history("self").last()
                assertEquals(text to sent, echoed.text to echoed.entities)

                val italic = listOf(TextEntity(0, 4, EntityType.Italic))
                repos.messages.edit("self", echoed.id, "bold link", italic)
                assertEquals(italic, repos.messages.history("self").last().entities)
                repos.messages.edit("self", echoed.id, "plain")
                assertEquals(emptyList(), repos.messages.history("self").last().entities)

                repos.messages.remind("self", id, FakeTelegram.NOW + 86_400)
                val forward = assertNotNull(fake.lastForward)
                assertEquals(listOf(id) to FakeTelegram.NOW + 86_400, forward.id to forward.scheduleDate)
                assertEquals(InputPeerSelf, forward.toPeer)

                val alice = assertIs<LinkTarget.Chat>(repos.links.open("tg://user?id=501")).dialog
                assertEquals("u501" to "Alice Example", alice.key to alice.title)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun customEmojiDocumentsComeInBatches(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("9996624242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "22222")).profile.id

                val ids = listOf(FakeTelegram.EMOJI_STAR, FakeTelegram.EMOJI_HEART, FakeTelegram.EMOJI_MISSING, FakeTelegram.EMOJI_STAR)
                val found = repos.stickers.customEmoji(ids)
                assertEquals(listOf(ids.distinct()), fake.customEmojiCalls.toList())
                val star = found.getValue(FakeTelegram.EMOJI_STAR)
                assertEquals(Triple("\u2B50", true, true), Triple(star.emoji, star.textColor, star.animated))
                assertEquals(false, found.getValue(FakeTelegram.EMOJI_HEART).textColor)
                assertEquals(setOf(FakeTelegram.EMOJI_STAR, FakeTelegram.EMOJI_HEART), found.keys)
                assertContentEquals(fake.stickerBytes, repos.media.sticker(star.key))

                fake.customEmojiCalls.clear()
                assertEquals(found, repos.stickers.customEmoji(listOf(FakeTelegram.EMOJI_STAR, FakeTelegram.EMOJI_HEART)))
                assertEquals(emptyList(), fake.customEmojiCalls.toList())
                repos.stickers.customEmoji((1_000L until 1_450L).toList())
                assertEquals(listOf(200, 200, 50), fake.customEmojiCalls.map { it.size })

                fake.expireStickerReference()
                fake.customEmojiCalls.clear()
                java.io.File(dir, "stickers").deleteRecursively()
                assertContentEquals(fake.webpBytes, repos.media.sticker(found.getValue(FakeTelegram.EMOJI_HEART).key))
                assertEquals(listOf(listOf(FakeTelegram.EMOJI_HEART)), fake.customEmojiCalls.toList())
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}
