package app.miogram.data.telegram

import app.miogram.data.WebAppStore
import cn.ellerotta.core.model.CodeRequest
import cn.ellerotta.core.model.CodeType
import cn.ellerotta.core.model.ProfileInfo
import cn.ellerotta.core.model.SessionInfo
import cn.ellerotta.ktproto.api.functions.account.GetAuthorizations
import cn.ellerotta.ktproto.api.functions.account.GetPassword
import cn.ellerotta.ktproto.api.functions.account.ResetAuthorization
import cn.ellerotta.ktproto.api.functions.auth.LogOut
import cn.ellerotta.ktproto.api.functions.auth.ResendCode
import cn.ellerotta.ktproto.api.functions.auth.ResetAuthorizations
import cn.ellerotta.ktproto.api.functions.auth.SendCode
import cn.ellerotta.ktproto.api.functions.auth.SignIn
import cn.ellerotta.ktproto.api.functions.auth.SignUp
import cn.ellerotta.ktproto.api.types.CodeSettings
import cn.ellerotta.ktproto.api.types.auth.Authorization
import cn.ellerotta.ktproto.api.types.auth.AuthorizationSignUpRequired
import cn.ellerotta.ktproto.api.types.auth.CodeTypeCall
import cn.ellerotta.ktproto.api.types.auth.CodeTypeFlashCall
import cn.ellerotta.ktproto.api.types.auth.CodeTypeFragmentSms
import cn.ellerotta.ktproto.api.types.auth.CodeTypeMissedCall
import cn.ellerotta.ktproto.api.types.auth.CodeTypeSms
import cn.ellerotta.ktproto.api.types.auth.SentCode
import cn.ellerotta.ktproto.api.types.auth.SentCodeTypeApp
import cn.ellerotta.ktproto.api.types.auth.SentCodeTypeCall
import cn.ellerotta.ktproto.api.types.auth.SentCodeTypeEmailCode
import cn.ellerotta.ktproto.api.types.auth.SentCodeTypeFirebaseSms
import cn.ellerotta.ktproto.api.types.auth.SentCodeTypeFlashCall
import cn.ellerotta.ktproto.api.types.auth.SentCodeTypeFragmentSms
import cn.ellerotta.ktproto.api.types.auth.SentCodeTypeMissedCall
import cn.ellerotta.ktproto.api.types.auth.SentCodeTypeSms
import cn.ellerotta.ktproto.api.types.auth.TypeSentCode
import cn.ellerotta.ktproto.api.types.TypeUser
import cn.ellerotta.ktproto.client.QrLogin
import cn.ellerotta.ktproto.client.checkPassword
import cn.ellerotta.ktproto.client.qrLogin
import cn.ellerotta.ktproto.session.RpcException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface QrState {
    data class Code(val url: String) : QrState

    data class LoggedIn(val profile: ProfileInfo) : QrState

    data class PasswordRequired(val hint: String?) : QrState
}

sealed interface SignInResult {
    data class LoggedIn(val profile: ProfileInfo) : SignInResult
    data object SignUpRequired : SignInResult
    data class PasswordRequired(val hint: String?) : SignInResult
}

private const val SESSIONS = "sessions"

@Singleton
class AuthRepository @Inject constructor(private val telegram: TelegramClientHolder, private val media: MediaRepository) {
    private val registry get() = telegram.peers

    suspend fun sendCode(phone: String): CodeRequest {
        val digits = phone.filter { it.isDigit() }
        return codeRequest(digits, telegram.client().invoke(SendCode(digits, telegram.apiId, telegram.apiHash, CodeSettings())))
    }

    suspend fun resendCode(request: CodeRequest): CodeRequest =
        codeRequest(request.phone, telegram.client().invoke(ResendCode(phoneNumber = request.phone, phoneCodeHash = request.hash)))

    private fun codeRequest(phone: String, answer: TypeSentCode): CodeRequest {
        val sent = answer as? SentCode ?: error("unexpected answer to auth.sendCode")
        val (type, length) = when (val type = sent.type) {
            is SentCodeTypeApp -> CodeType.App to type.length
            is SentCodeTypeSms -> CodeType.Sms to type.length
            is SentCodeTypeFirebaseSms -> CodeType.Sms to type.length
            is SentCodeTypeCall -> CodeType.Call to type.length
            is SentCodeTypeFlashCall -> CodeType.FlashCall to null
            is SentCodeTypeMissedCall -> CodeType.MissedCall to type.length
            is SentCodeTypeEmailCode -> CodeType.Email to type.length
            is SentCodeTypeFragmentSms -> CodeType.Fragment to type.length
            else -> CodeType.Unknown to null
        }
        val next = when (sent.nextType) {
            CodeTypeSms -> CodeType.Sms
            CodeTypeCall -> CodeType.Call
            CodeTypeFlashCall -> CodeType.FlashCall
            CodeTypeMissedCall -> CodeType.MissedCall
            CodeTypeFragmentSms -> CodeType.Fragment
            null -> null
        }
        return CodeRequest(phone, sent.phoneCodeHash, length, telegram.requireClient().homeDcId, type, next, sent.timeout, System.currentTimeMillis())
    }

    suspend fun signIn(request: CodeRequest, code: String): SignInResult {
        val result = try {
            telegram.client().invoke(SignIn(phoneNumber = request.phone, phoneCodeHash = request.hash, phoneCode = code))
        } catch (e: RpcException) {
            if (e.errorMessage != "SESSION_PASSWORD_NEEDED") throw e
            return SignInResult.PasswordRequired(telegram.client().invoke(GetPassword).hint)
        }
        return when (result) {
            is Authorization -> SignInResult.LoggedIn(loggedIn(result.user))
            is AuthorizationSignUpRequired -> SignInResult.SignUpRequired
        }
    }

    fun qrLogin(): Flow<QrState> = flow {
        val current = telegram.client()
        try {
            current.qrLogin().collect { step ->
                when (step) {
                    is QrLogin.Token -> emit(QrState.Code(step.url))
                    is QrLogin.Success -> emit(QrState.LoggedIn(loggedIn(step.authorization.user)))
                }
            }
        } catch (e: RpcException) {
            if (e.errorMessage != "SESSION_PASSWORD_NEEDED") throw e
            emit(QrState.PasswordRequired(current.invoke(GetPassword).hint))
        }
    }

    suspend fun checkPassword(password: String): ProfileInfo =
        loggedIn((telegram.client().checkPassword(password) as Authorization).user)

    suspend fun signUp(request: CodeRequest, firstName: String, lastName: String): ProfileInfo {
        val result = telegram.client().invoke(SignUp(phoneNumber = request.phone, phoneCodeHash = request.hash, firstName = firstName, lastName = lastName))
        return loggedIn((result as Authorization).user)
    }

    private suspend fun loggedIn(user: TypeUser): ProfileInfo {
        val profile = registry.profileOf(user)
        telegram.cache?.owner(profile.id)
        return profile
    }

    suspend fun sessions(): List<SessionInfo> =
        telegram.client().invoke(GetAuthorizations).authorizations.map { session ->
            SessionInfo(
                hash = session.hash,
                device = session.deviceModel,
                platform = session.platform,
                system = session.systemVersion,
                app = session.appName,
                appVersion = session.appVersion,
                location = listOf(session.region, session.country).filter { it.isNotBlank() }.joinToString(", "),
                ip = session.ip,
                dateActive = session.dateActive,
                current = session.current,
                official = session.officialApp,
            )
        }.sortedWith(compareByDescending<SessionInfo> { it.current }.thenByDescending { it.dateActive }).also { telegram.memory[SESSIONS] = it }

    fun storedSessions(): List<SessionInfo>? = telegram.memory[SESSIONS]

    suspend fun terminateSession(hash: Long) {
        telegram.client().invoke(ResetAuthorization(hash))
    }

    suspend fun terminateOtherSessions() {
        telegram.client().invoke(ResetAuthorizations)
    }

    suspend fun logOut() {
        telegram.expectLogout()
        runCatching { telegram.client().invoke(LogOut) }
        forgetAccount()
    }

    val sessionEnded: Flow<Unit> get() = telegram.sessionEnded

    suspend fun forgetAccount() {
        registry.avatarRefs.clear()
        registry.customEmoji.clear()
        listOf("avatars", "stickers", "downloads", WebAppStore.DIR).forEach { File(telegram.filesDir, it).deleteRecursively() }
        media.clearStreams()
        telegram.cache?.clear()
        telegram.memory.clear()
    }
}
