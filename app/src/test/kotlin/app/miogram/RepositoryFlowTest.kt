package app.miogram

import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.OutgoingMedia
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.Birthday
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.CodeType
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.GlobalPrivacy
import cn.ellerotta.core.model.PrivacyBase
import cn.ellerotta.core.model.PrivacyKey
import cn.ellerotta.core.model.PrivacySetting
import cn.ellerotta.core.model.preview
import cn.ellerotta.ktproto.client.UploadSource
import cn.ellerotta.ktproto.session.RpcException
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class RepositoryFlowTest {
    @Test
    fun connectLoginChatDownloadProfileLogout(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                val config = repos.telegram.connect(12345, "hash")
                assertEquals(2, config.thisDc)
                assertTrue(config.dcs.any { it.startsWith("dc1 ") })

                val first = repos.auth.sendCode("+999 66 1 4242")
                assertEquals(1, first.homeDc)
                assertEquals(CodeType.App, first.type)
                assertEquals(5, first.codeLength)
                val request = repos.auth.resendCode(first)
                assertEquals(CodeType.Sms, request.type)
                assertEquals(CodeType.Call, request.nextType)
                assertEquals(60, request.timeoutSeconds)
                val profile = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "11111")).profile
                fake.selfId = profile.id
                assertTrue(repos.telegram.isAuthorized)

                repos.updates.updateState.first { it != null }
                val caughtUp = async { repos.updates.events().first { it is ChatEvent.NewMessage && it.message.text == "while away" } }
                delay(300)
                fake.cluster.updates.message(fromId = 7, text = "while away", push = false)
                fake.cluster.updates.tooLong()
                caughtUp.await()
                assertEquals(1, fake.cluster.updates.differenceRequests.size)

                val live = Channel<ChatEvent>(Channel.UNLIMITED)
                val listening = launch { repos.updates.events().collect { live.send(it) } }
                delay(300)
                fake.cluster.updates.message(fromId = 501, text = "live from alice")
                val arrived = withTimeout(10_000) { nextOf<ChatEvent.NewMessage>(live) }
                assertEquals("u501", arrived.key)
                assertEquals("live from alice", arrived.message.text)
                fake.cluster.updates.deleteMessages(listOf(arrived.message.id))
                val deleted = withTimeout(10_000) { nextOf<ChatEvent.Deleted>(live) }
                assertEquals(listOf(arrived.message.id), deleted.ids)
                listening.cancel()

                val dialogs = repos.dialogs.dialogs().dialogs
                assertEquals(listOf(true, false), dialogs.map { it.isSaved })
                assertEquals(PeerKind.Saved, dialogs[0].kind)
                val alice = dialogs[1]
                assertEquals("Alice Example", alice.title)
                assertEquals("hi from alice", alice.preview.text)
                assertEquals(1, alice.unread)
                assertEquals(PeerKind.User, alice.kind)

                val folders = repos.dialogs.folders()
                assertEquals(listOf(ChatFolder.ALL, 2), folders.map { it.id })
                val archive = repos.dialogs.dialogs(archived = true).dialogs
                assertEquals(listOf("Bob"), archive.map { it.title })
                assertTrue(archive.single().archived && archive.single().contact)
                assertEquals(listOf("Bob"), folders[1].pick(dialogs + archive).map { it.title })

                repos.dialogs.setPinned(alice.key, true)
                repos.dialogs.setMuted(alice.key, true)
                repos.dialogs.markRead(alice.key)
                val changed = repos.dialogs.dialogs().dialogs.single { it.key == alice.key }
                assertTrue(changed.pinned && changed.muted)
                assertEquals(0, changed.unread)
                repos.dialogs.setArchived(alice.key, true)
                assertEquals(setOf("Alice Example", "Bob"), repos.dialogs.dialogs(archived = true).dialogs.map { it.title }.toSet())
                repos.dialogs.setArchived(alice.key, false)
                val avatar = assertNotNull(repos.media.avatar(assertNotNull(alice.avatarKey)))
                assertContentEquals(fake.avatarBytes, avatar)
                assertTrue(dir.resolve("avatars").listFiles()!!.isNotEmpty())

                repos.messages.send(dialogs[0].key, "hello from the app")
                fake.addDocumentMessage()
                val history = repos.messages.history(dialogs[0].key)
                assertEquals("hello from the app", history.first().text)
                val media = assertNotNull(history.last().media)
                assertEquals("big.bin", media.fileName)
                assertEquals(MediaKind.File, media.kind)

                fake.addStickerMessage()
                val sticker = assertNotNull(repos.messages.history(dialogs[0].key).last().media)
                assertEquals(MediaKind.Sticker, sticker.kind)
                assertTrue(sticker.animated)
                assertEquals("\uD83D\uDE00", sticker.emoji)
                assertContentEquals(fake.stickerBytes, repos.media.sticker(sticker.key))
                assertTrue(dir.resolve("stickers").listFiles()!!.isNotEmpty())

                assertEquals(listOf("Alice Example"), repos.dialogs.search("ali").map { it.title })
                val channel = repos.dialogs.search("kotl").single()
                assertEquals("Kotlin News", channel.title)
                assertEquals(PeerKind.Channel, channel.kind)
                assertEquals("@kotlinnews", channel.preview.text)
                assertEquals((71..120).toList(), repos.messages.history(channel.key).map { it.id })

                fake.addPhotoMessage()
                val photo = assertNotNull(repos.messages.history(dialogs[0].key).last().media)
                assertEquals(MediaKind.Photo, photo.kind)
                assertEquals(1280 to 960, photo.width to photo.height)
                assertEquals(listOf("m", "x", "w"), photo.sizes.map { it.type })
                val thumb = assertNotNull(photo.thumb)
                assertEquals(listOf(0xff, 0xd8), thumb.take(2).map { it.toInt() and 0xff })
                assertEquals(30 to 40, javax.imageio.ImageIO.read(thumb.inputStream()).let { it.height to it.width })
                val bubble = assertNotNull(photo.preview(780))
                assertEquals("x", bubble.key.substringAfter('@'))
                val photoFile = assertIs<DownloadState.Done>(repos.media.download(bubble).toList().last()).file
                assertContentEquals(fake.photoBytes, photoFile.readBytes())
                val downloads = fake.photoDownloads
                assertIs<DownloadState.Done>(repos.media.download(bubble).toList().single())
                assertEquals(downloads, fake.photoDownloads)
                assertContentEquals(fake.photoSizes.getValue("w"), assertIs<DownloadState.Done>(repos.media.download(photo).toList().last()).file.readBytes())
                assertEquals(listOf("x", "w"), fake.photoTypes.distinct())

                fake.addVideoMessage()
                val video = assertNotNull(repos.messages.history(dialogs[0].key).last().media)
                assertEquals(MediaKind.Video, video.kind)
                assertEquals(Triple(1280, 720, 84), Triple(video.width, video.height, video.duration))
                assertEquals("video/mp4", video.mimeType)
                assertNotNull(video.thumb)
                val poster = assertNotNull(video.preview(500))
                assertContentEquals(fake.videoThumbBytes, assertIs<DownloadState.Done>(repos.media.download(poster).toList().last()).file.readBytes())

                fake.addLinkMessage()
                val linked = repos.messages.history(dialogs[0].key).last()
                val link = assertNotNull(linked.link)
                assertEquals("Telegram APIs", link.siteName)
                assertEquals("Uploading and Downloading Files", link.title)
                assertEquals("How to transfer large data batches correctly.", link.description)
                assertEquals("photo${FakeTelegram.PHOTO_ID}", link.photo?.key)

                val visual = repos.messages.visualMedia(dialogs[0].key, linked.id)
                assertEquals(listOf(MediaKind.Photo, MediaKind.Video), visual.map { it.media?.kind })

                val original = repos.messages.history(dialogs[0].key).last()
                repos.messages.send(dialogs[0].key, "an answer", replyTo = original.id)
                val answer = repos.messages.history(dialogs[0].key).last()
                assertEquals(original.id, answer.replyTo)
                repos.messages.edit(dialogs[0].key, answer.id, "a better answer")
                val edited = repos.messages.history(dialogs[0].key).last()
                assertEquals("a better answer", edited.text)
                assertTrue(edited.edited)
                repos.messages.delete(dialogs[0].key, listOf(answer.id), revoke = true)
                assertTrue(repos.messages.history(dialogs[0].key).none { it.id == answer.id })
                assertEquals(true, fake.lastRevoke)

                val albumIds = fake.addAlbum()
                val album = repos.messages.history(dialogs[0].key).filter { it.id in albumIds }
                assertEquals(listOf(FakeTelegram.ALBUM_ID, FakeTelegram.ALBUM_ID), album.map { it.groupedId })
                assertEquals(listOf("two cats", ""), album.map { it.text })

                fake.addSavedMessages(120)
                val newest = repos.messages.history(dialogs[0].key, limit = 50)
                assertEquals("note 119", newest.last().text)
                val second = repos.messages.history(dialogs[0].key, limit = 50, before = newest.first().id)
                assertEquals(50, second.size)
                assertTrue(second.last().id < newest.first().id)
                val third = repos.messages.history(dialogs[0].key, limit = 50, before = second.first().id)
                assertTrue(third.size < 50)

                val upload = kotlin.random.Random(5).nextBytes(1_200_000)
                val sent = repos.messages.sendFile(dialogs[0].key, OutgoingMedia("report.pdf", "application/pdf", UploadSource.of(upload))).toList()
                assertEquals(upload.size.toLong(), sent.last().downloaded)
                assertContentEquals(upload, fake.lastUpload)
                val document = assertNotNull(repos.messages.history(dialogs[0].key).last().media)
                assertEquals("report.pdf", document.fileName)
                assertEquals(upload.size.toLong(), document.size)

                val recording = dir.resolve("recording.ogg").apply { writeBytes(kotlin.random.Random(9).nextBytes(20_000)) }
                val samples = List(100) { it % 32 }
                repos.messages.sendVoice(dialogs[0].key, recording, 3, samples).toList()
                assertContentEquals(recording.readBytes(), fake.lastUpload)
                val voice = assertNotNull(repos.messages.history(dialogs[0].key).last().media)
                assertEquals(MediaKind.Voice, voice.kind)
                assertEquals(3, voice.duration)
                assertEquals(samples, voice.waveform)

                val states = repos.media.download(media).toList()
                val done = assertIs<DownloadState.Done>(states.last())
                assertContentEquals(fake.cluster.fileBytes, done.file.readBytes())
                assertTrue(states.filterIsInstance<DownloadState.Progress>().size >= 3)

                val me = repos.profiles.me()
                assertEquals(profile.id, me.id)

                val own = repos.profiles.profile(null)
                assertTrue(own.isSelf)
                assertEquals("testing ktproto", own.about)
                assertEquals(Presence.Online, own.presence)
                assertEquals(listOf("tester"), own.usernames)
                val aliceProfile = repos.profiles.profile(alice.key)
                assertEquals("likes compose", aliceProfile.about)
                assertEquals(Birthday(14, 3, 1990), aliceProfile.birthday)
                assertEquals(Presence.LongAgo, aliceProfile.presence)
                assertEquals(FakeTelegram.EMOJI_HEART to FakeTelegram.EMOJI_STAR, aliceProfile.emojiStatusId to aliceProfile.patternEmojiId)
                fake.aliceWearsGift = true
                val wearing = repos.profiles.profile(alice.key)
                assertEquals(FakeTelegram.EMOJI_STAR to FakeTelegram.EMOJI_STAR, wearing.emojiStatusId to wearing.patternEmojiId)
                assertEquals(6_201L, wearing.collectibleGiftId)
                assertNull(aliceProfile.collectibleGiftId)
                fake.aliceWearsGift = false
                assertEquals(listOf("photo${FakeTelegram.PHOTO_ID}"), repos.profiles.photos(alice.key).map { it.key })
                assertEquals("Profile Song 1", aliceProfile.music?.title)
                assertEquals(listOf("Profile Song 1", "Profile Song 2"), repos.profiles.music(alice.key).map { it.title })

                val sessions = repos.auth.sessions()
                assertEquals(listOf(true, false, false), sessions.map { it.current })
                repos.auth.terminateSession(sessions[1].hash)
                assertEquals(2, repos.auth.sessions().size)
                repos.auth.terminateOtherSessions()
                assertEquals(listOf("Pixel 9"), repos.auth.sessions().map { it.device })

                assertEquals(PrivacySetting(PrivacyKey.LastSeen, PrivacyBase.Contacts, allowed = 0, disallowed = 1), repos.privacy.privacy(PrivacyKey.LastSeen))
                assertEquals(PrivacySetting(PrivacyKey.LastSeen, PrivacyBase.Nobody, allowed = 0, disallowed = 1), repos.privacy.setPrivacy(PrivacyKey.LastSeen, PrivacyBase.Nobody))
                assertEquals(PrivacyBase.Everybody, repos.privacy.privacy(PrivacyKey.Bio).base)
                val global = repos.privacy.setGlobalPrivacy(GlobalPrivacy(archiveAndMuteNonContacts = true, hideReadMarks = false))
                assertTrue(global.archiveAndMuteNonContacts && fake.globalPrivacy.keepArchivedUnmuted)
                assertEquals(365, repos.privacy.accountTtl())
                repos.privacy.setAccountTtl(90)
                assertEquals(90, repos.privacy.accountTtl())

                assertTrue(repos.telegram.log.value.any { it.text.contains("PHONE_MIGRATE_1") })
                assertTrue(repos.telegram.log.value.any { it.text.contains("exporting authorization to dc 2") })

                repos.auth.logOut()
                assertFalse(repos.telegram.isAuthorized)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun cloudPasswordFinishesTheLogin(): Unit = runBlocking {
        FakeTelegram(password = "tiramisu").use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("9996624242")
                val needed = assertIs<SignInResult.PasswordRequired>(repos.auth.signIn(request, "22222"))
                assertEquals("starts with t", needed.hint)
                assertFalse(repos.telegram.isAuthorized)
                val wrong = assertFailsWith<RpcException> { repos.auth.checkPassword("tiramisu?") }
                assertEquals("PASSWORD_HASH_INVALID", wrong.errorMessage)
                val profile = repos.auth.checkPassword("tiramisu")
                assertTrue(repos.telegram.isAuthorized)
                assertEquals(profile.id, repos.profiles.me().id)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}

private suspend inline fun <reified T : ChatEvent> nextOf(events: Channel<ChatEvent>): T {
    while (true) {
        val event = events.receive()
        if (event is T) return event
    }
}
