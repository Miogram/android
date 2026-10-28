package app.miogram

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.LocusId
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.SettingsStore
import app.miogram.data.telegram.PushAction
import app.miogram.data.telegram.PushMessage
import app.miogram.lock.AppLock
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import app.miogram.push.ChatOnScreen
import app.miogram.push.NotificationHistory
import app.miogram.push.NotificationPeers
import app.miogram.push.NotificationPreviews
import app.miogram.push.PushNotifier
import cn.ellerotta.core.model.ArticleBlock
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.ArticleText
import cn.ellerotta.core.model.AppPrivacy
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.PeerKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import kotlin.system.measureTimeMillis
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConversationNotificationsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val settings = SettingsStore(context)
    private val navigator = Navigator()
    private val onScreen = ChatOnScreen(navigator, AppLock(settings, FakeDeviceAuth()))
    private val peers = FakePeers()
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    private val previews = FakePreviews()

    private fun notifier() = PushNotifier(context, settings, NotificationHistory(context), peers, onScreen, previews)

    @Before
    fun grant() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun messagesOfOneChatStackInOneConversation(): Unit = runBlocking {
        notifier().handle(alice(41, "see you at 8"))
        notifier().handle(alice(42, "bring the book"))

        assertEquals(1, notifications.size(), "one notification, no summary for a single chat")
        val shown = chat("u501")
        val style = style(shown)
        assertEquals(listOf("see you at 8", "bring the book"), style.messages.map { it.text.toString() })
        assertEquals(listOf("Alice Liddell", "Alice Liddell"), style.messages.map { it.person?.name.toString() })
        assertEquals(listOf("u501", "u501"), style.messages.map { it.person?.key })
        assertFalse(style.isGroupConversation)
        assertEquals("Alice Liddell", shown.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNotNull(shown.getLargeIcon(), "the chat's photo or its placeholder")
        assertTrue("u501" in peers.asked, "the photo was looked up")
        assertEquals(2, shown.number)
        assertEquals(PushNotifier.GROUP, shown.group)
        assertEquals(PushNotifier.CHANNEL_PRIVATE, shown.channelId)

        assertEquals("u501", shown.shortcutId)
        val shortcut = context.getSystemService(ShortcutManager::class.java).dynamicShortcuts.single { it.id == "u501" }
        assertEquals(true, shortcut.javaClass.getMethod("isLongLived").invoke(shortcut))
        assertEquals(setOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION), shortcut.categories)
        assertEquals("Alice Liddell", shortcut.shortLabel.toString())
        assertEquals(LocusId("u501"), shortcut.locusId)

        val open = shadowOf(shown.contentIntent).savedIntent
        assertEquals(PushNotifier.OPEN_CHAT, open.component?.className)
        assertEquals("u501", open.getStringExtra(PushNotifier.EXTRA_DIALOG))
        assertEquals(41, open.getIntExtra(PushNotifier.EXTRA_MESSAGE, 0))

        notifier().handle(alice(43, "", media = MediaKind.Photo))
        notifier().handle(alice(43, "", media = MediaKind.Photo))
        assertEquals(listOf("see you at 8", "bring the book", "Photo"), style(chat("u501")).messages.map { it.text.toString() })

        (44..52).forEach { notifier().handle(alice(it, "line $it")) }
        val kept = style(chat("u501")).messages.map { it.text.toString() }
        assertEquals((45..52).map { "line $it" }, kept)
    }

    @Test
    fun twoChatsGetTwoNotificationsAndASummary(): Unit = runBlocking {
        val notifier = notifier()
        notifier.handle(alice(41, "see you at 8"))
        notifier.handle(group(5, "Bob", "u502", "the meeting moved"))

        assertEquals(3, notifications.size())
        val summary = assertNotNull(notifications.getNotification(PushNotifier.SUMMARY_TAG, PushNotifier.SUMMARY_ID))
        assertTrue(summary.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        assertEquals(PushNotifier.GROUP, summary.group)
        assertEquals(PushNotifier.GROUP, chat("u501").group)
        assertEquals(PushNotifier.GROUP, chat("c77").group)
        assertEquals(2, summary.number)
        val lines = summary.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)!!.map { it.toString() }
        assertEquals(setOf("Book club: the meeting moved", "Alice Liddell: see you at 8"), lines.toSet())
        assertEquals("2 new messages", summary.extras.getCharSequence(Notification.EXTRA_TITLE).toString())

        notifier.handle(read("c77", 5))
        assertEquals(1, notifications.size())
        assertNull(notifications.getNotification(PushNotifier.SUMMARY_TAG, PushNotifier.SUMMARY_ID))
    }

    @Test
    fun groupConversationsShowTheirSenders(): Unit = runBlocking {
        val notifier = notifier()
        notifier.handle(group(5, "Bob", "u502", "who is in"))
        notifier.handle(group(6, "Carol", "u503", "me"))

        val shown = chat("c77")
        val style = style(shown)
        assertTrue(style.isGroupConversation)
        assertEquals("Book club", style.conversationTitle.toString())
        assertEquals(listOf("Bob", "Carol"), style.messages.map { it.person?.name.toString() })
        assertEquals(listOf("u502", "u503"), style.messages.map { it.person?.key })
        assertEquals(PushNotifier.CHANNEL_GROUPS, shown.channelId)
        assertTrue(setOf("c77", "u502", "u503").all { it in peers.asked }, "the group's and the senders' photos were looked up")
    }

    @Test
    fun hiddenContentShowsOnlyNewMessageAndTheCount(): Unit = runBlocking {
        settings.setAppPrivacy(AppPrivacy(hideNotificationContent = true))
        val notifier = notifier()
        notifier.handle(group(5, "Bob", "u502", "the secret plan"))
        notifier.handle(group(6, "Bob", "u502", "more secrets"))
        notifier.handle(alice(41, "a private word"))

        val hidden = chat("c77")
        assertEquals("New message", hidden.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("2 new messages", hidden.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(hidden))
        assertNull(hidden.shortcutId)
        val summary = assertNotNull(notifications.getNotification(PushNotifier.SUMMARY_TAG, PushNotifier.SUMMARY_ID))
        assertEquals("New message", summary.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("3 new messages", summary.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        notifications.allNotifications.forEach { shown ->
            val everything = shown.extras.keySet().joinToString(" ") { shown.extras.get(it).toString() }
            assertFalse(listOf("Bob", "secret", "Book club", "Alice", "private").any { it in everything }, everything)
        }
        assertTrue(ShortcutManagerCompat.getDynamicShortcuts(context).isEmpty())
    }

    @Test
    fun readAndDeletedMessagesLeave(): Unit = runBlocking {
        val notifier = notifier()
        listOf(5, 6, 7).forEach { notifier.handle(group(it, "Bob", "u502", "message $it")) }

        notifier.handle(read("c77", 6))
        assertEquals(listOf("message 7"), style(chat("c77")).messages.map { it.text.toString() })
        assertEquals(Notification.GROUP_ALERT_SUMMARY, chat("c77").groupAlertBehavior, "an update does not alert again")

        notifier.handle(PushMessage(PushAction.Delete, "c77", null, "", null, "", null, null, listOf(7), null, false))
        assertNull(notifications.getNotification("c77", PushNotifier.CHAT_ID))
        assertNull(NotificationHistory(context).chat("c77"))

        notifier.handle(alice(41, "hi"))
        notifier.handle(read("u501", null))
        assertEquals(0, notifications.size())
        notifier.handle(alice(42, "hi again"))
        notifier.read("u501", 42)
        assertEquals(0, notifications.size())

        notifier.handle(alice(43, "are you there"))
        notifier.clearChat("u501")
        assertEquals(0, notifications.size())
        notifier.handle(alice(44, "hello?"))
        notifier.handle(group(8, "Bob", "u502", "ping"))
        notifier.clear()
        assertEquals(0, notifications.size())
        assertTrue(NotificationHistory(context).chats().isEmpty())
        assertTrue(ShortcutManagerCompat.getDynamicShortcuts(context).isEmpty())
    }

    @Test
    fun theChatOnScreenGetsNoNotification(): Unit = runBlocking {
        val notifier = notifier()
        onScreen.started(true)
        navigator.go(Route.Chats)
        navigator.go(Route.Chat("u501", "Alice Liddell"))

        notifier.handle(alice(41, "you are reading this"))
        notifier.handle(group(5, "Bob", "u502", "another chat"))
        assertNull(notifications.getNotification("u501", PushNotifier.CHAT_ID))
        assertNotNull(notifications.getNotification("c77", PushNotifier.CHAT_ID))
        assertNull(NotificationHistory(context).chat("u501"))

        onScreen.started(false)
        notifier.handle(alice(42, "now in the background"))
        assertNotNull(notifications.getNotification("u501", PushNotifier.CHAT_ID))
    }

    @Test
    fun aPushDroppedForAMissingPermissionIsShownWhenItComesAgain(): Unit = runBlocking {
        val notifier = notifier()
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.handle(alice(41, "nobody can see this yet"))
        assertEquals(0, notifications.size())

        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.handle(alice(41, "nobody can see this yet"))
        assertEquals(listOf("nobody can see this yet"), style(chat("u501")).messages.map { it.text.toString() })

        notifier.handle(alice(41, "nobody can see this yet"))
        assertEquals(1, style(chat("u501")).messages.size)
    }

    @Test
    fun aLatePushForAMessageSeenInTheOpenChatDoesNotNotify(): Unit = runBlocking {
        val notifier = notifier()
        onScreen.started(true)
        navigator.go(Route.Chats)
        navigator.go(Route.Chat("u501", "Alice Liddell"))
        notifier.handle(alice(41, "came while the chat was open"))
        assertNull(notifications.getNotification("u501", PushNotifier.CHAT_ID))

        onScreen.started(false)
        notifier.handle(alice(41, "came while the chat was open"))
        assertNull(notifications.getNotification("u501", PushNotifier.CHAT_ID))
        notifier.handle(alice(42, "a newer one"))
        assertEquals(listOf("a newer one"), style(chat("u501")).messages.map { it.text.toString() })
    }

    @Test
    fun aPushAtOrBelowTheKnownReadMaxIsDropped(): Unit = runBlocking {
        val notifier = notifier()
        notifier.handle(alice(40, "old"))
        notifier.read("u501", 45)
        assertEquals(0, notifications.size())
        notifier.handle(alice(44, "read already"))
        notifier.handle(alice(45, "read too"))
        assertEquals(0, notifications.size())
        notifier.handle(alice(46, "new"))
        assertEquals(listOf("new"), style(chat("u501")).messages.map { it.text.toString() })
        notifier().handle(alice(43, "very late"))
        assertEquals(listOf("new"), style(chat("u501")).messages.map { it.text.toString() })
    }

    @Test
    fun mutedPushesAreDroppedAndSilentOnesDoNotAlert(): Unit = runBlocking {
        val notifier = notifier()
        notifier.handle(alice(41, "loud"))
        assertEquals(Notification.GROUP_ALERT_ALL, chat("u501").groupAlertBehavior)
        peers.muted += "c77"
        notifier.handle(group(5, "Bob", "u502", "quiet"))
        assertNull(notifications.getNotification("c77", PushNotifier.CHAT_ID))
        notifier.handle(alice(42, "silent post").copy(silent = true))
        assertEquals(Notification.GROUP_ALERT_SUMMARY, chat("u501").groupAlertBehavior)
    }

    @Test
    fun aMessageFromThePushAndTheUpdatesShowsOnce(): Unit = runBlocking {
        val notifier = notifier()
        notifier.live(alice(41, "live first"))
        notifier.handle(alice(41, "live first"))
        notifier.handle(alice(42, "push first"))
        notifier.live(alice(42, "push first"))
        assertEquals(listOf("live first", "push first"), style(chat("u501")).messages.map { it.text.toString() })

        notifier.clearChat("u501")
        notifier().live(alice(42, "push first"))
        notifier().handle(alice(41, "live first"))
        assertNull(notifications.getNotification("u501", PushNotifier.CHAT_ID))

        val article = ArticleMessage(listOf(ArticleBlock.Paragraph(ArticleText.Plain("weekly notes")), ArticleBlock.Photo(null)))
        notifier.live(alice(43, "").copy(article = article))
        assertEquals("weekly notes  Photo", style(chat("u501")).messages.single().text.toString())
    }

    @Test
    fun mutedChatsNotifyOnlyMentionsThatAreNotSilent(): Unit = runBlocking {
        val notifier = notifier()
        peers.muted += "c77"
        notifier.live(group(5, "Bob", "u502", "chatter"))
        assertNull(notifications.getNotification("c77", PushNotifier.CHAT_ID), "a muted chat's messages stay quiet")

        notifier.live(group(6, "Bob", "u502", "@test look").copy(mention = true))
        assertEquals(listOf("@test look"), style(chat("c77")).messages.map { it.text.toString() })
        assertEquals(Notification.GROUP_ALERT_ALL, chat("c77").groupAlertBehavior)

        notifier.live(group(7, "Bob", "u502", "@test quietly").copy(mention = true, silent = true))
        assertEquals(Notification.GROUP_ALERT_SUMMARY, chat("c77").groupAlertBehavior)

        notifier.handle(group(8, "Bob", "u502", "@test again").copy(mention = true))
        assertEquals(Notification.GROUP_ALERT_ALL, chat("c77").groupAlertBehavior)
        assertEquals(listOf("@test look", "@test quietly", "@test again"), style(chat("c77")).messages.map { it.text.toString() })
    }

    @Test
    fun aStickerOrPhotoPostsItsTextAtOnceAndTheJobAddsTheImage(): Unit = runBlocking {
        val notifier = notifier()
        notifier.handle(alice(41, "", media = MediaKind.Sticker))
        notifier.handle(alice(42, "", media = MediaKind.Photo))
        notifier.handle(alice(43, "a text", media = null))
        notifier.handle(alice(44, "", media = MediaKind.File))

        assertEquals(listOf("Sticker", "Photo", "a text", "File"), style(chat("u501")).messages.map { it.text.toString() })
        assertTrue(style(chat("u501")).messages.all { it.dataUri == null })
        assertEquals(listOf(Triple("u501", 41, false), Triple("u501", 42, false)), previews.enqueued)
        assertTrue(notifier.wantsImage("u501", 41, false))

        val png = ByteArrayOutputStream().also { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        notifier.attachImage("u501", 41, png, "image/png")
        val shown = chat("u501")
        val messages = style(shown).messages
        assertEquals(listOf("image/png", null, null, null), messages.map { it.dataMimeType })
        assertEquals(listOf("Sticker", "Photo", "a text", "File"), messages.map { it.text.toString() })
        val uri = assertNotNull(messages.first().dataUri)
        assertEquals("${context.packageName}.files", uri.authority)
        assertContentEquals(png, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        assertEquals(Notification.GROUP_ALERT_SUMMARY, shown.groupAlertBehavior, "the picture arrives silently")
        assertFalse(notifier.wantsImage("u501", 41, false), "it has one")

        notifier().attachImage("u501", 41, png + png, "image/png")
        assertContentEquals(png, context.contentResolver.openInputStream(assertNotNull(style(chat("u501")).messages.first().dataUri))!!.use { it.readBytes() })

        notifier.read("u501", 41)
        assertNull(style(chat("u501")).messages.firstOrNull { it.text.toString() == "Sticker" })
        assertEquals(emptyList(), java.io.File(context.cacheDir, "notification_media").listFiles().orEmpty().toList())
    }

    @Test
    fun noPictureIsWantedForAMessageThatWasDismissedReadOrHidden(): Unit = runBlocking {
        val notifier = notifier()
        notifier.handle(alice(41, "", media = MediaKind.Photo))
        notifier.handle(group(5, "Bob", "u502", "").copy(media = MediaKind.Sticker))
        notifier.handle(group(6, "Bob", "u502", "").copy(media = MediaKind.Photo, dialogKey = "c78", title = "Other"))

        notifier.dismissed("u501")
        assertFalse(notifier.wantsImage("u501", 41, false), "dismissed")
        notifier.attachImage("u501", 41, byteArrayOf(1), "image/jpeg")
        assertNull(NotificationHistory(context).chat("u501"), "nothing to attach it to")

        notifier.read("c77", 5)
        assertFalse(notifier.wantsImage("c77", 5, false), "read")

        settings.setAppPrivacy(AppPrivacy(hideNotificationContent = true))
        assertFalse(notifier.wantsImage("c78", 6, false), "hidden")
        notifier.attachImage("c78", 6, byteArrayOf(1), "image/jpeg")
        assertEquals(emptyList(), java.io.File(context.cacheDir, "notification_media").listFiles().orEmpty().toList())
    }

    @Test
    fun hiddenContentEnqueuesNoPicture(): Unit = runBlocking {
        settings.setAppPrivacy(AppPrivacy(hideNotificationContent = true))
        val notifier = notifier()
        notifier.handle(alice(41, "", media = MediaKind.Photo))
        notifier.handle(alice(42, "", media = MediaKind.Sticker))
        assertEquals(emptyList(), previews.enqueued)
    }

    @Test
    fun aMutedChatGetsNoPictureAndAMentionDoes(): Unit = runBlocking {
        val notifier = notifier()
        notifier.handle(alice(41, "", media = MediaKind.Photo))
        peers.muted += "u501"
        assertFalse(notifier.wantsImage("u501", 41, false), "muted since the push")
        assertTrue(notifier.wantsImage("u501", 41, true), "a mention shows in a muted chat")
        previews.enqueued.clear()
        notifier.handle(alice(42, "", media = MediaKind.Photo))
        assertEquals(emptyList(), previews.enqueued, "a muted chat shows nothing")
    }

    @Test
    fun aPhotoNeverLoadedIsTheInitialsAndTheNotificationIsPostedOnce(): Unit = runBlocking {
        val started = System.nanoTime()
        notifier().handle(alice(41, "no photo yet"))
        val took = (System.nanoTime() - started) / 1_000_000
        assertTrue(took < 2_000, "nothing waits for a photo ($took ms)")
        assertNotEquals(8, personIcon(chat("u501")).width, "the initials placeholder, drawn in the app")
        delay(500)
        assertEquals(Notification.GROUP_ALERT_ALL, chat("u501").groupAlertBehavior)
        assertEquals(listOf("u501"), peers.asked.toList(), "the photo is looked up once")
    }

    @Test
    fun aPhotoFromThePeersIsTheIconWithoutWaiting(): Unit = runBlocking {
        peers.photo = ByteArrayOutputStream().also { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        notifier().handle(alice(41, "with photo"))
        assertEquals(8, personIcon(chat("u501")).width, "the photo, round, not the 192 px placeholder")
    }

    @Test
    fun aBurstOfPushesDoesNotQueueBehindSlowPhotoReads(): Unit = runBlocking {
        peers.photoDelay = 500
        val notifier = notifier()
        val took = measureTimeMillis {
            coroutineScope {
                (1..6).map { n -> async(Dispatchers.Default) { notifier.handle(chatPush(n)) } }.awaitAll()
            }
        }
        assertEquals(6, (1..6).count { notifications.getNotification("u60$it", PushNotifier.CHAT_ID) != null })
        assertTrue(took < 2_000, "six photos of half a second each were read together, not one after the other ($took ms)")
    }

    private fun chatPush(n: Int) = PushMessage(
        action = PushAction.Show, dialogKey = "u60$n", kind = PeerKind.User, title = "Chat $n", sender = null, text = "hi $n", media = null,
        messageId = n, messageIds = emptyList(), maxId = null, silent = false, senderKey = "u60$n",
    )

    private fun personIcon(notification: Notification): Bitmap =
        assertNotNull((assertNotNull(style(notification).messages.last().person?.icon).toIcon(context).loadDrawable(context) as? android.graphics.drawable.BitmapDrawable)?.bitmap)

    private fun chat(key: String) = assertNotNull(notifications.getNotification(key, PushNotifier.CHAT_ID), "notification of $key")

    private fun style(notification: Notification) = assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification))

    private fun alice(id: Int, text: String, media: MediaKind? = null) = PushMessage(
        action = PushAction.Show, dialogKey = "u501", kind = PeerKind.User, title = "Alice Liddell", sender = null, text = text, media = media,
        messageId = id, messageIds = emptyList(), maxId = null, silent = false, senderKey = "u501",
    )

    private fun group(id: Int, sender: String, senderKey: String, text: String) = PushMessage(
        action = PushAction.Show, dialogKey = "c77", kind = PeerKind.Group, title = "Book club", sender = sender, text = text, media = null,
        messageId = id, messageIds = emptyList(), maxId = null, silent = false, senderKey = senderKey,
    )

    private fun read(key: String, maxId: Int?) = PushMessage(PushAction.Read, key, null, "", null, "", null, null, emptyList(), maxId, false)

    private class FakePreviews : NotificationPreviews {
        val enqueued: MutableList<Triple<String, Int, Boolean>> = java.util.concurrent.CopyOnWriteArrayList()

        override fun enqueue(key: String, id: Int, mention: Boolean) {
            enqueued += Triple(key, id, mention)
        }

        override fun cancelAll() = enqueued.clear()
    }

    private class FakePeers : NotificationPeers {
        val asked: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
        val muted: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

        @Volatile var photo: ByteArray? = null

        @Volatile var photoDelay = 0L

        override suspend fun avatar(peerKey: String): ByteArray? {
            asked += peerKey
            delay(photoDelay)
            return photo
        }

        override suspend fun muted(dialogKey: String, kind: PeerKind?) = dialogKey in muted
    }
}
