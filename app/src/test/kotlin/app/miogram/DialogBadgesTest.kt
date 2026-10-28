package app.miogram

import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.MemberCounts
import cn.ellerotta.core.model.PeerKind
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class DialogBadgesTest {
    @Test
    fun theListCarriesStatusScamAndMemberCounts(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                fake.groups.inDialogs = true
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("9996624242"), "22222")).profile.id

                val plain = repos.dialogs.dialogs().dialogs.first { it.key == "u${fake.alice.id}" }
                assertEquals(Triple(null, false, false), Triple(plain.emojiStatus, plain.scam, plain.fake))

                fake.aliceShowsStatus = true
                fake.aliceIsScam = true
                val marked = repos.dialogs.dialogs().dialogs
                val alice = marked.first { it.key == "u${fake.alice.id}" }
                assertEquals(FakeTelegram.EMOJI_HEART, alice.emojiStatus)
                assertEquals(true, alice.scam)
                assertFalse(alice.fake)

                val devs = marked.first { it.key == "ch${FakeGroups.DEVS_ID}" }
                assertEquals(PeerKind.Group to 65, devs.kind to devs.members)
                assertNull(devs.membersOnline)
                assertEquals(3, marked.first { it.key == "c${FakeGroups.FAMILY_ID}" }.members)

                fake.listed += fake.carol
                val strangers = repos.dialogs.dialogs().dialogs
                val carol = strangers.first { it.key == "u${fake.carol.id}" }
                assertEquals("15550001111" to false, carol.phone to carol.support)
                assertEquals("15550001111", carol.phoneTitle)
                assertNull(strangers.first { it.key == "u${fake.alice.id}" }.phoneTitle)
                fake.carolIsSupport = true
                fake.listed.clear()
                fake.listed += fake.carol
                val support = repos.dialogs.dialogs().dialogs.first { it.key == "u${fake.carol.id}" }
                assertEquals(true to null, support.support to support.phoneTitle)
                fake.carolIsSupport = false
                fake.listed.clear()

                val found = repos.dialogs.search("alice").first { it.key == "u${fake.alice.id}" }
                assertEquals(FakeTelegram.EMOJI_HEART to true, found.emojiStatus to found.scam)

                assertEquals(MemberCounts(65, FakeGroups.DEVS_ONLINE), repos.channels.memberCounts("ch${FakeGroups.DEVS_ID}"))
                assertEquals(MemberCounts(3, 1), repos.channels.memberCounts("c${FakeGroups.FAMILY_ID}"))
                assertNull(repos.channels.memberCounts("u${fake.alice.id}"))
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}
