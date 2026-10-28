package app.miogram.data.telegram

import cn.ellerotta.ktproto.api.types.PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object PasswordHash {
    private const val SIZE = 256
    private const val ITERATIONS = 100_000
    private const val SALT_EXTRA = 32
    private const val PRIME_CERTAINTY = 25

    fun create(
        algo: PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow,
        password: String,
        random: (Int) -> ByteArray = { ByteArray(it).also(SecureRandom()::nextBytes) },
    ): Pair<PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow, ByteArray> {
        val prime = BigInteger(1, algo.p)
        check(isSafe(algo.g, prime)) { "the password's group is not safe" }
        val salt1 = algo.salt1 + random(SALT_EXTRA)
        val x = BigInteger(1, hash(password.encodeToByteArray(), salt1, algo.salt2))
        val verifier = BigInteger.valueOf(algo.g.toLong()).modPow(x, prime)
        return PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow(salt1, algo.salt2, algo.g, algo.p) to fixed(verifier)
    }

    fun isSafe(g: Int, p: BigInteger): Boolean {
        if (p.bitLength() != 2048 || !p.testBit(0)) return false
        val residue = when (g) {
            2 -> p.mod(EIGHT).toInt() == 7
            3 -> p.mod(THREE).toInt() == 2
            4 -> true
            5 -> p.mod(FIVE).toInt() in setOf(1, 4)
            6 -> p.mod(TWENTY_FOUR).toInt() in setOf(19, 23)
            7 -> p.mod(SEVEN).toInt() in setOf(3, 5, 6)
            else -> false
        }
        return residue && p.isProbablePrime(PRIME_CERTAINTY) && p.shiftRight(1).isProbablePrime(PRIME_CERTAINTY)
    }

    fun hash(password: ByteArray, salt1: ByteArray, salt2: ByteArray): ByteArray {
        val first = salted(salted(password, salt1), salt2)
        return salted(pbkdf2(first, salt1), salt2)
    }

    private fun salted(data: ByteArray, salt: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").apply { update(salt); update(data); update(salt) }.digest()

    private fun pbkdf2(password: ByteArray, salt: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(password, "HmacSHA512"))
        mac.update(salt)
        var block = mac.doFinal(byteArrayOf(0, 0, 0, 1))
        val out = block.copyOf()
        repeat(ITERATIONS - 1) {
            block = mac.doFinal(block)
            for (i in out.indices) out[i] = (out[i].toInt() xor block[i].toInt()).toByte()
        }
        return out
    }

    private fun fixed(value: BigInteger): ByteArray {
        val bytes = value.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        return ByteArray(SIZE - bytes.size) + bytes
    }

    private val THREE = BigInteger.valueOf(3)
    private val FIVE = BigInteger.valueOf(5)
    private val SEVEN = BigInteger.valueOf(7)
    private val EIGHT = BigInteger.valueOf(8)
    private val TWENTY_FOUR = BigInteger.valueOf(24)
}
