package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.contacts.AddContact
import cn.ellerotta.ktproto.api.functions.contacts.DeleteContacts
import cn.ellerotta.ktproto.api.functions.contacts.GetContacts
import cn.ellerotta.ktproto.api.functions.contacts.GetStatuses
import cn.ellerotta.ktproto.api.functions.contacts.ImportContacts
import cn.ellerotta.ktproto.api.types.Contact
import cn.ellerotta.ktproto.api.types.ContactStatus
import cn.ellerotta.ktproto.api.types.ImportedContact
import cn.ellerotta.ktproto.api.types.InputUser
import cn.ellerotta.ktproto.api.types.TypeUserStatus
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.UserStatusLastWeek
import cn.ellerotta.ktproto.api.types.UserStatusOffline
import cn.ellerotta.ktproto.api.types.UserStatusOnline
import cn.ellerotta.ktproto.api.types.contacts.Contacts
import cn.ellerotta.ktproto.api.types.contacts.ContactsNotModified
import cn.ellerotta.ktproto.api.types.contacts.ImportedContacts
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import cn.ellerotta.ktproto.tl.TlWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakeContacts(private val fake: FakeTelegram) {
    val erin = User(id = 506, accessHash = 13, firstName = "Erin", lastName = "Online", contact = true, mutualContact = true, status = UserStatusOnline(Int.MAX_VALUE))
    val frank = User(id = 507, accessHash = 14, firstName = "Frank", phone = "15550002222", contact = true, status = UserStatusOffline(NOW))

    val ids: MutableMap<Long, Boolean> = ConcurrentHashMap(mapOf(502L to false, erin.id to true, frank.id to false))

    val statuses: MutableMap<Long, TypeUserStatus> = ConcurrentHashMap()

    val getCalls: MutableList<Long> = CopyOnWriteArrayList()
    val imports: MutableList<ImportContacts> = CopyOnWriteArrayList()
    val adds: MutableList<AddContact> = CopyOnWriteArrayList()
    val deletes: MutableList<List<Long>> = CopyOnWriteArrayList()

    private fun people(): List<User> = listOf(fake.alice, fake.bob, fake.carol, erin, frank)

    private fun asContact(user: User): User = User(
        contact = true, mutualContact = ids[user.id] == true, id = user.id, accessHash = user.accessHash, firstName = user.firstName,
        lastName = user.lastName, username = user.username, phone = user.phone, photo = user.photo,
        status = statuses[user.id] ?: user.status ?: if (user.id == fake.bob.id) UserStatusLastWeek() else null,
    )

    private fun list(): Contacts {
        val users = people().filter { it.id in ids }.map(::asContact)
        return Contacts(users.map { Contact(it.id, ids[it.id] == true) }, savedCount = 0, users = users)
    }

    fun hash(): Long {
        var hash = 0L
        for (id in listOf(0L) + ids.keys.sorted()) {
            hash = hash xor (hash ushr 21)
            hash = hash xor (hash shl 35)
            hash = hash xor (hash ushr 4)
            hash += id
        }
        return hash
    }

    private fun changed(user: User) = FakeAnswer.Result(Updates(emptyList(), listOf(asContact(user)), emptyList(), NOW, 0))

    fun register(server: FakeTelegramServer) {
        server.rpc.on(GetContacts.CONSTRUCTOR_ID) { request ->
            val hash = GetContacts.decode(request).hash
            getCalls += hash
            FakeAnswer.Result(if (hash != 0L && hash == hash()) ContactsNotModified else list())
        }
        server.rpc.on(GetStatuses.CONSTRUCTOR_ID) {
            val w = TlWriter()
            w.writeVector(list().users.filterIsInstance<User>().mapNotNull { user -> user.status?.let { ContactStatus(user.id, it) } }) { it.serialize(this) }
            FakeAnswer.Raw(w.toByteArray())
        }
        server.rpc.on(ImportContacts.CONSTRUCTOR_ID) { request ->
            val call = ImportContacts.decode(request)
            imports += call
            val wanted = call.contacts.single()
            val user = people().firstOrNull { it.phone == wanted.phone }
                ?: return@on FakeAnswer.Result(ImportedContacts(emptyList(), emptyList(), emptyList(), emptyList()))
            ids.putIfAbsent(user.id, false)
            FakeAnswer.Result(ImportedContacts(listOf(ImportedContact(user.id, wanted.clientId)), emptyList(), emptyList(), listOf(asContact(user))))
        }
        server.rpc.on(AddContact.CONSTRUCTOR_ID) { request ->
            val call = AddContact.decode(request)
            adds += call
            val user = people().firstOrNull { it.id == (call.id as? InputUser)?.userId } ?: return@on FakeAnswer.Error(400, "USER_ID_INVALID")
            ids.putIfAbsent(user.id, false)
            changed(user)
        }
        server.rpc.on(DeleteContacts.CONSTRUCTOR_ID) { request ->
            val removed = DeleteContacts.decode(request).id.mapNotNull { (it as? InputUser)?.userId }
            deletes += removed
            removed.forEach { ids.remove(it) }
            FakeAnswer.Result(Updates(emptyList(), people().filter { it.id in removed }, emptyList(), NOW, 0))
        }
    }
}
