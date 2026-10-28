package app.miogram

import app.miogram.FakeWallpapers.Companion.DARK_PATTERN_ID
import app.miogram.FakeWallpapers.Companion.FILL_ID
import app.miogram.FakeWallpapers.Companion.GRADIENT_ID
import app.miogram.FakeWallpapers.Companion.HASH
import app.miogram.FakeWallpapers.Companion.IMAGE_ID
import app.miogram.FakeWallpapers.Companion.PATTERN_ID
import app.miogram.FakeWallpapers.Companion.UPLOADED_ID
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.WallpapersRepository
import app.miogram.data.telegram.settingsOf
import cn.ellerotta.core.model.ServiceKind
import cn.ellerotta.core.model.Wallpaper
import cn.ellerotta.core.model.WallpaperKind
import cn.ellerotta.core.model.NO_WALLPAPER
import cn.ellerotta.core.model.PLAIN_WALLPAPER
import cn.ellerotta.ktproto.api.types.InputWallPaper
import cn.ellerotta.ktproto.api.types.InputWallPaperNoFile
import cn.ellerotta.ktproto.api.types.WallPaperSettings
import cn.ellerotta.ktproto.tl.decode
import cn.ellerotta.ktproto.tl.toByteArray
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class WallpapersRepositoryTest {
    @Test
    fun listPreviewsInstallAndKeep(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                val wallpapers = repos.wallpapers

                val list = wallpapers.wallpapers()
                assertEquals(listOf(0L), fake.wallpapers.getCalls.map { it.hash })
                assertEquals(listOf("wp$PATTERN_ID", "wp$DARK_PATTERN_ID", "wp$IMAGE_ID", "wp$FILL_ID", "wp$GRADIENT_ID"), list.map { it.key })
                val pattern = list[0]
                assertEquals(WallpaperKind.Pattern, pattern.kind)
                assertEquals(listOf(0xdbddbb, 0x6ba587, 0xd5d88d, 0x88b884), pattern.colors)
                assertEquals(50, pattern.intensity)
                val dark = list[1]
                assertTrue(dark.dark)
                assertEquals(-60, dark.intensity)
                assertEquals(45, dark.rotation)
                assertEquals(WallpaperKind.Image, list[2].kind)
                assertTrue(list[2].blur && list[2].motion)
                assertEquals(WallpaperKind.Fill, list[3].kind)
                assertEquals(listOf(0x7fa381), list[3].colors)
                assertEquals(90, list[4].rotation)
                assertEquals(list, wallpapers.wallpapers())
                assertEquals(listOf(0L, HASH), fake.wallpapers.getCalls.map { it.hash })

                assertContentEquals(fake.wallpapers.patternBytes, File(assertNotNull(wallpapers.preview(pattern.key))).readBytes())
                assertContentEquals(fake.wallpapers.thumbBytes, File(assertNotNull(wallpapers.preview(list[2].key))).readBytes())
                assertNull(wallpapers.preview(list[3].key))
                assertEquals(listOf("$PATTERN_ID", "$IMAGE_ID@m"), fake.wallpapers.downloads)
                wallpapers.preview(pattern.key)
                assertEquals(2, fake.wallpapers.downloads.size)

                fake.wallpapers.expireReference()
                assertContentEquals(fake.wallpapers.patternBytes, File(assertNotNull(wallpapers.preview(dark.key))).readBytes())

                wallpapers.choose(pattern.key)
                val install = fake.wallpapers.installCalls.single()
                assertEquals(PATTERN_ID to PATTERN_ID + 1, (install.wallpaper as InputWallPaper).let { it.id to it.accessHash })
                val sent = install.settings
                assertEquals(listOf(0xdbddbb, 0x6ba587, 0xd5d88d, 0x88b884), listOf(sent.backgroundColor, sent.secondBackgroundColor, sent.thirdBackgroundColor, sent.fourthBackgroundColor))
                assertEquals(50, sent.intensity)
                assertEquals(0, sent.rotation)
                assertTrue(flagsOf(sent) and FLAG_SECOND_COLOR_AND_ROTATION != 0)
                val chosen = assertNotNull(wallpapers.chosen.value)
                assertEquals(pattern, chosen.wallpaper)
                assertContentEquals(fake.wallpapers.patternBytes, File(assertNotNull(chosen.file)).readBytes())

                wallpapers.choose(dark.key)
                val darkSettings = fake.wallpapers.installCalls.last().settings
                assertEquals(Triple(0x1b2836, 0x5f7f9c, 45), Triple(darkSettings.backgroundColor, darkSettings.secondBackgroundColor, darkSettings.rotation))
                assertEquals(-60, darkSettings.intensity)
                assertNull(darkSettings.thirdBackgroundColor)

                wallpapers.choose(list[2].key)
                val imageSettings = fake.wallpapers.installCalls.last().settings
                assertTrue(imageSettings.blur && imageSettings.motion)
                assertNull(imageSettings.backgroundColor)
                assertEquals(0, flagsOf(imageSettings) and FLAG_SECOND_COLOR_AND_ROTATION)
                assertTrue("$IMAGE_ID" in fake.wallpapers.downloads)
                assertContentEquals(fake.wallpapers.imageBytes, File(assertNotNull(wallpapers.chosen.value?.file)).readBytes())

                val installs = fake.wallpapers.installCalls.size
                wallpapers.choose(list[4].key)
                assertEquals(installs, fake.wallpapers.installCalls.size)
                assertEquals(list[4], wallpapers.chosen.value?.wallpaper)
                assertNull(wallpapers.chosen.value?.file)
                assertFalse(File(dir, "wallpapers/chosen_$IMAGE_ID").exists())

                wallpapers.choose(pattern.key)
                val again = WallpapersRepository(repos.telegram)
                assertEquals(wallpapers.chosen.value, again.chosen.value)
                again.wallpapers()
                assertEquals(HASH, fake.wallpapers.getCalls.last().hash)

                wallpapers.choose(null)
                assertNull(wallpapers.chosen.value)
                wallpapers.choose(pattern.key)
                wallpapers.forget()
                assertNull(wallpapers.chosen.value)
                assertFalse(File(dir, "wallpapers").exists())
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun editedSettingsInstallAndKeep(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                val wallpapers = repos.wallpapers
                val list = wallpapers.wallpapers()

                assertContentEquals(fake.wallpapers.imageBytes, File(assertNotNull(wallpapers.file(list[2].key))).readBytes())
                assertNull(wallpapers.file(list[3].key))

                val edited = list[0].copy(colors = listOf(0x4f9de0, 0x2b2b2b), rotation = 135, intensity = -30, motion = true)
                wallpapers.choose(edited.key, edited)
                val sent = fake.wallpapers.installCalls.single().settings
                assertEquals(listOf(0x4f9de0, 0x2b2b2b, null, null), listOf(sent.backgroundColor, sent.secondBackgroundColor, sent.thirdBackgroundColor, sent.fourthBackgroundColor))
                assertEquals(Triple(135, -30, true), Triple(sent.rotation, sent.intensity, sent.motion))
                assertEquals(edited, wallpapers.chosen.value?.wallpaper)
                assertEquals(edited, WallpapersRepository(repos.telegram).chosen.value?.wallpaper)

                val fill = list[3].copy(colors = listOf(0xfec496), motion = true)
                wallpapers.choose(fill.key, fill)
                assertEquals(1, fake.wallpapers.installCalls.size)
                assertEquals(fill, WallpapersRepository(repos.telegram).chosen.value?.wallpaper)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun anOwnPhotoUploadsAndInstalls(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                val wallpapers = repos.wallpapers
                wallpapers.wallpapers()
                val jpeg = ByteArray(5000) { (it * 7).toByte() }
                val path = wallpapers.importPhoto(jpeg)
                assertContentEquals(jpeg, File(path).readBytes())

                wallpapers.choosePhoto(path, Wallpaper("photo", WallpaperKind.Image, blur = true, motion = true))
                val upload = fake.wallpapers.uploadCalls.single()
                assertEquals(Triple(false, "image/jpeg", true), Triple(upload.forChat, upload.mimeType, upload.settings.blur && upload.settings.motion))
                assertEquals(UPLOADED_ID, (fake.wallpapers.installCalls.single().wallpaper as InputWallPaper).id)

                val chosen = assertNotNull(wallpapers.chosen.value)
                assertEquals(listOf("wp$UPLOADED_ID", WallpaperKind.Image, true, true), chosen.wallpaper.let { listOf(it.key, it.kind, it.blur, it.motion) })
                assertContentEquals(jpeg, File(assertNotNull(chosen.file)).readBytes())
                assertTrue(fake.wallpapers.downloads.isEmpty())

                assertEquals("wp$UPLOADED_ID", wallpapers.wallpapers().last().key)
                assertEquals(HASH, fake.wallpapers.getCalls.last().hash)
                assertEquals(chosen, WallpapersRepository(repos.telegram).chosen.value)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun aChatsOwnWallpaper(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                val wallpapers = repos.wallpapers
                val alice = "u${fake.alice.id}"
                repos.dialogs.dialogs()
                val list = wallpapers.wallpapers()

                assertTrue(wallpapers.allowsOwn(alice))
                assertFalse(wallpapers.allowsOwn("u${fake.selfId}"))
                assertFalse(wallpapers.allowsOwn("ch1"))
                wallpapers.loadChat(alice)
                assertNull(wallpapers.chats.value[alice])

                coroutineScope {
                    val live = Channel<ChatEvent>(Channel.UNLIMITED)
                    val listening = launch { repos.updates.events().collect { live.send(it) } }
                    delay(300)

                    val edited = list[0].copy(intensity = 70, motion = true)
                    wallpapers.setChat(alice, edited.key, edited)
                    val call = fake.wallpapers.setChatCalls.single()
                    assertEquals(PATTERN_ID to PATTERN_ID + 1, (call.wallpaper as InputWallPaper).let { it.id to it.accessHash })
                    assertEquals(70 to true, call.settings?.intensity to call.settings?.motion)
                    assertFalse(call.forBoth || call.revert)
                    val shown = assertNotNull(wallpapers.chats.value[alice])
                    assertEquals(edited, shown.wallpaper)
                    assertContentEquals(fake.wallpapers.patternBytes, File(assertNotNull(shown.file)).readBytes())
                    assertNull(wallpapers.chosen.value)
                    assertEquals(ChatEvent.ChatWallpaperChanged(alice), withTimeout(10_000) { nextWallpaper(live) })
                    listening.cancel()
                }
                val own = repos.messages.history(alice).single { it.wallpaper != null }
                assertTrue(own.out)
                assertEquals(ServiceKind.Wallpaper, own.service)
                assertEquals(70, own.wallpaper?.wallpaper?.intensity)

                wallpapers.setChat(alice, list[3].key, list[3])
                assertEquals(FILL_ID, (fake.wallpapers.setChatCalls.last().wallpaper as InputWallPaperNoFile).id)
                assertEquals(list[3], wallpapers.chats.value[alice]?.wallpaper)
                assertNull(wallpapers.chats.value[alice]?.file)
                assertFalse(File(dir, "wallpapers/chat_$PATTERN_ID").exists())

                val jpeg = ByteArray(5000) { (it * 7).toByte() }
                wallpapers.setChatPhoto(alice, wallpapers.importPhoto(jpeg), Wallpaper("photo", WallpaperKind.Image, blur = true))
                assertTrue(fake.wallpapers.uploadCalls.single().forChat)
                assertEquals(UPLOADED_ID, (fake.wallpapers.setChatCalls.last().wallpaper as InputWallPaper).id)
                assertContentEquals(jpeg, File(assertNotNull(wallpapers.chats.value[alice]?.file)).readBytes())
                assertTrue(fake.wallpapers.installCalls.isEmpty())

                wallpapers.resetChat(alice)
                val reset = fake.wallpapers.setChatCalls.last()
                assertEquals(listOf(null, false, null), listOf(reset.wallpaper, reset.revert, reset.id))
                assertNull(wallpapers.chats.value[alice])

                val offered = fake.wallpapers.offer(fake.alice.id, fake.wallpapers.wallpapers()[2])
                val message = repos.messages.history(alice).single { it.id == offered.id }
                val action = assertNotNull(message.wallpaper)
                assertFalse(message.out || action.same || action.forBoth)
                assertEquals("wp$IMAGE_ID" to true, action.wallpaper.key to action.wallpaper.blur)
                assertContentEquals(fake.wallpapers.thumbBytes, File(assertNotNull(wallpapers.preview(action.wallpaper.key))).readBytes())
                wallpapers.applyFromMessage(alice, message.id, action.wallpaper)
                val apply = fake.wallpapers.setChatCalls.last()
                assertEquals(Triple(offered.id, null, true), Triple(apply.id, apply.wallpaper, apply.settings?.blur))
                assertEquals(action.wallpaper, wallpapers.chats.value[alice]?.wallpaper)
                assertContentEquals(fake.wallpapers.imageBytes, File(assertNotNull(wallpapers.chats.value[alice]?.file)).readBytes())
                assertTrue(repos.messages.history(alice).single { it.wallpaper?.same == true }.out)

                fake.wallpapers.offer(fake.alice.id, fake.wallpapers.wallpapers()[4], forBoth = true)
                wallpapers.loadChat(alice)
                assertEquals("wp$GRADIENT_ID", wallpapers.chats.value[alice]?.wallpaper?.key)
                wallpapers.resetChat(alice)
                assertTrue(fake.wallpapers.setChatCalls.last().revert)
                assertEquals("wp$IMAGE_ID", wallpapers.chats.value[alice]?.wallpaper?.key)

                wallpapers.forget()
                assertTrue(wallpapers.chats.value.isEmpty())
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun noWallpaperStaysOnTheDevice(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                val wallpapers = repos.wallpapers
                val alice = "u${fake.alice.id}"
                repos.dialogs.dialogs()
                val list = wallpapers.wallpapers()

                wallpapers.choose(list[0].key)
                wallpapers.choose(NO_WALLPAPER)
                assertEquals(PLAIN_WALLPAPER, wallpapers.chosen.value?.wallpaper)
                assertEquals(1, fake.wallpapers.installCalls.size)
                assertEquals(PLAIN_WALLPAPER, WallpapersRepository(repos.telegram).chosen.value?.wallpaper)
                wallpapers.choose(null)
                assertNull(WallpapersRepository(repos.telegram).chosen.value)

                wallpapers.setChat(alice, list[3].key, list[3])
                val calls = fake.wallpapers.setChatCalls.size
                wallpapers.setChatPlain(alice)
                assertEquals(calls, fake.wallpapers.setChatCalls.size)
                assertEquals(PLAIN_WALLPAPER, wallpapers.chats.value[alice]?.wallpaper)
                assertEquals(PLAIN_WALLPAPER, WallpapersRepository(repos.telegram).chats.value[alice]?.wallpaper)
                wallpapers.loadChat(alice)
                assertEquals(PLAIN_WALLPAPER, wallpapers.chats.value[alice]?.wallpaper)

                wallpapers.resetChat(alice)
                assertNull(wallpapers.chats.value[alice])
                assertNull(WallpapersRepository(repos.telegram).chats.value[alice])

                wallpapers.setChatPlain(alice)
                wallpapers.setChat(alice, list[3].key, list[3])
                assertEquals(list[3], wallpapers.chats.value[alice]?.wallpaper)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    private suspend fun nextWallpaper(live: Channel<ChatEvent>): ChatEvent {
        while (true) {
            val event = live.receive()
            if (event is ChatEvent.ChatWallpaperChanged) return event
        }
    }

    @Test
    fun gradientSettingsAlwaysCarryARotation() {
        val gradient = settingsOf(Wallpaper("wp1", WallpaperKind.Fill, colors = listOf(0x112233, 0x445566)))
        assertEquals(0, gradient.rotation)
        val decoded = WallPaperSettings.decode(gradient.toByteArray())
        assertEquals(0x445566 to 0, decoded.secondBackgroundColor to decoded.rotation)
        val solid = settingsOf(Wallpaper("wp2", WallpaperKind.Fill, colors = listOf(0x112233), rotation = 90))
        assertNull(solid.rotation)
        assertEquals(0, flagsOf(solid) and FLAG_SECOND_COLOR_AND_ROTATION)
        assertNull(settingsOf(Wallpaper("wp3", WallpaperKind.Fill, colors = listOf(1))).intensity)
    }
}

private const val FLAG_SECOND_COLOR_AND_ROTATION = 1 shl 4

private fun flagsOf(settings: WallPaperSettings): Int = ByteBuffer.wrap(settings.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).getInt(4)
