@file:OptIn(InternalKtprotoApi::class)

package app.miogram

import app.miogram.data.telegram.PasswordHash
import cn.ellerotta.ktproto.InternalKtprotoApi
import cn.ellerotta.ktproto.api.types.PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow
import cn.ellerotta.ktproto.crypto.DhChecks
import cn.ellerotta.ktproto.crypto.PasswordSrp
import cn.ellerotta.ktproto.crypto.toFixedBytes
import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PasswordHashTest {
    private val p = DhChecks.KNOWN_SAFE_PRIME.toFixedBytes(256)
    private val algo = PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow(Random(1).nextBytes(40), Random(2).nextBytes(16), 3, p)

    @Test
    fun theVerifierIsWhatTheLibraryComputes() {
        val extra = Random(9).nextBytes(32)
        val (renewed, hash) = PasswordHash.create(algo, "pässword ✓") { extra }

        assertContentEquals(algo.salt1 + extra, renewed.salt1)
        assertContentEquals(algo.salt2, renewed.salt2)
        assertEquals(3, renewed.g)
        assertContentEquals(p, renewed.p)
        val expected = PasswordSrp.verifier(PasswordSrp.passwordHash("pässword ✓".encodeToByteArray(), renewed.salt1, renewed.salt2), 3, p)
        assertEquals(256, hash.size)
        assertContentEquals(expected, hash)
    }

    @Test
    fun anotherPasswordOrSaltGivesAnotherVerifier() {
        val random = { n: Int -> ByteArray(n) { 7 } }
        val one = PasswordHash.create(algo, "one", random).second
        assertFalse(one.contentEquals(PasswordHash.create(algo, "two", random).second))
        assertFalse(one.contentEquals(PasswordHash.create(algo, "one") { ByteArray(it) { 8 } }.second))
    }

    @Test
    fun aGroupThatIsNotSafeIsRefused() {
        val prime = DhChecks.KNOWN_SAFE_PRIME
        assertTrue(PasswordHash.isSafe(3, prime))
        assertFalse(PasswordHash.isSafe(3, prime.add(BigInteger.TWO)))
        assertFalse(PasswordHash.isSafe(3, BigInteger.ONE.shiftLeft(2047)))
        assertFalse(PasswordHash.isSafe(3, BigInteger.valueOf(1019)))
        assertFalse(PasswordHash.isSafe(1, prime))
        assertFalse(PasswordHash.isSafe(8, prime))
        assertFalse(PasswordHash.isSafe(2, prime))

        val unsafe = PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow(algo.salt1, algo.salt2, 3, prime.add(BigInteger.TWO).toFixedBytes(256))
        assertFailsWith<IllegalStateException> { PasswordHash.create(unsafe, "x") }
    }
}
