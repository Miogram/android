package app.miogram

import app.miogram.data.telegram.SignInResult
import app.miogram.feature.chat.ChatTyping
import cn.ellerotta.core.model.ChatAction
import cn.ellerotta.core.model.UploadKind
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.SendMessageCancelAction
import cn.ellerotta.ktproto.api.types.SendMessageRecordAudioAction
import cn.ellerotta.ktproto.api.types.SendMessageTypingAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadPhotoAction
import cn.ellerotta.ktproto.api.types.TypeSendMessageAction
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class TypingTest {
    @Test
    fun actionsReachTheServerThrottled(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                repos.dialogs.dialogs()
                var clock = 0L
                val typing = ChatTyping(scope, { repos.messages.setTyping("u501", it) }, { clock })
                suspend fun sentUntil(count: Int): List<TypeSendMessageAction> {
                    while (fake.typingCalls.size < count) delay(20)
                    delay(100)
                    return fake.typingCalls.map { it.action }
                }

                typing.typed("h")
                clock = 1_000
                typing.typed("he")
                clock = 5_000
                typing.typed("hel")
                assertEquals(listOf(SendMessageTypingAction, SendMessageTypingAction), sentUntil(2))
                typing.typed("")
                typing.typed("")
                assertEquals(SendMessageCancelAction, sentUntil(3).last())
                assertEquals(3, fake.typingCalls.size)

                clock = 6_000
                typing.typed("a")
                typing.uploading(ChatAction.Upload(UploadKind.Photo, 0))
                clock = 7_000
                typing.uploading(ChatAction.Upload(UploadKind.Photo, 40))
                clock = 11_000
                typing.uploading(ChatAction.Upload(UploadKind.Photo, 80))
                typing.cancel()
                val uploads = sentUntil(7).drop(3)
                assertEquals(listOf(SendMessageTypingAction, SendMessageUploadPhotoAction(0), SendMessageUploadPhotoAction(80), SendMessageCancelAction), uploads)

                typing.startRecording()
                assertEquals(SendMessageRecordAudioAction, sentUntil(8).last())
                typing.stopRecording()
                typing.cancel()
                assertEquals(SendMessageCancelAction, sentUntil(9).last())
                assertEquals(9, fake.typingCalls.size)
                assertEquals(setOf(501L), fake.typingCalls.map { (it.peer as InputPeerUser).userId }.toSet())
                assertEquals(UploadKind.Photo, repos.messages.uploadKindOf("image/jpeg", 1_000))
                assertEquals(UploadKind.Video, repos.messages.uploadKindOf("video/mp4", 1_000))
                assertEquals(UploadKind.File, repos.messages.uploadKindOf("image/jpeg", 20L * 1024 * 1024))
            }
            scope.cancel()
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}
