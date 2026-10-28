package app.miogram

import app.miogram.FakeTelegram.Companion.BOOL_TRUE
import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.account.CheckUsername
import cn.ellerotta.ktproto.api.functions.account.ReorderUsernames
import cn.ellerotta.ktproto.api.functions.account.ToggleUsername
import cn.ellerotta.ktproto.api.functions.account.UpdateBirthday
import cn.ellerotta.ktproto.api.functions.account.UpdatePersonalChannel
import cn.ellerotta.ktproto.api.functions.account.UpdateProfile
import cn.ellerotta.ktproto.api.functions.account.UpdateUsername
import cn.ellerotta.ktproto.api.functions.channels.GetAdminedPublicChannels
import cn.ellerotta.ktproto.api.functions.photos.DeletePhotos
import cn.ellerotta.ktproto.api.functions.photos.UpdateProfilePhoto
import cn.ellerotta.ktproto.api.functions.photos.UploadProfilePhoto
import cn.ellerotta.ktproto.api.types.Birthday
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.ChatPhotoEmpty
import cn.ellerotta.ktproto.api.types.InputChannel
import cn.ellerotta.ktproto.api.types.InputFile
import cn.ellerotta.ktproto.api.types.InputPhoto
import cn.ellerotta.ktproto.api.types.Photo
import cn.ellerotta.ktproto.api.types.PhotoSize
import cn.ellerotta.ktproto.api.types.TypePhoto
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.UserProfilePhoto
import cn.ellerotta.ktproto.api.types.Username
import cn.ellerotta.ktproto.api.types.messages.Chats
import cn.ellerotta.ktproto.api.types.photos.Photo as PhotosPhoto
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeCluster
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.CopyOnWriteArrayList

class FakeProfileEdit(private val fake: FakeTelegram) {
    @Volatile private var customised = false

    private val names = mutableListOf("Test", "User")

    @Volatile var about: String? = null
        private set

    @Volatile var birthday: Birthday? = Birthday(day = 14, month = 3, year = 1990)
        private set

    @Volatile var personalChannelId: Long? = null
        private set

    @Volatile var username: String? = "tester"
        private set

    @Volatile var collectibles: List<Username> = emptyList()
        set(value) {
            field = value
            customised = true
        }

    val taken: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply { add("taken_name") }
    val forSale: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply { add("for_sale") }

    @Volatile var admined: List<Channel> = emptyList()

    val checks: MutableList<String> = CopyOnWriteArrayList()
    val updates: MutableList<UpdateProfile> = CopyOnWriteArrayList()
    val usernameUpdates: MutableList<String> = CopyOnWriteArrayList()
    val toggles: MutableList<ToggleUsername> = CopyOnWriteArrayList()
    val reorders: MutableList<List<String>> = CopyOnWriteArrayList()
    val birthdayUpdates: MutableList<Birthday?> = CopyOnWriteArrayList()
    val channelUpdates: MutableList<Long?> = CopyOnWriteArrayList()

    val uploads: MutableList<ByteArray> = CopyOnWriteArrayList()

    private val photoIds = CopyOnWriteArrayList<Long>()
    private var nextPhotoId = PHOTO_ID

    @Volatile private var reference = 1

    val refused: MutableList<Long> = CopyOnWriteArrayList()

    fun seedPhotos(count: Int) {
        customised = true
        repeat(count) { photoIds += nextPhotoId++ }
    }

    fun rotateReferences() {
        reference++
    }

    private fun referenceOf(id: Long) = "ref$id.$reference".toByteArray()

    private fun photo(id: Long): Photo = Photo(id = id, accessHash = id * 3, fileReference = referenceOf(id), date = NOW, sizes = listOf(PhotoSize("x", 800, 800, 4_000)), dcId = 2)

    fun photos(): List<TypePhoto> = photoIds.map(::photo)

    private fun currentPhoto() = photoIds.firstOrNull()?.let { UserProfilePhoto(photoId = it, dcId = 2) }

    fun overlay(base: User): User {
        if (!customised) return base
        val all = collectibles
        val usernames = if (all.isEmpty()) null else listOfNotNull(username?.let { Username(editable = true, active = true, username = it) }) + all
        return User(
            self = true, premium = base.premium, id = base.id, accessHash = base.accessHash, firstName = names[0], lastName = names[1].ifEmpty { null },
            username = username, usernames = usernames, phone = base.phone, photo = currentPhoto(),
        )
    }

    val edited: Boolean get() = customised

    fun personalChannel(): Channel? = personalChannelId?.let { id -> admined.firstOrNull { it.id == id } }

    private fun validUsername(name: String) = name.length in 5..32 && name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' } && !name[0].isDigit()

    private fun refusal(name: String): FakeAnswer? = when {
        !validUsername(name) -> FakeAnswer.Error(400, "USERNAME_INVALID")
        name in forSale -> FakeAnswer.Error(400, "USERNAME_PURCHASE_AVAILABLE")
        name in taken || collectibles.any { it.username == name } -> FakeAnswer.Error(400, "USERNAME_OCCUPIED")
        else -> null
    }

    private fun live(input: InputPhoto): Long? {
        if (input.id !in photoIds) return null
        return input.id.takeIf { input.fileReference.contentEquals(referenceOf(input.id)) }
    }

    fun register(server: FakeTelegramServer, cluster: FakeCluster) {
        server.rpc.on(UpdateProfile.CONSTRUCTOR_ID) { request ->
            val call = UpdateProfile.decode(request)
            call.firstName?.let { if (it.isBlank()) return@on FakeAnswer.Error(400, "FIRSTNAME_INVALID") }
            call.about?.let { if (it.length > if (fake.articles.premium) 140 else 70) return@on FakeAnswer.Error(400, "ABOUT_TOO_LONG") }
            updates += call
            customised = true
            call.firstName?.let { names[0] = it }
            call.lastName?.let { names[1] = it }
            call.about?.let { about = it }
            FakeAnswer.Result(fake.selfUser())
        }
        server.rpc.on(CheckUsername.CONSTRUCTOR_ID) { request ->
            val name = CheckUsername.decode(request).username
            checks += name
            refusal(name) ?: BOOL_TRUE
        }
        server.rpc.on(UpdateUsername.CONSTRUCTOR_ID) { request ->
            val name = UpdateUsername.decode(request).username
            if (name == username.orEmpty()) return@on FakeAnswer.Error(400, "USERNAME_NOT_MODIFIED")
            if (name.isNotEmpty()) refusal(name)?.let { return@on it }
            usernameUpdates += name
            customised = true
            username = name.ifEmpty { null }
            FakeAnswer.Result(fake.selfUser())
        }
        server.rpc.on(ToggleUsername.CONSTRUCTOR_ID) { request ->
            val call = ToggleUsername.decode(request)
            val found = collectibles.firstOrNull { it.username == call.username } ?: return@on FakeAnswer.Error(400, "USERNAME_NOT_OCCUPIED")
            toggles += call
            val rest = collectibles - found
            collectibles = if (call.active) rest.filter { it.active } + Username(active = true, username = found.username) + rest.filter { !it.active } else rest.filter { it.active } + rest.filter { !it.active } + Username(active = false, username = found.username)
            BOOL_TRUE
        }
        server.rpc.on(ReorderUsernames.CONSTRUCTOR_ID) { request ->
            val order = ReorderUsernames.decode(request).order
            val active = listOfNotNull(username) + collectibles.filter { it.active }.map { it.username }
            if (order.sorted() != active.sorted()) return@on FakeAnswer.Error(400, "USERNAME_INVALID")
            reorders += order
            collectibles = order.filter { it != username }.map { Username(active = true, username = it) } + collectibles.filter { !it.active }
            BOOL_TRUE
        }
        server.rpc.on(UpdateBirthday.CONSTRUCTOR_ID) { request ->
            val set = UpdateBirthday.decode(request).birthday
            if (set != null && set.day !in 1..31) return@on FakeAnswer.Error(400, "BIRTHDAY_INVALID")
            birthdayUpdates += set
            customised = true
            birthday = set
            BOOL_TRUE
        }
        server.rpc.on(GetAdminedPublicChannels.CONSTRUCTOR_ID) {
            FakeAnswer.Result(Chats(admined))
        }
        server.rpc.on(UpdatePersonalChannel.CONSTRUCTOR_ID) { request ->
            val channel = UpdatePersonalChannel.decode(request).channel
            val id = (channel as? InputChannel)?.channelId
            if (id != null && admined.none { it.id == id }) return@on FakeAnswer.Error(400, "CHANNEL_INVALID")
            channelUpdates += id
            customised = true
            personalChannelId = id
            BOOL_TRUE
        }
        server.rpc.on(UploadProfilePhoto.CONSTRUCTOR_ID) { request ->
            val file = UploadProfilePhoto.decode(request).file as? InputFile ?: return@on FakeAnswer.Error(400, "PHOTO_INVALID")
            val bytes = cluster.uploadedFile(file.id, file.parts) ?: return@on FakeAnswer.Error(400, "PHOTO_INVALID")
            uploads += bytes
            customised = true
            val id = nextPhotoId++
            photoIds.add(0, id)
            FakeAnswer.Result(PhotosPhoto(photo(id), listOf(fake.selfUser())))
        }
        server.rpc.on(UpdateProfilePhoto.CONSTRUCTOR_ID) { request ->
            val input = UpdateProfilePhoto.decode(request).id as? InputPhoto ?: return@on FakeAnswer.Error(400, "PHOTO_ID_INVALID")
            val id = live(input) ?: return@on stale(input)
            photoIds.remove(id)
            photoIds.add(0, id)
            FakeAnswer.Result(PhotosPhoto(photo(id), listOf(fake.selfUser())))
        }
        server.rpc.on(DeletePhotos.CONSTRUCTOR_ID) { request ->
            val inputs = DeletePhotos.decode(request).id.filterIsInstance<InputPhoto>()
            val ids = inputs.map { live(it) ?: return@on stale(it) }
            photoIds.removeAll(ids.toSet())
            customised = true
            val writer = cn.ellerotta.ktproto.tl.TlWriter()
            writer.writeVector(ids) { writeLong(it) }
            FakeAnswer.Raw(writer.toByteArray())
        }
    }

    private fun stale(input: InputPhoto): FakeAnswer {
        if (input.id !in photoIds) return FakeAnswer.Error(400, "PHOTO_ID_INVALID")
        refused += input.id
        return FakeAnswer.Error(400, "FILE_REFERENCE_EXPIRED")
    }

    companion object {
        const val PHOTO_ID = 7_001L

        fun myNotes() = Channel(broadcast = true, id = 8_101, accessHash = 99, title = "My notes", photo = ChatPhotoEmpty, date = NOW, participantsCount = 3)
    }
}
