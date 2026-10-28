package app.miogram.feature.log

import androidx.lifecycle.ViewModel
import app.miogram.data.telegram.TelegramClientHolder
import cn.ellerotta.core.model.LogLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(telegram: TelegramClientHolder) : ViewModel() {
    val lines: StateFlow<List<LogLine>> = telegram.log
}
