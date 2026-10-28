package cn.ellerotta.core.ui.components.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.CodeRequest
import cn.ellerotta.core.model.CodeType
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.shake
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut
import java.util.Locale
import kotlinx.coroutines.delay

enum class CodeCellsState { Typing, Wrong, Right }

private const val INACTIVITY_TIMEOUT_MS = 60_000L

@Composable
fun CodeEntryScreen(
    request: CodeRequest?,
    testMode: Boolean,
    busy: Boolean,
    wrongCount: Int,
    accepted: Boolean,
    onBack: () -> Unit,
    onCode: (String) -> Unit,
    onResend: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val codeLength = request?.codeLength ?: 5
    var code by rememberSaveable { mutableStateOf("") }
    var cells by remember { mutableStateOf(CodeCellsState.Typing) }
    var showWrong by remember { mutableStateOf(false) }
    var showCodeNotReceivedDialog by rememberSaveable { mutableStateOf(false) }
    var interactions by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val phone = remember(request?.phone) {
        request?.phone?.let { digits ->
            val numbers = PhoneNumbers.get(context)
            val (countryCode, national) = numbers.splitCode(digits)
            numbers.international(countryCode, national)
        }.orEmpty()
    }
    val onUserInteraction = { interactions++ }

    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(interactions, showCodeNotReceivedDialog) {
        if (showCodeNotReceivedDialog) return@LaunchedEffect
        delay(INACTIVITY_TIMEOUT_MS)
        showCodeNotReceivedDialog = true
    }
    LaunchedEffect(code) {
        if (code.length == codeLength && cells == CodeCellsState.Typing) {
            keyboard?.hide()
            onCode(code)
        }
    }
    LaunchedEffect(wrongCount) {
        if (wrongCount == 0) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
        cells = CodeCellsState.Wrong
        showWrong = true
        delay(850)
        while (code.isNotEmpty()) {
            code = code.dropLast(1)
            delay(50)
        }
        cells = CodeCellsState.Typing
        focusRequester.requestFocus()
        keyboard?.show()
        delay(750)
        showWrong = false
    }
    LaunchedEffect(accepted) {
        if (accepted) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            cells = CodeCellsState.Right
        }
    }

    val type = request?.type ?: CodeType.Unknown
    AuthScreenLayout(onBack = onBack) {
        AuthHeader(
            title = stringResource(
                when (type) {
                    CodeType.App -> R.string.code_title_app
                    CodeType.Call, CodeType.FlashCall, CodeType.MissedCall -> R.string.code_title_call
                    CodeType.Email -> R.string.code_title_email
                    CodeType.Fragment -> R.string.code_title_fragment
                    else -> R.string.code_title
                },
            ),
            subtitle = when (type) {
                CodeType.App -> stringResource(R.string.code_text_app)
                CodeType.Sms -> stringResource(R.string.code_text_sms, phone)
                CodeType.Call, CodeType.FlashCall -> stringResource(R.string.code_text_call, phone)
                CodeType.MissedCall -> stringResource(R.string.code_text_missed_call, phone, codeLength)
                CodeType.Email -> stringResource(R.string.code_text_email)
                CodeType.Fragment -> stringResource(R.string.code_text_fragment, phone)
                CodeType.Unknown -> stringResource(R.string.code_text, phone)
            },
        )

        InlineError(if (showWrong) stringResource(R.string.error_code_invalid) else null, textModifier = Modifier.testTag("wrongCode"))
        if (showWrong) Spacer(Modifier.height(12.dp))

        val digitsLabel = stringResource(R.string.code_digits, code.length, codeLength)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .shake(wrongCount)
                .semantics { contentDescription = digitsLabel },
        ) {
            CodeBoxes(
                code = code,
                codeLength = codeLength,
                state = cells,
                onClick = {
                    onUserInteraction()
                    focusRequester.requestFocus()
                    keyboard?.show()
                },
            )
            BasicTextField(
                value = code,
                onValueChange = { value ->
                    if (cells == CodeCellsState.Wrong || busy) return@BasicTextField
                    onUserInteraction()
                    code = value.filter { it.isDigit() }.take(codeLength)
                },
                singleLine = true,
                cursorBrush = SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0f)
                    .focusRequester(focusRequester)
                    .semantics { contentType = ContentType.SmsOtpCode }
                    .testTag("code"),
            )
        }

        AnimatedVisibility(busy, enter = MaterialTheme.motionScheme.expandFadeIn(), exit = MaterialTheme.motionScheme.shrinkFadeOut()) {
            LoadingIndicator(Modifier.padding(top = 16.dp).size(48.dp))
        }

        Spacer(Modifier.height(24.dp))

        if (request != null) ResendLine(request, busy, onResend, onNoCode = { showCodeNotReceivedDialog = true })
        if (testMode && request != null) {
            val testCode = request.homeDc.toString().repeat(codeLength)
            AssistChip(
                onClick = {
                    onUserInteraction()
                    code = testCode
                },
                label = { Text(stringResource(R.string.code_test_hint, testCode)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_science), null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("testCode"),
            )
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                onUserInteraction()
                onBack()
            },
            enabled = !busy,
            modifier = Modifier.testTag("wrongNumber"),
        ) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.padding(start = 8.dp))
            Text(stringResource(R.string.code_wrong_number))
        }
    }

    if (showCodeNotReceivedDialog) {
        CodeNotReceivedDialog(
            onBackToCodeEntry = {
                showCodeNotReceivedDialog = false
                onUserInteraction()
            },
            onChangeNumber = {
                showCodeNotReceivedDialog = false
                onBack()
            },
        )
    }
}

@Composable
private fun CodeBoxes(code: String, codeLength: Int, state: CodeCellsState, onClick: () -> Unit) {
    val pulse = remember { Animatable(1f) }
    val fastSpatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val defaultSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(state) {
        if (state == CodeCellsState.Right) {
            pulse.animateTo(1.1f, fastSpatial)
            pulse.animateTo(1f, defaultSpatial)
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        for (i in 0 until codeLength) {
            DigitBox(
                char = code.getOrNull(i)?.toString() ?: "",
                isCursor = i == code.length && state == CodeCellsState.Typing,
                state = state,
            )
        }
    }
}

@Composable
private fun DigitBox(char: String, isCursor: Boolean, state: CodeCellsState) {
    val colors = MaterialTheme.colorScheme
    val isFilled = char.isNotEmpty()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            state == CodeCellsState.Wrong -> colors.errorContainer
            isCursor -> colors.primaryContainer
            else -> colors.surfaceContainerHigh
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "digitBg",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            state == CodeCellsState.Wrong -> colors.error
            state == CodeCellsState.Right -> colors.primary
            isCursor -> colors.primary
            isFilled -> colors.outline
            else -> colors.outlineVariant
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "digitBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isCursor || state != CodeCellsState.Typing) 2.dp else 1.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "digitBorderWidth",
    )

    val scaleAnim = remember { Animatable(1f) }
    val fastSpatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val defaultSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fastSpatialIntOffset = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()

    LaunchedEffect(isFilled) {
        if (isFilled) {
            scaleAnim.animateTo(1.1f, animationSpec = fastSpatial)
            scaleAnim.animateTo(1f, animationSpec = defaultSpatial)
        } else {
            scaleAnim.snapTo(1f)
        }
    }

    Surface(
        modifier = Modifier
            .size(width = 50.dp, height = 64.dp)
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
            }
            .border(width = borderWidth, color = borderColor, shape = MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        color = backgroundColor,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.clipToBounds()) {
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    (slideInVertically(animationSpec = fastSpatialIntOffset) { it } + fadeIn(animationSpec = fastEffects))
                        .togetherWith(slideOutVertically(animationSpec = fastSpatialIntOffset) { -it } + fadeOut(animationSpec = fastEffects))
                },
                contentAlignment = Alignment.Center,
                label = "digit",
            ) { digit ->
                Text(
                    text = digit,
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = when {
                        state == CodeCellsState.Wrong -> colors.onErrorContainer
                        isCursor -> colors.onPrimaryContainer
                        else -> colors.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun ResendLine(request: CodeRequest, busy: Boolean, onResend: () -> Unit, onNoCode: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(request) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val next = request.nextType
    if (next == null) {
        TextButton(onClick = onNoCode, modifier = Modifier.testTag("noCode")) { Text(stringResource(R.string.code_no_code_title)) }
        return
    }
    val left = ((request.timeoutSeconds ?: 0) - (now - request.sentAtMillis) / 1000).coerceAtLeast(0)
    val viaCall = next == CodeType.Call || next == CodeType.FlashCall || next == CodeType.MissedCall
    if (left > 0) {
        val clock = String.format(Locale.ROOT, "%d:%02d", left / 60, left % 60)
        Text(
            stringResource(if (viaCall) R.string.code_call_in else R.string.code_sms_in, clock),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("resendTimer"),
        )
    } else {
        TextButton(onClick = onResend, enabled = !busy, modifier = Modifier.testTag("resend")) {
            Text(stringResource(if (viaCall) R.string.code_resend_call else R.string.code_resend_sms))
        }
    }
}
