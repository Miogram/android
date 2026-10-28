package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import cn.ellerotta.core.model.BotButtonMark
import cn.ellerotta.core.model.mark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.text.font.FontWeight
import cn.ellerotta.core.model.BotCommand
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.AttachMenuBot
import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.LoginRequest
import cn.ellerotta.core.model.BotButtonStyle
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.ReplyKeyboard
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.CheckRow

class ChatBotActions(
    val onCallback: (messageId: Int, data: ByteArray) -> Unit,
    val onAnswerShown: () -> Unit,
    val onSwitchInline: (String) -> Unit,
    val onLogin: (messageId: Int, button: BotButton.LoginUrl) -> Unit = { _, _ -> },
    val onAnswerLogin: (accepted: Boolean?, writeAllowed: Boolean, sharePhone: Boolean) -> Unit = { _, _, _ -> },
    val onLoginOpened: () -> Unit = {},
    val onWebApp: (message: MessageItem, button: BotButton.WebApp, theme: String) -> Unit = { _, _, _ -> },
    val onWebAppData: (String) -> Unit = {},
    val onCloseWebApp: () -> Unit = {},
    val onMenuApp: (theme: String) -> Unit = {},
    val onAttachApp: (bot: AttachMenuBot, theme: String, writeAllowed: Boolean) -> Unit = { _, _, _ -> },
    val webAppDevice: WebAppDevice = WebAppDevice.None,
)

@Composable
internal fun KeyboardUnder(message: MessageItem, onButton: ((BotButton) -> Unit)?, emoji: InlineEmoji, widen: Boolean, bubble: @Composable () -> Unit) {
    if (message.buttons.isEmpty()) {
        bubble()
    } else {
        val content = @Composable {
            bubble()
            InlineKeyboard(message, onButton, emoji)
        }
        Layout(content) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0)
            val wanted = if (widen) measurables[1].maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(constraints.maxWidth) else 0
            val top = measurables[0].measure(loose.copy(minWidth = wanted))
            val keyboard = measurables[1].measure(Constraints.fixedWidth(top.width))
            val gap = KEYBOARD_GAP.roundToPx()
            layout(top.width, top.height + gap + keyboard.height) {
                top.place(0, 0)
                keyboard.place(0, top.height + gap)
            }
        }
    }
}

@Composable
internal fun InlineKeyboard(message: MessageItem, onButton: ((BotButton) -> Unit)?, emoji: InlineEmoji, modifier: Modifier = Modifier) {
    val last = message.buttons.lastIndex
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KEYBOARD_GAP)) {
        message.buttons.forEachIndexed { row, buttons ->
            Row(Modifier.fillMaxWidth().height(INLINE_ROW), horizontalArrangement = Arrangement.spacedBy(INLINE_BUTTON_GAP)) {
                buttons.forEachIndexed { column, button ->
                    val shape = RoundedCornerShape(
                        topStart = INLINE_CORNER,
                        topEnd = INLINE_CORNER,
                        bottomEnd = if (row == last && column == buttons.lastIndex) BOTTOM_CORNER else INLINE_CORNER,
                        bottomStart = if (row == last && column == 0) BOTTOM_CORNER else INLINE_CORNER,
                    )
                    InlineButton(
                        button, shape, emoji, enabled = !message.pending, onClick = { onButton?.invoke(button) },
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("button_${message.id}_${row}_$column"),
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineButton(button: BotButton, shape: Shape, emoji: InlineEmoji, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val active = enabled && button !is BotButton.Disabled
    GlassPill(
        shape,
        modifier.clickable(enabled = active, role = Role.Button, onClick = onClick),
        fill = fillOf(button.style),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp).alpha(if (active) 1f else DISABLED_ALPHA),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val document = button.icon?.let { emoji.documents[it] }
            val bytes = document?.let { emoji.files[it.key] }
            if (document != null) {
                Box(Modifier.size(ICON_SIZE)) {
                    if (bytes != null) StickerView(document, bytes, if (document.textColor) Modifier.tinted(Color.White) else Modifier, size = ICON_SIZE)
                }
            }
            Text(button.text, color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        markIcon(button.mark)?.let { icon ->
            Icon(painterResource(icon), null, Modifier.align(Alignment.TopEnd).padding(4.dp).size(12.dp), tint = Color.White)
        }
    }
}

private fun markIcon(mark: BotButtonMark?): Int? = when (mark) {
    BotButtonMark.Link -> R.drawable.ic_arrow_outward
    BotButtonMark.Share -> R.drawable.ic_forward
    BotButtonMark.Copy -> R.drawable.ic_content_copy
    BotButtonMark.Profile -> R.drawable.ic_person
    BotButtonMark.WebApp -> R.drawable.ic_crop_square
    BotButtonMark.Payment -> R.drawable.ic_credit_card
    null -> null
}

@Composable
private fun fillOf(style: BotButtonStyle): Color = styleFill(style, dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f)

internal fun styleFill(style: BotButtonStyle, dark: Boolean): Color = when (style) {
    BotButtonStyle.Primary -> Color(if (dark) 0xFF0050B3 else 0xFF0062D1)
    BotButtonStyle.Danger -> Color(if (dark) 0xFFB3141E else 0xFFD70015)
    BotButtonStyle.Success -> Color(if (dark) 0xFF1A6E2F else 0xFF1E7F36)
    BotButtonStyle.Default, BotButtonStyle.Link -> Color.Black.copy(alpha = PILL_ALPHA)
}

private val KEYBOARD_GAP = 2.dp
private val INLINE_ROW = 44.dp
private val INLINE_BUTTON_GAP = 4.dp
private val INLINE_CORNER = 8.dp
private val BOTTOM_CORNER = 18.dp
private val ICON_SIZE = 20.dp
private const val DISABLED_ALPHA = 0.5f

@Composable
internal fun ReplyKeyboardPanel(keyboard: ReplyKeyboard, onButton: (BotButton) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().testTag("botKeyboard")) {
        val fill = !keyboard.resize && keyboard.rows.size <= FILLED_ROWS
        val column = if (fill) Modifier.height(PANEL_HEIGHT) else Modifier.heightIn(max = PANEL_HEIGHT).verticalScroll(rememberScrollState())
        Column(column.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            keyboard.rows.forEachIndexed { row, buttons ->
                Row(if (fill) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    buttons.forEachIndexed { column, button ->
                        val size = if (fill) Modifier.weight(1f).fillMaxHeight() else Modifier.weight(1f).heightIn(min = 44.dp)
                        KeyboardButton(button, { onButton(button) }, size.testTag("keyboardButton_${row}_$column"))
                    }
                }
            }
        }
    }
}

@Composable
internal fun KeyboardButton(button: BotButton, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled && button !is BotButton.Disabled,
        shape = RoundedCornerShape(12.dp),
        colors = colorsOf(button.style),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = modifier,
    ) {
        Text(button.text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
        val icon = when (button) {
            is BotButton.Url, is BotButton.LoginUrl -> R.drawable.ic_link
            is BotButton.SwitchInline -> R.drawable.ic_forward
            is BotButton.Copy -> R.drawable.ic_content_copy
            is BotButton.UserProfile -> R.drawable.ic_person
            else -> null
        }
        icon?.let { Icon(painterResource(it), null, Modifier.padding(start = 4.dp).size(14.dp)) }
    }
}

@Composable
internal fun colorsOf(style: BotButtonStyle): ButtonColors = when (style) {
    BotButtonStyle.Default -> ButtonDefaults.filledTonalButtonColors()
    BotButtonStyle.Primary -> ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
    BotButtonStyle.Danger -> ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
    BotButtonStyle.Success -> ButtonDefaults.filledTonalButtonColors(
        containerColor = styleFill(BotButtonStyle.Success, dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f),
        contentColor = Color.White,
    )
    BotButtonStyle.Link -> ButtonDefaults.filledTonalButtonColors(containerColor = Color.Transparent, contentColor = bubbleColors().accent)
}

@Composable
internal fun OpenLinkDialog(url: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_open_link_title)) },
        text = { Text(url) },
        confirmButton = { TextButton(onClick = onOpen, modifier = Modifier.testTag("openLink")) { Text(stringResource(R.string.chat_open_link)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        modifier = Modifier.testTag("openLinkDialog"),
    )
}

@Composable
internal fun LoginDialog(request: LoginRequest, onAnswer: (accepted: Boolean?, writeAllowed: Boolean, sharePhone: Boolean) -> Unit) {
    var write by remember(request) { mutableStateOf(true) }
    var phone by remember(request) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onAnswer(null, false, false) },
        title = { Text(stringResource(R.string.chat_login_title, request.domain)) },
        text = {
            Column {
                Text(stringResource(R.string.chat_login_text, request.domain, request.bot))
                if (request.writeAccess) {
                    CheckRow(write, { write = it }, stringResource(R.string.chat_login_write, request.bot), "loginWrite")
                }
                if (request.phone) {
                    CheckRow(phone, { phone = it }, stringResource(R.string.chat_login_phone), "loginPhone")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(true, request.writeAccess && write, request.phone && phone) }, modifier = Modifier.testTag("loginAccept")) {
                Text(stringResource(R.string.chat_login_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(false, false, false) }, modifier = Modifier.testTag("loginDecline")) {
                Text(stringResource(R.string.chat_login_decline))
            }
        },
        modifier = Modifier.testTag("loginDialog"),
    )
}

@Composable
internal fun BotAlert(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("botAlertOk")) { Text(stringResource(R.string.common_ok)) } },
        modifier = Modifier.testTag("botAlert"),
    )
}

private val PANEL_HEIGHT = 260.dp
private const val FILLED_ROWS = 4

@Composable
internal fun BotCommandList(commands: List<BotCommand>, onSend: (BotCommand) -> Unit, onInsert: (BotCommand) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().testTag("botCommands")) {
        LazyColumn(Modifier.heightIn(max = COMMANDS_HEIGHT)) {
            items(commands, key = { it.text }) { command ->
                ListItem(
                    headlineContent = { Text(command.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = command.description.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                    trailingContent = {
                        IconButton(onClick = { onInsert(command) }, modifier = Modifier.testTag("insertCommand_${command.text}"), shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.chat_command_insert))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.clickable { onSend(command) }.testTag("command_${command.text}"),
                )
            }
        }
    }
}

private val COMMANDS_HEIGHT = 240.dp
