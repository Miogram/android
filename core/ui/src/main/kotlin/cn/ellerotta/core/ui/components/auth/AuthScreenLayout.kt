package cn.ellerotta.core.ui.components.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.BackPill
import cn.ellerotta.core.ui.components.common.TopBarTokens
import cn.ellerotta.core.ui.theme.crossfade
import kotlinx.coroutines.delay

private val AuthScreenPadding = 24.dp
private val AuthScreenBottomPadding = 32.dp
private const val ENTRY_DELAY_MS = 300L
private val ENTRY_TRANSLATION = 24.dp

@Composable
fun AuthScreenLayout(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inspecting = LocalInspectionMode.current
    val contentProgress = remember { Animatable(if (inspecting) 1f else 0f) }
    val entrySpec: AnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatialSpec()

    LaunchedEffect(Unit) {
        delay(ENTRY_DELAY_MS)
        contentProgress.animateTo(1f, animationSpec = entrySpec)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .clipToBounds(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = AuthScreenPadding)
                .padding(top = TopBarTokens.EdgeInset + TopBarTokens.PillSize, bottom = AuthScreenBottomPadding)
                .verticalScroll(rememberScrollState(), reverseScrolling = true)
                .graphicsLayer {
                    alpha = contentProgress.value
                    translationY = (1f - contentProgress.value) * ENTRY_TRANSLATION.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
        if (onBack != null) {
            BackPill(onBack, Modifier.statusBarsPadding().padding(start = TopBarTokens.EdgeInset, top = TopBarTokens.EdgeInset))
        }
    }
}

@Composable
internal fun AuthHeader(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading() }.testTag("authTitle"),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(32.dp))
}

@Composable
internal fun ContinueButton(
    enabled: Boolean,
    busy: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { if (enabled && !busy) onClick() },
        enabled = enabled || busy,
        shapes = ButtonDefaults.shapesFor(ButtonDefaults.LargeContainerHeight),
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .semantics { contentDescription = label },
    ) {
        val motion = MaterialTheme.motionScheme
        AnimatedContent(busy, transitionSpec = { motion.crossfade() }, label = "continue") { loading ->
            if (loading) {
                LoadingIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, modifier = Modifier.size(36.dp))
            }
        }
    }
}
