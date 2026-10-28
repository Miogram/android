package cn.ellerotta.core.ui.components.chats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import androidx.compose.ui.util.lerp as lerpFloat

enum class ChatsWindow(val tag: String) { Chats("openChats"), Contacts("openContacts"), Settings("openSettings") }

@Composable
internal fun ChatsFloatingToolbar(
    currentWindow: ChatsWindow,
    onWindowSelected: (ChatsWindow) -> Unit,
    onOpenArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.padding(bottom = 8.dp),
        shape = RoundedCornerShape(100),
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.surface,
            toolbarContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        WindowSwitcher(currentWindow = currentWindow, onWindowSelected = onWindowSelected, onOpenArchive = onOpenArchive)
    }
}

@Composable
private fun WindowSwitcher(
    currentWindow: ChatsWindow,
    onWindowSelected: (ChatsWindow) -> Unit,
    onOpenArchive: () -> Unit,
) {
    val windows = ChatsWindow.entries
    val selectedIndex = windows.indexOf(currentWindow).coerceAtLeast(0)
    val animatedSelection = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "window_switcher_selection",
    )

    val pillColor = MaterialTheme.colorScheme.primaryContainer
    val selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer
    val unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val spacingPx = with(density) { 6.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val archiveLabel = stringResource(R.string.chats_open_archive)

    Box(modifier = Modifier.padding(6.dp)) {
        SubcomposeLayout { _ ->
            val tabPlaceables = windows.mapIndexed { index, window ->
                subcompose("tab_$index") {
                    val distance = abs(index - animatedSelection.value).coerceIn(0f, 1f)
                    val iconTint = lerp(unselectedIconColor, selectedIconColor, 1f - distance)
                    val label = stringResource(
                        when (window) {
                            ChatsWindow.Chats -> R.string.chats_title
                            ChatsWindow.Contacts -> R.string.contacts_title
                            ChatsWindow.Settings -> R.string.profile_settings
                        },
                    )
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(100))
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onLongClickLabel = archiveLabel.takeIf { window == ChatsWindow.Chats },
                                onLongClick = if (window == ChatsWindow.Chats) {
                                    {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onOpenArchive()
                                    }
                                } else {
                                    null
                                },
                            ) {
                                if (window != currentWindow) onWindowSelected(window)
                            }
                            .padding(horizontal = 24.dp)
                            .semantics {
                                contentDescription = label
                                selected = window == currentWindow
                                if (window == ChatsWindow.Chats) {
                                    customActions = listOf(CustomAccessibilityAction(archiveLabel) { onOpenArchive(); true })
                                }
                            }
                            .testTag(window.tag),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(
                                when (window) {
                                    ChatsWindow.Chats -> if (window == currentWindow) R.drawable.ic_chat_filled else R.drawable.ic_chat
                                    ChatsWindow.Contacts -> if (window == currentWindow) R.drawable.ic_group_filled else R.drawable.ic_group
                                    ChatsWindow.Settings -> if (window == currentWindow) R.drawable.ic_settings_filled else R.drawable.ic_settings
                                },
                            ),
                            contentDescription = null,
                            tint = iconTint,
                        )
                    }
                }.first().measure(Constraints())
            }

            val tabLefts = FloatArray(tabPlaceables.size)
            val tabWidths = FloatArray(tabPlaceables.size)
            var xAccum = 0f
            tabPlaceables.forEachIndexed { i, placeable ->
                tabLefts[i] = xAccum
                tabWidths[i] = placeable.width.toFloat()
                xAccum += placeable.width.toFloat() + spacingPx
            }

            val totalWidth = if (tabPlaceables.isEmpty()) 0 else tabPlaceables.sumOf { it.width } + (spacingPx * (tabPlaceables.size - 1)).toInt()
            val heightPx = tabPlaceables.maxOfOrNull { it.height } ?: 0

            val last = (tabPlaceables.size - 1).coerceAtLeast(0)
            val selectionPage = animatedSelection.value.coerceIn(0f, last.toFloat())
            val currentIndex = floor(selectionPage).toInt().coerceIn(0, last)
            val targetIndex = ceil(selectionPage).toInt().coerceIn(0, last)
            val fraction = selectionPage - currentIndex

            val pillLeft = if (tabPlaceables.isEmpty()) 0f else lerpFloat(tabLefts[currentIndex], tabLefts[targetIndex], fraction)
            val pillWidth = if (tabPlaceables.isEmpty()) 0f else lerpFloat(tabWidths[currentIndex], tabWidths[targetIndex], fraction)

            val pillPlaceable = subcompose("pill") {
                Box(Modifier.clip(CircleShape).background(pillColor))
            }.first().measure(Constraints.fixed(width = pillWidth.toInt().coerceAtLeast(0), height = heightPx.coerceAtLeast(0)))

            layout(totalWidth, heightPx) {
                pillPlaceable.placeRelative(pillLeft.toInt(), 0)
                var x = 0
                tabPlaceables.forEach { placeable ->
                    placeable.placeRelative(x, (heightPx - placeable.height) / 2)
                    x += placeable.width + spacingPx.toInt()
                }
            }
        }
    }
}
