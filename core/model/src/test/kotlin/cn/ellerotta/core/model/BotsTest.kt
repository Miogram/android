package cn.ellerotta.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class BotsTest {
    @Test
    fun inlineQueriesStartWithANameAndASpace() {
        assertEquals(InlineQuery("gifbot", ""), inlineQueryOf("@gifbot "))
        assertEquals(InlineQuery("gifbot", "funny cats\nand dogs"), inlineQueryOf("@gifbot funny cats\nand dogs"))
        assertNull(inlineQueryOf("@gifbot"))
        assertNull(inlineQueryOf("hi @gifbot cats"))
        assertNull(inlineQueryOf("@gif cats"))
        assertNull(inlineQueryOf("@1gifbot cats"))
        assertEquals("gi", inlineMention("@gi"))
        assertEquals("", inlineMention("@"))
        assertNull(inlineMention("@gi f"))
        assertNull(inlineMention("gi"))
    }

    @Test
    fun telegramLinksAndTheKeyboardMessage() {
        listOf("tg://resolve?domain=a", "https://t.me/kotlinnews", "t.me/+abc", "http://telegram.me/a", "https://telegram.dog/a", "https://name.t.me").forEach { assertTrue(isTelegramUrl(it), it) }
        listOf("https://example.com", "https://t.me.example.com/a", "https://nott.me/a").forEach { assertFalse(isTelegramUrl(it), it) }

        val keyboard = KeyboardChange.Show(ReplyKeyboard(listOf(listOf(BotButton.Text("Yes")))))
        val older = MessageItem(1, "old", out = false, date = 0, keyboard = KeyboardChange.Hide)
        val shown = MessageItem(2, "q", out = false, date = 0, keyboard = keyboard)
        val own = MessageItem(3, "mine", out = true, date = 0, keyboard = KeyboardChange.Hide)
        val plain = MessageItem(4, "plain", out = false, date = 0)
        assertEquals(shown, keyboardMessage(listOf(older, shown, own, plain)))
        assertNull(keyboardMessage(listOf(plain)))
    }

    @Test
    fun commandsMatchASlashPrefix() {
        val commands = listOf(BotCommand("start", "Start"), BotCommand("help", "Help"), BotCommand("weather", "Weather", "helperbot"))
        assertEquals(commands, commandsFor("/", commands))
        assertEquals(listOf("/help"), commandsFor("/HE", commands).map { it.text })
        assertEquals(listOf("/weather@helperbot"), commandsFor("/w", commands).map { it.text })
        assertTrue(commandsFor("/help ", commands).isEmpty())
        assertTrue(commandsFor("say /help", commands).isEmpty())
        assertTrue(commandsFor("", commands).isEmpty())
    }

    @Test
    fun ephemeralCommandsAreFoundAtTheStart() {
        val commands = listOf(
            BotCommand("help", "Help", ephemeral = false, botKey = "u504"),
            BotCommand("secret", "Secret", ephemeral = true, botKey = "u504"),
            BotCommand("secret", "Secret", "otherbot", ephemeral = false, botKey = "u505"),
        )
        assertEquals("u504", ephemeralCommandOf("/secret", commands)?.botKey)
        assertEquals("u504", ephemeralCommandOf("/SECRET tell me", commands)?.botKey)
        assertNull(ephemeralCommandOf("/secret@otherbot", commands))
        assertEquals("u504", ephemeralCommandOf("/secret@helperbot", commands.take(2))?.botKey)
        assertNull(ephemeralCommandOf("/help", commands))
        assertNull(ephemeralCommandOf("/secretive", commands))
        assertNull(ephemeralCommandOf("say /secret", commands))
        assertNull(ephemeralCommandOf("/secret", emptyList()))
    }
}
