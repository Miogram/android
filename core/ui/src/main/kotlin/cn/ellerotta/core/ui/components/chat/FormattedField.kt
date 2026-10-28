package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.model.edited
import cn.ellerotta.core.model.toggled
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut

@Composable
internal fun FormattedField(initial: FormattedText, onChange: (FormattedText) -> Unit, placeholder: String, tag: String, modifier: Modifier = Modifier) {
    var field by remember { mutableStateOf(TextFieldValue(initial.text, TextRange(initial.text.length))) }
    var entities by remember { mutableStateOf(initial.entities) }
    var linking by remember { mutableStateOf<TextRange?>(null) }
    val motion = MaterialTheme.motionScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(!field.selection.collapsed, enter = motion.expandFadeIn(), exit = motion.shrinkFadeOut()) {
            FormattingBar { action ->
                val selection = field.selection
                if (action == FormatAction.Link) {
                    linking = selection
                } else {
                    entities = formatted(FormattedText(field.text, entities), action, selection).entities
                    onChange(FormattedText(field.text, entities))
                }
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 24.dp, max = FIELD_MAX), contentAlignment = Alignment.CenterStart) {
                if (field.text.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                val colors = composerColors()
                Box(Modifier.verticalScroll(rememberScrollState())) {
                    BasicTextField(
                        value = field,
                        onValueChange = { value ->
                            entities = FormattedText(field.text, entities).edited(value.text).entities
                            field = value
                            onChange(FormattedText(value.text, entities))
                        },
                        visualTransformation = remember(entities, colors) { EntitiesTransformation(entities, colors) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().testTag(tag),
                    )
                }
            }
        }
    }
    linking?.let { selection ->
        LinkDialog(
            initial = linkAt(FormattedText(field.text, entities), selection),
            onConfirm = { url ->
                linking = null
                entities = FormattedText(field.text, entities).toggled(EntityType.TextUrl(url), selection.min, selection.max).entities
                onChange(FormattedText(field.text, entities))
            },
            onDismiss = { linking = null },
        )
    }
}

private val FIELD_MAX = 132.dp
