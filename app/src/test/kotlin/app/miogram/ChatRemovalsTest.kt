package app.miogram

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.miogram.FakeGroups.Companion.DEVS_ID
import app.miogram.FakeGroups.Companion.FAMILY_ID
import app.miogram.data.AvatarStore
import app.miogram.data.ChatRemovals
import app.miogram.data.DialogsStore
import app.miogram.data.SettingsStore
import app.miogram.data.telegram.SignInResult
import app.miogram.lock.AppLock
import app.miogram.nav.Navigator
import app.miogram.push.ChatOnScreen
import app.miogram.push.NotificationAvatars
import app.miogram.push.NotificationHistory
import app.miogram.push.PushNotifier
import app.miogram.push.StoredNotificationPeers
import app.miogram.push.WorkNotificationPreviews
import cn.ellerotta.core.model.ChatRemoval
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.session.RpcException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatRemovalsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private class World(val fake: FakeTelegram, val store: DialogsStore, val removals: ChatRemovals) {
        fun dialog(key: String): DialogItem = store.state.value.let { (it.dialogs + it.archived).first { row -> row.key == key } }

        fun shown(key: String) = store.decorated.value.let { (it.dialogs + it.archived).any { row -> row.key == key } }

        suspend fun until(what: String, condition: () -> Boolean) {
            withTimeout(20_000) { while (!condition()) delay(25) }
        }
    }

    private fun world(undo: Int = 400, test: suspend World.() -> Unit) = runBlocking<Unit> {
        val dir = Files.createTempDirectory("poc").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        FakeTelegram().use { fake ->
            fake.groups.inDialogs = true
            val repos = TestRepositories(dir, fake)
            val settings = SettingsStore(context)
            val store = DialogsStore(repos.dialogs, repos.updates, AvatarStore(repos.media, scope), scope)
            val onScreen = ChatOnScreen(Navigator(), AppLock(settings, FakeDeviceAuth()))
            val notifier = PushNotifier(context, settings, NotificationHistory(context), StoredNotificationPeers(NotificationAvatars(dir), repos.notifySettings), onScreen, WorkNotificationPreviews(context))
            val removals = ChatRemovals(repos.dialogs, store, notifier, scope).apply { this.undo = undo.milliseconds }
            withTimeout(120_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                repos.updates.updateState.first { it != null }
                store.start()
                store.state.first { it.loaded }
                World(fake, store, removals).test()
            }
            repos.telegram.close()
        }
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun aDeletedPrivateChatLeavesAtOnceAndItsHistoryGoesAfterTheUndoTimeRoundByRound(): Unit = world(undo = 2_000) {
        fake.removals.moreRounds = 2
        val alice = dialog("u501")
        removals.remove(alice, ChatRemoval.Delete, revoke = true)

        until("hidden") { !shown("u501") }
        assertEquals(ChatRemoval.Delete, removals.pending.value?.removal)
        assertTrue(fake.removals.historyCalls.isEmpty())

        until("three rounds") { fake.removals.historyCalls.size == 3 }
        fake.removals.historyCalls.forEach { call ->
            assertFalse(call.justClear)
            assertTrue(call.revoke)
            assertEquals(501L, (call.peer as InputPeerUser).userId)
        }
        until("the row is forgotten") { store.state.value.dialogs.none { it.key == "u501" } }
        until("the snackbar is gone") { removals.pending.value == null }
        assertEquals(3, fake.removals.historyCalls.size)
        until("still hidden") { !shown("u501") }
    }

    @Test
    fun anUndoBringsTheRowBackAndNothingIsSent(): Unit = world(undo = 500) {
        removals.remove(dialog("u501"), ChatRemoval.Delete, revoke = false)
        until("hidden") { !shown("u501") }
        removals.undoPending()
        assertNull(removals.pending.value)
        until("shown again") { shown("u501") }
        delay(1_200)
        assertTrue(fake.removals.historyCalls.isEmpty())
        assertTrue(shown("u501"))
    }

    @Test
    fun aSecondRemovalSendsTheFirstAtOnce(): Unit = world(undo = 20_000) {
        removals.remove(dialog("u501"), ChatRemoval.Clear, revoke = true)
        until("still listed") { shown("u501") }
        assertEquals("", store.state.value.dialogs.first { it.key == "u501" }.preview.text)
        removals.remove(dialog("u502"), ChatRemoval.Delete, revoke = false)

        until("the first is sent") { fake.removals.historyCalls.size == 1 }
        val first = fake.removals.historyCalls.single()
        assertTrue(first.justClear)
        assertTrue(first.revoke)
        assertEquals(501L, (first.peer as InputPeerUser).userId)
        assertEquals("u502", removals.pending.value?.dialog?.key)
        removals.undoPending()
        delay(300)
        assertEquals(1, fake.removals.historyCalls.size)
        assertTrue(shown("u502"))
    }

    @Test
    fun theOtherSideIsAskedOnlyInAPrivateChatWithAPerson(): Unit = world(undo = 100) {
        val family = dialog("c$FAMILY_ID")
        removals.remove(family, ChatRemoval.Clear, revoke = true)
        until("cleared") { fake.removals.historyCalls.isNotEmpty() }
        assertTrue(fake.removals.historyCalls.single().justClear)
        assertFalse(fake.removals.historyCalls.single().revoke)
    }

    @Test
    fun aBasicGroupIsLeftThenItsHistoryDeleted(): Unit = world(undo = 100) {
        removals.remove(dialog("c$FAMILY_ID"), ChatRemoval.Delete)
        until("gone") { fake.removals.gone.contains("c$FAMILY_ID") }
        assertEquals(1, fake.groups.deleteChatUserCalls.size)
        assertEquals(FAMILY_ID, fake.groups.deleteChatUserCalls.single().chatId)
        assertFalse(fake.removals.historyCalls.single().justClear)
        until("forgotten") { store.state.value.dialogs.none { it.key == "c$FAMILY_ID" } }
    }

    @Test
    fun aChannelIsLeftWithoutDeletingItsHistory(): Unit = world(undo = 100) {
        removals.remove(dialog("ch$DEVS_ID"), ChatRemoval.Delete)
        until("left") { fake.groups.leaveCalls.isNotEmpty() }
        assertEquals(listOf(DEVS_ID), fake.groups.leaveCalls)
        assertTrue(fake.removals.historyCalls.isEmpty())
        until("forgotten") { store.state.value.dialogs.none { it.key == "ch$DEVS_ID" } }
    }

    @Test
    fun aSupergroupIsClearedForTheUserOnly(): Unit = world(undo = 100) {
        removals.remove(dialog("ch$DEVS_ID"), ChatRemoval.Clear, revoke = true)
        until("cleared") { fake.removals.channelHistoryCalls.isNotEmpty() }
        assertFalse(fake.removals.channelHistoryCalls.single().forEveryone)
        assertTrue(shown("ch$DEVS_ID"))
    }

    @Test
    fun theOwnerDeletesAGroupForEveryone(): Unit = world(undo = 100) {
        val devs = dialog("ch$DEVS_ID")
        assertTrue(devs.owner)
        assertEquals(listOf(ChatRemoval.Clear, ChatRemoval.Delete, ChatRemoval.DeleteForEveryone), devs.removals)
        removals.remove(devs, ChatRemoval.DeleteForEveryone)
        until("deleted") { fake.removals.deletedChannels.isNotEmpty() }
        assertEquals(listOf(DEVS_ID), fake.removals.deletedChannels)
        until("forgotten") { store.state.value.dialogs.none { it.key == "ch$DEVS_ID" } }

        val family = dialog("c$FAMILY_ID")
        assertEquals(PeerKind.Group, family.kind)
        assertEquals(listOf(ChatRemoval.Clear, ChatRemoval.Delete), family.removals)
        removals.remove(family.copy(owner = true), ChatRemoval.DeleteForEveryone)
        until("deleted chat") { fake.removals.deletedChats.isNotEmpty() }
        assertEquals(listOf(FAMILY_ID), fake.removals.deletedChats)
    }

    @Test
    fun aFailedRemovalBringsTheRowBackAndSaysWhy(): Unit = world(undo = 100) {
        fake.removals.failing = true
        coroutineScope {
            val failure = async { removals.failures.first() }
            delay(50)
            removals.remove(dialog("u501"), ChatRemoval.Delete, revoke = true)
            val error = withTimeout(20_000) { failure.await() }
            assertIs<RpcException>(error)
        }
        until("the row is back") { shown("u501") }
        assertNull(removals.pending.value)
    }
}
