package app.miogram

import android.content.Context
import android.os.Looper
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.SettingsStore
import app.miogram.data.telegram.SignInResult
import app.miogram.music.MusicPlayer
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MusicModes
import cn.ellerotta.core.model.MusicOrder
import cn.ellerotta.core.model.MusicRepeat
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.MusicTrack
import cn.ellerotta.ktproto.api.types.InputMessagesFilterMusic
import cn.ellerotta.ktproto.api.types.InputMessagesFilterVoice
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MusicPlayerTest {
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("music").toFile()
    private val repos = TestRepositories(dir, fake)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val engine = FakeMusicPlayer()
    private val settings = SettingsStore(context)
    private val music = MusicPlayer(context, repos.messages, repos.media, settings, { engine }).also { (context as android.app.Application).refuseMusicServiceBind() }

    @After
    fun tearDown() {
        runBlocking { repos.telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    @Test
    fun theQueueGrowsFromSearchPagesAroundTheTappedSong() {
        val ids = fake.addMusicMessages(50)
        login()
        val tapped = track(ids[24])

        music.play(tapped)
        assertEquals(listOf("self/${ids[24]}"), engine.mediaIds.take(1))
        assertTrue(engine.playWhenReady)

        waitFor { (music.music.value?.queue?.size ?: 0) == 20 }
        val around = fake.musicCalls.first()
        assertEquals(InputMessagesFilterMusic, around.filter)
        assertEquals(ids[24] + 1, around.offsetId)
        assertEquals(-10, around.addOffset)
        assertEquals(20, around.limit)
        val state = assertNotNull(music.music.value)
        assertEquals((ids[15]..ids[34]).toList(), state.queue.map { it.messageId })
        assertEquals(ids[24], state.track.messageId)
        assertEquals(9, state.index)
        assertEquals(state.queue.map { it.id }, engine.mediaIds)
        assertEquals(9, engine.currentMediaItemIndex)

        music.playAt(1)
        waitFor { (music.music.value?.queue?.size ?: 0) == 35 }
        val older = fake.musicCalls.last()
        assertEquals(ids[15], older.offsetId)
        assertEquals(0, older.addOffset)
        assertEquals(ids[0], music.music.value!!.queue.first().messageId)
        assertEquals(ids[16], music.music.value!!.track.messageId)
        assertEquals(engine.mediaIds, music.music.value!!.queue.map { it.id })

        music.playAt(music.music.value!!.queue.lastIndex)
        waitFor { (music.music.value?.queue?.size ?: 0) == 50 }
        val newer = fake.musicCalls.last()
        assertEquals(ids[34], newer.offsetId)
        assertEquals(-20, newer.addOffset)
        assertEquals(ids[34], newer.minId)
        assertEquals(ids, music.music.value!!.queue.map { it.messageId })
        val calls = fake.musicCalls.size
        music.playAt(0)
        music.playAt(49)
        idle()
        assertEquals(calls, fake.musicCalls.size)
    }

    @Test
    fun theStateFollowsThePlayer(): Unit = runBlocking {
        val ids = fake.addMusicMessages(3)
        login()
        music.play(track(ids[0]))
        waitFor { music.music.value?.queue?.size == 3 }
        var state: MusicState = assertNotNull(music.music.value)
        assertEquals("Song 1", state.track.title)
        assertEquals("Band", state.track.performer)
        assertEquals(185_000L, state.lengthMs)
        assertTrue(state.playing)
        waitFor { music.music.value?.cover?.contentEquals(fake.musicCoverBytes) == true }

        music.play(track(ids[0]))
        idle()
        assertFalse(music.music.value!!.playing)
        music.toggle()
        idle()
        assertTrue(music.music.value!!.playing)

        music.next()
        idle()
        state = music.music.value!!
        assertEquals("Song 2", state.track.title)
        music.previous()
        idle()
        assertEquals("Song 1", music.music.value!!.track.title)
        music.seekTo(60_000)
        idle()
        assertEquals(60_000, music.music.value!!.positionMs)

        music.toggleShuffle()
        music.cycleRepeat()
        idle()
        assertEquals(MusicOrder.Shuffle, music.music.value!!.order)
        assertEquals(MusicRepeat.All, music.music.value!!.repeat)
        music.cycleRepeat()
        idle()
        assertEquals(MusicRepeat.One, music.music.value!!.repeat)
        assertEquals(Player.REPEAT_MODE_ONE, engine.repeatMode)

        music.pause()
        idle()
        assertFalse(music.music.value!!.playing)

        music.close()
        assertNull(music.music.value)
        assertEquals(0, engine.mediaItemCount)
        assertEquals("app.miogram.music.MusicService", shadowOf(context as android.app.Application).nextStoppedService?.component?.className)
    }

    @Test
    fun aSongOfAnotherChatStartsANewQueue() {
        val ids = fake.addMusicMessages(2)
        login()
        music.play(track(ids[0]))
        waitFor { music.music.value?.queue?.size == 2 }
        val other = MusicTrack("u501", 7, track(ids[1]).media)
        music.play(other)
        idle()
        assertEquals(listOf("u501/7"), music.music.value!!.queue.map { it.id })
        assertEquals(listOf("u501/7"), engine.mediaIds)
    }

    @Test
    fun aVoiceMessagePlaysOnToTheNextUnlistenedIncomingOnes() {
        val ids = fake.addVoiceMessages(listOf(true, true, true, false, true))
        login()
        val tapped = voice(ids[0])
        assertTrue(tapped.isVoice)
        assertTrue(tapped.unread)

        music.play(tapped)
        assertEquals(listOf("self/${ids[0]}"), engine.mediaIds.take(1))
        assertEquals(1f, engine.speed)
        waitFor { music.music.value?.queue?.size == 3 }
        val asked = fake.voiceCalls.first()
        assertEquals(InputMessagesFilterVoice, asked.filter)
        assertEquals(ids[0], asked.offsetId)
        assertEquals(ids[0], asked.minId)
        assertEquals(ids.take(3), music.music.value!!.queue.map { it.messageId })
        assertEquals(ids.take(3).map { "self/$it" }, engine.mediaIds)
        waitFor { fake.readContents == listOf(ids[0]) }

        music.seekTo(1_500)
        idle()
        val bubble = assertNotNull(music.music.value!!.voicePlayback())
        assertEquals(tapped.media.key, bubble.key)
        assertEquals(1_500, bubble.positionMs)

        engine.finish()
        waitFor { fake.readContents == listOf(ids[0], ids[1]) }
        assertEquals(ids[1], music.music.value!!.track.messageId)

        music.cycleSpeed()
        idle()
        assertEquals(1.5f, engine.speed)
        assertEquals(1.5f, settings.voiceSpeed.value)
        assertEquals(1.5f, SettingsStore(context).voiceSpeed.value)
        assertEquals(1.5f, music.music.value!!.voicePlayback()!!.speed)

        engine.finish()
        waitFor { fake.readContents.size == 3 }
        engine.finish()
        waitFor { music.music.value == null }
        assertEquals(0, engine.mediaItemCount)
    }

    @Test
    fun musicAndVoiceMessagesReplaceEachOther() {
        val songs = fake.addMusicMessages(2)
        val voices = fake.addVoiceMessages(listOf(false))
        login()
        settings.setVoiceSpeed(2f)
        music.play(track(songs[0]))
        waitFor { music.music.value?.queue?.size == 2 }
        assertEquals(1f, engine.speed)
        assertNull(music.music.value!!.voicePlayback())

        music.play(voice(voices[0]))
        idle()
        assertEquals(listOf("self/${voices[0]}"), engine.mediaIds)
        assertEquals(2f, engine.speed)
        assertTrue(music.music.value!!.track.isVoice)
        assertTrue(fake.readContents.isEmpty())

        music.play(track(songs[1]))
        waitFor { music.music.value?.queue?.size == 2 }
        assertEquals(1f, engine.speed)
        assertFalse(music.music.value!!.track.isVoice)
        assertEquals(songs[1], music.music.value!!.track.messageId)
    }

    @Test
    fun theOrdersArrangeThePlayersItems() {
        val ids = fake.addMusicMessages(10)
        login()
        music.play(track(ids[4]))
        waitFor { music.music.value?.queue?.size == 10 }
        assertEquals(ids.map { "self/$it" }, engine.mediaIds)

        music.setOrder(MusicOrder.Reverse)
        idle()
        assertEquals(ids.reversed().map { "self/$it" }, engine.mediaIds)
        assertEquals(MusicOrder.Reverse, music.music.value!!.order)
        assertEquals(ids[4], music.music.value!!.track.messageId)
        assertEquals(5, engine.currentMediaItemIndex)
        assertFalse(engine.shuffleModeEnabled)
        music.next()
        idle()
        assertEquals(ids[3], music.music.value!!.track.messageId)

        music.toggleShuffle()
        idle()
        val shuffled = engine.mediaIds
        assertEquals("self/${ids[3]}", shuffled.first())
        assertEquals(ids.map { "self/$it" }.toSet(), shuffled.toSet())
        assertEquals(10, shuffled.size)
        assertTrue(engine.shuffleModeEnabled)
        assertEquals(MusicOrder.Shuffle, music.music.value!!.order)
        assertEquals(music.music.value!!.queue.map { it.id }, shuffled)

        music.toggleShuffle()
        idle()
        assertEquals(ids.reversed().map { "self/$it" }, engine.mediaIds)
        assertEquals(MusicOrder.Reverse, music.music.value!!.order)
        music.toggleShuffle()
        idle()
        assertEquals(shuffled, engine.mediaIds)

        music.setOrder(MusicOrder.Chat)
        idle()
        assertEquals(ids.map { "self/$it" }, engine.mediaIds)
        assertEquals(MusicModes(), settings.musicModes.value)
    }

    @Test
    fun newestFirstLoadsOlderPagesAtItsEnd() {
        val ids = fake.addMusicMessages(50)
        login()
        settings.setMusicModes(MusicModes(reverse = true))
        music.play(track(ids[24]))
        waitFor { (music.music.value?.queue?.size ?: 0) == 20 }
        assertEquals((ids[15]..ids[34]).reversed().map { "self/$it" }, engine.mediaIds)
        assertEquals(10, engine.currentMediaItemIndex)
        music.playAt(18)
        waitFor { (music.music.value?.queue?.size ?: 0) == 35 }
        assertEquals((ids[0]..ids[34]).reversed().map { "self/$it" }, engine.mediaIds)
        assertEquals(ids[16], music.music.value!!.track.messageId)
    }

    @Test
    fun aShuffledQueueTakesNewPagesAmongTheSongsToCome() {
        val ids = fake.addMusicMessages(50)
        login()
        settings.setMusicModes(MusicModes(shuffle = true))
        music.play(track(ids[24]))
        waitFor { (music.music.value?.queue?.size ?: 0) == 20 }
        assertEquals("self/${ids[24]}", engine.mediaIds.first())
        assertTrue(engine.shuffleModeEnabled)
        val before = engine.mediaIds
        music.playAt(17)
        waitFor { (music.music.value?.queue?.size ?: 0) > 20 }
        val after = engine.mediaIds
        assertEquals(before.take(18), after.take(18))
        assertEquals(before.drop(18), after.drop(18).filter { it in before })
        assertEquals(17, engine.currentMediaItemIndex)
        assertEquals(after, music.music.value!!.queue.map { it.id })
    }

    @Test
    fun theQueueIsEditedWhileTheSongPlaysOn() {
        val ids = fake.addMusicMessages(5)
        login()
        music.play(track(ids[1]))
        waitFor { music.music.value?.queue?.size == 5 }
        fun order() = music.music.value!!.queue.map { it.messageId }

        music.move(4, 0)
        idle()
        assertEquals(listOf(ids[4], ids[0], ids[1], ids[2], ids[3]), order())
        assertEquals(ids[1], music.music.value!!.track.messageId)
        assertEquals(2, engine.currentMediaItemIndex)

        music.playNext(0)
        idle()
        assertEquals(listOf(ids[0], ids[1], ids[4], ids[2], ids[3]), order())
        music.next()
        idle()
        assertEquals(ids[4], music.music.value!!.track.messageId)

        music.remove(0)
        idle()
        assertEquals(listOf(ids[1], ids[4], ids[2], ids[3]), order())
        music.remove(1)
        idle()
        assertEquals(4, order().size)
        assertEquals(order().map { "self/$it" }, engine.mediaIds)
        music.setOrder(MusicOrder.Reverse)
        idle()
        assertEquals(listOf(ids[4], ids[3], ids[2], ids[1]), order())
    }

    @Test
    fun theModesAreKeptAcrossRestartsAndLeaveVoiceMessagesAlone() {
        val ids = fake.addMusicMessages(3)
        val voices = fake.addVoiceMessages(listOf(false))
        login()
        music.play(track(ids[0]))
        waitFor { music.music.value?.queue?.size == 3 }
        music.setOrder(MusicOrder.Reverse)
        music.cycleRepeat()
        idle()
        assertEquals(MusicModes(reverse = true, repeat = MusicRepeat.All), SettingsStore(context).musicModes.value)

        music.play(voice(voices[0]))
        idle()
        assertEquals(MusicOrder.Chat, music.music.value!!.order)
        assertEquals(Player.REPEAT_MODE_OFF, engine.repeatMode)
        music.toggleShuffle()
        idle()
        assertFalse(engine.shuffleModeEnabled)
        assertEquals(MusicModes(reverse = true, repeat = MusicRepeat.All), settings.musicModes.value)
        music.close()

        val again = FakeMusicPlayer()
        val restarted = MusicPlayer(context, repos.messages, repos.media, SettingsStore(context), { again })
        restarted.play(track(ids[1]))
        val until = System.currentTimeMillis() + 30_000
        while (restarted.music.value?.queue?.size != 3) {
            idle()
            check(System.currentTimeMillis() < until)
            Thread.sleep(10)
        }
        assertEquals(ids.reversed().map { "self/$it" }, again.mediaIds)
        assertEquals(Player.REPEAT_MODE_ALL, again.repeatMode)
        assertEquals(MusicOrder.Reverse, restarted.music.value!!.order)
        restarted.close()
    }

    @Test
    fun aControllersShuffleIsTheSongsShuffle() {
        val ids = fake.addMusicMessages(4)
        login()
        music.play(track(ids[0]))
        waitFor { music.music.value?.queue?.size == 4 }
        engine.shuffleModeEnabled = true
        idle()
        assertEquals(MusicOrder.Shuffle, music.music.value!!.order)
        assertTrue(settings.musicModes.value.shuffle)
        assertEquals("self/${ids[0]}", engine.mediaIds.first())
        engine.repeatMode = Player.REPEAT_MODE_ONE
        idle()
        assertEquals(MusicRepeat.One, settings.musicModes.value.repeat)
        engine.shuffleModeEnabled = false
        idle()
        assertEquals(ids.map { "self/$it" }, engine.mediaIds)
    }

    @Test
    fun aSleepTimerPausesAfterItsMinutesOrAtTheEndOfTheTrack() {
        val ids = fake.addMusicMessages(3)
        login()
        music.play(track(ids[0]))
        waitFor { music.music.value?.queue?.size == 3 }
        music.sleep(15)
        idle()
        assertEquals(15 * 60_000L, music.music.value!!.sleep!!.leftMs)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMinutes(10))
        assertTrue(engine.playWhenReady)
        assertTrue(music.music.value!!.sleep!!.leftMs in 1..5 * 60_000L)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMinutes(6))
        assertFalse(engine.playWhenReady)
        assertNull(music.music.value!!.sleep)

        music.toggle()
        music.sleepAtEnd()
        idle()
        assertTrue(music.music.value!!.sleep!!.endOfTrack)
        music.next()
        idle()
        assertTrue(engine.playWhenReady)
        engine.finish()
        idle()
        assertFalse(engine.playWhenReady)
        assertEquals(ids[2], music.music.value!!.track.messageId)
        assertNull(music.music.value!!.sleep)
        music.sleep(30)
        music.sleep(null)
        idle()
        assertNull(music.music.value!!.sleep)
    }

    @Test
    fun aLongSongKeepsASpeedOfItsOwnAndTheFilesWordsShow(): Unit = runBlocking {
        val ids = fake.addMusicMessages(2)
        login()
        val long = track(ids[0]).let { it.copy(media = it.media.copy(duration = 45 * 60)) }
        assertTrue(long.isLong)
        music.playList(listOf(long, track(ids[1])))
        idle()
        assertEquals(1f, engine.speed)
        music.cycleSpeed()
        idle()
        assertEquals(1.5f, engine.speed)
        assertEquals(1.5f, SettingsStore(context).longSpeed.value)
        assertEquals(1f, settings.voiceSpeed.value)
        music.next()
        idle()
        assertEquals(1f, engine.speed)
        music.cycleSpeed()
        idle()
        assertEquals(1f, engine.speed)
        music.previous()
        idle()
        assertEquals(1.5f, engine.speed)

        val uslt = byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(0) + "first line\nsecond line".toByteArray()
        val format = androidx.media3.common.Format.Builder().setSampleMimeType("audio/mpeg")
            .setMetadata(androidx.media3.common.Metadata(androidx.media3.extractor.metadata.id3.BinaryFrame("USLT", uslt))).build()
        val group = androidx.media3.common.Tracks.Group(androidx.media3.common.TrackGroup(format), false, intArrayOf(androidx.media3.common.C.FORMAT_HANDLED), booleanArrayOf(true))
        engine.tracks["self/${ids[1]}"] = androidx.media3.common.Tracks(listOf(group))
        assertNull(music.music.value!!.lyrics)
        music.next()
        idle()
        assertEquals("first line\nsecond line", music.music.value!!.lyrics)
        music.previous()
        idle()
        assertNull(music.music.value!!.lyrics)

        repos.profiles.saveMusic(track(ids[1]).media.key)
        assertEquals((FakeTelegram.MUSIC_ID + 2).toLong(), fake.ownSongs.first().id)
    }

    private fun voice(id: Int): MusicTrack {
        val message = runBlocking { repos.messages.history("self", limit = 100) }.first { it.id == id }
        return assertNotNull(repos.messages.voiceTrackOf("self", message))
    }

    private fun track(id: Int): MusicTrack {
        val message = runBlocking { repos.messages.history("self", limit = 100) }.first { it.id == id }
        val media = assertNotNull(message.media)
        assertEquals(MediaKind.Music, media.kind)
        return MusicTrack("self", id, media)
    }

    private fun login() = runBlocking {
        repos.telegram.connect(12345, "hash")
        val request = repos.auth.sendCode("9996624242")
        fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "22222")).profile.id
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun waitFor(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 30_000
        while (true) {
            idle()
            if (condition()) return
            check(System.currentTimeMillis() < until) { "timed out, music is ${music.music.value}" }
            Thread.sleep(10)
        }
    }
}
