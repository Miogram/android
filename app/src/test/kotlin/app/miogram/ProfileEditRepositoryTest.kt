package app.miogram

import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.Birthday
import cn.ellerotta.core.model.OwnUsername
import cn.ellerotta.core.model.UsernameCheck
import cn.ellerotta.ktproto.api.types.Username
import cn.ellerotta.ktproto.session.RpcException
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class ProfileEditRepositoryTest {
    private fun run(block: suspend (TestRepositories, FakeTelegram) -> Unit): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                block(repos, fake)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun nameAndBioAreSetAndRefused(): Unit = run { repos, fake ->
        val edits = repos.profileEdits
        val details = edits.updateProfile(firstName = "Ann", lastName = null, about = "likes tea")
        val call = fake.profileEdit.updates.single()
        assertEquals(Triple<String?, String?, String?>("Ann", null, "likes tea"), Triple(call.firstName, call.lastName, call.about))
        assertEquals("Ann", details.firstName)
        assertEquals("User", details.lastName)
        assertEquals("Ann User", details.name)
        assertEquals("likes tea", details.about)
        assertEquals("likes tea", repos.profiles.own.value?.about)
        assertEquals("Ann User", repos.profiles.self.value?.name)

        assertEquals("FIRSTNAME_INVALID", assertFailsWith<RpcException> { edits.updateProfile(firstName = " ", lastName = null, about = null) }.errorMessage)
        assertEquals("ABOUT_TOO_LONG", assertFailsWith<RpcException> { edits.updateProfile(firstName = null, lastName = null, about = "x".repeat(71)) }.errorMessage)
        assertEquals("likes tea", repos.profiles.profile(null).about)

        assertEquals(70, edits.aboutLimit(premium = false))
        assertEquals(140, edits.aboutLimit(premium = true))
        assertNull(edits.updateProfile(firstName = null, lastName = null, about = "").about)
    }

    @Test
    fun usernamesAreCheckedAndSet(): Unit = run { repos, fake ->
        val edits = repos.profileEdits
        assertEquals(UsernameCheck.Free, edits.checkUsername("tester_new"))
        assertEquals(UsernameCheck.Taken, edits.checkUsername("taken_name"))
        assertEquals(UsernameCheck.ForSale, edits.checkUsername("for_sale"))
        assertEquals(UsernameCheck.Invalid(null), edits.checkUsername("ab"))
        assertEquals(listOf("tester_new", "taken_name", "for_sale", "ab"), fake.profileEdit.checks)

        val details = edits.updateUsername("tester_new")
        assertEquals(listOf("tester_new"), details.usernames)
        assertEquals("tester_new", repos.profiles.self.value?.username)
        assertEquals(listOf(OwnUsername("tester_new", active = true, editable = true)), details.usernameList)
        assertEquals("USERNAME_OCCUPIED", assertFailsWith<RpcException> { edits.updateUsername("taken_name") }.errorMessage)
        assertEquals(listOf("tester_new"), edits.updateUsername("tester_new").usernames)
        assertEquals(emptyList(), edits.updateUsername("").usernames)
        assertNull(repos.profiles.self.value?.username)
    }

    @Test
    fun collectibleUsernamesAreSwitchedAndOrdered(): Unit = run { repos, fake ->
        fake.profileEdit.collectibles = listOf(Username(active = true, username = "frag_one"), Username(active = true, username = "frag_two"), Username(active = false, username = "frag_off"))
        val edits = repos.profileEdits
        val loaded = repos.profiles.profile(null)
        assertEquals(
            listOf(OwnUsername("tester", true, true), OwnUsername("frag_one", true, false), OwnUsername("frag_two", true, false), OwnUsername("frag_off", false, false)),
            loaded.usernameList,
        )
        assertEquals(listOf("tester", "frag_one", "frag_two"), loaded.usernames)

        val on = edits.toggleUsername("frag_off", active = true)
        assertEquals(listOf("tester", "frag_one", "frag_two", "frag_off"), on.usernames)
        val off = edits.toggleUsername("frag_one", active = false)
        assertEquals(listOf("tester", "frag_two", "frag_off"), off.usernames)
        assertEquals(OwnUsername("frag_one", false, false), off.usernameList.last())

        val ordered = edits.reorderUsernames(listOf("tester", "frag_off", "frag_two"))
        assertEquals(listOf("tester", "frag_off", "frag_two"), ordered.usernames)
        assertEquals(listOf(listOf("tester", "frag_off", "frag_two")), fake.profileEdit.reorders)
        assertEquals(listOf("frag_off" to true, "frag_one" to false), fake.profileEdit.toggles.map { it.username to it.active })
    }

    @Test
    fun theBirthdayIsSetWithOrWithoutAYearAndRemoved(): Unit = run { repos, fake ->
        val edits = repos.profileEdits
        assertEquals(Birthday(14, 3, 1990), repos.profiles.profile(null).birthday)
        assertEquals(Birthday(29, 2, null), edits.setBirthday(Birthday(29, 2, null)).birthday)
        assertEquals(Birthday(1, 12, 2001), edits.setBirthday(Birthday(1, 12, 2001)).birthday)
        assertNull(edits.setBirthday(null).birthday)
        assertEquals(listOf(Triple(1, 12, 2001)), fake.profileEdit.birthdayUpdates.filterNotNull().drop(1).map { Triple(it.day, it.month, it.year) })
        assertNull(fake.profileEdit.birthdayUpdates.last())
    }

    @Test
    fun thePersonalChannelIsChosenAmongTheOwnedOnes(): Unit = run { repos, fake ->
        fake.profileEdit.admined = listOf(fake.news(), FakeProfileEdit.myNotes())
        val edits = repos.profileEdits
        assertNull(repos.profiles.profile(null).channel)
        val choices = edits.personalChannelChoices()
        assertEquals(listOf("ch${FakeTelegram.NEWS_ID}" to "Kotlin News", "ch8101" to "My notes"), choices.map { it.key to it.title })

        val chosen = edits.setPersonalChannel("ch${FakeTelegram.NEWS_ID}")
        assertEquals("Kotlin News", chosen.channel?.title)
        assertEquals(listOf<Long?>(FakeTelegram.NEWS_ID), fake.profileEdit.channelUpdates)
        assertNull(edits.setPersonalChannel(null).channel)
        assertEquals(listOf<Long?>(FakeTelegram.NEWS_ID, null), fake.profileEdit.channelUpdates)
    }

    @Test
    fun profilePhotosAreAddedMadeCurrentAndDeleted(): Unit = run { repos, fake ->
        fake.profileEdit.seedPhotos(2)
        val edits = repos.profileEdits
        val own = "u${fake.selfId}"
        val old = FakeProfileEdit.PHOTO_ID
        assertEquals(listOf("photo$old", "photo${old + 1}"), repos.profiles.photos(own).map { it.key })
        assertEquals("$old", repos.profiles.profile(null).avatarKey)

        val uploaded = edits.uploadPhoto(fake.photoBytes)
        assertContentEquals(fake.photoBytes, fake.profileEdit.uploads.single())
        assertEquals("${old + 2}", uploaded.avatarKey)
        assertEquals("${old + 2}", repos.profiles.self.value?.avatarKey)
        assertEquals("${old + 2}", repos.profiles.own.value?.avatarKey)
        assertEquals(listOf("photo${old + 2}", "photo$old", "photo${old + 1}"), repos.profiles.photos(own).map { it.key })

        val again = edits.setMainPhoto("photo${old + 1}")
        assertEquals("${old + 1}", again.avatarKey)
        assertEquals(listOf("photo${old + 1}", "photo${old + 2}", "photo$old"), repos.profiles.photos(own).map { it.key })

        val deleted = edits.deletePhoto("photo${old + 1}")
        assertEquals("${old + 2}", deleted.avatarKey)
        assertEquals(listOf("photo${old + 2}", "photo$old"), repos.profiles.photos(own).map { it.key })
        assertNotEquals(deleted.avatarKey, "${old + 1}")
    }

    @Test
    fun aStaleFileReferenceIsRefreshedOnce(): Unit = run { repos, fake ->
        fake.profileEdit.seedPhotos(2)
        val edits = repos.profileEdits
        val own = "u${fake.selfId}"
        val old = FakeProfileEdit.PHOTO_ID
        repos.profiles.photos(own)
        fake.profileEdit.rotateReferences()
        val details = edits.deletePhoto("photo${old + 1}")
        assertEquals(listOf(old + 1), fake.profileEdit.refused)
        assertEquals("$old", details.avatarKey)
        assertEquals(listOf("photo$old"), repos.profiles.photos(own).map { it.key })
        assertTrue(fake.profileEdit.photos().size == 1)
    }
}
