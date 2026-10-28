package app.miogram

import app.miogram.data.telegram.SignInResult
import app.miogram.feature.chat.ChatTranslations
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.model.plainText
import cn.ellerotta.ktproto.session.RpcException
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class TranslationsRepositoryTest {
    @Test
    fun textsAndRichMessagesTranslateAndErrorsComeBack(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                val text = fake.translations.addText("hello world")
                val rich = fake.translations.addRich(listOf("first", "second"))
                val saved = repos.dialogs.dialogs().dialogs.first { it.isSaved }.key

                val translated = repos.translations.translate(saved, text, "ru")
                assertEquals("ru", translated.language)
                assertEquals("ru: hello world", translated.text.text)
                assertEquals(listOf(TextEntity(4, 5, EntityType.Bold)), translated.text.entities)
                val call = fake.translations.textCalls.single()
                assertEquals(listOf(text) to "ru", call.id to call.toLang)
                assertNull(call.text)

                val article = assertNotNull(repos.translations.translateRich(saved, rich, "ru", bot = null).article)
                assertEquals("ru: first\nru: second", article.plainText())
                assertEquals(listOf(rich), fake.translations.richCalls.single().id)

                val invalid = runCatching { repos.translations.translate(saved, text, "klingon") }.exceptionOrNull()
                assertEquals("TO_LANG_INVALID", assertIs<RpcException>(invalid).errorMessage)
                fake.translations.quota = 3
                val quota = runCatching { repos.translations.translate(saved, text, "de") }.exceptionOrNull()
                assertEquals("QUOTA_EXCEEDED", assertIs<RpcException>(quota).errorMessage)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun translationsAreCachedPerMessageUntilTheTextChanges(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                val id = fake.translations.addText("hello world")
                val saved = repos.dialogs.dialogs().dialogs.first { it.isSaved }.key
                val message = repos.messages.history(saved).single { it.id == id }
                val translations = ChatTranslations(saved, repos.translations) { block -> scope.launch { block() } }
                suspend fun shown(expected: String?) {
                    while (translations.shown.value[id]?.text?.text != expected) delay(20)
                }

                translations.translate(message, "ru")
                shown("ru: hello world")
                assertEquals("ru: hello world", translations.apply(listOf(message), translations.shown.value).single().translation?.text?.text)
                translations.showOriginal(id)
                shown(null)
                assertNull(translations.apply(listOf(message), translations.shown.value).single().translation)

                translations.translate(message, "ru")
                shown("ru: hello world")
                assertEquals(1, fake.translations.textCalls.size)

                translations.edited(message.copy(edited = true))
                assertTrue(id in translations.shown.value)
                val changed = message.copy(text = "hello there")
                translations.edited(changed)
                shown(null)
                translations.translate(changed, "ru")
                shown("ru: hello world")
                assertEquals(2, fake.translations.textCalls.size)
                translations.translate(changed, "de")
                shown("de: hello world")
                assertEquals(3, fake.translations.textCalls.size)
            }
            scope.cancel()
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}
