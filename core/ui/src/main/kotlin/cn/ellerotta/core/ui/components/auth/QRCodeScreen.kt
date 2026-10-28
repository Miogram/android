package cn.ellerotta.core.ui.components.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R

@Composable
fun QRCodeScreen(url: String?, onBack: () -> Unit, onSwitchToPhone: () -> Unit, modifier: Modifier = Modifier) {
    AuthScreenLayout(modifier = modifier, onBack = onBack) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_qr_code),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.qr_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }.testTag("authTitle"),
        )
        Spacer(Modifier.height(20.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            InstructionStep(number = 1, text = stringResource(R.string.qr_step_open))
            InstructionStep(number = 2, text = stringResource(R.string.qr_step_devices))
            InstructionStep(number = 3, text = stringResource(R.string.qr_step_scan))
        }
        Spacer(Modifier.height(24.dp))
        Surface(
            shape = MaterialTheme.shapes.extraLargeIncreased,
            color = Color.White,
            modifier = Modifier.size(260.dp),
        ) {
            val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(
                targetState = url,
                transitionSpec = {
                    (fadeIn(effects) + scaleIn(spatial, initialScale = 0.85f)) togetherWith fadeOut(effects)
                },
                contentAlignment = Alignment.Center,
                label = "qr",
            ) { current ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                ) {
                    if (current != null) {
                        QrCode(
                            text = current,
                            description = stringResource(R.string.qr_code_description),
                            color = QR_INK,
                            accent = lerp(MaterialTheme.colorScheme.primary, QR_INK, 0.55f),
                            modifier = Modifier.size(212.dp).testTag("qrCode"),
                        )
                    } else {
                        ContainedLoadingIndicator(Modifier.testTag("qrLoading"))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onSwitchToPhone,
            shapes = ButtonShapes(shape = ButtonDefaults.shape, pressedShape = ButtonDefaults.pressedShape),
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonDefaults.MediumContainerHeight)
                .testTag("usePhone"),
        ) {
            Icon(painterResource(R.drawable.ic_smartphone), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.qr_use_phone), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun InstructionStep(number: Int, text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val QR_INK = Color(0xFF15171C)
