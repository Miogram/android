package app.miogram.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miogram.feature.media.DownloadsViewModel
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.PremiumSpeedSnackbar
import cn.ellerotta.core.ui.components.common.SavedSnackbar

@Composable
internal fun DownloadsHost(model: DownloadsViewModel) {
    val saved by model.saved.collectAsStateWithLifecycle()
    val premium by model.premiumNotice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val file = saved
    if (file == null) {
        val notice = premium ?: return
        Box(Modifier.fillMaxSize()) {
            PremiumSpeedSnackbar(notice, notice.upload, notice.speedup, model::dismissPremium, Modifier.align(Alignment.BottomCenter))
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        SavedSnackbar(
            key = file,
            onOpen = {
                model.dismiss()
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, file.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    context.startActivity(view)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.downloads_no_app, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = model::dismiss,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
