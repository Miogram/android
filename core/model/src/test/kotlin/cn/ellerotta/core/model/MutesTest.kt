package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MutesTest {
    private val now = 1_800_000_000L

    @Test
    fun aChatWithoutASettingFollowsTheDefault() {
        assertTrue(Mutes.muted(null, Int.MAX_VALUE, now))
        assertTrue(Mutes.muted(null, (now + 60).toInt(), now))
        assertFalse(Mutes.muted(null, 0, now))
        assertFalse(Mutes.muted(null, null, now), "an unknown default is unmuted")
    }

    @Test
    fun anOwnSettingWinsOverTheDefault() {
        assertFalse(Mutes.muted(0, Int.MAX_VALUE, now), "unmuted on its own in a muted kind")
        assertTrue(Mutes.muted(Int.MAX_VALUE, 0, now), "muted on its own in an unmuted kind")
        assertFalse(Mutes.muted((now - 1).toInt(), Int.MAX_VALUE, now), "a mute that ran out stays the chat's own setting")
    }

    @Test
    fun theTimeDecides() {
        assertTrue(Mutes.muted((now + 1).toInt(), null, now))
        assertFalse(Mutes.muted(now.toInt(), null, now))
        assertFalse(Mutes.muted(5, null, now))
        assertTrue(Mutes.muted(Int.MAX_VALUE, null, now))
    }

    @Test
    fun scopesFollowTelegramsThreeDefaults() {
        assertEquals(NotifyScope.Users, NotifyScope.of(PeerKind.User))
        assertEquals(NotifyScope.Users, NotifyScope.of(PeerKind.Bot))
        assertEquals(NotifyScope.Users, NotifyScope.of(PeerKind.Saved))
        assertEquals(NotifyScope.Chats, NotifyScope.of(PeerKind.Group), "supergroups are groups")
        assertEquals(NotifyScope.Broadcasts, NotifyScope.of(PeerKind.Channel))
    }
}
