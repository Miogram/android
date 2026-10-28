package app.miogram

import app.miogram.data.MediaFileInfo
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.OutgoingMedia
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.SendOptions
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.ktproto.api.types.DocumentAttributeAudio
import cn.ellerotta.ktproto.api.types.DocumentAttributeFilename
import cn.ellerotta.ktproto.api.types.DocumentAttributeVideo
import cn.ellerotta.ktproto.api.types.InputFile
import cn.ellerotta.ktproto.api.types.InputMediaUploadedDocument
import cn.ellerotta.ktproto.api.types.InputMediaUploadedPhoto
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import cn.ellerotta.ktproto.api.types.MessageEntityBold
import cn.ellerotta.ktproto.api.types.TypeMessageEntity
import cn.ellerotta.ktproto.client.UploadSource
import cn.ellerotta.ktproto.session.RpcException
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class SendingRepositoryTest {
    private fun spans(entities: List<TypeMessageEntity>?) = entities?.map { (it as MessageEntityBold).let { bold -> bold.offset to bold.length } }

    private fun withChat(block: suspend (repos: TestRepositories, fake: FakeTelegram, saved: String) -> Unit): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                val profile = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile
                fake.selfId = profile.id
                fake.addSavedMessages(3)
                block(repos, fake, repos.dialogs.dialogs().dialogs.first { it.isSaved }.key)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun aSendKeepsItsReplySilenceAndSchedule(): Unit = withChat { repos, fake, saved ->
        val reply = repos.messages.history(saved).first().id
        val recording = Files.createTempFile("voice", ".ogg").toFile().apply { writeBytes(Random(2).nextBytes(5_000)) }

        repos.messages.send(saved, "quiet", replyTo = reply, options = SendOptions(silent = true))
        val text = assertNotNull(fake.lastSendMessage)
        assertTrue(text.silent)
        assertNull(text.scheduleDate)
        assertEquals(reply, assertIs<InputReplyToMessage>(text.replyTo).replyToMsgId)

        repos.messages.sendFile(saved, OutgoingMedia("notes.bin", "application/octet-stream", UploadSource.of(Random(1).nextBytes(3_000))), replyTo = reply).toList()
        assertEquals(reply, assertIs<InputReplyToMessage>(assertNotNull(fake.lastSendMedia).replyTo).replyToMsgId)
        assertEquals(reply, repos.messages.history(saved).last().replyTo)
        repos.messages.sendVoice(saved, recording, 2, listOf(1, 2, 3), replyTo = reply).toList()
        assertEquals(reply, repos.messages.history(saved).last().replyTo)

        val date = (System.currentTimeMillis() / 1000).toInt() + 3_600
        repos.messages.send(saved, "later", options = SendOptions(scheduleDate = date))
        assertEquals(date, assertNotNull(fake.lastSendMessage).scheduleDate)
        assertFalse(assertNotNull(fake.lastSendMessage).silent)
        assertTrue(repos.messages.history(saved).none { it.text == "later" })
        assertEquals(listOf("later"), fake.sending.scheduled.map { it.message })

        repos.messages.send(saved, "when online", options = SendOptions(scheduleDate = SendOptions.WHEN_ONLINE))
        assertEquals(SendOptions.WHEN_ONLINE, fake.lastSendMessage?.scheduleDate)
    }

    @Test
    fun aCaptionAndItsEntitiesGoWithTheFile(): Unit = withChat { repos, fake, saved ->
        val caption = FormattedText("look at this", listOf(TextEntity(0, 4, EntityType.Bold)))
        repos.messages.sendFile(saved, OutgoingMedia("notes.bin", "application/octet-stream", UploadSource.of(Random(1).nextBytes(3_000))), caption).toList()
        val call = assertNotNull(fake.lastSendMedia)
        assertEquals("look at this", call.message)
        assertEquals(listOf(0 to 4), spans(call.entities))
        val sent = repos.messages.history(saved).last()
        assertEquals("look at this", sent.text)
        assertEquals(listOf(TextEntity(0, 4, EntityType.Bold)), sent.entities)
    }

    @Test
    fun videosAndMusicCarryWhatTheySayAboutThemselves(): Unit = withChat { repos, fake, saved ->
        val video = Random(3).nextBytes(4_000)
        val thumb = Random(4).nextBytes(600)
        repos.messages.sendFile(
            saved,
            OutgoingMedia("clip.mp4", "video/mp4", UploadSource.of(video), info = MediaFileInfo(width = 640, height = 360, duration = 12.5), thumb = thumb),
        ).toList()
        val sentVideo = assertIs<InputMediaUploadedDocument>(assertNotNull(fake.lastSendMedia).media)
        assertEquals("video/mp4", sentVideo.mimeType)
        assertFalse(sentVideo.forceFile)
        val attribute = sentVideo.attributes.filterIsInstance<DocumentAttributeVideo>().single()
        assertTrue(attribute.supportsStreaming)
        assertEquals(Triple(640, 360, 12.5), Triple(attribute.w, attribute.h, attribute.duration))
        assertEquals("clip.mp4", sentVideo.attributes.filterIsInstance<DocumentAttributeFilename>().single().fileName)
        val thumbFile = assertIs<InputFile>(assertNotNull(sentVideo.thumb))
        assertContentEquals(thumb, fake.cluster.uploadedFile(thumbFile.id, thumbFile.parts))
        assertContentEquals(video, fake.lastUpload)

        val song = Random(5).nextBytes(2_000)
        repos.messages.sendFile(
            saved,
            OutgoingMedia("song.mp3", "audio/mpeg", UploadSource.of(song), info = MediaFileInfo(duration = 201.4, title = "Dawn", performer = "Birds")),
        ).toList()
        val sentSong = assertIs<InputMediaUploadedDocument>(assertNotNull(fake.lastSendMedia).media)
        val audio = sentSong.attributes.filterIsInstance<DocumentAttributeAudio>().single()
        assertFalse(audio.voice)
        assertEquals(Triple(201, "Dawn", "Birds"), Triple(audio.duration, audio.title, audio.performer))

        repos.messages.sendFile(
            saved,
            OutgoingMedia("clip.mp4", "video/mp4", UploadSource.of(video), asFile = true, info = MediaFileInfo(width = 640, height = 360, duration = 12.5), thumb = thumb),
        ).toList()
        val sentFile = assertIs<InputMediaUploadedDocument>(assertNotNull(fake.lastSendMedia).media)
        assertTrue(sentFile.forceFile)
        assertNull(sentFile.thumb)
        assertTrue(sentFile.attributes.none { it is DocumentAttributeVideo })
    }

    @Test
    fun aSpoilerHidesOnlyPhotosAndVideosThatAreNotFiles(): Unit = withChat { repos, fake, saved ->
        val bytes = Random(6).nextBytes(2_000)
        repos.messages.sendFile(saved, OutgoingMedia("cat.jpg", "image/jpeg", UploadSource.of(bytes), spoiler = true)).toList()
        assertTrue(assertIs<InputMediaUploadedPhoto>(assertNotNull(fake.lastSendMedia).media).spoiler)

        repos.messages.sendFile(saved, OutgoingMedia("clip.mp4", "video/mp4", UploadSource.of(bytes), spoiler = true)).toList()
        assertTrue(assertIs<InputMediaUploadedDocument>(assertNotNull(fake.lastSendMedia).media).spoiler)

        repos.messages.sendFile(saved, OutgoingMedia("cat.jpg", "image/jpeg", UploadSource.of(bytes), asFile = true, spoiler = true)).toList()
        val document = assertIs<InputMediaUploadedDocument>(assertNotNull(fake.lastSendMedia).media)
        assertTrue(document.forceFile)
        assertFalse(document.spoiler)

        repos.messages.sendFile(saved, OutgoingMedia("notes.bin", "application/octet-stream", UploadSource.of(bytes), spoiler = true)).toList()
        assertFalse(assertIs<InputMediaUploadedDocument>(assertNotNull(fake.lastSendMedia).media).spoiler)
    }

    @Test
    fun anAlbumIsUploadedItemByItemAndSentOnce(): Unit = withChat { repos, fake, saved ->
        val reply = repos.messages.history(saved).first().id
        val files = List(3) { OutgoingMedia("part$it.bin", "application/octet-stream", UploadSource.of(Random(10 + it).nextBytes(2_000 + it))) }
        val caption = FormattedText("three parts", listOf(TextEntity(0, 5, EntityType.Bold)))
        val randomIds = listOf(101L, 102L, 103L)

        val progress = repos.messages.sendAlbum(saved, files, caption, replyTo = reply, randomIds = randomIds, options = SendOptions(silent = true)).toList()
        assertEquals(setOf(0, 1, 2), progress.map { it.index }.toSet())
        assertEquals(3, fake.articles.uploadMediaCalls.size)
        val album = fake.sending.albums.single()
        assertTrue(album.silent)
        assertNull(album.scheduleDate)
        assertEquals(reply, assertIs<InputReplyToMessage>(album.replyTo).replyToMsgId)
        assertEquals(randomIds, album.multiMedia.map { it.randomId })
        assertEquals(listOf("three parts", "", ""), album.multiMedia.map { it.message })
        assertEquals(listOf(listOf(0 to 5), null, null), album.multiMedia.map { single -> spans(single.entities) })

        val sent = repos.messages.history(saved).takeLast(3)
        assertEquals(1, sent.map { it.groupedId }.toSet().size)
        assertNotNull(sent.first().groupedId)
        assertEquals(listOf("three parts", "", ""), sent.map { it.text })
        assertEquals(files.map { it.source.size }, sent.map { it.media?.size })
        assertEquals(listOf(reply, reply, reply), sent.map { it.replyTo })

        repos.messages.sendAlbum(saved, files, caption, replyTo = reply, randomIds = randomIds).toList()
        assertEquals(1, fake.sending.albums.size)
        assertEquals(3, repos.messages.history(saved).count { it.groupedId == sent.first().groupedId })
    }

    @Test
    fun anAlbumThatFailsIsSentAgainAndAnAlbumNeedsAtLeastTwoItems(): Unit = withChat { repos, fake, saved ->
        val files = List(2) { OutgoingMedia("part$it.bin", "application/octet-stream", UploadSource.of(Random(20 + it).nextBytes(1_500))) }
        fake.sending.failAlbums = 1
        val failure = assertFailsWith<RpcException> { repos.messages.sendAlbum(saved, files, randomIds = listOf(201L, 202L)).toList() }
        assertEquals("CHAT_SEND_MEDIA_FORBIDDEN", failure.errorMessage)
        assertTrue(fake.sending.albums.isEmpty())
        repos.messages.sendAlbum(saved, files, randomIds = listOf(201L, 202L)).toList()
        assertEquals(1, fake.sending.albums.size)

        assertFailsWith<IllegalArgumentException> { repos.messages.sendAlbum(saved, files.take(1)).toList() }
        assertFailsWith<IllegalArgumentException> { repos.messages.sendAlbum(saved, List(11) { files[0] }).toList() }
    }

    @Test
    fun aScheduledAlbumWaitsAsItsOwnMessages(): Unit = withChat { repos, fake, saved ->
        val files = List(2) { OutgoingMedia("part$it.bin", "application/octet-stream", UploadSource.of(Random(30 + it).nextBytes(1_500))) }
        val date = (System.currentTimeMillis() / 1000).toInt() + 7_200
        val before = repos.messages.history(saved).size
        repos.messages.sendAlbum(saved, files, FormattedText("later"), options = SendOptions(scheduleDate = date)).toList()
        assertEquals(before, repos.messages.history(saved).size)
        val waiting = repos.scheduled.list(saved)
        assertEquals(listOf(date, date), waiting.map { it.date })
        assertEquals(listOf("later", ""), waiting.map { it.text })
        assertEquals(1, waiting.map { it.groupedId }.toSet().size)
        assertEquals(2, fake.sending.scheduled.size)
    }

    @Test
    fun scheduledMessagesAreListedMovedEditedSentAndDeleted(): Unit = withChat { repos, fake, saved ->
        val now = (System.currentTimeMillis() / 1000).toInt()
        repos.messages.send(saved, "second", options = SendOptions(scheduleDate = now + 7_200))
        repos.messages.send(saved, "first", options = SendOptions(scheduleDate = now + 3_600))
        repos.messages.send(saved, "third", options = SendOptions(scheduleDate = SendOptions.WHEN_ONLINE))

        val listed = repos.scheduled.list(saved)
        assertEquals(listOf("first", "second", "third"), listed.map { it.text })
        assertEquals(listOf(now + 3_600, now + 7_200, SendOptions.WHEN_ONLINE), listed.map { it.date })
        assertTrue(listed.map { it.id }.toSet().size == 3)
        assertTrue(listed.all { it.out })

        val first = listed[0]
        repos.scheduled.reschedule(saved, first.id, now + 10_800)
        assertEquals(setOf("first", "second", "third"), repos.scheduled.list(saved).map { it.text }.toSet())
        assertEquals(now + 10_800, repos.scheduled.list(saved).single { it.id == first.id }.date)
        assertEquals("first", repos.scheduled.list(saved).single { it.id == first.id }.text)

        repos.scheduled.edit(saved, listed[1].id, "second, changed", listOf(TextEntity(0, 6, EntityType.Bold)))
        val edited = repos.scheduled.list(saved).single { it.id == listed[1].id }
        assertEquals("second, changed", edited.text)
        assertEquals(listOf(TextEntity(0, 6, EntityType.Bold)), edited.entities)
        assertEquals(now + 7_200, edited.date)

        repos.scheduled.sendNow(saved, listOf(listed[2].id))
        assertEquals(listOf(listed[2].id), fake.sending.sentNow)
        assertEquals(1, repos.messages.history(saved).count { it.text == "third" })
        assertTrue(repos.scheduled.list(saved).none { it.text == "third" })

        repos.scheduled.delete(saved, listOf(first.id))
        assertEquals(listOf(first.id), fake.sending.deleted)
        assertEquals(listOf("second, changed"), repos.scheduled.list(saved).map { it.text })
    }

    @Test
    fun theScheduledMessageUpdatesAreEvents(): Unit = withChat { repos, fake, saved ->
        val events = Channel<ChatEvent>(Channel.UNLIMITED)
        val listening = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { repos.updates.events().collect { events.send(it) } }
        delay(300)
        val date = (System.currentTimeMillis() / 1000).toInt() + 3_600

        fake.sending.scheduleText("from my other phone", date)
        val added = withTimeout(10_000) { nextOf<ChatEvent.ScheduledNew>(events) }
        assertEquals(saved, added.key)
        assertEquals("from my other phone", added.message.text)
        assertEquals(date, added.message.date)

        val id = fake.sending.scheduled.single().id
        fake.sending.pushDeleted(listOf(id))
        val gone = withTimeout(10_000) { nextOf<ChatEvent.ScheduledDeleted>(events) }
        assertEquals(saved to listOf(id), gone.key to gone.ids)
        assertTrue(gone.sent.isEmpty())
        assertTrue(repos.scheduled.list(saved).isEmpty())

        fake.sending.scheduleText("goes now", date)
        withTimeout(10_000) { nextOf<ChatEvent.ScheduledNew>(events) }
        val waiting = fake.sending.scheduled.single().id
        repos.scheduled.sendNow(saved, listOf(waiting))
        val sentEvent = withTimeout(10_000) { nextOf<ChatEvent.ScheduledDeleted>(events) }
        assertEquals(listOf(waiting), sentEvent.ids)
        assertEquals(1, sentEvent.sent.size)
        listening.cancel()
    }
}

private suspend inline fun <reified T : ChatEvent> nextOf(events: Channel<ChatEvent>): T {
    while (true) {
        val event = events.receive()
        if (event is T) return event
    }
}
