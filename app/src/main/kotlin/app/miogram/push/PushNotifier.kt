package app.miogram.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.FileProvider
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.miogram.data.SettingsStore
import app.miogram.data.telegram.PushAction
import app.miogram.data.telegram.PREVIEW_KINDS
import app.miogram.data.telegram.PushMessage
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.preview
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.articleLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val history: NotificationHistory,
    private val peers: NotificationPeers,
    private val onScreen: ChatOnScreen,
    private val previews: NotificationPreviews,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val lock = Mutex()

    private val photos = LruCache<String, Bitmap>(PHOTOS)

    suspend fun handle(push: PushMessage) {
        val key = push.dialogKey ?: return
        when (push.action) {
            PushAction.Show -> showUnlessMuted(key, push)
            PushAction.Delete -> deleted(key, push.messageIds)
            PushAction.Read -> read(key, push.maxId)
            PushAction.Revoke, PushAction.DcUpdate, PushAction.Ignore -> Unit
        }
    }

    suspend fun live(push: PushMessage) {
        val key = push.dialogKey ?: return
        if (push.action == PushAction.Show) showUnlessMuted(key, push)
    }

    private suspend fun showUnlessMuted(key: String, push: PushMessage) {
        if (!push.mention && peers.muted(key, push.kind)) return
        show(key, push)
    }

    private suspend fun show(key: String, push: PushMessage) {
        warm(history.chat(key), key, push.senderKey)
        lock.withLock { showNow(key, push) }
    }

    private fun showNow(key: String, push: PushMessage) {
        val id = push.messageId ?: 0
        if (id != 0 && (id <= history.readMax(key) || history.seen(key, id))) return
        if (onScreen.shows(key)) {
            if (id != 0) history.markSeen(key, id)
            return
        }
        if (!allowed()) return
        val old = history.chat(key)
        val message = NotifiedMessage(
            id = id,
            text = textOf(push),
            date = System.currentTimeMillis(),
            sender = push.sender ?: push.title,
            senderKey = push.senderKey,
        )
        val chat = NotifiedChat(key, push.title, push.kind, (old?.messages.orEmpty() + message).takeLast(NotificationHistory.KEEP))
        history.put(chat)
        post(chat, alert = !push.silent && !push.quiet)
        if (id != 0) history.markSeen(key, id)
        refreshed()
        if (id != 0 && push.media in PREVIEW_KINDS && !settings.appPrivacy.value.hideNotificationContent) previews.enqueue(key, id, push.mention)
    }

    private fun textOf(push: PushMessage): String =
        push.article?.preview({ context.getString(articleLabel(it)) }) ?: push.media?.let { context.getString(mediaLabel(it)) } ?: push.text

    suspend fun read(key: String, maxId: Int?) {
        warm(history.chat(key))
        lock.withLock { readNow(key, maxId) }
    }

    private fun readNow(key: String, maxId: Int?) {
        if (maxId != null) history.markRead(key, maxId)
        val chat = history.chat(key)
        if (chat == null) {
            cancel(key)
            return
        }
        val first = if (maxId == null) -1 else chat.messages.indexOfFirst { !it.own && it.id > maxId }
        val left = if (maxId == null || first < 0) emptyList() else chat.messages.drop(first).filter { it.own || it.id > maxId }
        changed(chat, left)
    }

    suspend fun deleted(key: String?, ids: List<Int>) {
        chatsOf(key).forEach { warm(it) }
        lock.withLock { chatsOf(key).forEach { chat -> changed(chat, chat.messages.filter { it.own || it.id !in ids }) } }
    }

    private fun chatsOf(key: String?): List<NotifiedChat> =
        if (key == null) history.chats().filter { !it.key.startsWith("ch") } else listOfNotNull(history.chat(key))

    suspend fun clearChat(key: String) = lock.withLock {
        history.remove(key)
        cancel(key)
        refreshed()
    }

    suspend fun dismissed(key: String) = lock.withLock {
        history.remove(key)
        refreshed()
    }

    suspend fun replied(key: String, text: String) {
        warm(history.chat(key))
        lock.withLock { repliedNow(key, text) }
    }

    private fun repliedNow(key: String, text: String) {
        val chat = history.chat(key) ?: return
        val next = chat.copy(messages = (chat.messages + NotifiedMessage(0, text, System.currentTimeMillis(), own = true)).takeLast(NotificationHistory.KEEP))
        history.put(next)
        post(next, alert = false)
    }

    suspend fun repost(key: String) {
        warm(history.chat(key))
        lock.withLock { history.chat(key)?.let { post(it, alert = false) } ?: cancel(key) }
    }

    suspend fun forget(key: String) {
        clearChat(key)
        ShortcutManagerCompat.removeLongLivedShortcuts(context, listOf(key))
    }

    suspend fun clear() = lock.withLock {
        val keys = history.chats().map { it.key } + ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }
        history.clear()
        manager.cancelAll()
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        if (keys.isNotEmpty()) ShortcutManagerCompat.removeLongLivedShortcuts(context, keys.distinct())
        photos.evictAll()
        previews.cancelAll()
        sweep()
    }

    private fun changed(chat: NotifiedChat, left: List<NotifiedMessage>) {
        if (left.none { !it.own }) {
            history.remove(chat.key)
            cancel(chat.key)
        } else if (left != chat.messages) {
            val next = chat.copy(messages = left)
            history.put(next)
            post(next, alert = false)
        }
        refreshed()
    }

    private fun cancel(key: String) = manager.cancel(key, CHAT_ID)

    private fun refreshed() {
        sweep()
        summary()
    }

    private fun mediaDir() = File(context.cacheDir, MEDIA_DIR)

    private fun sweep() {
        val files = mediaDir().listFiles() ?: return
        val used = history.chats().flatMap { chat -> chat.messages.mapNotNull { it.image } }.toSet()
        files.filter { it.name !in used }.forEach { it.delete() }
    }

    suspend fun wantsImage(key: String, id: Int, mention: Boolean): Boolean {
        if (settings.appPrivacy.value.hideNotificationContent || onScreen.shows(key) || id <= history.readMax(key)) return false
        val chat = history.chat(key) ?: return false
        if (chat.messages.none { it.id == id && !it.own && it.image == null }) return false
        return mention || !peers.muted(key, chat.kind)
    }

    suspend fun attachImage(key: String, id: Int, bytes: ByteArray, mime: String) {
        warm(history.chat(key))
        lock.withLock {
            if (settings.appPrivacy.value.hideNotificationContent || onScreen.shows(key) || id <= history.readMax(key) || !allowed()) return
            val chat = history.chat(key) ?: return
            if (chat.messages.none { it.id == id && !it.own && it.image == null }) return
            val name = "${key}_$id.${extensionOf(mime)}"
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = mediaDir().apply { mkdirs() }
                    File(dir, name).writeBytes(bytes)
                }.isSuccess
            }
            if (!written) return
            val next = chat.copy(messages = chat.messages.map { if (it.id == id && !it.own) it.copy(image = name, imageMime = mime) else it })
            history.put(next)
            post(next, alert = false)
        }
    }

    private fun extensionOf(mime: String) = when (mime) {
        "image/webp" -> "webp"
        "image/png" -> "png"
        else -> "jpg"
    }

    private fun imageUri(message: NotifiedMessage): Uri? {
        val file = message.image?.let { File(mediaDir(), it) }?.takeIf { it.exists() } ?: return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.grantUriPermission(SYSTEM_UI, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return uri
    }

    private fun allowed() = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(chat: NotifiedChat, alert: Boolean) {
        val hidden = settings.appPrivacy.value.hideNotificationContent
        val last = chat.messages.last()
        val builder = NotificationCompat.Builder(context, channel(chat.kind))
            .setSmallIcon(R.drawable.ic_chat)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP)
            .setWhen(last.date)
            .setShowWhen(true)
            .setNumber(chat.unread)
            .setContentIntent(openIntent(chat.key, chat.firstId))
            .setDeleteIntent(broadcast(NotificationActionReceiver.ACTION_DISMISS, chat.key, PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .setSilent(!alert)
            .addAction(replyAction(chat.key))
            .addAction(readAction(chat))
        if (hidden) {
            builder.setContentTitle(context.getString(R.string.notifications_new_message))
                .setContentText(context.resources.getQuantityString(R.plurals.notifications_messages, chat.unread, chat.unread))
        } else {
            val photos = photosOf(photoKeys(chat))
            val group = chat.kind == PeerKind.Group
            val style = NotificationCompat.MessagingStyle(me()).setGroupConversation(group)
            if (group) style.setConversationTitle(chat.title)
            chat.messages.forEach { message ->
                val line = NotificationCompat.MessagingStyle.Message(message.text, message.date, if (message.own) null else person(message, photos))
                imageUri(message)?.let { line.setData(message.imageMime, it) }
                style.addMessage(line)
            }
            val chatPhoto = photos[chat.key] ?: placeholderPhoto(chat.title, chat.key)
            builder.setStyle(style)
                .setContentTitle(chat.title)
                .setContentText(last.text)
                .setLargeIcon(chatPhoto)
                .setShortcutId(shortcut(chat, chatPhoto, photos))
                .setLocusId(LocusIdCompat(chat.key))
        }
        manager.notify(chat.key, CHAT_ID, builder.build())
    }

    private fun summary() {
        val chats = history.chats()
        if (chats.size < 2) {
            manager.cancel(SUMMARY_TAG, SUMMARY_ID)
            return
        }
        val count = chats.sumOf { it.unread }
        val messages = context.resources.getQuantityString(R.plurals.notifications_messages, count, count)
        val from = context.resources.getQuantityString(R.plurals.notifications_chats, chats.size, chats.size)
        val builder = NotificationCompat.Builder(context, channel(PeerKind.User))
            .setSmallIcon(R.drawable.ic_chat)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setNumber(count)
            .setContentIntent(openIntent(null, null))
            .setAutoCancel(true)
            .setSilent(true)
        if (settings.appPrivacy.value.hideNotificationContent) {
            builder.setContentTitle(context.getString(R.string.notifications_new_message)).setContentText(messages)
        } else {
            val style = NotificationCompat.InboxStyle().setBigContentTitle(messages).setSummaryText(from)
            chats.forEach { chat -> style.addLine(context.getString(R.string.notifications_group_message, chat.title, chat.messages.last().text)) }
            builder.setContentTitle(messages).setContentText(chats.joinToString(", ") { it.title }).setStyle(style)
        }
        manager.notify(SUMMARY_TAG, SUMMARY_ID, builder.build())
    }

    private fun me() = Person.Builder().setKey(SELF).setName(context.getString(R.string.notifications_you)).build()

    private fun person(message: NotifiedMessage, photos: Map<String, Bitmap>): Person {
        val name = message.sender.orEmpty()
        val key = message.senderKey
        val photo = key?.let { photos[it] } ?: placeholderPhoto(name, key ?: name)
        return Person.Builder().setKey(key).setName(name).setIcon(IconCompat.createWithBitmap(photo)).build()
    }

    private fun photoKeys(chat: NotifiedChat): List<String> = (listOf(chat.key) + chat.messages.mapNotNull { it.senderKey }).distinct()

    private suspend fun warm(chat: NotifiedChat?, vararg extra: String?) {
        if (settings.appPrivacy.value.hideNotificationContent) return
        ((chat?.let(::photoKeys).orEmpty()) + extra.filterNotNull()).distinct().filter { photos[it] == null }.forEach { loadPhoto(it) }
    }

    private fun photosOf(keys: List<String>): Map<String, Bitmap> = keys.mapNotNull { key -> photos[key]?.let { key to it } }.toMap()

    private suspend fun loadPhoto(key: String) {
        try {
            peers.avatar(key)?.let { roundPhoto(it) }?.let { photos.put(key, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private fun shortcut(chat: NotifiedChat, photo: Bitmap, photos: Map<String, Bitmap>): String {
        val builder = ShortcutInfoCompat.Builder(context, chat.key)
            .setShortLabel(chat.title.ifEmpty { context.getString(R.string.notifications_new_message) })
            .setLongLived(true)
            .setIsConversation()
            .setLocusId(LocusIdCompat(chat.key))
            .setCategories(setOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
            .setIcon(IconCompat.createWithBitmap(photo))
            .setIntent(openIntentOf(chat.key, null).setAction(Intent.ACTION_VIEW))
        if (chat.kind == PeerKind.User || chat.kind == PeerKind.Bot) {
            builder.setPerson(Person.Builder().setKey(chat.key).setName(chat.title).setIcon(IconCompat.createWithBitmap(photos[chat.key] ?: photo)).build())
        }
        ShortcutManagerCompat.pushDynamicShortcut(context, builder.build())
        return chat.key
    }

    private fun replyAction(key: String): NotificationCompat.Action {
        val label = context.getString(R.string.notifications_reply)
        val input = RemoteInput.Builder(NotificationActionReceiver.EXTRA_REPLY).setLabel(label).build()
        val pending = broadcast(NotificationActionReceiver.ACTION_REPLY, key, PendingIntent.FLAG_MUTABLE)
        return NotificationCompat.Action.Builder(R.drawable.ic_reply, label, pending)
            .addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    private fun readAction(chat: NotifiedChat): NotificationCompat.Action {
        val intent = actionIntent(NotificationActionReceiver.ACTION_READ, chat.key)
        chat.newestId?.let { intent.putExtra(NotificationActionReceiver.EXTRA_MAX_ID, it) }
        val pending = PendingIntent.getBroadcast(context, requestCode(NotificationActionReceiver.ACTION_READ, chat.key), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Action.Builder(R.drawable.ic_mark_chat_read, context.getString(R.string.notifications_mark_read), pending)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    private fun actionIntent(action: String, key: String) = Intent(context, NotificationActionReceiver::class.java)
        .setAction(action)
        .putExtra(EXTRA_DIALOG, key)

    private fun broadcast(action: String, key: String, mutability: Int): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode(action, key), actionIntent(action, key), PendingIntent.FLAG_UPDATE_CURRENT or mutability)

    private fun requestCode(action: String, key: String) = "$action/$key".hashCode()

    private fun openIntent(key: String?, messageId: Int?): PendingIntent =
        PendingIntent.getActivity(context, key.hashCode(), openIntentOf(key, messageId), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun openIntentOf(key: String?, messageId: Int?): Intent = Intent().setComponent(ComponentName(context, OPEN_CHAT)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (key != null) putExtra(EXTRA_DIALOG, key)
        if (messageId != null) putExtra(EXTRA_MESSAGE, messageId)
    }

    private fun channel(kind: PeerKind?): String {
        val (id, name, importance) = when (kind) {
            PeerKind.Channel -> Triple(CHANNEL_CHANNELS, R.string.notifications_channel_channels, NotificationManager.IMPORTANCE_DEFAULT)
            PeerKind.Group -> Triple(CHANNEL_GROUPS, R.string.notifications_channel_groups, NotificationManager.IMPORTANCE_HIGH)
            else -> Triple(CHANNEL_PRIVATE, R.string.notifications_channel_private, NotificationManager.IMPORTANCE_HIGH)
        }
        manager.createNotificationChannel(NotificationChannel(id, context.getString(name), importance))
        return id
    }

    private fun mediaLabel(kind: MediaKind): Int = when (kind) {
        MediaKind.Photo -> R.string.media_photo
        MediaKind.Video -> R.string.media_video
        MediaKind.Round -> R.string.media_round
        MediaKind.Voice -> R.string.media_voice
        MediaKind.Music -> R.string.media_music
        MediaKind.Gif -> R.string.media_gif
        MediaKind.Sticker -> R.string.media_sticker
        MediaKind.File -> R.string.media_file
        MediaKind.Location -> R.string.media_location
        MediaKind.Contact -> R.string.media_contact
        MediaKind.Poll -> R.string.media_poll
        MediaKind.Link -> R.string.media_link
        MediaKind.Other -> R.string.media_other
    }

    companion object {
        const val EXTRA_DIALOG = "app.miogram.dialog"

        const val OPEN_CHAT = "app.miogram.OpenChat"

        const val EXTRA_MESSAGE = "app.miogram.message"

        const val CHANNEL_PRIVATE = "messages_private"
        const val CHANNEL_GROUPS = "messages_groups"
        const val CHANNEL_CHANNELS = "messages_channels"

        const val CHAT_ID = 1

        const val SUMMARY_TAG = "summary"
        const val SUMMARY_ID = 0

        const val GROUP = "app.miogram.messages"

        private const val SELF = "self"
        private const val SYSTEM_UI = "com.android.systemui"
        private const val MEDIA_DIR = "notification_media"
        private const val PHOTOS = 64
    }
}
