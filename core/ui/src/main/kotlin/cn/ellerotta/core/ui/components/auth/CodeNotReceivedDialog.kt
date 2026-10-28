package cn.ellerotta.core.ui.components.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R

@Composable
fun CodeNotReceivedDialog(
    onBackToCodeEntry: () -> Unit,
    onChangeNumber: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag("codeNotReceived"),
        onDismissRequest = onBackToCodeEntry,
        title = {
            Text(
                text = stringResource(R.string.code_not_found_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                val steps = listOf(
                    stringResource(R.string.code_not_found_step_device),
                    stringResource(R.string.code_not_found_step_chat),
                    stringResource(R.string.code_not_found_step_wait),
                )
                steps.forEachIndexed { index, step ->
                    val description = stringResource(R.string.code_not_found_step, index + 1, step)
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = description },
                    ) {
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (index < steps.lastIndex) {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onBackToCodeEntry, modifier = Modifier.testTag("codeNotReceivedBack")) {
                Text(stringResource(R.string.code_not_found_back))
            }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onChangeNumber, modifier = Modifier.testTag("changeNumber")) {
                Text(stringResource(R.string.code_not_found_change))
            }
        },
    )
}
