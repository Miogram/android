package cn.ellerotta.core.ui.components.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ConfigSummary
import cn.ellerotta.core.ui.R

@Composable
fun ServerSheet(
    apiId: String,
    apiHash: String,
    testMode: Boolean,
    config: ConfigSummary?,
    version: String,
    onCredentials: (String, String) -> Unit,
    onOpenLog: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ServerSettings(apiId, apiHash, testMode, config, version, onCredentials, onOpenLog)
    }
}

@Composable
fun ServerSettings(
    apiId: String,
    apiHash: String,
    testMode: Boolean,
    config: ConfigSummary?,
    version: String,
    onCredentials: (String, String) -> Unit,
    onOpenLog: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.server_title), style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(if (testMode) R.drawable.ic_science else R.drawable.ic_public), null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(if (testMode) R.string.server_mode_test else R.string.server_mode_production),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("serverMode"),
            )
        }
        OutlinedTextField(
            value = apiId,
            onValueChange = { onCredentials(it.filter(Char::isDigit), apiHash) },
            label = { Text(stringResource(R.string.connect_api_id)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("apiId"),
        )
        OutlinedTextField(
            value = apiHash,
            onValueChange = { onCredentials(apiId, it.trim()) },
            label = { Text(stringResource(R.string.connect_api_hash)) },
            supportingText = { Text(stringResource(R.string.server_api_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("apiHash"),
        )
        if (config != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().testTag("config"),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.connect_config, config.thisDc, config.testMode.toString()), style = MaterialTheme.typography.titleSmall)
                    config.dcs.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                }
            }
        }
        FilledTonalButton(onClick = onOpenLog, modifier = Modifier.testTag("openLog"), shapes = ButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_terminal), null, Modifier.size(18.dp))
            Text(stringResource(R.string.chats_log), Modifier.padding(start = 8.dp))
        }
        Text(stringResource(R.string.common_version, version), style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("version"))
    }
}
