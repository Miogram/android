package app.miogram

import app.miogram.FakeGroups.Companion.DEVS_ID
import app.miogram.FakeGroups.Companion.FAMILY_ID
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.UsernameAvailability
import cn.ellerotta.ktproto.api.types.InputUser
import cn.ellerotta.ktproto.session.RpcException
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class NewChatsRepositoryTest {
    private fun withRepos(block: suspend (FakeTelegram, TestRepositories) -> Unit): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            fake.groups.inDialogs = true
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                block(fake, repos)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    private val photo = ByteArray(2_000) { it.toByte() }

    @Test
    fun aGroupIsASupergroupWithItsPhotoAndMembers() = withRepos { fake, repos ->
        val chats = fake.groups.newChats
        val erin = fake.contacts.erin
        val frank = fake.contacts.frank
        repos.contacts.contacts()
        chats.restricted += frank.id

        val group = repos.groups.createGroup("  Book lovers  ")
        val call = chats.createCalls.single()
        assertTrue(call.megagroup && !call.broadcast)
        assertEquals("Book lovers", call.title)
        assertTrue(group.key.startsWith("ch"))
        assertEquals("Book lovers", group.title)

        repos.groups.setPhoto(group.key, photo)
        assertEquals(FakeNewChats.UPLOADED, chats.photos[group.key.drop(2).toLong()])

        val missing = repos.groups.invite(group.key, listOf("u${erin.id}", "u${frank.id}"))
        assertEquals(listOf("u${frank.id}"), missing.map { it.userKey })
        assertEquals(listOf(erin.id, frank.id), chats.inviteCalls.single().users.map { (it as InputUser).userId })

        repos.groups.sendInviteLink(group.key, missing.map { it.userKey })
        assertEquals(1, fake.savedTexts(repos.invites.links(group.key).primary!!.link))
    }

    @Test
    fun aChannelHasItsDescriptionAndAUsernameTelegramChecks() = withRepos { fake, repos ->
        val chats = fake.groups.newChats
        val channel = repos.groups.createChannel("Film news", "  every week  ")
        val call = chats.createCalls.single()
        assertTrue(call.broadcast && !call.megagroup)
        assertEquals("every week", call.about)

        assertEquals(UsernameAvailability.Available, repos.groups.checkUsername(channel.key, "filmnews"))
        assertEquals(UsernameAvailability.Taken, repos.groups.checkUsername(channel.key, "kotlin"))
        assertEquals(UsernameAvailability.Invalid, repos.groups.checkUsername(channel.key, "admin"))
        assertEquals(UsernameAvailability.TooManyPublic, repos.groups.checkUsername(channel.key, "toomany"))

        repos.groups.setUsername(channel.key, "filmnews")
        assertEquals("filmnews", chats.usernames[channel.key.drop(2).toLong()])
        assertFailsWith<RpcException> { repos.groups.setUsername(channel.key, "kotlin") }
    }

    @Test
    fun someoneAlreadyInAGroupDoesNotStopTheOthers() = withRepos { fake, repos ->
        repos.dialogs.dialogs()
        val chats = fake.groups.newChats
        val erin = fake.contacts.erin
        val frank = fake.contacts.frank
        repos.contacts.contacts()
        chats.members[DEVS_ID] = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>().apply { add(erin.id) }

        val missing = repos.groups.invite("ch$DEVS_ID", listOf("u${erin.id}", "u${frank.id}"))
        assertTrue(missing.isEmpty())
        assertEquals(listOf(2, 1, 1), chats.inviteCalls.map { it.users.size })
        assertTrue(frank.id in chats.members.getValue(DEVS_ID))
    }

    @Test
    fun aBasicGroupTakesPeopleOneByOne() = withRepos { fake, repos ->
        val chats = fake.groups.newChats
        val erin = fake.contacts.erin
        val frank = fake.contacts.frank
        repos.contacts.contacts()
        chats.restricted += frank.id

        val missing = repos.groups.invite("c$FAMILY_ID", listOf("u${fake.bob.id}", "u${frank.id}", "u${erin.id}"))
        assertEquals(listOf("u${frank.id}"), missing.map { it.userKey })
        assertEquals(3, chats.addChatUserCalls.size)
        assertTrue(chats.addChatUserCalls.all { it.fwdLimit > 0 })
        assertTrue(erin.id in chats.members.getValue(FAMILY_ID))
        assertFalse(frank.id in chats.members.getValue(FAMILY_ID))
    }

    @Test
    fun titleDescriptionAndPhotoChangeForEachKindOfGroup() = withRepos { fake, repos ->
        repos.dialogs.dialogs()
        val chats = fake.groups.newChats

        repos.groups.setTitle("ch$DEVS_ID", "  Kotlin devs 2  ")
        repos.groups.setAbout("ch$DEVS_ID", "  new rules  ")
        repos.groups.setPhoto("ch$DEVS_ID", photo)
        assertEquals("Kotlin devs 2", chats.titles[DEVS_ID])
        assertEquals("new rules", chats.abouts[DEVS_ID])
        assertEquals(FakeNewChats.UPLOADED, chats.photos[DEVS_ID])
        repos.groups.setPhoto("ch$DEVS_ID", null)
        assertEquals(FakeNewChats.REMOVED, chats.photos[DEVS_ID])

        repos.groups.setTitle("c$FAMILY_ID", "Family chat")
        repos.groups.setAbout("c$FAMILY_ID", "dinner plans")
        repos.groups.setPhoto("c$FAMILY_ID", photo)
        assertEquals("Family chat", chats.titles[FAMILY_ID])
        assertEquals("dinner plans", chats.abouts[FAMILY_ID])
        assertEquals(FakeNewChats.UPLOADED, chats.photos[FAMILY_ID])
    }

    @Test
    fun onlyAdminsWithTheRightMayEditTheInfo() = withRepos { _, repos ->
        repos.dialogs.dialogs()
        assertTrue(repos.channels.info("ch$DEVS_ID")!!.canChangeInfo)
        assertTrue(repos.profiles.profile("ch$DEVS_ID").canEdit)
        assertFalse(repos.channels.info("c$FAMILY_ID")!!.canChangeInfo)
        assertFalse(repos.profiles.profile("c$FAMILY_ID").canEdit)
    }
}
