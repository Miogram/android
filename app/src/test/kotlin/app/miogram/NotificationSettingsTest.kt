package app.miogram

import app.miogram.data.AvatarStore
import app.miogram.data.DialogsState
import app.miogram.data.DialogsStore
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.NotifyScope
import cn.ellerotta.core.model.ScopeNotifySettings
import cn.ellerotta.ktproto.api.types.InputNotifyBroadcasts
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class NotificationSettingsTest {
    private val blog = "ch${FakeDiscussion.BLOG_ID}"
    private val devs = "ch${FakeGroups.DEVS_ID}"
    private val family = "c${FakeGroups.FAMILY_ID}"

    private class App(val fake: FakeTelegram, val repos: TestRepositories, val store: DialogsStore)

    private fun app(server: FakeTelegram.() -> Unit = {}, test: suspend App.() -> Unit) = runBlocking<Unit> {
        val dir = Files.createTempDirectory("poc").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        FakeTelegram().apply { groups.inDialogs = true; groups.discussion.inDialogs = true }.use { fake ->
            fake.server()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                repos.updates.updateState.first { it != null }
                val store = DialogsStore(repos.dialogs, repos.updates, AvatarStore(repos.media, scope), scope)
                store.start()
                store.state.first { it.loaded }
                App(fake, repos, store).test()
            }
            repos.telegram.close()
        }
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun App.row(key: String): DialogItem = store.state.value.let { (it.dialogs + it.archived).first { row -> row.key == key } }

    private suspend fun App.until(condition: (DialogsState) -> Boolean) = withTimeout(20_000) { store.state.first(condition) }

    @Test
    fun theDefaultsAreReadAndAChangeReachesTheList(): Unit = app(server = { defaultPreviews["users"] = false }) {
        val defaults = repos.notifySettings.defaults()
        assertEquals(ScopeNotifySettings(0, showPreviews = false), defaults[NotifyScope.Users])
        assertEquals(ScopeNotifySettings(0, showPreviews = true), defaults[NotifyScope.Broadcasts])
        assertFalse(row(blog).muted)

        repos.notifySettings.setDefault(NotifyScope.Broadcasts, ScopeNotifySettings(Int.MAX_VALUE, showPreviews = false))
        assertIs<InputNotifyBroadcasts>(fake.lastNotifySettings?.peer)
        assertEquals(Int.MAX_VALUE, fake.defaultMutes["broadcasts"])
        assertEquals(false, fake.defaultPreviews["broadcasts"])
        until { state -> state.dialogs.first { it.key == blog }.muted }
        assertFalse(row(devs).muted, "a supergroup follows the default for groups")
    }

    @Test
    fun theExceptionsAreTheChatsWithASettingOfTheirOwn(): Unit = app(server = {
        ownMutes[FakeGroups.DEVS_ID] = Int.MAX_VALUE
        ownMutes[FakeDiscussion.BLOG_ID] = 0
    }) {
        val exceptions = repos.notifySettings.exceptions()
        assertEquals(listOf(devs), exceptions[NotifyScope.Chats]?.map { it.key })
        assertEquals("Kotlin devs", exceptions[NotifyScope.Chats]?.single()?.name)
        assertEquals(Int.MAX_VALUE, exceptions[NotifyScope.Chats]?.single()?.muteUntil)
        assertEquals(listOf(blog), exceptions[NotifyScope.Broadcasts]?.map { it.key })
        assertEquals(0, exceptions[NotifyScope.Broadcasts]?.single()?.muteUntil)
        assertNull(exceptions[NotifyScope.Users])
    }

    @Test
    fun anExceptionChangesAndGoesBackToTheDefault(): Unit = app(server = {
        defaultMutes["chats"] = Int.MAX_VALUE
        ownMutes[FakeGroups.FAMILY_ID] = 0
    }) {
        assertFalse(row(family).muted)
        assertTrue(row(devs).muted)

        repos.notifySettings.setException(family, Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, fake.ownMutes[FakeGroups.FAMILY_ID])
        until { state -> state.dialogs.first { it.key == family }.muted }

        repos.notifySettings.setException(devs, 0)
        until { state -> !state.dialogs.first { it.key == devs }.muted }
        assertFalse(row(devs).followsDefault)

        repos.notifySettings.setException(devs, null)
        assertNull(fake.ownMutes[FakeGroups.DEVS_ID])
        until { state -> state.dialogs.first { it.key == devs }.let { it.muted && it.followsDefault } }
        assertEquals(listOf(family), repos.notifySettings.exceptions()[NotifyScope.Chats]?.map { it.key })
    }

    @Test
    fun aResetTakesEveryExceptionAndDefaultAway(): Unit = app(server = {
        defaultMutes["broadcasts"] = Int.MAX_VALUE
        ownMutes[FakeGroups.DEVS_ID] = Int.MAX_VALUE
    }) {
        repos.notifySettings.exceptions()
        assertTrue(row(blog).muted)
        assertTrue(row(devs).muted)

        val defaults = repos.notifySettings.reset()
        assertEquals(NotifyScope.entries.associateWith { ScopeNotifySettings() }, defaults)
        until { state -> state.dialogs.none { it.key in setOf(blog, devs) && it.muted } }
        assertTrue(repos.notifySettings.exceptions().isEmpty())
        assertTrue(row(devs).followsDefault)
    }
}
