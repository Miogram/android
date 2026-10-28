package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicTest {
    private fun song(id: Int, chat: String = "u1") = MusicTrack(chat, id, MediaItem("doc$id", "song$id.mp3", 1_000, 2, isPhoto = false, kind = MediaKind.Music, duration = 185))

    @Test
    fun pagesAreAskedAroundOlderAndNewer() {
        val list = MusicPlaylist("u1", listOf(song(50)))
        assertEquals(MusicQuery(offsetId = 51, addOffset = -10, limit = 20), list.query(MusicPage.Around, 50))
        val longer = MusicPlaylist("u1", (41..60).map { song(it) })
        assertEquals(MusicQuery(offsetId = 41, addOffset = 0, limit = 20), longer.query(MusicPage.Older))
        assertEquals(MusicQuery(offsetId = 60, addOffset = -20, limit = 20, minId = 60), longer.query(MusicPage.Newer))
    }

    @Test
    fun pagesMergeSortedWithoutDuplicatesAndShortOnesEndASide() {
        val start = MusicPlaylist("u1", listOf(song(50)))
        val full = start.merged(MusicPage.Around, (41..60).map { song(it) }.reversed(), 50)
        assertEquals((41..60).toList(), full.tracks.map { it.messageId })
        assertFalse(full.olderDone)
        assertFalse(full.newerDone)
        val top = start.merged(MusicPage.Around, (40..53).map { song(it) }, 50)
        assertTrue(top.newerDone)
        assertFalse(top.olderDone)

        val older = full.merged(MusicPage.Older, (21..40).map { song(it) } + song(41) + song(30, chat = "u2"), 41)
        assertEquals((21..60).toList(), older.tracks.map { it.messageId })
        assertFalse(older.olderDone)
        assertTrue(older.merged(MusicPage.Older, (15..20).map { song(it) }).olderDone)

        val newer = full.merged(MusicPage.Newer, (61..79).map { song(it) })
        assertFalse(newer.newerDone)
        assertEquals(79, newer.tracks.last().messageId)
        assertTrue(newer.merged(MusicPage.Newer, (80..82).map { song(it) }).newerDone)
    }

    @Test
    fun pagesLoadNearTheEndsOnly() {
        val list = MusicPlaylist("u1", (1..20).map { song(it) })
        assertEquals(MusicPage.Older, list.pageFor(1))
        assertEquals(MusicPage.Newer, list.pageFor(18))
        assertNull(list.pageFor(10))
        assertNull(list.copy(olderDone = true, newerDone = true).pageFor(0))
        assertEquals(MusicPage.Newer, list.copy(olderDone = true).pageFor(19))
    }

    @Test
    fun tracksAndStateShowWhatTheyHave() {
        val bare = song(1)
        assertEquals("song1", bare.title)
        assertNull(bare.performer)
        assertEquals("u1/1", bare.id)
        val named = MusicTrack("u1", 2, bare.media.copy(title = "Yesterday", performer = "The Beatles"))
        assertEquals("Yesterday", named.title)
        assertEquals("The Beatles", named.performer)

        val state = MusicState(listOf(bare, named), index = 1, playing = true)
        assertEquals(named, state.track)
        assertEquals(185_000, state.lengthMs)
        assertEquals(200_000, state.copy(durationMs = 200_000).lengthMs)
        assertTrue(state.isTrack("u1", 2))
        assertFalse(state.isTrack("u2", 2))

        val covered = bare.media.copy(sizes = listOf(MediaSize("s", 90, 90, 1_000), MediaSize("m", 320, 320, 9_000)))
        assertEquals("doc1@m", covered.cover()?.key)
        assertNull(bare.media.cover())
    }

    private fun voice(id: Int, unread: Boolean = true, out: Boolean = false, chat: String = "u1") =
        MusicTrack(chat, id, MediaItem("v$id", "voice.ogg", 1_000, 2, isPhoto = false, kind = MediaKind.Voice, duration = 3), out = out, unread = unread)

    @Test
    fun aVoiceQueueRunsOnWhileTheNextOnesAreIncomingAndUnlistened() {
        val tapped = voice(10, unread = false)
        val found = listOf(voice(15), voice(14, unread = false), voice(12), voice(11), voice(11), voice(9), voice(13, chat = "u2"))
        assertEquals(listOf(10, 11, 12), voiceQueue(tapped, found).map { it.messageId })
        assertEquals(listOf(10), voiceQueue(tapped, listOf(voice(11, out = true), voice(12))).map { it.messageId })
        assertEquals(listOf(10), voiceQueue(tapped, emptyList()).map { it.messageId })
        assertEquals(MusicQuery(offsetId = 10, addOffset = -20, limit = 20, minId = 10), voiceQuery(10))
    }

    @Test
    fun voiceSpeedsCycleAndTheBubbleFollowsThePlayer() {
        assertEquals(1.5f, nextVoiceSpeed(1f))
        assertEquals(2f, nextVoiceSpeed(1.5f))
        assertEquals(1f, nextVoiceSpeed(2f))
        assertEquals(1f, nextVoiceSpeed(3f))
        val state = MusicState(listOf(voice(1)), index = 0, playing = true, positionMs = 1_200, durationMs = 3_000, speed = 2f)
        assertEquals(VoicePlayback("v1", playing = true, positionMs = 1_200, durationMs = 3_000, speed = 2f), state.voicePlayback())
        assertTrue(state.track.isVoice)
        assertNull(MusicState(listOf(song(1)), index = 0, playing = true).voicePlayback())
    }

    @Test
    fun ordersArrangeTheChatsTracks() {
        val tracks = (1..8).map { song(it) }
        assertEquals(tracks, arranged(tracks, MusicOrder.Chat, tracks[3], seed = 7))
        assertEquals(tracks.reversed(), arranged(tracks, MusicOrder.Reverse, tracks[3], seed = 7))
        val shuffled = arranged(tracks, MusicOrder.Shuffle, tracks[3], seed = 7)
        assertEquals(tracks[3], shuffled.first())
        assertEquals(tracks.toSet(), shuffled.toSet())
        assertEquals(shuffled, arranged(tracks, MusicOrder.Shuffle, tracks[3], seed = 7))
        assertTrue((1L..20L).any { arranged(tracks, MusicOrder.Shuffle, tracks[3], seed = it) != shuffled })
        assertEquals(shuffleKey(tracks[0], 7), shuffleKey(song(1), 7))
    }

    @Test
    fun pagesGoWhereTheOrderPlaysThem() {
        val queue = (10..14).map { song(it) }
        val older = (5..9).map { song(it) }
        val newer = (15..16).map { song(it) }
        assertEquals((5..16).toList(), grown(queue, 2, older, newer, MusicOrder.Chat, seed = 1).map { it.messageId })
        val reversed = queue.reversed()
        assertEquals((5..16).reversed().toList(), grown(reversed, 2, older, newer, MusicOrder.Reverse, seed = 1).map { it.messageId })
        val shuffled = arranged(queue, MusicOrder.Shuffle, queue[0], seed = 3)
        val grownShuffle = grown(shuffled, 1, older, newer, MusicOrder.Shuffle, seed = 3)
        assertEquals(shuffled.take(2), grownShuffle.take(2))
        assertEquals(shuffled.drop(2), grownShuffle.drop(2).filter { it in shuffled })
        assertEquals((5..16).toSet(), grownShuffle.map { it.messageId }.toSet())
        assertEquals(arranged((5..16).map { song(it) }, MusicOrder.Shuffle, queue[0], seed = 3).drop(1).filter { it != shuffled[1] }, grownShuffle.drop(2))
    }

    @Test
    fun theQueueIsEditedInPlace() {
        val queue = (1..5).map { song(it) }
        assertEquals(listOf(5, 1, 2, 3, 4), queue.moved(4, 0).map { it.messageId })
        assertEquals(listOf(2, 3, 1, 4, 5), queue.moved(0, 2).map { it.messageId })
        assertEquals(queue, queue.moved(1, 9))
        assertEquals(listOf(1, 2, 5, 3, 4), playNext(queue, 1, 4).map { it.messageId })
        assertEquals(listOf(2, 3, 1, 4, 5), playNext(queue, 2, 0).map { it.messageId })
        assertEquals(queue, playNext(queue, 2, 2))
    }

    @Test
    fun shuffleOffReturnsToTheOrderBeforeIt() {
        val reverse = MusicModes().with(MusicOrder.Reverse)
        assertEquals(MusicOrder.Reverse, reverse.order)
        val shuffled = reverse.with(MusicOrder.Shuffle)
        assertEquals(MusicOrder.Shuffle, shuffled.order)
        assertEquals(MusicOrder.Reverse, shuffled.copy(shuffle = false).order)
        assertEquals(MusicModes(), shuffled.with(MusicOrder.Chat))
        assertEquals(listOf(MusicOrder.Shuffle, MusicOrder.Reverse, MusicOrder.Chat), listOf(MusicOrder.Chat, MusicOrder.Shuffle, MusicOrder.Reverse).map(::nextOrder))
        val list = MusicPlaylist("u1", (1..20).map { song(it) })
        assertNull(list.pageAhead(3))
        assertEquals(MusicPage.Older, list.pageAhead(2))
        assertEquals(MusicPage.Newer, list.copy(olderDone = true).pageAhead(0))
        assertNull(list.copy(olderDone = true, newerDone = true).pageAhead(0))
    }
}
