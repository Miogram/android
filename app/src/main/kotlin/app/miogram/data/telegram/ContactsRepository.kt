package app.miogram.data.telegram

import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.ktproto.api.functions.contacts.AddContact
import cn.ellerotta.ktproto.api.functions.contacts.DeleteContacts
import cn.ellerotta.ktproto.api.functions.contacts.GetContacts
import cn.ellerotta.ktproto.api.functions.contacts.GetStatuses
import cn.ellerotta.ktproto.api.functions.contacts.ImportContacts
import cn.ellerotta.ktproto.api.functions.contacts.Search as ContactsSearch
import cn.ellerotta.ktproto.api.types.InputPhoneContact
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.TypeUserStatus
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.contacts.Contacts
import cn.ellerotta.ktproto.api.types.contacts.ContactsNotModified
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class ContactsRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers
    private val lock = Mutex()

    private var list: Contacts? = null

    private var statuses: Map<Long, TypeUserStatus> = emptyMap()

    suspend fun stored(): List<ContactItem> = lock.withLock { known()?.let(::itemsOf).orEmpty() }

    suspend fun contacts(): List<ContactItem> = lock.withLock {
        val known = known()
        when (val answer = telegram.client().invoke(GetContacts(known?.let(::contactsHash) ?: 0))) {
            is Contacts -> {
                list = answer
                statuses = emptyMap()
                telegram.cache?.putContacts(answer)
            }
            ContactsNotModified -> statuses = telegram.client().invoke(GetStatuses).associate { it.userId to it.status }
        }
        list?.let(::itemsOf).orEmpty()
    }

    suspend fun import(phone: String, firstName: String, lastName: String): ContactItem? {
        val number = phone.filter { it.isDigit() }
        val result = telegram.client().invoke(ImportContacts(listOf(InputPhoneContact(Random.nextLong(), number, firstName.trim(), lastName.trim()))))
        val userId = result.imported.firstOrNull()?.userId ?: return null
        val user = result.users.filterIsInstance<User>().firstOrNull { it.id == userId } ?: return null
        registry.rememberUsers(result.users)
        refresh()
        return itemOf(user, mutual = user.mutualContact, contact = true)
    }

    suspend fun add(key: String, firstName: String, lastName: String, sharePhone: Boolean) {
        telegram.client().invoke(AddContact(addPhonePrivacyException = sharePhone, id = registry.user(key), firstName = firstName.trim(), lastName = lastName.trim(), phone = ""))
        refresh()
    }

    suspend fun delete(keys: List<String>) {
        telegram.client().invoke(DeleteContacts(keys.map { registry.user(it) }))
        refresh()
    }

    suspend fun search(query: String, limit: Int = 20): List<ContactItem> {
        val found = telegram.client().invoke(ContactsSearch(q = query, limit = limit))
        val users = found.users.filterIsInstance<User>().associateBy { it.id }
        registry.rememberUsers(found.users)
        return (found.myResults + found.results).filterIsInstance<PeerUser>()
            .mapNotNull { users[it.userId] }
            .filter { !it.self && !it.contact }
            .distinctBy { it.id }
            .map { itemOf(it, mutual = false) }
    }

    private suspend fun refresh() {
        lock.withLock {
            val answer = telegram.client().invoke(GetContacts(0)) as? Contacts ?: return
            list = answer
            statuses = emptyMap()
            telegram.cache?.putContacts(answer)
        }
    }

    private suspend fun known(): Contacts? = list ?: telegram.cache?.contacts()?.also { list = it }

    private fun itemsOf(contacts: Contacts): List<ContactItem> {
        registry.rememberUsers(contacts.users)
        val users = contacts.users.filterIsInstance<User>().associateBy { it.id }
        return contacts.contacts.mapNotNull { contact -> users[contact.userId]?.let { itemOf(it, contact.mutual, contact = true) } }
    }

    private fun itemOf(user: User, mutual: Boolean, contact: Boolean = user.contact): ContactItem {
        val key = "u${user.id}"
        val now = System.currentTimeMillis() / 1000
        return ContactItem(
            key = key,
            userId = user.id,
            name = nameOf(user),
            presence = presenceOf(statuses[user.id] ?: user.status, now),
            avatarKey = registry.avatarKey(avatarOf(key, user)),
            phone = user.phone,
            username = user.username,
            mutual = mutual,
            contact = contact,
        )
    }
}

internal fun contactsHash(contacts: Contacts): Long =
    listHash(listOf(contacts.savedCount.toLong()) + contacts.contacts.map { it.userId }.sorted().take(100_000))

internal fun listHash(ids: List<Long>): Long {
    var hash = 0L
    for (id in ids) {
        hash = hash xor (hash ushr 21)
        hash = hash xor (hash shl 35)
        hash = hash xor (hash ushr 4)
        hash += id
    }
    return hash
}
