package cn.ellerotta.core.ui.components.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.lottie.Lottie
import cn.ellerotta.core.lottie.rememberLottieResource
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.theme.crossfade
import kotlinx.coroutines.delay

private val appNames = listOf(
    "miogram",
    "ミオグラム",
    "миограм",
    "米奥格拉姆",
    "미오그램",
)

private val bounceFrames = floatArrayOf(24f, 192f, 215f, 234f, 361f, 383f, 408f, 540f)

@Composable
fun StartScreen(
    testMode: Boolean,
    busy: Boolean,
    onStartAuth: () -> Unit,
    serverSheet: @Composable (onDismiss: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    onQrLogin: () -> Unit = {},
) {
    val inspecting = LocalInspectionMode.current
    val contentProgress = remember { Animatable(if (inspecting) 1f else 0f) }
    val hapticFeedback = LocalHapticFeedback.current
    val background = rememberLottieResource(R.raw.core_ui_welcome_bg)
    val themedColors = mapOf(
        ".primaryContainer" to MaterialTheme.colorScheme.primaryContainer,
        ".secondaryContainer" to MaterialTheme.colorScheme.secondaryContainer,
    )
    var lastTriggeredIndex by remember { mutableIntStateOf(-1) }
    var lastFrame by remember { mutableFloatStateOf(0f) }
    var showServer by rememberSaveable { mutableStateOf(false) }

    var nameIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2400)
            nameIndex = (nameIndex + 1) % appNames.size
        }
    }

    val entry = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(Unit) {
        delay(400)
        contentProgress.animateTo(1f, animationSpec = entry)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .clipToBounds(),
    ) {
        Lottie(
            data = background,
            layerColors = themedColors,
            resolution = 0.5f,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 3f
                    scaleY = 3f
                },
            onFrame = { frame ->
                if (frame < lastFrame) lastTriggeredIndex = -1
                lastFrame = frame
                val reached = bounceFrames.indexOfLast { frame >= it }
                if (reached > lastTriggeredIndex) {
                    lastTriggeredIndex = reached
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            },
        )

        if (testMode) {
            AssistChip(
                onClick = { showServer = true },
                label = { Text(stringResource(R.string.welcome_test_servers)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_science), null, Modifier.size(18.dp)) },
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp).testTag("testServers"),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .graphicsLayer {
                    alpha = contentProgress.value
                    translationY = (1f - contentProgress.value) * 24.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = nameIndex,
                transitionSpec = {
                    (slideInHorizontally(slide) { it } + fadeIn(fade))
                        .togetherWith(slideOutHorizontally(slide) { -it } + fadeOut(fade))
                        .using(SizeTransform(clip = false))
                },
                contentAlignment = Alignment.Center,
                label = "appNameCycle",
                modifier = Modifier.padding(bottom = 32.dp),
            ) { idx ->
                Text(
                    text = appNames[idx],
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val serverLabel = stringResource(R.string.welcome_server)
                FilledTonalIconButton(
                    onClick = { showServer = true },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier
                        .size(width = 120.dp, height = 96.dp)
                        .semantics { contentDescription = serverLabel }
                        .testTag("server"),
                ) {
                    Icon(painterResource(R.drawable.ic_tune), contentDescription = null, modifier = Modifier.size(36.dp))
                }

                val startLabel = stringResource(R.string.welcome_start)
                Button(
                    onClick = onStartAuth,
                    enabled = !busy,
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.LargeContainerHeight),
                    modifier = Modifier
                        .weight(0.75f)
                        .height(96.dp)
                        .semantics { contentDescription = startLabel }
                        .testTag("start"),
                ) {
                    val motion = MaterialTheme.motionScheme
                    AnimatedContent(busy, transitionSpec = { motion.crossfade() }, label = "start") { loading ->
                        if (loading) {
                            LoadingIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }

            TextButton(onClick = onQrLogin, enabled = !busy, modifier = Modifier.padding(top = 12.dp).testTag("qrLogin")) {
                Icon(painterResource(R.drawable.ic_qr_code), null, Modifier.size(20.dp))
                Text(stringResource(R.string.welcome_qr), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    if (showServer) serverSheet { showServer = false }
}
