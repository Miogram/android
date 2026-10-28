@file:OptIn(InternalKtprotoApi::class)

package app.miogram

import app.miogram.FakeTelegram.Companion.BOOL_TRUE
import cn.ellerotta.ktproto.InternalKtprotoApi
import cn.ellerotta.ktproto.api.functions.account.CancelPasswordEmail
import cn.ellerotta.ktproto.api.functions.account.ConfirmPasswordEmail
import cn.ellerotta.ktproto.api.functions.account.GetPassword
import cn.ellerotta.ktproto.api.functions.account.GetPasswordSettings
import cn.ellerotta.ktproto.api.functions.account.ResendPasswordEmail
import cn.ellerotta.ktproto.api.functions.account.UpdatePasswordSettings
import cn.ellerotta.ktproto.api.functions.auth.RecoverPassword
import cn.ellerotta.ktproto.api.functions.auth.RequestPasswordRecovery
import cn.ellerotta.ktproto.api.types.InputCheckPasswordSRP
import cn.ellerotta.ktproto.api.types.PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow
import cn.ellerotta.ktproto.api.types.PasswordKdfAlgoUnknown
import cn.ellerotta.ktproto.api.types.SecurePasswordKdfAlgoUnknown
import cn.ellerotta.ktproto.api.types.TypeInputCheckPasswordSRP
import cn.ellerotta.ktproto.api.types.account.Password
import cn.ellerotta.ktproto.api.types.account.PasswordSettings
import cn.ellerotta.ktproto.api.types.auth.Authorization
import cn.ellerotta.ktproto.api.types.auth.PasswordRecovery
import cn.ellerotta.ktproto.crypto.DhChecks
import cn.ellerotta.ktproto.crypto.PasswordSrp
import cn.ellerotta.ktproto.crypto.constantTimeEquals
import cn.ellerotta.ktproto.crypto.toFixedBytes
import cn.ellerotta.ktproto.crypto.unsignedBigInteger
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class FakeTwoStep(private val fake: FakeTelegram) {
    private val prime = DhChecks.KNOWN_SAFE_PRIME.toFixedBytes(256)
    private val baseSalt1 = Random(5).nextBytes(40)
    private val salt2 = Random(6).nextBytes(16)
    private val sessions = ConcurrentHashMap<Long, BigInteger>()

    @Volatile private var algo: PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow? = null
    @Volatile private var verifier: BigInteger? = null

    @Volatile var hint: String? = null
        private set

    @Volatile var recoveryEmail: String? = null
        private set

    @Volatile var pendingEmail: String? = null
        private set

    @Volatile var resent = 0
        private set

    @Volatile var lastUpdate: UpdatePasswordSettings? = null
        private set

    val enabled: Boolean get() = verifier != null

    fun preset(password: String, hint: String? = null, email: String? = null) {
        val set = PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow(baseSalt1, salt2, G, prime)
        algo = set
        verifier = unsignedBigInteger(PasswordSrp.verifier(PasswordSrp.passwordHash(password.encodeToByteArray(), set.salt1, set.salt2), G, prime))
        this.hint = hint
        recoveryEmail = email
    }

    private fun fresh() = PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow(baseSalt1, salt2, G, prime)

    private fun password(): Password {
        val current = algo
        val v = verifier
        val pending = pendingEmail?.let { mask(it) }
        if (current == null || v == null) {
            return Password(newAlgo = fresh(), newSecureAlgo = SecurePasswordKdfAlgoUnknown, secureRandom = Random.nextBytes(32), emailUnconfirmedPattern = pending)
        }
        val p = unsignedBigInteger(prime)
        val b = unsignedBigInteger(Random.nextBytes(256))
        val gB = PasswordSrp.multiplier(G, prime).multiply(v).add(BigInteger.valueOf(G.toLong()).modPow(b, p)).mod(p)
        val id = Random.nextLong()
        sessions[id] = b
        return Password(
            hasRecovery = recoveryEmail != null,
            hasPassword = true,
            currentAlgo = current,
            srpB = gB.toFixedBytes(256),
            srpId = id,
            hint = hint,
            emailUnconfirmedPattern = pending,
            newAlgo = fresh(),
            newSecureAlgo = SecurePasswordKdfAlgoUnknown,
            secureRandom = Random.nextBytes(32),
        )
    }

    private fun proven(check: TypeInputCheckPasswordSRP): Boolean {
        val current = algo ?: return true
        val v = verifier ?: return true
        val srp = check as? InputCheckPasswordSRP ?: return false
        val b = sessions.remove(srp.srpId) ?: return false
        val p = unsignedBigInteger(prime)
        val gB = PasswordSrp.multiplier(G, prime).multiply(v).add(BigInteger.valueOf(G.toLong()).modPow(b, p)).mod(p).toFixedBytes(256)
        val u = PasswordSrp.scrambler(srp.A, gB)
        val s = unsignedBigInteger(srp.A).multiply(v.modPow(u, p)).mod(p).modPow(b, p)
        return constantTimeEquals(PasswordSrp.proof(G, prime, current.salt1, current.salt2, srp.A, gB, s), srp.M1)
    }

    internal fun register(server: FakeTelegramServer) {
        val rpc = server.rpc
        rpc.on(GetPassword.CONSTRUCTOR_ID) { FakeAnswer.Result(password()) }
        rpc.on(GetPasswordSettings.CONSTRUCTOR_ID) { request ->
            if (!proven(GetPasswordSettings.decode(request).password)) return@on FakeAnswer.Error(400, "PASSWORD_HASH_INVALID")
            FakeAnswer.Result(PasswordSettings(email = recoveryEmail))
        }
        rpc.on(UpdatePasswordSettings.CONSTRUCTOR_ID) { request ->
            val call = UpdatePasswordSettings.decode(request)
            if (!proven(call.password)) return@on FakeAnswer.Error(400, "PASSWORD_HASH_INVALID")
            val settings = call.newSettings
            if (settings.email?.let { it.isNotEmpty() && '@' !in it } == true) return@on FakeAnswer.Error(400, "EMAIL_INVALID")
            when (val new = settings.newAlgo) {
                null -> Unit
                PasswordKdfAlgoUnknown -> {
                    algo = null
                    verifier = null
                    hint = null
                    recoveryEmail = null
                    pendingEmail = null
                }
                is PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow -> {
                    val hash = settings.newPasswordHash ?: return@on FakeAnswer.Error(400, "NEW_SETTINGS_INVALID")
                    if (new.salt1.size <= baseSalt1.size || !new.salt1.copyOf(baseSalt1.size).contentEquals(baseSalt1) || !new.salt2.contentEquals(salt2) || hash.size != 256) {
                        return@on FakeAnswer.Error(400, "NEW_SALT_INVALID")
                    }
                    algo = new
                    verifier = unsignedBigInteger(hash)
                    hint = settings.hint?.takeIf { it.isNotEmpty() }
                }
            }
            lastUpdate = call
            val email = settings.email
            if (!email.isNullOrEmpty()) {
                pendingEmail = email
                return@on FakeAnswer.Error(400, "EMAIL_UNCONFIRMED_6")
            }
            BOOL_TRUE
        }
        rpc.on(ConfirmPasswordEmail.CONSTRUCTOR_ID) { request ->
            val pending = pendingEmail ?: return@on FakeAnswer.Error(400, "EMAIL_HASH_EXPIRED")
            if (ConfirmPasswordEmail.decode(request).code != EMAIL_CODE) return@on FakeAnswer.Error(400, "CODE_INVALID")
            recoveryEmail = pending
            pendingEmail = null
            BOOL_TRUE
        }
        rpc.on(ResendPasswordEmail.CONSTRUCTOR_ID) {
            resent++
            BOOL_TRUE
        }
        rpc.on(CancelPasswordEmail.CONSTRUCTOR_ID) {
            pendingEmail = null
            BOOL_TRUE
        }
        rpc.on(RequestPasswordRecovery.CONSTRUCTOR_ID) {
            val email = recoveryEmail ?: return@on FakeAnswer.Error(400, "PASSWORD_RECOVERY_NA")
            FakeAnswer.Result(PasswordRecovery(emailPattern = mask(email)))
        }
        rpc.on(RecoverPassword.CONSTRUCTOR_ID) { request ->
            if (RecoverPassword.decode(request).code != RECOVERY_CODE) return@on FakeAnswer.Error(400, "CODE_INVALID")
            algo = null
            verifier = null
            hint = null
            recoveryEmail = null
            FakeAnswer.Result(Authorization(user = fake.selfUser()))
        }
    }

    private fun mask(email: String) = email.first() + "***" + email.substringAfter('@').let { "@" + it.first() + "***" }

    companion object {
        private const val G = 3

        const val EMAIL_CODE = "123456"

        const val RECOVERY_CODE = "654321"
    }
}
