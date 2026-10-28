package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationsTest {
    private val now = 1_000L

    @Test
    fun aDefaultIsMutedByTheSharedRule() {
        assertFalse(ScopeNotifySettings().muted(now))
        assertTrue(ScopeNotifySettings(Int.MAX_VALUE).muted(now))
        assertFalse(ScopeNotifySettings(500).muted(now), "a mute that ran out")
    }

    @Test
    fun anExceptionDiffersOnlyWhenItChangesSomething() {
        val on = ScopeNotifySettings()
        val off = ScopeNotifySettings(Int.MAX_VALUE)
        fun chat(until: Int?, previews: Boolean? = null) = NotifyException("c1", "a", PeerKind.Group, muteUntil = until, showPreviews = previews)
        assertTrue(chat(Int.MAX_VALUE).differs(on, now))
        assertFalse(chat(Int.MAX_VALUE).differs(off, now), "muted under a muted default")
        assertTrue(chat(0).differs(off, now))
        assertFalse(chat(0).differs(on, now))
        assertFalse(chat(500).differs(on, now), "its mute ran out")
        assertTrue(chat(2_000).differs(on, now))
        assertTrue(chat(null, previews = false).differs(on, now))
        assertFalse(chat(null, previews = true).differs(on, now))
        assertTrue(chat(Int.MAX_VALUE).differs(null, now), "an unknown default is unmuted")
    }

    @Test
    fun theExceptionsOfAScopeAreMutedFirstThenByName() {
        val state = NotificationSettingsState(
            scopes = mapOf(NotifyScope.Chats to ScopeNotifySettings()),
            exceptions = mapOf(
                NotifyScope.Chats to listOf(
                    NotifyException("c1", "zeta", PeerKind.Group, muteUntil = Int.MAX_VALUE),
                    NotifyException("c2", "Alpha", PeerKind.Group, showPreviews = false),
                    NotifyException("c3", "beta", PeerKind.Group, muteUntil = Int.MAX_VALUE),
                    NotifyException("c4", "same", PeerKind.Group, muteUntil = 0),
                ),
            ),
        )
        assertEquals(listOf("c3", "c1", "c2"), state.exceptionsOf(NotifyScope.Chats, now)?.map { it.key })
        assertEquals(emptyList(), state.exceptionsOf(NotifyScope.Users, now))
        assertNull(NotificationSettingsState().exceptionsOf(NotifyScope.Users, now))
    }
}
