package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.messages.SendMessage
import cn.ellerotta.ktproto.api.functions.stories.CanSendStory
import cn.ellerotta.ktproto.api.functions.stories.DeleteStories
import cn.ellerotta.ktproto.api.functions.stories.EditStory
import cn.ellerotta.ktproto.api.functions.stories.GetAllStories
import cn.ellerotta.ktproto.api.functions.stories.GetPeerStories
import cn.ellerotta.ktproto.api.functions.stories.GetStoriesByID
import cn.ellerotta.ktproto.api.functions.stories.GetStoryViewsList
import cn.ellerotta.ktproto.api.functions.stories.IncrementStoryViews
import cn.ellerotta.ktproto.api.functions.stories.ReadStories
import cn.ellerotta.ktproto.api.functions.stories.SendReaction
import cn.ellerotta.ktproto.api.functions.stories.SendStory
import cn.ellerotta.ktproto.api.functions.stories.TogglePeerStoriesHidden
import cn.ellerotta.ktproto.api.functions.stories.TogglePinned
import cn.ellerotta.ktproto.api.functions.upload.GetFile
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.DocumentAttributeVideo
import cn.ellerotta.ktproto.api.types.InputDocumentFileLocation
import cn.ellerotta.ktproto.api.types.InputFile
import cn.ellerotta.ktproto.api.types.InputFileBig
import cn.ellerotta.ktproto.api.types.InputMediaUploadedDocument
import cn.ellerotta.ktproto.api.types.InputMediaUploadedPhoto
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.InputPhotoFileLocation
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowCloseFriends
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowContacts
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.api.types.MessageMediaPhoto
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.Photo
import cn.ellerotta.ktproto.api.types.PhotoSize
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowAll
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowCloseFriends
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowContacts
import cn.ellerotta.ktproto.api.types.ReactionCustomEmoji
import cn.ellerotta.ktproto.api.types.ReactionEmoji
import cn.ellerotta.ktproto.api.types.ReactionEmpty
import cn.ellerotta.ktproto.api.types.StoriesStealthMode
import cn.ellerotta.ktproto.api.types.StoryItem
import cn.ellerotta.ktproto.api.types.StoryItemDeleted
import cn.ellerotta.ktproto.api.types.StoryItemSkipped
import cn.ellerotta.ktproto.api.types.StoryView
import cn.ellerotta.ktproto.api.types.StoryViews
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.TypeInputPrivacyRule
import cn.ellerotta.ktproto.api.types.TypeMessageMedia
import cn.ellerotta.ktproto.api.types.TypePrivacyRule
import cn.ellerotta.ktproto.api.types.TypeReaction
import cn.ellerotta.ktproto.api.types.TypeStoryItem
import cn.ellerotta.ktproto.api.types.TypeUpdate
import cn.ellerotta.ktproto.api.types.UpdateReadStories
import cn.ellerotta.ktproto.api.types.UpdateSentStoryReaction
import cn.ellerotta.ktproto.api.types.UpdateStory
import cn.ellerotta.ktproto.api.types.UpdateStoryID
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.storage.FilePartial
import cn.ellerotta.ktproto.api.types.stories.AllStories
import cn.ellerotta.ktproto.api.types.stories.AllStoriesNotModified
import cn.ellerotta.ktproto.api.types.stories.CanSendStoryCount
import cn.ellerotta.ktproto.api.types.stories.Stories
import cn.ellerotta.ktproto.api.types.stories.StoryViewsList
import cn.ellerotta.ktproto.api.types.upload.File
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import cn.ellerotta.ktproto.tl.TlWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import cn.ellerotta.ktproto.api.types.PeerStories as TlPeerStories
import cn.ellerotta.ktproto.api.types.stories.PeerStories as StoriesPeerStories

class FakeStories(private val fake: FakeTelegram) {
    private data class Story(
        val id: Int,
        val date: Int,
        val fileId: Long,
        val video: DocumentAttributeVideo? = null,
        val closeFriends: Boolean = false,
        val out: Boolean = false,
        val caption: String = "",
        val views: StoryViews? = null,
        val reaction: TypeReaction? = null,
        val pinned: Boolean = false,
        val privacy: List<TypePrivacyRule>? = null,
    )

    @Volatile var reference: ByteArray = byteArrayOf(1)

    @Volatile var version = 1

    @Volatile var canSendError: String? = null

    val photoBytes = ByteArray(3_000) { (it * 11).toByte() }
    val videoBytes = ByteArray(5_000) { (it * 17).toByte() }

    private val lists = ConcurrentHashMap<Long, List<Story>>()
    private val maxRead = ConcurrentHashMap<Long, Int>()
    private val hiddenIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    private val files = ConcurrentHashMap<Long, ByteArray>()
    private val nextStory = AtomicInteger(10)

    val allCalls: MutableList<GetAllStories> = CopyOnWriteArrayList()
    val peerCalls: MutableList<Long> = CopyOnWriteArrayList()
    val byIdCalls: MutableList<List<Int>> = CopyOnWriteArrayList()

    val reads: MutableList<Pair<Long, Int>> = CopyOnWriteArrayList()
    val views: MutableList<Pair<Long, List<Int>>> = CopyOnWriteArrayList()
    val toggles: MutableList<Pair<Long, Boolean>> = CopyOnWriteArrayList()
    val reactions: MutableList<SendReaction> = CopyOnWriteArrayList()
    val viewerCalls: MutableList<GetStoryViewsList> = CopyOnWriteArrayList()
    val sent: MutableList<SendStory> = CopyOnWriteArrayList()
    val edits: MutableList<EditStory> = CopyOnWriteArrayList()
    val deletes: MutableList<List<Int>> = CopyOnWriteArrayList()
    val pins: MutableList<TogglePinned> = CopyOnWriteArrayList()

    val replies: MutableList<SendMessage> = CopyOnWriteArrayList()

    val downloads: MutableList<Long> = CopyOnWriteArrayList()

    @Volatile
    var holdFiles: CompletableDeferred<Unit>? = null

    init {
        lists[ALICE] = listOf(Story(1, NOW - 600, ALICE_READ_PHOTO), Story(2, NOW - 300, ALICE_PHOTO, closeFriends = true))
        maxRead[ALICE] = 1
        lists[CAROL] = listOf(Story(1, NOW - 200, CAROL_VIDEO, video = DocumentAttributeVideo(supportsStreaming = true, duration = 5.0, w = 720, h = 1280)))
        hiddenIds += CAROL
        val seen = StoryViews(hasViewers = true, viewsCount = 2, reactionsCount = 1, recentViewers = listOf(ALICE, BOB))
        lists[SELF] = listOf(Story(1, NOW - 100, OWN_PHOTO, out = true, caption = "own story", views = seen, privacy = listOf(PrivacyValueAllowContacts)))
        maxRead[SELF] = 1
    }

    private fun me() = User(self = true, id = fake.selfId, accessHash = 1, firstName = "Test", lastName = "User")

    private fun carol() = User(id = CAROL, accessHash = 11, firstName = "Carol", lastName = "Writer", username = "carol_writer", phone = "15550001111", storiesHidden = CAROL in hiddenIds)

    private fun user(key: Long): User = when (key) {
        SELF -> me()
        ALICE -> fake.alice
        CAROL -> carol()
        else -> fake.bob
    }

    private fun idOf(key: Long) = if (key == SELF) fake.selfId else key

    private fun keyOf(peer: TypeInputPeer): Long = when (peer) {
        InputPeerSelf -> SELF
        is InputPeerUser -> if (peer.userId == fake.selfId) SELF else peer.userId
        else -> -1
    }

    private fun state(hidden: Boolean, page: Int) = "${if (hidden) "hidden" else "main"}-$version-$page"

    private fun mediaOf(story: Story): TypeMessageMedia {
        val bytes = bytesOf(story.fileId) ?: ByteArray(0)
        val video = story.video ?: return MessageMediaPhoto(
            photo = Photo(id = story.fileId, accessHash = story.fileId + 1, fileReference = reference, date = story.date, sizes = listOf(PhotoSize("x", 720, 1280, bytes.size)), dcId = 2),
        )
        val document = Document(
            id = story.fileId,
            accessHash = story.fileId + 1,
            fileReference = reference,
            date = story.date,
            mimeType = "video/mp4",
            size = bytes.size.toLong(),
            dcId = 2,
            attributes = listOf(video),
        )
        return MessageMediaDocument(video = true, document = document)
    }

    private fun tl(story: Story, skipped: Boolean = false): TypeStoryItem = if (skipped) {
        StoryItemSkipped(closeFriends = story.closeFriends, id = story.id, date = story.date, expireDate = story.date + DAY)
    } else {
        StoryItem(
            pinned = story.pinned,
            closeFriends = story.closeFriends,
            out = story.out,
            id = story.id,
            date = story.date,
            expireDate = story.date + DAY,
            caption = story.caption,
            media = mediaOf(story),
            privacy = story.privacy,
            views = story.views,
            sentReaction = story.reaction,
        )
    }

    private fun peerStories(key: Long, inList: Boolean) = TlPeerStories(
        peer = PeerUser(idOf(key)),
        maxReadId = maxRead[key],
        stories = lists[key].orEmpty().map { tl(it, skipped = inList && key == ALICE && it.id == 1) },
    )

    private val unlisted = ConcurrentHashMap.newKeySet<Long>()

    private fun listed(hidden: Boolean): List<Long> =
        lists.filterValues { it.isNotEmpty() }.keys.filter { it !in unlisted && (it in hiddenIds) == hidden }.sortedBy { if (it == SELF) -1 else it }

    fun addUnlisted(userId: Long, ids: List<Int>) {
        unlisted += userId
        change(userId) { ids.map { id -> Story(id, NOW - 50 + id, PUSHED_PHOTO) } }
    }

    private fun page(keys: List<Long>, count: Int, hasMore: Boolean, state: String) = AllStories(
        hasMore = hasMore,
        count = count,
        state = state,
        peerStories = keys.map { peerStories(it, inList = true) },
        chats = emptyList(),
        users = keys.map(::user),
        stealthMode = StoriesStealthMode(),
    )

    private fun change(key: Long, change: (List<Story>) -> List<Story>) {
        lists[key] = change(lists[key].orEmpty())
        version++
    }

    private fun bytesOf(id: Long): ByteArray? = files[id] ?: when (id) {
        ALICE_READ_PHOTO, ALICE_PHOTO, OWN_PHOTO, PUSHED_PHOTO -> photoBytes
        CAROL_VIDEO -> videoBytes
        else -> null
    }

    suspend fun file(call: GetFile): FakeAnswer? {
        val (id, fileReference) = when (val location = call.location) {
            is InputPhotoFileLocation -> location.id to location.fileReference
            is InputDocumentFileLocation -> location.id to location.fileReference
            else -> return null
        }
        val bytes = bytesOf(id) ?: return null
        holdFiles?.await()
        if (!fileReference.contentEquals(reference)) return FakeAnswer.Error(400, "FILE_REFERENCE_EXPIRED")
        if (call.offset == 0L) downloads += id
        val start = minOf(call.offset, bytes.size.toLong()).toInt()
        return FakeAnswer.Result(File(FilePartial, 0, bytes.copyOfRange(start, minOf(start + call.limit, bytes.size))))
    }

    suspend fun pushStory(userId: Long, id: Int, closeFriends: Boolean = false) {
        val story = Story(id, NOW, PUSHED_PHOTO, closeFriends = closeFriends)
        change(userId) { stories -> stories.filter { it.id != id } + story }
        push(UpdateStory(PeerUser(userId), tl(story)), userId)
    }

    suspend fun pushDeleted(userId: Long, id: Int) {
        change(userId) { stories -> stories.filter { it.id != id } }
        push(UpdateStory(PeerUser(userId), StoryItemDeleted(id)), userId)
    }

    suspend fun pushRead(userId: Long, maxId: Int) {
        maxRead[userId] = maxId
        push(UpdateReadStories(PeerUser(userId), maxId), userId)
    }

    private suspend fun push(update: TypeUpdate, userId: Long) {
        fake.cluster.updates.push(Updates(listOf(update), listOf(user(userId)), emptyList(), NOW, 0))
    }

    fun register(server: FakeTelegramServer) {
        server.rpc.on(GetAllStories.CONSTRUCTOR_ID) { request ->
            val call = GetAllStories.decode(request)
            allCalls += call
            val keys = listed(call.hidden)
            val last = state(call.hidden, 2)
            val answer = when {
                call.next && call.state == state(call.hidden, 1) -> page(keys.drop(1), keys.size, hasMore = false, state = last)
                !call.next && call.state == last -> return@on FakeAnswer.Result(AllStoriesNotModified(0, last, StoriesStealthMode()))
                keys.size > 1 -> page(keys.take(1), keys.size, hasMore = true, state = state(call.hidden, 1))
                else -> page(keys, keys.size, hasMore = false, state = last)
            }
            FakeAnswer.Result(answer)
        }
        server.rpc.on(GetPeerStories.CONSTRUCTOR_ID) { request ->
            val key = keyOf(GetPeerStories.decode(request).peer)
            peerCalls += idOf(key)
            FakeAnswer.Result(StoriesPeerStories(peerStories(key, inList = false), emptyList(), listOf(user(key))))
        }
        server.rpc.on(GetStoriesByID.CONSTRUCTOR_ID) { request ->
            val call = GetStoriesByID.decode(request)
            val key = keyOf(call.peer)
            byIdCalls += call.id
            val stories = lists[key].orEmpty()
            val found = call.id.map { id -> stories.firstOrNull { it.id == id }?.let { tl(it) } ?: StoryItemDeleted(id) }
            FakeAnswer.Result(Stories(count = found.size, stories = found, chats = emptyList(), users = listOf(user(key))))
        }
        server.rpc.on(ReadStories.CONSTRUCTOR_ID) { request ->
            val call = ReadStories.decode(request)
            val key = keyOf(call.peer)
            reads += idOf(key) to call.maxId
            val before = maxRead[key] ?: 0
            maxRead[key] = maxOf(before, call.maxId)
            val w = TlWriter()
            w.writeVector(lists[key].orEmpty().map { it.id }.filter { it in (before + 1)..call.maxId }) { writeInt(it) }
            FakeAnswer.Raw(w.toByteArray())
        }
        server.rpc.on(IncrementStoryViews.CONSTRUCTOR_ID) { request ->
            val call = IncrementStoryViews.decode(request)
            views += idOf(keyOf(call.peer)) to call.id
            FakeTelegram.BOOL_TRUE
        }
        server.rpc.on(TogglePeerStoriesHidden.CONSTRUCTOR_ID) { request ->
            val call = TogglePeerStoriesHidden.decode(request)
            val key = keyOf(call.peer)
            toggles += key to call.hidden
            if (call.hidden) hiddenIds += key else hiddenIds -= key
            version++
            FakeTelegram.BOOL_TRUE
        }
        server.rpc.on(SendReaction.CONSTRUCTOR_ID) { request ->
            val call = SendReaction.decode(request)
            val key = keyOf(call.peer)
            reactions += call
            val chosen = call.reaction.takeIf { it != ReactionEmpty }
            lists[key] = lists[key].orEmpty().map { if (it.id == call.storyId) it.copy(reaction = chosen) else it }
            FakeAnswer.Result(Updates(listOf(UpdateSentStoryReaction(PeerUser(idOf(key)), call.storyId, call.reaction)), listOf(user(key)), emptyList(), NOW, 0))
        }
        server.rpc.on(GetStoryViewsList.CONSTRUCTOR_ID) { request ->
            val call = GetStoryViewsList.decode(request)
            viewerCalls += call
            val (seen, next) = if (call.offset.isEmpty()) {
                StoryView(userId = ALICE, date = NOW - 60, reaction = ReactionEmoji(FakeTelegram.HEART)) to "1"
            } else {
                StoryView(userId = BOB, date = NOW - 120, reaction = ReactionCustomEmoji(FakeTelegram.EMOJI_STAR)) to null
            }
            FakeAnswer.Result(
                StoryViewsList(count = 2, viewsCount = 2, forwardsCount = 0, reactionsCount = 2, views = listOf(seen), chats = emptyList(), users = listOf(fake.alice, fake.bob), nextOffset = next),
            )
        }
        server.rpc.on(CanSendStory.CONSTRUCTOR_ID) {
            canSendError?.let { FakeAnswer.Error(400, it) } ?: FakeAnswer.Result(CanSendStoryCount(3))
        }
        server.rpc.on(SendStory.CONSTRUCTOR_ID) { request ->
            val call = SendStory.decode(request)
            sent += call
            val (file, video) = when (val media = call.media) {
                is InputMediaUploadedPhoto -> media.file to null
                is InputMediaUploadedDocument -> media.file to media.attributes.filterIsInstance<DocumentAttributeVideo>().firstOrNull()
                else -> return@on FakeAnswer.Error(400, "MEDIA_INVALID")
            }
            val (fileId, parts) = when (file) {
                is InputFile -> file.id to file.parts
                is InputFileBig -> file.id to file.parts
                else -> return@on FakeAnswer.Error(400, "MEDIA_INVALID")
            }
            val bytes = fake.cluster.uploadedFile(fileId, parts) ?: return@on FakeAnswer.Error(400, "FILE_PARTS_INVALID")
            val id = nextStory.getAndIncrement()
            files[POSTED_ID + id] = bytes
            val story = Story(id, NOW, POSTED_ID + id, video = video, out = true, caption = call.caption.orEmpty())
            change(SELF) { it + story }
            FakeAnswer.Result(Updates(listOf(UpdateStoryID(id, call.randomId), UpdateStory(PeerUser(fake.selfId), tl(story))), listOf(me()), emptyList(), NOW, 0))
        }
        server.rpc.on(EditStory.CONSTRUCTOR_ID) { request ->
            val call = EditStory.decode(request)
            edits += call
            val old = lists[SELF].orEmpty().firstOrNull { it.id == call.id } ?: return@on FakeAnswer.Error(400, "STORY_ID_INVALID")
            var story = old.copy(caption = call.caption ?: old.caption)
            call.privacyRules?.let { rules -> story = story.copy(privacy = rules.map(::privacyOf), closeFriends = InputPrivacyValueAllowCloseFriends in rules) }
            when (val media = call.media) {
                null -> Unit
                is InputMediaUploadedPhoto, is InputMediaUploadedDocument -> {
                    val file = if (media is InputMediaUploadedPhoto) media.file else (media as InputMediaUploadedDocument).file
                    val (fileId, parts) = when (file) {
                        is InputFile -> file.id to file.parts
                        is InputFileBig -> file.id to file.parts
                        else -> return@on FakeAnswer.Error(400, "MEDIA_INVALID")
                    }
                    val bytes = fake.cluster.uploadedFile(fileId, parts) ?: return@on FakeAnswer.Error(400, "FILE_PARTS_INVALID")
                    val newFile = POSTED_ID + nextStory.getAndIncrement()
                    files[newFile] = bytes
                    story = story.copy(fileId = newFile, video = (media as? InputMediaUploadedDocument)?.attributes?.filterIsInstance<DocumentAttributeVideo>()?.firstOrNull())
                }
                else -> return@on FakeAnswer.Error(400, "MEDIA_INVALID")
            }
            val edited = story
            change(SELF) { stories -> stories.map { if (it.id == call.id) edited else it } }
            FakeAnswer.Result(Updates(listOf(UpdateStory(PeerUser(fake.selfId), tl(edited))), listOf(me()), emptyList(), NOW, 0))
        }
        server.rpc.on(DeleteStories.CONSTRUCTOR_ID) { request ->
            val call = DeleteStories.decode(request)
            deletes += call.id
            val deleted = lists[SELF].orEmpty().map { it.id }.filter { it in call.id }
            change(SELF) { stories -> stories.filter { it.id !in call.id } }
            val w = TlWriter()
            w.writeVector(deleted) { writeInt(it) }
            FakeAnswer.Raw(w.toByteArray())
        }
        server.rpc.on(TogglePinned.CONSTRUCTOR_ID) { request ->
            val call = TogglePinned.decode(request)
            pins += call
            val changed = lists[SELF].orEmpty().map { it.id }.filter { it in call.id }
            change(SELF) { stories -> stories.map { if (it.id in call.id) it.copy(pinned = call.pinned) else it } }
            val w = TlWriter()
            w.writeVector(changed) { writeInt(it) }
            FakeAnswer.Raw(w.toByteArray())
        }
    }

    private fun privacyOf(rule: TypeInputPrivacyRule): TypePrivacyRule = when (rule) {
        InputPrivacyValueAllowCloseFriends -> PrivacyValueAllowCloseFriends
        InputPrivacyValueAllowContacts -> PrivacyValueAllowContacts
        else -> PrivacyValueAllowAll
    }

    companion object {
        const val ALICE = 501L
        const val BOB = 502L
        const val CAROL = 503L

        const val SELF = 0L
        const val DAY = 86_400

        const val ALICE_READ_PHOTO = 6_101L
        const val ALICE_PHOTO = 6_102L
        const val OWN_PHOTO = 6_103L
        const val PUSHED_PHOTO = 6_104L
        const val CAROL_VIDEO = 6_201L

        const val POSTED_ID = 6_300L
    }
}
