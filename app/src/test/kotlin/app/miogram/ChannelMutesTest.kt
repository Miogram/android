package app.miogram

import app.miogram.data.AvatarStore
import app.miogram.data.DialogsState
import app.miogram.data.DialogsStore
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.ktproto.api.types.NotifyBroadcasts
import cn.ellerotta.ktproto.api.types.NotifyChats
import cn.ellerotta.ktproto.api.types.NotifyPeer
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerNotifySettings
import cn.ellerotta.ktproto.api.types.UpdateNotifySettings
import cn.ellerotta.ktproto.api.types.UpdateShort
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class ChannelMutesTest {
    private val blog = "ch${FakeDiscussion.BLOG_ID}"
    private val chat = "ch${FakeDiscussion.CHAT_ID}"
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

    private fun App.muted() = listOf(blog, chat, devs, family).associateWith { row(it).muted }

    private suspend fun App.push(settings: UpdateNotifySettings) = fake.cluster.updates.push(UpdateShort(settings, FakeTelegram.NOW))

    @Test
    fun aChannelWithoutASettingFollowsTheDefaultForChannels(): Unit = app(server = { defaultMutes["broadcasts"] = Int.MAX_VALUE }) {
        assertEquals(PeerKind.Channel, row(blog).kind)
        assertEquals(mapOf(blog to true, chat to false, devs to false, family to false), muted(), "only the broadcast channel is muted by the default for channels")
    }

    @Test
    fun aChannelWithoutASettingIsUnmutedWhenTheDefaultIs(): Unit = app {
        assertEquals(mapOf(blog to false, chat to false, devs to false, family to false), muted())
    }

    @Test
    fun aSupergroupFollowsTheDefaultForGroupsNotForChannels(): Unit = app(server = { defaultMutes["chats"] = Int.MAX_VALUE }) {
        assertEquals(PeerKind.Group, row(chat).kind)
        assertEquals(mapOf(blog to false, chat to true, devs to true, family to true), muted(), "supergroups and basic groups are muted by the default for groups, the broadcast channel is not")
    }

    @Test
    fun anOwnSettingWinsOverTheDefault(): Unit = app(server = {
        defaultMutes["broadcasts"] = Int.MAX_VALUE
        defaultMutes["chats"] = Int.MAX_VALUE
        ownMutes[FakeDiscussion.BLOG_ID] = 0
        ownMutes[FakeGroups.DEVS_ID] = Int.MAX_VALUE
        ownMutes[FakeGroups.FAMILY_ID] = 5
    }) {
        assertEquals(mapOf(blog to false, chat to true, devs to true, family to false), muted())
        assertFalse(row(blog).followsDefault)
        assertTrue(row(chat).followsDefault)
    }

    @Test
    fun aMuteThatRanOutIsUnmutedAndOneInTheFutureIsNot(): Unit = app(server = {
        ownMutes[FakeDiscussion.BLOG_ID] = 5
        ownMutes[FakeDiscussion.CHAT_ID] = (System.currentTimeMillis() / 1000).toInt() + 3_600
        ownMutes[FakeGroups.DEVS_ID] = Int.MAX_VALUE
    }) {
        assertEquals(mapOf(blog to false, chat to true, devs to true, family to false), muted())
    }

    @Test
    fun changingTheDefaultForChannelsChecksTheChannelsAgain(): Unit = app(server = { ownMutes[FakeGroups.DEVS_ID] = Int.MAX_VALUE }) {
        assertEquals(mapOf(blog to false, chat to false, devs to true, family to false), muted())

        push(UpdateNotifySettings(NotifyBroadcasts, PeerNotifySettings(muteUntil = Int.MAX_VALUE)))
        until { state -> state.dialogs.first { it.key == blog }.muted }
        assertEquals(mapOf(blog to true, chat to false, devs to true, family to false), muted(), "groups are not channels")

        fake.ownMutes[FakeDiscussion.BLOG_ID] = 0
        push(UpdateNotifySettings(NotifyPeer(PeerChannel(FakeDiscussion.BLOG_ID)), PeerNotifySettings(muteUntil = 0)))
        until { state -> !state.dialogs.first { it.key == blog }.muted }
        push(UpdateNotifySettings(NotifyBroadcasts, PeerNotifySettings(muteUntil = Int.MAX_VALUE)))
        push(UpdateNotifySettings(NotifyChats, PeerNotifySettings(muteUntil = Int.MAX_VALUE)))
        until { state -> state.dialogs.first { it.key == chat }.muted }
        assertEquals(mapOf(blog to false, chat to true, devs to true, family to true), muted(), "its own unmuted setting beats the default for channels")

        push(UpdateNotifySettings(NotifyChats, PeerNotifySettings()))
        until { state -> !state.dialogs.first { it.key == chat }.muted }
        assertEquals(mapOf(blog to false, chat to false, devs to true, family to false), muted())
    }

    @Test
    fun anUpdateWithoutAMuteUntilGivesTheChannelBackToTheDefault(): Unit = app(server = {
        defaultMutes["broadcasts"] = Int.MAX_VALUE
        ownMutes[FakeDiscussion.BLOG_ID] = 0
    }) {
        assertFalse(row(blog).muted)
        push(UpdateNotifySettings(NotifyPeer(PeerChannel(FakeDiscussion.BLOG_ID)), PeerNotifySettings()))
        until { state -> state.dialogs.first { it.key == blog }.muted }
        assertTrue(row(blog).followsDefault)
    }

    @Test
    fun unmutingAChannelTheDefaultMutesStaysUnmuted(): Unit = app(server = { defaultMutes["broadcasts"] = Int.MAX_VALUE }) {
        assertTrue(row(blog).muted)
        repos.dialogs.setMuted(blog, false)
        assertEquals(0, fake.lastNotifySettings?.settings?.muteUntil, "an unmute is mute_until 0, not nothing")
        val reloaded = repos.dialogs.peerDialogs(listOf(blog)).single()
        assertFalse(reloaded.muted, "telegram's own unmuted setting beats the default for channels")
        assertFalse(reloaded.followsDefault)
        repos.dialogs.setMuted(blog, true)
        assertTrue(repos.dialogs.peerDialogs(listOf(blog)).single().muted)
    }

    @Test
    fun profilesFollowTheSameRule(): Unit = app(server = { defaultMutes["broadcasts"] = Int.MAX_VALUE }) {
        assertTrue(repos.profiles.profile(blog).muted, "a channel without a setting is muted by the default for channels")
        assertFalse(repos.profiles.profile(chat).muted, "a supergroup is not")
        assertFalse(repos.profiles.profile(family).muted)
        fake.defaultMutes["chats"] = Int.MAX_VALUE
        fake.defaultMutes["broadcasts"] = 0
        push(UpdateNotifySettings(NotifyChats, PeerNotifySettings(muteUntil = Int.MAX_VALUE)))
        push(UpdateNotifySettings(NotifyBroadcasts, PeerNotifySettings(muteUntil = 0)))
        until { state -> state.dialogs.first { it.key == chat }.muted }
        until { state -> !state.dialogs.first { it.key == blog }.muted }
        assertFalse(repos.profiles.profile(blog).muted)
        assertTrue(repos.profiles.profile(chat).muted)
        assertTrue(repos.profiles.profile(family).muted)
        fake.ownMutes[FakeGroups.DEVS_ID] = 0
        assertFalse(repos.profiles.profile(devs).muted, "its own setting wins in the profile too")
    }
}
