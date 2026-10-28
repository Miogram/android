package app.miogram

import app.miogram.data.telegram.SignInResult
import cn.ellerotta.ktproto.session.RpcException
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class TwoStepRepositoryTest {
    private fun run(block: suspend (TestRepositories, FakeTelegram) -> Unit): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(120_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                block(repos, fake)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    private suspend fun rejects(message: String, block: suspend () -> Unit) = assertEquals(message, assertFailsWith<RpcException> { block() }.errorMessage)

    @Test
    fun statusFollowsTheAccount(): Unit = run { repos, fake ->
        val off = repos.twoStep.status()
        assertFalse(off.enabled)
        assertNull(off.hint)
        assertEquals(false, repos.twoStep.enabled.value)

        fake.twoStep.preset("hunter22", hint = "a fish", email = "ann@example.com")
        val on = repos.twoStep.status()
        assertTrue(on.enabled)
        assertTrue(on.hasRecovery)
        assertEquals("a fish", on.hint)
        assertEquals(true, repos.twoStep.enabled.value)
    }

    @Test
    fun aPasswordIsTurnedOnWithoutAnEmail(): Unit = run { repos, fake ->
        val status = repos.twoStep.create("correct horse", hint = "a battery", email = null)
        assertTrue(status.enabled)
        assertEquals("a battery", status.hint)
        assertFalse(status.hasRecovery)
        assertNull(status.unconfirmedEmail)
        assertTrue(fake.twoStep.enabled)

        repos.twoStep.verify("correct horse")
        rejects("PASSWORD_HASH_INVALID") { repos.twoStep.verify("Correct horse") }
    }

    @Test
    fun anEmailWaitsForItsCode(): Unit = run { repos, fake ->
        val status = repos.twoStep.create("correct horse", hint = null, email = "ann@example.com")
        assertTrue(status.enabled)
        assertEquals("a***@e***", status.unconfirmedEmail)
        assertEquals(6, status.codeLength)
        assertFalse(status.hasRecovery)

        rejects("CODE_INVALID") { repos.twoStep.confirmEmail("000000") }
        assertNotNull(fake.twoStep.pendingEmail)
        repos.twoStep.resendCode()
        assertEquals(1, fake.twoStep.resent)

        val confirmed = repos.twoStep.confirmEmail(FakeTwoStep.EMAIL_CODE)
        assertTrue(confirmed.hasRecovery)
        assertNull(confirmed.unconfirmedEmail)
        assertEquals("ann@example.com", fake.twoStep.recoveryEmail)
    }

    @Test
    fun anUnwantedEmailIsCancelled(): Unit = run { repos, fake ->
        rejects("EMAIL_INVALID") { repos.twoStep.create("other password", hint = null, email = "nowhere") }
        assertFalse(fake.twoStep.enabled)
        repos.twoStep.create("correct horse", hint = null, email = "ann@example.com")
        val status = repos.twoStep.cancelEmail()
        assertNull(status.unconfirmedEmail)
        assertNull(fake.twoStep.pendingEmail)
        assertTrue(status.enabled)
    }

    @Test
    fun theCurrentPasswordIsProvedToChangeIt(): Unit = run { repos, fake ->
        fake.twoStep.preset("old secret", hint = "old")
        rejects("PASSWORD_HASH_INVALID") { repos.twoStep.change("not it", "new secret", "new") }
        repos.twoStep.verify("old secret")
        assertEquals("old", fake.twoStep.hint)

        val status = repos.twoStep.change("old secret", "new secret", "new")
        assertEquals("new", status.hint)
        repos.twoStep.verify("new secret")
        rejects("PASSWORD_HASH_INVALID") { repos.twoStep.verify("old secret") }
        assertNull(fake.twoStep.lastUpdate?.newSettings?.email)
    }

    @Test
    fun theRecoveryEmailIsSetWithThePassword(): Unit = run { repos, fake ->
        fake.twoStep.preset("old secret")
        rejects("PASSWORD_HASH_INVALID") { repos.twoStep.setRecoveryEmail("not it", "ann@example.com") }
        val status = repos.twoStep.setRecoveryEmail("old secret", "ann@example.com")
        assertEquals("a***@e***", status.unconfirmedEmail)
        assertNull(fake.twoStep.lastUpdate?.newSettings?.newAlgo)
        assertTrue(repos.twoStep.confirmEmail(FakeTwoStep.EMAIL_CODE).hasRecovery)
        repos.twoStep.verify("old secret")
    }

    @Test
    fun thePasswordIsTurnedOff(): Unit = run { repos, fake ->
        fake.twoStep.preset("old secret", hint = "old", email = "ann@example.com")
        rejects("PASSWORD_HASH_INVALID") { repos.twoStep.remove("not it") }
        assertTrue(fake.twoStep.enabled)

        val status = repos.twoStep.remove("old secret")
        assertFalse(status.enabled)
        assertFalse(status.hasRecovery)
        assertFalse(fake.twoStep.enabled)
        assertEquals(false, repos.twoStep.enabled.value)
    }

    @Test
    fun aForgottenPasswordIsRecoveredByEmail(): Unit = run { repos, fake ->
        fake.twoStep.preset("old secret", email = "ann@example.com")
        assertEquals("a***@e***", repos.twoStep.requestRecovery())
        rejects("CODE_INVALID") { repos.twoStep.recover("111111") }
        assertTrue(fake.twoStep.enabled)

        val status = repos.twoStep.recover(FakeTwoStep.RECOVERY_CODE)
        assertFalse(status.enabled)
        assertFalse(fake.twoStep.enabled)
    }

    @Test
    fun withoutARecoveryEmailThereIsNothingToRecoverBy(): Unit = run { repos, fake ->
        fake.twoStep.preset("old secret")
        rejects("PASSWORD_RECOVERY_NA") { repos.twoStep.requestRecovery() }
        assertTrue(fake.twoStep.enabled)
    }
}
