package app.miogram

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.miogram.data.AvatarStore
import app.miogram.data.DialogsStore
import app.miogram.data.SettingsStore
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.peerAvatarFile
import app.miogram.lock.AppLock
import app.miogram.nav.Navigator
import app.miogram.push.BackgroundSession
import app.miogram.push.ChatOnScreen
import app.miogram.push.NotificationActionReceiver
import app.miogram.push.NotificationActions
import app.miogram.push.NotificationAvatars
import app.miogram.push.NotificationHistory
import app.miogram.push.PushHandler
import app.miogram.push.PushNotifier
import app.miogram.push.PushRegistrationWorker
import app.miogram.push.StoredNotificationPeers
import app.miogram.push.WorkNotificationPreviews
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.UpdateReadHistoryInbox
import cn.ellerotta.ktproto.api.types.UpdateShort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PushFlowTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun workManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private fun daily(): WorkInfo? = WorkManager.getInstance(context).getWorkInfosForUniqueWork(PushRegistrationWorker.NAME).get().singleOrNull()

    @Test
    fun registersAfterLoginAndShowsDecryptedPushes(): Unit = runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        val dir = Files.createTempDirectory("poc").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        FakeTelegram().use { fake ->
            val repos = TestRepositories(dir, fake)
            val settings = SettingsStore(context)
            val store = DialogsStore(repos.dialogs, repos.updates, AvatarStore(repos.media, scope), scope)
            val session = BackgroundSession(repos.telegram, ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test"))
            val connection = keptConnected(repos.telegram, scope)
            val onScreen = ChatOnScreen(Navigator(), AppLock(settings, FakeDeviceAuth()))
            val notifier = PushNotifier(context, settings, NotificationHistory(context), StoredNotificationPeers(NotificationAvatars(dir), repos.notifySettings), onScreen, WorkNotificationPreviews(context))
            val push = PushHandler(repos.push, { "fcm-token" }, notifier, repos.updates, connection, session, context, repos.notifySettings, scope)
            val actions = NotificationActions(notifier, repos.messages, repos.dialogs, store, session, connection, scope)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                push.register()
                assertTrue(fake.cluster.push.registerRequests.isEmpty(), "nothing to register before the login")

                val request = repos.auth.sendCode("+999 66 1 4242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "11111")).profile.id
                push.register()
                push.register()
                val device = assertNotNull(fake.cluster.push.device("fcm-token"))
                assertEquals(2, device.tokenType)
                assertFalse(device.appSandbox)
                assertEquals(256, device.secret.size)
                assertTrue(device.noMuted, "no pushes at all for muted chats")
                assertEquals(1, fake.cluster.push.registerRequests.size, "once per token and process")
                val job = assertNotNull(daily())
                assertEquals(WorkInfo.State.ENQUEUED, job.state)
                assertEquals(TimeUnit.DAYS.toMillis(1), job.periodicityInfo?.repeatIntervalMillis)
                assertEquals(NetworkType.CONNECTED, job.constraints.requiredNetworkType)
                assertTrue(push.refresh())
                assertEquals(2, fake.cluster.push.registerRequests.size)
                assertTrue(fake.cluster.push.registerRequests.all { it.noMuted })
                repos.updates.updateState.first { it != null }
                repos.dialogs.dialogs()

                push.onMessage(fcm(fake, """{"loc_key":"MESSAGE_TEXT","loc_args":["Alice Liddell","see you at 8"],"custom":{"from_id":"501","msg_id":"41"}}"""))
                val private = assertNotNull(notifications.getNotification("u501", PushNotifier.CHAT_ID))
                val talk = assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(private))
                assertEquals("see you at 8", talk.messages.single().text.toString())
                assertEquals("Alice Liddell", talk.messages.single().person?.name.toString())
                assertNotNull(talk.messages.single().person?.icon)
                assertEquals(PushNotifier.CHANNEL_PRIVATE, private.channelId)
                val open = shadowOf(private.contentIntent).savedIntent
                assertEquals(PushNotifier.OPEN_CHAT, open.component?.className)
                assertEquals("u501", open.getStringExtra(PushNotifier.EXTRA_DIALOG))
                assertEquals(41, open.getIntExtra(PushNotifier.EXTRA_MESSAGE, 0))

                push.onMessage(fcm(fake, """{"loc_key":"CHAT_MESSAGE_PHOTO","loc_args":["Bob","Book club"],"custom":{"chat_id":"77","chat_from_id":"502","msg_id":"5"}}"""))
                val group = assertNotNull(notifications.getNotification("c77", PushNotifier.CHAT_ID))
                val groupTalk = assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(group))
                assertTrue(groupTalk.isGroupConversation)
                assertEquals("Book club", groupTalk.conversationTitle.toString())
                assertEquals("Bob", groupTalk.messages.single().person?.name.toString())
                assertEquals("u502", groupTalk.messages.single().person?.key)
                assertEquals("Photo", groupTalk.messages.single().text.toString())
                assertEquals(PushNotifier.CHANNEL_GROUPS, group.channelId)
                assertNotNull(notifications.getNotification(PushNotifier.SUMMARY_TAG, PushNotifier.SUMMARY_ID))

                push.onMessage(fcm(fake, """{"loc_key":"MESSAGE_DELETED","custom":{"from_id":"501","messages":"41"}}"""))
                assertNull(notifications.getNotification("u501", PushNotifier.CHAT_ID))
                push.onMessage(fcm(fake, """{"loc_key":"READ_HISTORY","custom":{"chat_id":"77","max_id":"5"}}"""))
                assertNull(notifications.getNotification("c77", PushNotifier.CHAT_ID))

                val payload = fcm(fake, """{"loc_key":"MESSAGE_TEXT","loc_args":["Mallory","hi"],"custom":{"from_id":"666","msg_id":"1"}}""").getValue("p")
                val tampered = payload.dropLast(2) + (if (payload[payload.length - 2] == 'A') "B" else "A") + payload.last()
                push.onMessage(mapOf("p" to tampered))
                assertNull(notifications.getNotification("u666", PushNotifier.CHAT_ID))
                assertEquals(0, notifications.size())

                push.onMessage(fcm(fake, """{"loc_key":"MESSAGE_TEXT","loc_args":["Alice Liddell","are you coming?"],"custom":{"from_id":"501","msg_id":"42"}}"""))
                val asking = assertNotNull(notifications.getNotification("u501", PushNotifier.CHAT_ID))
                val reply = asking.actions.single { it.remoteInputs?.isNotEmpty() == true }
                assertEquals(NotificationActionReceiver.EXTRA_REPLY, reply.remoteInputs.single().resultKey)
                val replyIntent = shadowOf(reply.actionIntent).savedIntent
                assertEquals(NotificationActionReceiver::class.java.name, replyIntent.component?.className)
                assertEquals(NotificationActionReceiver.ACTION_REPLY, replyIntent.action)
                assertEquals("u501", replyIntent.getStringExtra(PushNotifier.EXTRA_DIALOG))
                actions.reply("u501", "on my way ")
                val sent = assertNotNull(fake.lastSendMessage)
                assertEquals("on my way", sent.message)
                assertEquals(501L, (sent.peer as InputPeerUser).userId)
                val answered = assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notifications.getNotification("u501", PushNotifier.CHAT_ID)))
                assertEquals(listOf("are you coming?", "on my way"), answered.messages.map { it.text.toString() })
                assertTrue(answered.messages.last().person.let { it == null || it.name.toString() == "You" }, "the reply is the user's")

                val markRead = notifications.getNotification("u501", PushNotifier.CHAT_ID).actions.single { it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ }
                val readIntent = shadowOf(markRead.actionIntent).savedIntent
                assertEquals(NotificationActionReceiver.ACTION_READ, readIntent.action)
                assertEquals(42, readIntent.getIntExtra(NotificationActionReceiver.EXTRA_MAX_ID, 0))
                actions.markRead("u501", 42)
                assertEquals(42, fake.readUpTo[501L])
                assertNull(notifications.getNotification("u501", PushNotifier.CHAT_ID))

                push.onMessage(fcm(fake, """{"loc_key":"MESSAGE_TEXT","loc_args":["Alice Liddell","still there?"],"custom":{"from_id":"501","msg_id":"43"}}"""))
                assertNotNull(notifications.getNotification("u501", PushNotifier.CHAT_ID))
                val read = UpdateReadHistoryInbox(peer = PeerUser(501), maxId = 43, stillUnreadCount = 0, pts = fake.cluster.updates.pts + 1, ptsCount = 1)
                fake.cluster.updates.push(UpdateShort(read, FakeTelegram.NOW))
                while (notifications.getNotification("u501", PushNotifier.CHAT_ID) != null) delay(50)

                push.onMessage(fcm(fake, """{"loc_key":"MESSAGE_TEXT","loc_args":["Alice Liddell","bye"],"custom":{"from_id":"501","msg_id":"44"}}"""))
                push.unregister()
                assertEquals(listOf("fcm-token"), fake.cluster.push.unregisterRequests)
                assertEquals(0, notifications.size())
                assertEquals(WorkInfo.State.CANCELLED, daily()?.state, "no daily registration after the log out")
                repos.auth.logOut()
                assertNull(repos.push.decryptPush(mapOf("p" to payload)))
            }
            repos.telegram.close()
        }
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun liveMessagesNotifyLikePushesAndOnlyOnce(): Unit = runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        val dir = Files.createTempDirectory("poc").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        FakeTelegram().use { fake ->
            val repos = TestRepositories(dir, fake)
            val settings = SettingsStore(context)
            val session = BackgroundSession(repos.telegram, ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test"))
            val connection = keptConnected(repos.telegram, scope)
            val onScreen = ChatOnScreen(Navigator(), AppLock(settings, FakeDeviceAuth()))
            val notifier = PushNotifier(context, settings, NotificationHistory(context), StoredNotificationPeers(NotificationAvatars(dir), repos.notifySettings), onScreen, WorkNotificationPreviews(context))
            val push = PushHandler(repos.push, { "fcm-token" }, notifier, repos.updates, connection, session, context, repos.notifySettings, scope)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("+999 66 1 4242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "11111")).profile.id
                push.register()
                repos.updates.updateState.first { it != null }
                repos.dialogs.dialogs()
                connection.foreground(true)

                val live = fake.cluster.updates.message(501, "live from alice")
                while (notifications.getNotification("u501", PushNotifier.CHAT_ID) == null) delay(50)
                val shown = notifications.getNotification("u501", PushNotifier.CHAT_ID)
                val talk = assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(shown))
                assertEquals("live from alice", talk.messages.single().text.toString())
                assertEquals("Alice Example", talk.messages.single().person?.name.toString())
                assertEquals(PushNotifier.CHANNEL_PRIVATE, shown.channelId)
                assertEquals(live.id, shadowOf(shown.contentIntent).savedIntent.getIntExtra(PushNotifier.EXTRA_MESSAGE, 0))

                push.onMessage(fcm(fake, """{"loc_key":"MESSAGE_TEXT","loc_args":["Alice Example","live from alice"],"custom":{"from_id":"501","msg_id":"${live.id}"}}"""))
                val after = assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notifications.getNotification("u501", PushNotifier.CHAT_ID)))
                assertEquals(listOf("live from alice"), after.messages.map { it.text.toString() })

                val next = live.id + 1
                push.onMessage(fcm(fake, """{"loc_key":"MESSAGE_TEXT","loc_args":["Alice Example","pushed first"],"custom":{"from_id":"501","msg_id":"$next"}}"""))
                assertEquals(next, fake.cluster.updates.message(501, "pushed first").id)
                delay(1_000)
                val both = assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notifications.getNotification("u501", PushNotifier.CHAT_ID)))
                assertEquals(listOf("live from alice", "pushed first"), both.messages.map { it.text.toString() })

                connection.foreground(false)
                fake.cluster.updates.message(501, "in the background")
                delay(1_000)
                assertEquals(listOf("live from alice", "pushed first"), shownTexts(notifications))

                push.onDeleted()
                assertTrue(push.missedPushes)
                repos.telegram.disconnect()
                fake.cluster.updates.message(501, "never pushed", push = false)
                connection.foreground(true)
                withTimeout(20_000) { while ("never pushed" !in shownTexts(notifications)) delay(50) }
                assertFalse(push.missedPushes)
                push.unregister()
            }
            repos.telegram.close()
        }
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun aPhotoLoadedByAScreenIsUsedByAPushWithoutTheNetwork(): Unit = runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        val dir = Files.createTempDirectory("poc").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        FakeTelegram().use { fake ->
            val repos = TestRepositories(dir, fake)
            val settings = SettingsStore(context)
            val session = BackgroundSession(repos.telegram, ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test"))
            val onScreen = ChatOnScreen(Navigator(), AppLock(settings, FakeDeviceAuth()))
            val notifier = PushNotifier(context, settings, NotificationHistory(context), StoredNotificationPeers(NotificationAvatars(dir), repos.notifySettings), onScreen, WorkNotificationPreviews(context))
            val push = PushHandler(repos.push, { "fcm-token" }, notifier, repos.updates, keptConnected(repos.telegram, scope), session, context, repos.notifySettings, scope)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("+999 66 1 4242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "11111")).profile.id
                push.register()
                repos.updates.updateState.first { it != null }
                val alice = repos.dialogs.dialogs().dialogs.first { it.key == "u501" }
                assertNull(peerAvatarFile(dir, "u501")?.takeIf { it.exists() }, "nothing is kept before a screen asks")

                assertContentEquals(fake.avatarBytes, repos.media.avatar(assertNotNull(alice.avatarKey)))
                assertContentEquals(fake.avatarBytes, assertNotNull(peerAvatarFile(dir, "u501")).readBytes())

                repos.telegram.disconnect()
                while (fake.cluster.servers.sumOf { it.openConnections() } > 0) delay(50)
                val accepted = fake.cluster.connectionsAccepted()
                push.onMessage(fcm(fake, """{"loc_key":"MESSAGE_TEXT","loc_args":["Alice Example","with your photo"],"custom":{"from_id":"501","msg_id":"61"}}"""))
                val shown = assertNotNull(notifications.getNotification("u501", PushNotifier.CHAT_ID))
                val person = assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(shown)).messages.single().person
                val icon = (assertNotNull(person?.icon).toIcon(context).loadDrawable(context) as android.graphics.drawable.BitmapDrawable).bitmap
                assertEquals(96, icon.width, "her photo, not the 192 px initials")
                assertEquals(accepted, fake.cluster.connectionsAccepted(), "no connection was opened for the push")
                push.unregister()
            }
            repos.telegram.close()
        }
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun droppedPushesCostNothingNowAndTheCatchUpComesQuietlyLater(): Unit = runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        val dir = Files.createTempDirectory("poc").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        FakeTelegram().use { fake ->
            val repos = TestRepositories(dir, fake)
            val settings = SettingsStore(context)
            val session = BackgroundSession(repos.telegram, ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test"))
            val connection = keptConnected(repos.telegram, scope)
            val onScreen = ChatOnScreen(Navigator(), AppLock(settings, FakeDeviceAuth()))
            val notifier = PushNotifier(context, settings, NotificationHistory(context), StoredNotificationPeers(NotificationAvatars(dir), repos.notifySettings), onScreen, WorkNotificationPreviews(context))
            val push = PushHandler(repos.push, { "fcm-token" }, notifier, repos.updates, connection, session, context, repos.notifySettings, scope)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("+999 66 1 4242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "11111")).profile.id
                push.register()
                repos.updates.updateState.first { it != null }
                repos.dialogs.dialogs()

                connection.foreground(false)
                repos.telegram.disconnect()
                while (fake.cluster.servers.sumOf { it.openConnections() } > 0) delay(50)
                val accepted = fake.cluster.connectionsAccepted()
                repeat(50) { push.onDeleted() }
                assertTrue(push.missedPushes)
                assertEquals(1, repos.telegram.log.value.count { "fcm dropped" in it.text })
                delay(300)
                assertEquals(0, notifications.size())
                assertEquals(accepted, fake.cluster.connectionsAccepted())

                fake.cluster.updates.message(501, "missed one", push = false)
                fake.cluster.updates.message(501, "missed two", push = false)
                push.clock = { System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2) }
                connection.foreground(true)
                withTimeout(20_000) { while (shownTexts(notifications).size < 2) delay(50) }
                assertEquals(listOf("missed one", "missed two"), shownTexts(notifications))
                assertEquals(Notification.GROUP_ALERT_SUMMARY, notifications.getNotification("u501", PushNotifier.CHAT_ID).groupAlertBehavior)
                assertFalse(push.missedPushes)

                push.onDeleted()
                assertFalse(push.missedPushes)

                push.clock = System::currentTimeMillis
                fake.cluster.updates.message(501, "now")
                withTimeout(20_000) { while ("now" !in shownTexts(notifications)) delay(50) }
                assertEquals(Notification.GROUP_ALERT_ALL, notifications.getNotification("u501", PushNotifier.CHAT_ID).groupAlertBehavior)
                push.unregister()
            }
            repos.telegram.close()
        }
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun shownTexts(notifications: org.robolectric.shadows.ShadowNotificationManager): List<String> =
        notifications.getNotification("u501", PushNotifier.CHAT_ID)?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }?.messages.orEmpty().map { it.text.toString() }

    private fun fcm(fake: FakeTelegram, json: String): Map<String, String> = fake.cluster.push.fcmData("fcm-token", json)
}
