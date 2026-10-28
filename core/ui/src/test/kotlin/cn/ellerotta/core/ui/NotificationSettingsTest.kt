package cn.ellerotta.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.ellerotta.core.model.NotificationSettingsState
import cn.ellerotta.core.model.NotifyException
import cn.ellerotta.core.model.NotifyScope
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.ScopeNotifySettings
import cn.ellerotta.core.ui.components.settings.NotificationActions
import cn.ellerotta.core.ui.components.settings.NotificationSettingsScreen
import cn.ellerotta.core.ui.components.settings.NotifyExceptionsScreen
import cn.ellerotta.core.ui.theme.MioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = 1_000L
    private val state = NotificationSettingsState(
        scopes = mapOf(
            NotifyScope.Users to ScopeNotifySettings(),
            NotifyScope.Chats to ScopeNotifySettings(Int.MAX_VALUE),
            NotifyScope.Broadcasts to ScopeNotifySettings(showPreviews = false),
        ),
        exceptions = mapOf(
            NotifyScope.Users to listOf(
                NotifyException("u1", "Ann", PeerKind.User, muteUntil = Int.MAX_VALUE),
                NotifyException("u2", "Bob", PeerKind.User, muteUntil = 0),
            ),
        ),
    )

    @Test
    fun aDefaultIsTurnedOffWithItsSwitch() {
        var changed: Pair<NotifyScope, ScopeNotifySettings>? = null
        compose.setContent { MioTheme { NotificationSettingsScreen(state, busy = false, now, NotificationActions(onDefault = { scope, settings -> changed = scope to settings })) } }
        compose.onNodeWithTag("notify_Users").assertIsOn().performClick()
        assertEquals(NotifyScope.Users to ScopeNotifySettings(Int.MAX_VALUE), changed)
        compose.onNodeWithTag("notifications").performScrollToNode(hasTestTag("notify_Chats"))
        compose.onNodeWithTag("notify_Chats").assertIsOff()
        compose.onNodeWithTag("notifications").performScrollToNode(hasTestTag("preview_Broadcasts"))
        compose.onNodeWithTag("preview_Broadcasts").assertIsOff()
    }

    @Test
    fun theExceptionsCountLeavesOutChatsThatChangeNothing() {
        var opened: NotifyScope? = null
        compose.setContent { MioTheme { NotificationSettingsScreen(state, busy = false, now, NotificationActions(onOpenExceptions = { opened = it })) } }
        compose.onNodeWithText("1 chat").assertIsDisplayed()
        compose.onNodeWithTag("exceptions_Users").performScrollTo().performClick()
        assertEquals(NotifyScope.Users, opened)
    }

    @Test
    fun proximityAlertsTurnOffAndOpenTheirChannel() {
        var proximity by mutableStateOf(true)
        var opened = 0
        compose.setContent {
            MioTheme { NotificationSettingsScreen(state, busy = false, now, NotificationActions(onProximity = { proximity = it }, onOpenProximity = { opened++ }), proximity) }
        }
        compose.onNodeWithTag("notifications").performScrollToNode(hasTestTag("notifyProximity"))
        compose.onNodeWithText("Proximity alerts").assertIsDisplayed()
        compose.onNodeWithTag("notifyProximity").assertIsOn().performClick()
        assertEquals(false, proximity)
        compose.onNodeWithTag("notifyProximity").assertIsOff()
        compose.onNodeWithTag("notifications").performScrollToNode(hasTestTag("notificationsProximitySystem"))
        compose.onNodeWithTag("notificationsProximitySystem").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun anExceptionGoesBackToTheDefault() {
        var changed: Pair<String, Int?>? = Pair("", 1)
        compose.setContent { MioTheme { NotifyExceptionsScreen(NotifyScope.Users, state, emptyMap(), busy = false, now, NotificationActions(onException = { key, until -> changed = key to until })) } }
        compose.onNodeWithTag("exception_u1").assertIsDisplayed().performClick()
        compose.onNodeWithTag("exceptionRemove").performScrollTo().performClick()
        assertEquals("u1" to null, changed)
    }

    @Test
    fun anExceptionIsMutedForAWhile() {
        var changed: Pair<String, Int?>? = null
        compose.setContent { MioTheme { NotifyExceptionsScreen(NotifyScope.Users, state, emptyMap(), busy = false, now, NotificationActions(onException = { key, until -> changed = key to until })) } }
        compose.onNodeWithTag("exception_u1").performClick()
        compose.onNodeWithTag("exceptionMuteHour").performClick()
        assertEquals("u1" to (now + 3_600).toInt(), changed)
    }
}
