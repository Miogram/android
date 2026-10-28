package cn.ellerotta.core.ui.components.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewSheetTheme
import cn.ellerotta.core.ui.components.common.PreviewTheme

@MioPreviews
@Composable
internal fun StartScreenPreview() {
    PreviewTheme {
        StartScreen(testMode = false, busy = false, onStartAuth = {}, serverSheet = {})
    }
}

@MioPreviews
@Composable
internal fun StartScreenTestServersPreview() {
    PreviewTheme {
        StartScreen(testMode = true, busy = true, onStartAuth = {}, serverSheet = {})
    }
}

@MioPreviews
@Composable
internal fun PhoneScreenEmptyPreview() {
    PreviewTheme {
        PhoneScreen(testMode = false, busy = false, onBack = {}, onSendCode = {})
    }
}

@MioPreviews
@Composable
internal fun PhoneScreenFilledPreview() {
    PreviewTheme {
        PhoneScreen(testMode = true, busy = false, onBack = {}, onSendCode = {})
    }
}

@MioPreviews
@Composable
internal fun CodeEntryAppPreview() {
    PreviewTheme {
        CodeEntryScreen(
            request = AuthPreviewData.appCode,
            testMode = false,
            busy = false,
            wrongCount = 0,
            accepted = false,
            onBack = {},
            onCode = {},
            onResend = {},
        )
    }
}

@MioPreviews
@Composable
internal fun CodeEntrySmsPreview() {
    PreviewTheme {
        CodeEntryScreen(
            request = remember { AuthPreviewData.smsCode(testNumber = true) },
            testMode = true,
            busy = false,
            wrongCount = 0,
            accepted = false,
            onBack = {},
            onCode = {},
            onResend = {},
        )
    }
}

@MioPreviews
@Composable
internal fun CodeEntryMissedCallPreview() {
    PreviewTheme {
        CodeEntryScreen(
            request = AuthPreviewData.missedCallCode,
            testMode = false,
            busy = false,
            wrongCount = 0,
            accepted = false,
            onBack = {},
            onCode = {},
            onResend = {},
        )
    }
}

@MioPreviews
@Composable
internal fun CodeEntryErrorPreview() {
    PreviewTheme {
        CodeEntryScreen(
            request = remember { AuthPreviewData.smsCode() },
            testMode = false,
            busy = false,
            wrongCount = 1,
            accepted = false,
            onBack = {},
            onCode = {},
            onResend = {},
        )
    }
}

@MioPreviews
@Composable
internal fun PasswordHintPreview() {
    PreviewTheme {
        PasswordScreen(hint = AuthPreviewData.PASSWORD_HINT, busy = false, wrongCount = 0, onBack = {}, onCheckPassword = {})
    }
}

@MioPreviews
@Composable
internal fun PasswordErrorPreview() {
    PreviewTheme {
        PasswordScreen(hint = null, busy = false, wrongCount = 1, onBack = {}, onCheckPassword = {})
    }
}

@MioPreviews
@Composable
internal fun QrLoginPreview() {
    PreviewTheme {
        QRCodeScreen(url = AuthPreviewData.QR_URL, onBack = {}, onSwitchToPhone = {})
    }
}

@MioPreviews
@Composable
internal fun QrLoginLoadingPreview() {
    PreviewTheme {
        QRCodeScreen(url = null, onBack = {}, onSwitchToPhone = {})
    }
}

@MioPreviews
@Composable
internal fun SignUpPreview() {
    PreviewTheme {
        SignUpScreen(busy = false, onBack = {}, onSignUp = { _, _ -> })
    }
}

@MioPreviews
@Composable
internal fun CountryPickerPreview() {
    PreviewSheetTheme {
        CountryList(countries = AuthPreviewData.countries, onPick = {})
    }
}

@MioPreviews
@Composable
internal fun ServerSheetPreview() {
    PreviewSheetTheme {
        ServerSettings(
            apiId = AuthPreviewData.API_ID,
            apiHash = AuthPreviewData.API_HASH,
            testMode = true,
            config = AuthPreviewData.config,
            version = AuthPreviewData.VERSION,
            onCredentials = { _, _ -> },
            onOpenLog = {},
        )
    }
}

@MioPreviews
@Composable
internal fun ServerSettingsPreview() {
    PreviewSheetTheme {
        ServerSettings(
            apiId = "",
            apiHash = "",
            testMode = false,
            config = null,
            version = AuthPreviewData.VERSION,
            onCredentials = { _, _ -> },
            onOpenLog = {},
        )
    }
}

@MioPreviews
@Composable
internal fun CodeNotReceivedDialogPreview() {
    PreviewTheme {
        CodeNotReceivedDialog(onBackToCodeEntry = {}, onChangeNumber = {})
    }
}

@MioPreviews
@Composable
internal fun UserResolutionFailedDialogPreview() {
    PreviewTheme {
        UserResolutionFailedDialog(message = AuthPreviewData.RESOLUTION_FAILURE, onRetry = {}, onCloseApp = {})
    }
}

@MioPreviews
@Composable
internal fun InlineErrorPreview() {
    PreviewTheme {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AuthPreviewData.errors.forEach { error -> InlineError(stringResource(error)) }
        }
    }
}
