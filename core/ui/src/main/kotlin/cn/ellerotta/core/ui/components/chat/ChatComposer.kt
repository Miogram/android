package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.AttachMenuBot
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.model.SendOptions
import cn.ellerotta.core.model.VoiceRecording
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
internal fun Composer(
    value: TextFieldValue,
    onValue: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    entities: List<TextEntity> = emptyList(),
    emoji: InlineEmoji = InlineEmoji(emptyMap(), emptyMap()),
    onFormat: (FormatAction) -> Unit = {},
    onAttachMedia: (() -> Unit)? = null,
    onAttachFile: (() -> Unit)? = null,
    attachMenu: AttachMenuActions = AttachMenuActions(),
    recording: VoiceRecording? = null,
    onVoiceStart: (() -> Unit)? = null,
    onVoiceStop: (Boolean) -> Unit = {},
    stickersOpen: Boolean = false,
    onToggleStickers: (() -> Unit)? = null,
    trailingHint: String? = null,
    placeholder: String? = null,
    botKeyboardOpen: Boolean = false,
    onToggleBotKeyboard: (() -> Unit)? = null,
    onArticle: (() -> Unit)? = null,
    articleDraft: String? = null,
    onOpenArticleDraft: () -> Unit = {},
    onDeleteArticleDraft: () -> Unit = {},
    sendReady: Boolean = false,
    menuButton: String? = null,
    onMenuButton: () -> Unit = {},
    attachBots: List<AttachMenuBot> = emptyList(),
    onAttachBot: (AttachMenuBot) -> Unit = {},
    onSendWith: ((SendOptions) -> Unit)? = null,
    onVoiceSendWith: ((SendOptions) -> Unit)? = null,
    whenOnline: Boolean = false,
) {
    var drag by remember { mutableFloatStateOf(0f) }
    var locked by remember { mutableStateOf(false) }
    LaunchedEffect(recording == null) { if (recording == null) locked = false }
    var hints by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(hints) {
        if (hints == 0) return@LaunchedEffect
        hint = true
        delay(HINT_MS)
        hint = false
    }
    val motion = MaterialTheme.motionScheme
    val effects = motion.fastEffectsSpec<Float>()
    val spatial = motion.fastSpatialSpec<Float>()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(hint, enter = motion.expandFadeIn(), exit = motion.shrinkFadeOut()) { VoiceHint() }
        AnimatedVisibility(!value.selection.collapsed && recording == null, enter = motion.expandFadeIn(), exit = motion.shrinkFadeOut()) { FormattingBar(onAction = onFormat) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            val shell = when {
                recording == null -> Shell.Field
                locked -> Shell.Locked
                else -> Shell.Held
            }
            AnimatedContent(
                shell,
                modifier = Modifier.weight(1f),
                transitionSpec = { (fadeIn(effects) + scaleIn(spatial, initialScale = 0.96f)) togetherWith (fadeOut(effects) + scaleOut(spatial, targetScale = 0.96f)) },
                label = "composerShell",
            ) { state ->
                when (state) {
                    Shell.Field -> ComposerPill(
                        value, onValue, entities, emoji, onAttachMedia, onAttachFile, attachMenu, stickersOpen, onToggleStickers, trailingHint, placeholder,
                        botKeyboardOpen, onToggleBotKeyboard, onArticle, articleDraft, onOpenArticleDraft, onDeleteArticleDraft,
                        menuButton, onMenuButton, attachBots, onAttachBot,
                    )
                    Shell.Held, Shell.Locked -> RecordingShell(
                        recording ?: VoiceRecording(0, 0f),
                        locked = state == Shell.Locked,
                        drag = drag,
                        onDiscard = { onVoiceStop(false) },
                    )
                }
            }
            val ready = value.text.isNotBlank() || articleDraft != null || sendReady
            val action = when {
                recording != null && locked -> Action.SendVoice
                ready && recording == null -> Action.Send
                onVoiceStart != null -> Action.Record
                else -> Action.None
            }
            AnimatedContent(
                action,
                transitionSpec = { (fadeIn(effects) + scaleIn(spatial, initialScale = 0.88f)) togetherWith (fadeOut(effects) + scaleOut(spatial, targetScale = 0.88f)) },
                label = "composerAction",
            ) { state ->
                when (state) {
                    Action.Send -> SendButton(onSend, stringResource(R.string.chat_send), "send", onSendWith, whenOnline)
                    Action.SendVoice -> SendButton({ onVoiceStop(true) }, stringResource(R.string.chat_voice_send), "sendVoice", onVoiceSendWith, whenOnline)
                    Action.Record -> Box(Modifier.padding(start = 8.dp)) {
                        RecordButton(
                            recording,
                            onStart = {
                                hint = false
                                onVoiceStart?.invoke()
                            },
                            onStop = onVoiceStop,
                            onLock = { locked = true },
                            onTap = { hints++ },
                            onDrag = { drag = it },
                        )
                    }
                    Action.None -> Unit
                }
            }
        }
    }
}

private enum class Shell { Field, Held, Locked }

private enum class Action { Send, SendVoice, Record, None }

@Composable
private fun SendButton(onClick: () -> Unit, label: String, tag: String, onSendWith: ((SendOptions) -> Unit)? = null, whenOnline: Boolean = false) {
    var menu by remember { mutableStateOf(false) }
    val press = rememberLongPress(onSendWith != null) { menu = true }
    Box(Modifier.padding(start = 8.dp)) {
        FilledIconButton(
            onClick = { if (!press.consumedClick()) onClick() },
            shapes = IconButtonDefaults.shapes(IconButtonDefaults.mediumRoundShape, IconButtonDefaults.mediumPressedShape),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            interactionSource = press.source,
            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()).then(if (onSendWith != null) Modifier.sendMenuAction(stringResource(R.string.chat_send_options)) { menu = true } else Modifier).testTag(tag),
        ) {
            Icon(painterResource(R.drawable.ic_send), label)
        }
        if (onSendWith != null) SendMenu(menu, { menu = false }, whenOnline, onSendWith)
    }
}

class AttachMenuActions(
    val onPoll: (() -> Unit)? = null,
    val onLocation: (() -> Unit)? = null,
    val onContact: (() -> Unit)? = null,
) {
    internal val any: Boolean get() = onPoll != null || onLocation != null || onContact != null
}

@Composable
private fun ComposerPill(
    value: TextFieldValue,
    onValue: (TextFieldValue) -> Unit,
    entities: List<TextEntity>,
    emoji: InlineEmoji,
    onAttachMedia: (() -> Unit)?,
    onAttachFile: (() -> Unit)?,
    attachMenu: AttachMenuActions,
    stickersOpen: Boolean,
    onToggleStickers: (() -> Unit)?,
    trailingHint: String?,
    placeholder: String?,
    botKeyboardOpen: Boolean,
    onToggleBotKeyboard: (() -> Unit)?,
    onArticle: (() -> Unit)?,
    articleDraft: String?,
    onOpenArticleDraft: () -> Unit,
    onDeleteArticleDraft: () -> Unit,
    menuButton: String?,
    onMenuButton: () -> Unit,
    attachBots: List<AttachMenuBot>,
    onAttachBot: (AttachMenuBot) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.heightIn(min = ACTION_SIZE).animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec()),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
            if (menuButton != null) {
                Surface(
                    onClick = onMenuButton,
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp).heightIn(min = 40.dp).widthIn(max = MENU_BUTTON_MAX).testTag("botMenuButton"),
                ) {
                    Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(menuButton, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (onAttachMedia != null || onAttachFile != null || attachMenu.any || attachBots.isNotEmpty()) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    PillButton(R.drawable.ic_attach_file, stringResource(R.string.chat_attach), "attach", Modifier.rotate(45f)) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                        onAttachMedia?.let { pick ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_attach_media)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_photo_library), null) },
                                onClick = {
                                    menu = false
                                    pick()
                                },
                                modifier = Modifier.testTag("attachMedia"),
                            )
                        }
                        onAttachFile?.let { pick ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_attach_file)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_description), null) },
                                onClick = {
                                    menu = false
                                    pick()
                                },
                                modifier = Modifier.testTag("attachFile"),
                            )
                        }
                        attachMenu.onPoll?.let { open ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_attach_poll)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_bar_chart), null) },
                                onClick = {
                                    menu = false
                                    open()
                                },
                                modifier = Modifier.testTag("attachPoll"),
                            )
                        }
                        attachMenu.onLocation?.let { open ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_attach_location)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_location_on), null) },
                                onClick = {
                                    menu = false
                                    open()
                                },
                                modifier = Modifier.testTag("attachLocation"),
                            )
                        }
                        attachMenu.onContact?.let { open ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_attach_contact)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_account_circle), null) },
                                onClick = {
                                    menu = false
                                    open()
                                },
                                modifier = Modifier.testTag("attachContact"),
                            )
                        }
                        attachBots.forEach { bot ->
                            DropdownMenuItem(
                                text = { Text(bot.shortName) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_public), null) },
                                onClick = {
                                    menu = false
                                    onAttachBot(bot)
                                },
                                modifier = Modifier.testTag("attachBot_${bot.key}"),
                            )
                        }
                    }
                }
            }
            if (onArticle != null && articleDraft == null) PillButton(R.drawable.ic_article, stringResource(R.string.chat_article_editor), "articleOpen", onClick = onArticle)
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.weight(1f).padding(start = 4.dp, top = 4.dp, bottom = 4.dp).heightIn(min = FIELD_HEIGHT, max = FIELD_MAX_HEIGHT),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f).padding(vertical = 12.dp).heightIn(min = 24.dp), contentAlignment = Alignment.CenterStart) {
                        if (articleDraft != null) {
                            Text(
                                articleDraft,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenArticleDraft).testTag("articleDraft"),
                            )
                        } else {
                            MessageField(value, onValue, entities, emoji, trailingHint, placeholder)
                        }
                    }
                    if (articleDraft != null) FieldButton(R.drawable.ic_close, stringResource(R.string.chat_article_delete_draft), "articleDraftDelete", onDeleteArticleDraft)
                    if (onToggleBotKeyboard != null) {
                        val icon = if (botKeyboardOpen) R.drawable.ic_keyboard else R.drawable.ic_dialpad
                        FieldButton(icon, stringResource(if (botKeyboardOpen) R.string.chat_keyboard else R.string.chat_bot_keyboard), "botKeyboardButton", onToggleBotKeyboard)
                    }
                    if (onToggleStickers != null) {
                        val icon = if (stickersOpen) R.drawable.ic_keyboard else R.drawable.ic_mood
                        FieldButton(icon, stringResource(if (stickersOpen) R.string.chat_keyboard else R.string.chat_stickers), "stickersButton", onToggleStickers)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageField(value: TextFieldValue, onValue: (TextFieldValue) -> Unit, entities: List<TextEntity>, emoji: InlineEmoji, trailingHint: String?, placeholder: String?) {
    val text = value.text
    if (text.isEmpty()) {
        Text(placeholder ?: stringResource(R.string.chat_message), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else if (trailingHint != null) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color.Transparent)) { append(text) }
                append(trailingHint)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("composerHint"),
        )
    }
    val colors = composerColors()
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    val drawn = remember(entities, emoji) { entities.mapNotNullTo(HashSet()) { entity -> (entity.type as? EntityType.CustomEmoji)?.documentId?.takeIf(emoji::ready) } }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Box(Modifier.fillMaxWidth().heightIn(max = TEXT_MAX_HEIGHT).verticalScroll(rememberScrollState())) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            visualTransformation = remember(entities, colors, drawn) { EntitiesTransformation(entities, colors, drawn) },
            onTextLayout = { layout = it },
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().testTag("messageInput"),
        )
        layout?.takeIf { it.layoutInput.text.text == text }?.let { FieldEmoji(it, entities, drawn, emoji, style) }
    }
}

@Composable
private fun FieldEmoji(layout: TextLayoutResult, entities: List<TextEntity>, drawn: Set<Long>, emoji: InlineEmoji, style: TextStyle) {
    val side = with(LocalDensity.current) { (style.fontSize * FIELD_EMOJI_SCALE).toDp() }
    for (entity in entities) {
        val id = (entity.type as? EntityType.CustomEmoji)?.documentId?.takeIf { it in drawn } ?: continue
        val media = emoji.documents.getValue(id)
        val center = glyphsCenter(layout, entity.offset, entity.end)
        val tint = if (media.textColor) Modifier.tinted(style.color) else Modifier
        key(entity.offset, id) {
            StickerView(
                media,
                emoji.files[media.key],
                Modifier
                    .offset { IntOffset((center.x - side.toPx() / 2).roundToInt(), (center.y - side.toPx() / 2).roundToInt()) }
                    .then(tint)
                    .testTag("fieldEmoji_$id"),
                size = side,
            )
        }
    }
}

internal fun glyphsCenter(layout: TextLayoutResult, start: Int, end: Int): Offset {
    val line = layout.getLineForOffset(start)
    val from = layout.getHorizontalPosition(start, usePrimaryDirection = true)
    val to = if (layout.getLineForOffset(end) == line) layout.getHorizontalPosition(end, usePrimaryDirection = true) else layout.getLineRight(line)
    return Offset((from + to) / 2, (layout.getLineTop(line) + layout.getLineBottom(line)) / 2)
}

@Composable
private fun PillButton(icon: Int, label: String, tag: String, iconModifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.padding(vertical = 4.dp).testTag(tag), shapes = IconButtonDefaults.shapes()) {
        Icon(painterResource(icon), label, iconModifier, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FieldButton(icon: Int, label: String, tag: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.testTag(tag), shapes = IconButtonDefaults.shapes()) {
        Icon(painterResource(icon), label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val HINT_MS = 2_000L
private val MENU_BUTTON_MAX = 120.dp
private val FIELD_MAX_HEIGHT = 132.dp
private val TEXT_MAX_HEIGHT = 88.dp
private const val FIELD_EMOJI_SCALE = 1.2f
