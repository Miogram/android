package cn.ellerotta.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import cn.ellerotta.core.model.AppPrivacy
import cn.ellerotta.core.model.GlobalPrivacy
import cn.ellerotta.core.model.PrivacyBase
import cn.ellerotta.core.model.PrivacyKey
import cn.ellerotta.core.model.PrivacySetting
import cn.ellerotta.core.model.PrivacyState
import cn.ellerotta.core.model.Birthday
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MuteDuration
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.ProfileDetails
import cn.ellerotta.core.model.SessionInfo
import cn.ellerotta.core.ui.components.profile.PeerProfileScreen
import cn.ellerotta.core.ui.components.profile.PrivacyScreen
import cn.ellerotta.core.ui.components.profile.SessionsScreen
import cn.ellerotta.core.ui.components.profile.isBirthdayToday
import cn.ellerotta.core.ui.components.profile.presenceText
import cn.ellerotta.core.ui.theme.MioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileTest {
    @get:Rule
    val compose = createComposeRule()

    private val me = ProfileDetails(
        key = "u1", id = 1, name = "Ada Lovelace", kind = PeerKind.Saved, presence = Presence.Online,
        phone = "79991234567", usernames = listOf("ada", "countess"), about = "first programmer",
        birthday = Birthday(10, 12, 1815), isSelf = true,
    )

    @Test
    fun presenceFollowsTelegramsRules() {
        val zone = ZoneOffset.UTC
        val now = ZonedDateTime.of(2026, 10, 2, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        val seconds = (now / 1000).toInt()
        val texts = mutableListOf<String>()
        @Composable
        fun add(presence: Presence) {
            texts += presenceText(presence, now, zone)
        }
        compose.setContent {
            add(Presence.Online)
            add(Presence.Offline(seconds - 20))
            add(Presence.Offline(seconds - 5 * 60))
            add(Presence.Offline(seconds - 3 * 3600))
            add(Presence.Offline(seconds - 20 * 3600))
            add(Presence.Recently)
            add(Presence.Members(12, 3, channel = false))
            add(Presence.Members(1, null, channel = true))
        }
        compose.waitForIdle()
        assertEquals(
            listOf(
                "online", "last seen just now", "last seen 5 minutes ago", "last seen 3 hours ago",
                "last seen yesterday at 19:00", "last seen recently", "12 members, 3 online", "1 subscriber",
            ),
            texts.take(8).map { it.replace("7:00 PM", "19:00") },
        )
        assertTrue(isBirthdayToday(Birthday(2, 10, null), LocalDate.of(2026, 10, 2)))
        assertFalse(isBirthdayToday(Birthday(3, 10, null), LocalDate.of(2026, 10, 2)))
    }

    @Test
    fun ownProfileShowsDetails() {
        compose.setContent {
            MioTheme {
                PeerProfileScreen(me, "", emptyMap(), busy = false, onBack = {}, onMessage = {}, onMute = {}, onUnmute = {}, self = true)
            }
        }
        compose.onNodeWithTag("profileName").assertIsDisplayed()
        compose.onNodeWithText("online").assertIsDisplayed()
        compose.onNodeWithTag("message").assertDoesNotExist()
        compose.onNodeWithTag("toggleMute").assertDoesNotExist()
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasText("+7 999 123-45-67"))
        compose.onNodeWithText("+7 999 123-45-67").assertIsDisplayed()
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasText("also @countess"))
        compose.onNodeWithText("also @countess").assertIsDisplayed()
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasText("first programmer"))
        compose.onNodeWithText("first programmer").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag("logOut").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun ownSongCardPlaysAndPutsTheSongsInOrder() {
        val songs = listOf("one", "two", "three").mapIndexed { i, title ->
            MediaItem("doc${i + 1}", "$title.mp3", 4_000_000, 2, isPhoto = false, kind = MediaKind.Music, title = "Song $title", performer = "Band")
        }
        var plays = 0
        var asked = 0
        val moves = mutableListOf<Pair<Int, Int>>()
        var order by mutableStateOf<List<MediaItem>?>(null)
        compose.setContent {
            MioTheme {
                PeerProfileScreen(
                    me.copy(music = songs[0]), "", emptyMap(), busy = false, onBack = {}, onMessage = {}, onMute = {}, onUnmute = {}, self = true,
                    onPlayMusic = { plays++ }, songOrder = order, onOrderSongs = { asked++; order = songs },
                    onMoveSong = { from, to -> moves += from to to; order = order!!.toMutableList().apply { add(to, removeAt(from)) } },
                    onCloseSongOrder = { order = null },
                )
            }
        }
        compose.onNodeWithTag("musicTitle", useUnmergedTree = true).assertTextEquals("Song one")
        compose.onNodeWithTag("profileMusic").performClick()
        compose.onNodeWithTag("profileMusic").performTouchInput { longClick() }
        assertEquals(1 to 1, plays to asked)
        compose.onNodeWithTag("songOrderSheet").assertExists()
        compose.onNodeWithTag("songUp_doc1").assertIsNotEnabled()
        compose.onNodeWithTag("songDown_doc3").assertIsNotEnabled()
        compose.onNodeWithTag("songDown_doc1").performClick()
        compose.onNodeWithTag("songUp_doc2").assertIsNotEnabled()
        compose.onNodeWithTag("songUp_doc3").performClick()
        assertEquals(listOf(0 to 1, 2 to 1), moves)
    }

    @Test
    fun peerProfileActionsAndSessions() {
        var messages = 0
        val mutes = mutableListOf<MuteDuration>()
        var unmutes = 0
        var muted by mutableStateOf(false)
        compose.setContent {
            MioTheme {
                PeerProfileScreen(
                    me.copy(isSelf = false, kind = PeerKind.User, presence = Presence.Recently, muted = muted),
                    "Ada", emptyMap(), busy = false, onBack = {}, onMessage = { messages++ },
                    onMute = { mutes += it; muted = true }, onUnmute = { unmutes++; muted = false },
                )
            }
        }
        compose.onNodeWithText("last seen recently").assertIsDisplayed()
        compose.onNodeWithTag("message").performClick()
        compose.onNodeWithTag("toggleMute").performClick()
        assertEquals(listOf("For 1 hour", "For 8 hours", "For 1 day", "For 7 days", "Forever"), MuteDuration.entries.map { compose.onNodeWithTag("muteFor${it.name}").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString() })
        compose.onNodeWithTag("muteForEightHours").performClick()
        compose.onNodeWithTag("muteMenu").assertDoesNotExist()
        compose.onNodeWithContentDescription("Unmute").assertIsDisplayed()
        compose.onNodeWithTag("toggleMute").performClick()
        compose.onNodeWithTag("muteMenu").assertDoesNotExist()
        assertEquals(Triple(1, listOf(MuteDuration.EightHours), 1), Triple(messages, mutes.toList(), unmutes))
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasTestTag("about"))
        compose.onNodeWithTag("about").assertIsDisplayed()
    }

    @Test
    fun sessionsTerminate() {
        val terminated = mutableListOf<Long>()
        var all = 0
        val sessions = listOf(
            SessionInfo(1, "Pixel 9", "Android", "16", "miogram", "0.1.0", "Amsterdam, Netherlands", "1.2.3.4", 0, current = true, official = false),
            SessionInfo(2, "iPhone 17", "iOS", "26", "Telegram iOS", "12.0", "Berlin, Germany", "5.6.7.8", 1_790_000_000, current = false, official = true),
        )
        compose.setContent { MioTheme { SessionsScreen(sessions, busy = false, onBack = {}, onTerminate = { terminated += it.hash }, onTerminateOthers = { all++ }) } }
        compose.onNodeWithText("This device").assertIsDisplayed()
        compose.onNodeWithTag("sessions").performScrollToNode(hasTestTag("session_2"))
        compose.onNodeWithTag("session_2").performClick()
        compose.onNodeWithTag("terminate").performClick()
        assertEquals(listOf(2L), terminated)
        compose.onNodeWithTag("sessions").performScrollToNode(hasTestTag("terminateOthers"))
        compose.onNodeWithTag("terminateOthers").performClick()
        compose.onNodeWithTag("confirmTerminateOthers").performClick()
        assertEquals(1, all)
    }

    @Test
    fun privacyShowsRulesAndChooses() {
        val chosen = mutableListOf<Pair<PrivacyKey, PrivacyBase>>()
        val globals = mutableListOf<GlobalPrivacy>()
        val apps = mutableListOf<AppPrivacy>()
        val state = PrivacyState(
            settings = listOf(
                PrivacySetting(PrivacyKey.LastSeen, PrivacyBase.Contacts, allowed = 2, disallowed = 1),
                PrivacySetting(PrivacyKey.FindByPhone, PrivacyBase.Everybody),
            ),
            global = GlobalPrivacy(),
            accountTtlDays = 180,
        )
        compose.setContent {
            MioTheme {
                PrivacyScreen(
                    state, AppPrivacy(lock = true, lockAfterMinutes = 5), busy = false, onBack = {}, onApp = { apps += it },
                    onPrivacy = { key, base -> chosen += key to base }, onGlobal = { globals += it }, onAccountTtl = {},
                )
            }
        }
        compose.onNodeWithText("5 minutes").assertIsDisplayed()
        compose.onNodeWithTag("secureWindow").performClick()
        compose.onNodeWithTag("lockAfter").performClick()
        compose.onNodeWithTag("lockAfter_0").performClick()
        assertEquals(listOf(AppPrivacy(lock = true, lockAfterMinutes = 5, secureWindow = true), AppPrivacy(lock = true, lockAfterMinutes = 0)), apps)
        compose.onNodeWithTag("privacy").performScrollToNode(hasText("My contacts (−1, +2)"))
        compose.onNodeWithText("My contacts (−1, +2)").assertIsDisplayed()
        compose.onNodeWithTag("privacy").performScrollToNode(hasTestTag("privacy_FindByPhone"))
        compose.onNodeWithTag("privacy_FindByPhone").performClick()
        compose.onNodeWithTag("privacyChoice_Contacts").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag("privacyChoice_Nobody").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("privacyChoice_Contacts").performClick()
        assertEquals(listOf(PrivacyKey.FindByPhone to PrivacyBase.Contacts), chosen)
        compose.onNodeWithTag("privacy").performScrollToNode(hasTestTag("hideReadTime"))
        compose.onNodeWithTag("hideReadTime").performClick()
        assertEquals(listOf(GlobalPrivacy(hideReadMarks = true)), globals)
        compose.onNodeWithTag("privacy").performScrollToNode(hasText("6 months"))
    }
}
