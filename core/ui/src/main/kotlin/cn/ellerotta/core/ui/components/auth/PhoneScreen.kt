package cn.ellerotta.core.ui.components.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.theme.crossfade
import kotlin.random.Random
import kotlinx.coroutines.delay

@Composable
fun PhoneScreen(testMode: Boolean, busy: Boolean, onBack: () -> Unit, onSendCode: (String) -> Unit) {
    val context = LocalContext.current
    val numbers = remember { PhoneNumbers.get(context) }
    val locale = LocalConfiguration.current.locales[0]
    val keyboard = LocalSoftwareKeyboardController.current
    val testName = stringResource(R.string.phone_test_country)
    var countryCode by rememberSaveable { mutableStateOf(if (testMode) PhoneNumbers.TEST_CODE.toString() else numbers.defaultCode(context)) }
    var nationalDigits by rememberSaveable { mutableStateOf(if (testMode) "66" else "") }
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var isFieldFocused by remember { mutableStateOf(false) }
    val codeFocusRequester = remember { FocusRequester() }
    val phoneFocusRequester = remember { FocusRequester() }
    val country = countryCode.toIntOrNull()?.let { numbers.country(it, locale, testName) }
    val isFormValid = numbers.isPossible(countryCode, nationalDigits)
    val submit = {
        if (isFormValid && !busy) {
            keyboard?.hide()
            confirming = true
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        phoneFocusRequester.requestFocus()
        keyboard?.show()
    }

    AuthScreenLayout(onBack = onBack) {
        AuthHeader(
            title = stringResource(R.string.phone_title),
            subtitle = stringResource(if (testMode) R.string.phone_subtitle_test else R.string.phone_subtitle),
        )

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable { picking = true }
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp)
                .testTag("country"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val motion = MaterialTheme.motionScheme
            AnimatedContent(country, transitionSpec = { motion.crossfade() }, label = "country") { shown ->
                Text(
                    shown?.name ?: stringResource(R.string.phone_country_unknown),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(painterResource(R.drawable.ic_expand_more), null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))

        PhoneCompositeField(
            numbers = numbers,
            flagEmoji = country?.flag.orEmpty(),
            countryCode = countryCode,
            nationalDigits = nationalDigits,
            isFieldFocused = isFieldFocused,
            onCountryCodeChange = { input ->
                val (newCode, overflow) = numbers.splitCode(input.filter(Char::isDigit))
                countryCode = newCode
                if (overflow.isNotEmpty()) {
                    nationalDigits = (overflow + nationalDigits).take(MAX_NATIONAL)
                    phoneFocusRequester.requestFocus()
                }
            },
            onNationalDigitsChange = { nationalDigits = it.take(MAX_NATIONAL) },
            onCountryCodeSubmit = { if (countryCode.isNotEmpty()) phoneFocusRequester.requestFocus() },
            onPhoneSubmit = submit,
            onFlagClick = { picking = true },
            onFieldFocusChange = { isFieldFocused = it },
            codeFocusRequester = codeFocusRequester,
            phoneFocusRequester = phoneFocusRequester,
            modifier = Modifier.fillMaxWidth(),
        )

        if (testMode) {
            Spacer(Modifier.height(16.dp))
            AssistChip(
                onClick = {
                    countryCode = PhoneNumbers.TEST_CODE.toString()
                    nationalDigits = "66" + Random.nextInt(1, 4) + Random.nextInt(0, 10_000).toString().padStart(4, '0')
                },
                label = { Text(stringResource(R.string.phone_random_test)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_science), null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("testNumber"),
            )
        }

        Spacer(Modifier.height(24.dp))

        ContinueButton(
            enabled = isFormValid,
            busy = busy,
            label = stringResource(R.string.phone_send_code),
            onClick = submit,
            modifier = Modifier.testTag("sendCode"),
        )
    }

    if (picking) {
        CountryPicker(
            countries = remember(locale) { numbers.countries(locale) },
            onPick = { picked ->
                countryCode = picked.code.toString()
                picking = false
                phoneFocusRequester.requestFocus()
            },
            onDismiss = { picking = false },
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.phone_confirm_title)) },
            text = {
                Text(
                    numbers.international(countryCode, nationalDigits),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("confirmNumber"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onSendCode(countryCode + nationalDigits)
                    },
                    modifier = Modifier.testTag("confirmPhone"),
                ) { Text(stringResource(R.string.phone_confirm_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }, modifier = Modifier.testTag("editPhone")) { Text(stringResource(R.string.phone_confirm_edit)) }
            },
        )
    }
}

@Composable
private fun PhoneCompositeField(
    numbers: PhoneNumbers,
    flagEmoji: String,
    countryCode: String,
    nationalDigits: String,
    isFieldFocused: Boolean,
    onCountryCodeChange: (String) -> Unit,
    onNationalDigitsChange: (String) -> Unit,
    onCountryCodeSubmit: () -> Unit,
    onPhoneSubmit: () -> Unit,
    onFlagClick: () -> Unit,
    onFieldFocusChange: (Boolean) -> Unit,
    codeFocusRequester: FocusRequester,
    phoneFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isFieldFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val inputStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    val hasFlag = flagEmoji.isNotEmpty()

    val flagAlpha by animateFloatAsState(
        targetValue = if (hasFlag) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "flagAlpha",
    )
    val flagScale by animateFloatAsState(
        targetValue = if (hasFlag) 1f else 0.3f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "flagScale",
    )
    val codeFieldWidth by animateDpAsState(
        targetValue = if (countryCode.isEmpty()) 16.dp else (countryCode.length * 11 + 8).dp.coerceAtMost(48.dp),
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>(),
        label = "codeWidth",
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, borderColor),
        modifier = modifier.height(56.dp),
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, end = 4.dp)
                    .graphicsLayer {
                        alpha = flagAlpha
                        scaleX = flagScale
                        scaleY = flagScale
                    }
                    .clickable(enabled = hasFlag, onClick = onFlagClick),
                contentAlignment = Alignment.Center,
            ) {
                if (hasFlag) Text(text = flagEmoji, style = MaterialTheme.typography.titleMedium)
            }

            Text(text = "+", style = inputStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val codeLabel = stringResource(R.string.phone_country_code)
            BasicTextField(
                value = countryCode,
                onValueChange = onCountryCodeChange,
                textStyle = inputStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { onCountryCodeSubmit() }),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .width(codeFieldWidth)
                    .padding(horizontal = 2.dp)
                    .focusRequester(codeFocusRequester)
                    .onFocusChanged { onFieldFocusChange(it.isFocused) }
                    .semantics { contentDescription = codeLabel }
                    .testTag("countryCode"),
            )

            Surface(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .width(2.dp)
                    .height(30.dp),
            ) { }

            val formatted = numbers.format(countryCode, nationalDigits)
            val mask = numbers.mask(countryCode)
            val numberLabel = stringResource(R.string.phone_number)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = TextFieldValue(formatted, TextRange(formatted.length)),
                    onValueChange = { input ->
                        val digits = input.text.filter(Char::isDigit)
                        onNationalDigitsChange(if (input.text.length < formatted.length && digits == nationalDigits) digits.dropLast(1) else digits)
                    },
                    textStyle = inputStyle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onPhoneSubmit() }),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { field ->
                        Box {
                            if (mask != null && mask.length > formatted.length) {
                                Text(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(color = Color.Transparent)) { append(formatted) }
                                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal)) {
                                            append(mask.substring(formatted.length))
                                        }
                                    },
                                    style = inputStyle,
                                    maxLines = 1,
                                )
                            }
                            field()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(phoneFocusRequester)
                        .onFocusChanged { onFieldFocusChange(it.isFocused) }
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Backspace && nationalDigits.isEmpty()) {
                                codeFocusRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        }
                        .semantics { contentDescription = numberLabel }
                        .testTag("phone"),
                )
            }
        }
    }
}

private const val MAX_NATIONAL = 15
