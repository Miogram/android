package app.miogram.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import app.miogram.feature.auth.AuthViewModel
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.auth.CodeEntryScreen
import cn.ellerotta.core.ui.components.auth.PasswordScreen
import cn.ellerotta.core.ui.components.auth.PhoneScreen
import cn.ellerotta.core.ui.components.auth.QRCodeScreen
import cn.ellerotta.core.ui.components.auth.ServerSheet
import cn.ellerotta.core.ui.components.auth.SignUpScreen
import cn.ellerotta.core.ui.components.auth.StartScreen
import cn.ellerotta.core.ui.components.auth.UserResolutionFailedDialog

internal fun EntryProviderScope<Route>.authEntries(activity: ComponentActivity, navigator: Navigator, version: String, back: () -> Unit) {
    entry<Route.Splash> {
        val auth = hiltViewModel<AuthViewModel>(activity)
        val state by auth.state.collectAsStateWithLifecycle()
        Box(Modifier.fillMaxSize())
        state.restoreError?.let { error ->
            UserResolutionFailedDialog(
                message = stringResource(R.string.auth_load_failed_text, error),
                onRetry = auth::retryRestore,
                onCloseApp = activity::finish,
            )
        }
    }
    entry<Route.Welcome> {
        val auth = hiltViewModel<AuthViewModel>(activity)
        val state by auth.state.collectAsStateWithLifecycle()
        Work(auth) { busy ->
            StartScreen(
                testMode = state.testMode,
                busy = busy,
                onStartAuth = auth::start,
                onQrLogin = auth::openQr,
                serverSheet = { dismiss ->
                    ServerSheet(state.apiId, state.apiHash, state.testMode, state.config, version, auth::setCredentials, onOpenLog = {
                        dismiss()
                        navigator.go(Route.Log)
                    }, onDismiss = dismiss)
                },
            )
        }
    }
    entry<Route.QrLogin> {
        val auth = hiltViewModel<AuthViewModel>(activity)
        val state by auth.state.collectAsStateWithLifecycle()
        DisposableEffect(Unit) {
            auth.startQr()
            onDispose { auth.stopQr() }
        }
        Work(auth) {
            QRCodeScreen(
                url = state.qrUrl,
                onBack = back,
                onSwitchToPhone = {
                    navigator.back()
                    auth.start()
                },
            )
        }
    }
    entry<Route.Phone> {
        val auth = hiltViewModel<AuthViewModel>(activity)
        Work(auth) { busy -> PhoneScreen(testMode = auth.state.value.testMode, busy = busy, onBack = back, onSendCode = auth::sendCode) }
    }
    entry<Route.Code> {
        val auth = hiltViewModel<AuthViewModel>(activity)
        val state by auth.state.collectAsStateWithLifecycle()
        Work(auth) { busy ->
            CodeEntryScreen(
                request = state.code,
                testMode = state.testMode,
                busy = busy,
                wrongCount = state.codeWrong,
                accepted = state.codeAccepted,
                onBack = back,
                onCode = auth::signIn,
                onResend = auth::resendCode,
            )
        }
    }
    entry<Route.Password> {
        val auth = hiltViewModel<AuthViewModel>(activity)
        val state by auth.state.collectAsStateWithLifecycle()
        Work(auth) { busy -> PasswordScreen(state.passwordHint, busy, state.passwordWrong, back, auth::checkPassword) }
    }
    entry<Route.SignUp> {
        val auth = hiltViewModel<AuthViewModel>(activity)
        Work(auth) { busy -> SignUpScreen(busy, back, auth::signUp) }
    }
}
