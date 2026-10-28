package cn.ellerotta.core.ui.components.profile

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.SessionInfo
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.formatChatDate
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState

internal val SessionGreen = Color(0xFF1E7F36)
internal val SessionOrange = Color(0xFFB35F00)
internal val SessionBlue = Color(0xFF0079FF)
internal val SessionGrey = Color(0xFF8E8E93)

private fun deviceLook(session: SessionInfo): Pair<Int, Color> {
    val text = "${session.platform} ${session.device} ${session.app}".lowercase()
    return when {
        "chrome" in text || "edge" in text -> R.drawable.ic_public to SessionGreen
        "firefox" in text || "brave" in text -> R.drawable.ic_public to SessionOrange
        "safari" in text || "web" in text -> R.drawable.ic_public to SessionBlue
        "android" in text -> R.drawable.ic_smartphone to SessionGreen
        "ios" in text || "iphone" in text || "ipad" in text -> R.drawable.ic_smartphone to SessionBlue
        "mac" in text || "windows" in text -> R.drawable.ic_devices to SessionBlue
        "ubuntu" in text -> R.drawable.ic_devices to SessionOrange
        else -> R.drawable.ic_devices to SessionGrey
    }
}

@Composable
fun SessionsScreen(sessions: List<SessionInfo>?, busy: Boolean, onBack: () -> Unit, onTerminate: (SessionInfo) -> Unit, onTerminateOthers: () -> Unit, now: Long = System.currentTimeMillis()) {
    var details by remember { mutableStateOf<SessionInfo?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    val current = sessions?.firstOrNull { it.current }
    val others = sessions.orEmpty().filter { !it.current }
    val list = rememberLazyListState()
    val title = stringResource(R.string.profile_devices)
    Page(title, rememberTopBarState(list), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("sessions"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "hero") {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val cookie = MaterialShapes.Cookie4Sided.toShape()
                    Box(Modifier.size(104.dp).background(MaterialTheme.colorScheme.primaryContainer, cookie), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_devices), null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.sessions_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp).testTag("sessionsLoading"))
                }
            }
            if (current != null) {
                item(key = "current") {
                    InfoCard(title = stringResource(R.string.sessions_this_device)) { SessionRow(current, now, onClick = { details = current }) }
                }
            }
            if (others.isNotEmpty()) {
                item(key = "terminate") {
                    FilledTonalButton(
                        onClick = { confirmAll = true },
                        enabled = !busy,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp).testTag("terminateOthers"),
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(R.string.sessions_terminate_others)) }
                }
                item(key = "othersTitle") { Spacer(Modifier.height(4.dp)) }
                item(key = "others") {
                    InfoCard(title = stringResource(R.string.sessions_active)) {
                        others.forEachIndexed { index, session ->
                            if (index > 0) InfoDivider()
                            SessionRow(session, now, onClick = { details = session })
                        }
                    }
                }
            }
        }
    }

    details?.let { session ->
        AlertDialog(
            onDismissRequest = { details = null },
            icon = {
                val (icon, color) = deviceLook(session)
                Box(Modifier.size(64.dp).background(color, MaterialTheme.shapes.largeIncreased), contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), null, Modifier.size(36.dp), tint = Color.White)
                }
            },
            title = { Text(session.device) },
            text = {
                Column {
                    Text(stringResource(R.string.sessions_application, "${session.app} ${session.appVersion}".trim()))
                    Text(stringResource(R.string.sessions_system, "${session.platform} ${session.system}".trim()))
                    if (session.location.isNotEmpty()) Text(stringResource(R.string.sessions_location, session.location))
                    if (session.ip.isNotEmpty()) Text(stringResource(R.string.sessions_ip, session.ip))
                }
            },
            confirmButton = {
                if (!session.current) {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                        details = null
                        onTerminate(session)
                    }, modifier = Modifier.testTag("terminate")) { Text(stringResource(R.string.sessions_terminate), color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { details = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.sessions_terminate_others)) },
            text = { Text(stringResource(R.string.sessions_terminate_others_text)) },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                    confirmAll = false
                    onTerminateOthers()
                }, modifier = Modifier.testTag("confirmTerminateOthers")) { Text(stringResource(R.string.sessions_terminate), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmAll = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun SessionRow(session: SessionInfo, now: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val (icon, color) = deviceLook(session)
    val active = if (session.current) stringResource(R.string.presence_online) else formatChatDate(session.dateActive, now, locale, DateFormat.is24HourFormat(context))
    InfoRow(
        icon,
        session.device,
        listOf("${session.app} ${session.appVersion}".trim(), listOf(session.location, active).filter { it.isNotEmpty() }.joinToString(" • ")).joinToString("\n"),
        tint = Color.White,
        container = color,
        onClick = onClick,
        modifier = Modifier.testTag("session_${session.hash}"),
    )
}
