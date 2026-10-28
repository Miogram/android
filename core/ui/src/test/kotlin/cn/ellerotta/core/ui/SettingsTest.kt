package cn.ellerotta.core.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import cn.ellerotta.core.model.Appearance
import cn.ellerotta.core.model.ProfileInfo
import cn.ellerotta.core.model.ThemeMode
import cn.ellerotta.core.ui.components.settings.SettingsActions
import cn.ellerotta.core.ui.components.settings.SettingsContent
import cn.ellerotta.core.ui.components.settings.SettingsPageState
import cn.ellerotta.core.ui.theme.MioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rowsLeadToTheirScreensAndChangeSettings() {
        val opened = mutableListOf<String>()
        val appearances = mutableListOf<Appearance>()
        var logOuts = 0
        val me = ProfileInfo(1, "Ada Lovelace", phone = "79991234567", username = "ada", avatarKey = null)
        compose.setContent {
            MioTheme {
                SettingsContent(
                    SettingsPageState(sessions = 3, version = "0.1.0-test (1)", library = "ktproto 0.2.0-370a651 · layer 229"),
                    me,
                    emptyMap(),
                    SettingsActions(
                        onOpenProfile = { opened += "profile" },
                        onOpenNotifications = { opened += "notifications" },
                        onOpenPrivacy = { opened += "privacy" },
                        onOpenSessions = { opened += "sessions" },
                        onAppearance = { appearances += it },
                        onOpenWallpapers = { opened += "wallpapers" },
                        onOpenLanguage = { opened += "language" },
                        onOpenLog = { opened += "log" },
                        onLogOut = { logOuts++ },
                    ),
                    rememberLazyListState(),
                    PaddingValues(),
                )
            }
        }
        compose.onNodeWithText("Ada Lovelace").assertIsDisplayed()
        compose.onNodeWithText("+7 999 123-45-67, @ada").assertIsDisplayed()
        compose.onNodeWithTag("openOwnProfile").performClick()
        compose.onNodeWithText("3 devices").assertIsDisplayed()
        for (tag in listOf("openPrivacySettings", "openSessions", "openNotifications", "openWallpapers", "openLanguage", "openLog")) {
            compose.onNodeWithTag("settings").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performClick()
        }
        assertEquals(listOf("profile", "privacy", "sessions", "notifications", "wallpapers", "language", "log"), opened)
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("theme_Dark"))
        compose.onNodeWithTag("theme_Dark").performClick()
        assertEquals(ThemeMode.Dark, appearances.last().mode)
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("dynamicColor"))
        compose.onNodeWithTag("dynamicColor").performClick()
        assertEquals(!Appearance().dynamicColor, appearances.last().dynamicColor)
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("library"))
        compose.onNodeWithText("ktproto 0.2.0-370a651 · layer 229").assertIsDisplayed()
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("logOut"))
        compose.onNodeWithTag("logOut").performClick()
        assertEquals(0, logOuts)
        compose.onNodeWithTag("confirmLogOut").performClick()
        assertEquals(1, logOuts)
    }
}
