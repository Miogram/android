package app.miogram.data.telegram

import cn.ellerotta.core.model.TWO_STEP_CODE_LENGTH
import cn.ellerotta.core.model.TwoStepStatus
import cn.ellerotta.ktproto.api.functions.account.CancelPasswordEmail
import cn.ellerotta.ktproto.api.functions.account.ConfirmPasswordEmail
import cn.ellerotta.ktproto.api.functions.account.GetPassword
import cn.ellerotta.ktproto.api.functions.account.GetPasswordSettings
import cn.ellerotta.ktproto.api.functions.account.ResendPasswordEmail
import cn.ellerotta.ktproto.api.functions.account.UpdatePasswordSettings
import cn.ellerotta.ktproto.api.functions.auth.RecoverPassword
import cn.ellerotta.ktproto.api.functions.auth.RequestPasswordRecovery
import cn.ellerotta.ktproto.api.types.InputCheckPasswordEmpty
import cn.ellerotta.ktproto.api.types.PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow
import cn.ellerotta.ktproto.api.types.PasswordKdfAlgoUnknown
import cn.ellerotta.ktproto.api.types.TypeInputCheckPasswordSRP
import cn.ellerotta.ktproto.api.types.account.Password
import cn.ellerotta.ktproto.api.types.account.PasswordInputSettings
import cn.ellerotta.ktproto.client.PasswordAlgorithmException
import cn.ellerotta.ktproto.client.passwordCheck
import cn.ellerotta.ktproto.session.RpcException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private fun RpcException.unconfirmedCodeLength(): Int? =
    if (errorMessage.startsWith("EMAIL_UNCONFIRMED")) errorMessage.substringAfterLast('_').toIntOrNull() ?: TWO_STEP_CODE_LENGTH else null

@Singleton
class TwoStepRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val enabledState = MutableStateFlow<Boolean?>(null)

    val enabled: StateFlow<Boolean?> = enabledState.asStateFlow()

    suspend fun status(): TwoStepStatus = statusOf(telegram.client().invoke(GetPassword)).also {
        enabledState.value = it.enabled
        telegram.memory["twoStep"] = it
    }

    fun storedStatus(): TwoStepStatus? = telegram.memory["twoStep"]

    suspend fun verify(password: String) {
        proven(password) { check, _ -> telegram.client().invoke(GetPasswordSettings(check)) }
    }

    suspend fun create(password: String, hint: String?, email: String?): TwoStepStatus =
        update(null, password, hint, email)

    suspend fun change(current: String, password: String, hint: String?): TwoStepStatus =
        update(current, password, hint, null)

    suspend fun setRecoveryEmail(current: String, email: String): TwoStepStatus = proven(current) { check, _ ->
        submit(PasswordInputSettings(email = email), check)
    }

    suspend fun remove(current: String): TwoStepStatus = proven(current) { check, _ ->
        telegram.client().invoke(
            UpdatePasswordSettings(check, PasswordInputSettings(newAlgo = PasswordKdfAlgoUnknown, newPasswordHash = ByteArray(0), hint = "", email = "")),
        )
        status()
    }

    suspend fun confirmEmail(code: String): TwoStepStatus {
        telegram.client().invoke(ConfirmPasswordEmail(code))
        return status()
    }

    suspend fun resendCode() {
        telegram.client().invoke(ResendPasswordEmail)
    }

    suspend fun cancelEmail(): TwoStepStatus {
        telegram.client().invoke(CancelPasswordEmail)
        return status()
    }

    suspend fun requestRecovery(): String = telegram.client().invoke(RequestPasswordRecovery).emailPattern

    suspend fun recover(code: String): TwoStepStatus {
        telegram.client().invoke(RecoverPassword(code))
        return status()
    }

    private suspend fun update(current: String?, password: String, hint: String?, email: String?): TwoStepStatus = proven(current) { check, state ->
        val algo = state.newAlgo as? PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow
            ?: throw PasswordAlgorithmException("unsupported password algorithm ${state.newAlgo}")
        val (renewed, hash) = withContext(Dispatchers.Default) { PasswordHash.create(algo, password) }
        submit(PasswordInputSettings(newAlgo = renewed, newPasswordHash = hash, hint = hint.orEmpty(), email = email), check)
    }

    private suspend fun submit(settings: PasswordInputSettings, check: TypeInputCheckPasswordSRP): TwoStepStatus {
        var length: Int? = null
        try {
            telegram.client().invoke(UpdatePasswordSettings(check, settings))
        } catch (e: RpcException) {
            length = e.unconfirmedCodeLength() ?: throw e
        }
        return status().let { if (it.unconfirmedEmail != null) it.copy(codeLength = length) else it }
    }

    private suspend fun <T> proven(password: String?, block: suspend (TypeInputCheckPasswordSRP, Password) -> T): T {
        var attempt = 0
        while (true) {
            val state = telegram.client().invoke(GetPassword)
            val check = if (password == null || !state.hasPassword) InputCheckPasswordEmpty else withContext(Dispatchers.Default) { passwordCheck(state, password) }
            try {
                return block(check, state)
            } catch (e: RpcException) {
                if (e.errorMessage != "SRP_ID_INVALID" || ++attempt > 1) throw e
            }
        }
    }

    private fun statusOf(state: Password) = TwoStepStatus(
        enabled = state.hasPassword,
        hint = state.hint?.takeIf { it.isNotBlank() },
        hasRecovery = state.hasRecovery,
        unconfirmedEmail = state.emailUnconfirmedPattern,
    )
}
