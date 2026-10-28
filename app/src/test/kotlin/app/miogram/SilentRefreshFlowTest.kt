package app.miogram

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import app.miogram.FakeGroups.Companion.DEVS_ID
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class SilentRefreshFlowTest {
    private val fake = FakeTelegram().apply { groups.inDialogs = true }
    private val dir = Files.createTempDirectory("poc").toFile()

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @BindValue
    @JvmField
    val server = ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test")

    @BindValue
    @JvmField
    val telegram = TelegramClientHolder(dir) { id, hash, logger -> fake.config(id, hash, logger) }

    @Inject lateinit var navigator: Navigator

    @After
    fun tearDown() {
        fake.late.values.forEach { it.complete(Unit) }
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    private fun hold(name: String) = CompletableDeferred<Unit>().also { fake.late[name] = it }

    private fun open(route: Route) {
        compose.runOnIdle { navigator.go(route) }
        compose.waitUntilIdle { navigator.current == route }
    }

    private fun back() {
        compose.runOnIdle { navigator.back() }
        compose.waitForIdle()
    }

    private fun absent(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()

    private fun scrollTo(tag: String) = compose.waitUntilIdle {
        !absent(tag) || runCatching { compose.onNodeWithTag("peerProfile").performScrollToNode(hasTestTag(tag)) }.isSuccess
    }

    private fun mediaTab() {
        val tab = hasAnyAncestor(hasTestTag("profileTabs")) and hasText("Media", substring = true)
        scrollTo("profileTabs")
        compose.waitUntilIdle { compose.onAllNodes(tab).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(tab)[0].performClick()
    }

    @Test
    fun profileShowsItsIndicatorOnlyTheFirstTime() {
        hilt.inject()
        compose.logIn()
        val route = Route.PeerProfile("u${fake.alice.id}", "Alice Example")

        var slow = hold("profile")
        open(route)
        compose.waitForTag("profileLoading", unmerged = true)
        slow.complete(Unit)
        compose.waitUntilIdle { absent("profileLoading") }
        back()

        slow = hold("profile")
        open(route)
        compose.waitForTag("peerProfile")
        compose.waitForText("Alice Example")
        compose.waitForIdle()
        check(absent("profileLoading")) { "the second open shows the loading line" }
        slow.complete(Unit)
    }

    @Test
    fun membersShowTheirIndicatorOnlyTheFirstTime() {
        hilt.inject()
        compose.logIn()
        val route = Route.Members("ch$DEVS_ID", "Devs")

        var slow = hold("members")
        open(route)
        compose.waitForTag("membersLoading", unmerged = true)
        slow.complete(Unit)
        compose.waitForTag("member_u${fake.alice.id}")
        back()

        slow = hold("members")
        open(route)
        compose.waitForTag("member_u${fake.alice.id}")
        compose.waitForIdle()
        check(absent("membersLoading")) { "the second open shows the loading line" }
        slow.complete(Unit)
    }

    @Test
    fun devicesShowTheirIndicatorOnlyTheFirstTime() {
        hilt.inject()
        compose.logIn()

        var slow = hold("sessions")
        open(Route.Sessions)
        compose.waitForTag("sessionsLoading", unmerged = true)
        slow.complete(Unit)
        compose.waitForText("This device")
        back()

        slow = hold("sessions")
        open(Route.Sessions)
        compose.waitForText("This device")
        compose.waitForIdle()
        check(absent("sessionsLoading")) { "the second open shows the loading line" }
        slow.complete(Unit)
    }

    @Test
    fun sharedMediaShowsItsIndicatorOnlyTheFirstTime() {
        val key = "u${fake.alice.id}"
        fake.sharedMedia.messages[key] = mutableListOf(fake.sharedMedia.photo(key, age = 200), fake.sharedMedia.photo(key, age = 100))
        hilt.inject()
        compose.logIn()
        val route = Route.PeerProfile(key, "Alice Example")

        var slow = hold("search")
        open(route)
        compose.waitForTag("peerProfile")
        mediaTab()
        scrollTo("sharedLoading")
        slow.complete(Unit)
        compose.waitForTag("sharedMonth", unmerged = true)
        back()

        slow = hold("search")
        val counters = hold("counters")
        open(route)
        compose.waitForTag("peerProfile")
        mediaTab()
        scrollTo("sharedMonth")
        compose.waitForIdle()
        check(absent("sharedLoading")) { "the second open shows the loading indicator" }
        counters.complete(Unit)
        slow.complete(Unit)
    }
}
