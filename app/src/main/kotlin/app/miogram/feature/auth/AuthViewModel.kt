package app.miogram.feature.auth

import androidx.lifecycle.viewModelScope
import app.miogram.data.AvatarStore
import app.miogram.data.BotDraftsStore
import app.miogram.data.EphemeralStore
import app.miogram.data.DialogsStore
import app.miogram.data.RecentSearches
import app.miogram.data.telegram.AuthRepository
import app.miogram.data.telegram.QrState
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.data.telegram.ProfileRepository
import app.miogram.data.telegram.StoriesRepository
import app.miogram.data.telegram.WallpapersRepository
import app.miogram.feature.common.WorkViewModel
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import app.miogram.push.PushHandler
import cn.ellerotta.core.model.CodeRequest
import cn.ellerotta.core.model.ConfigSummary
import cn.ellerotta.core.model.UiError
import cn.ellerotta.core.ui.components.common.DecodedPhotos
import cn.ellerotta.ktproto.session.RpcException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val apiId: String = "",
    val apiHash: String = "",
    val testMode: Boolean = true,
    val config: ConfigSummary? = null,
    val code: CodeRequest? = null,
    val codeWrong: Int = 0,
    val codeAccepted: Boolean = false,
    val passwordHint: String? = null,
    val passwordWrong: Int = 0,
    val qrUrl: String? = null,
    val restoreError: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val telegram: TelegramClientHolder,
    private val repository: AuthRepository,
    private val navigator: Navigator,
    private val avatarStore: AvatarStore,
    private val dialogsStore: DialogsStore,
    private val botDrafts: BotDraftsStore,
    private val ephemeral: EphemeralStore,
    private val push: PushHandler,
    private val wallpapers: WallpapersRepository,
    private val profiles: ProfileRepository,
    private val stories: StoriesRepository,
    private val recentSearches: RecentSearches,
    server: ServerConfig,
) : WorkViewModel() {
    private val stateFlow = MutableStateFlow(
        AuthState(apiId = server.apiId.takeIf { it != 0 }?.toString().orEmpty(), apiHash = server.apiHash, testMode = server.testMode),
    )
    val state: StateFlow<AuthState> = stateFlow.asStateFlow()

    private var prepared: Pair<Int, String>? = null

    init {
        viewModelScope.launch { restore() }
        viewModelScope.launch { repository.sessionEnded.collect { sessionEnded() } }
    }

    private suspend fun sessionEnded() {
        push.unregister()
        repository.forgetAccount()
        loggedOut()
        fail(UiError.SessionEnded)
    }

    private fun credentials(): Pair<Int, String>? {
        val id = state.value.apiId.trim().toIntOrNull() ?: return null
        val hash = state.value.apiHash.trim().ifEmpty { return null }
        return id to hash
    }

    private suspend fun restore() {
        val credentials = credentials()
        val authorized = if (credentials == null) {
            false
        } else {
            try {
                telegram.prepareOnce(credentials.first, credentials.second).also { prepared = credentials }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stateFlow.update { it.copy(restoreError = e.message ?: e::class.simpleName.orEmpty()) }
                return
            }
        }
        navigator.replaceAll(if (authorized) Route.Chats else Route.Welcome)
        if (prepared != null) warmUp()
    }

    fun retryRestore() {
        stateFlow.update { it.copy(restoreError = null) }
        viewModelScope.launch { restore() }
    }

    private fun warmUp() = viewModelScope.launch {
        runCatching { telegram.connect() }.onSuccess { config -> stateFlow.update { it.copy(config = config) } }
    }

    fun setCredentials(apiId: String, apiHash: String) {
        stateFlow.update { it.copy(apiId = apiId, apiHash = apiHash, config = null) }
        prepared = null
    }

    private suspend fun ready(): Boolean {
        val credentials = credentials() ?: return false.also { fail(UiError.MissingApiCredentials) }
        if (prepared != credentials) {
            telegram.prepare(credentials.first, credentials.second)
            prepared = credentials
        }
        val config = telegram.connect()
        stateFlow.update { it.copy(config = config) }
        return true
    }

    fun start() = work {
        if (ready()) navigator.go(Route.Phone)
    }

    fun openQr() = work {
        if (ready()) navigator.go(Route.QrLogin)
    }

    private var qrJob: Job? = null

    fun startQr() {
        qrJob?.cancel()
        stateFlow.update { it.copy(qrUrl = null) }
        qrJob = workWith {
            repository.qrLogin().collect { step ->
                when (step) {
                    is QrState.Code -> stateFlow.update { it.copy(qrUrl = step.url) }
                    is QrState.LoggedIn -> navigator.replaceAll(Route.Chats)
                    is QrState.PasswordRequired -> {
                        stateFlow.update { it.copy(passwordHint = step.hint, passwordWrong = 0) }
                        navigator.go(Route.Password)
                    }
                }
            }
        }
    }

    fun stopQr() {
        qrJob?.cancel()
        qrJob = null
    }

    fun sendCode(phone: String) = work {
        val request = repository.sendCode(phone)
        stateFlow.update { it.copy(code = request, codeWrong = 0, codeAccepted = false) }
        navigator.go(Route.Code)
    }

    fun resendCode() = work {
        val request = state.value.code ?: error("request a code first")
        stateFlow.update { it.copy(code = repository.resendCode(request)) }
    }

    fun signIn(code: String) = work {
        val request = state.value.code ?: error("request a code first")
        val result = try {
            repository.signIn(request, code)
        } catch (e: RpcException) {
            if (e.errorMessage != "PHONE_CODE_INVALID") throw e
            stateFlow.update { it.copy(codeWrong = it.codeWrong + 1) }
            return@work
        }
        if (result !is SignInResult.SignUpRequired) {
            stateFlow.update { it.copy(codeAccepted = true) }
            delay(ACCEPTED_PAUSE)
        }
        when (result) {
            is SignInResult.LoggedIn -> navigator.replaceAll(Route.Chats)
            SignInResult.SignUpRequired -> navigator.go(Route.SignUp)
            is SignInResult.PasswordRequired -> {
                stateFlow.update { it.copy(passwordHint = result.hint, passwordWrong = 0) }
                navigator.go(Route.Password)
            }
        }
    }

    fun checkPassword(password: String) = work {
        try {
            repository.checkPassword(password)
        } catch (e: RpcException) {
            if (e.errorMessage != "PASSWORD_HASH_INVALID") throw e
            stateFlow.update { it.copy(passwordWrong = it.passwordWrong + 1) }
            return@work
        }
        navigator.replaceAll(Route.Chats)
    }

    fun signUp(firstName: String, lastName: String) = work {
        val request = state.value.code ?: error("request a code first")
        repository.signUp(request, firstName, lastName)
        navigator.replaceAll(Route.Chats)
    }

    fun loggedOut() {
        avatarStore.clear()
        DecodedPhotos.clear()
        profiles.forget()
        dialogsStore.clear()
        botDrafts.clear()
        ephemeral.clear()
        wallpapers.forget()
        stories.clear()
        recentSearches.clear()
        stateFlow.update { it.copy(code = null, codeWrong = 0, codeAccepted = false, passwordHint = null) }
        navigator.replaceAll(Route.Welcome)
    }

    private companion object {
        const val ACCEPTED_PAUSE = 350L
    }
}
