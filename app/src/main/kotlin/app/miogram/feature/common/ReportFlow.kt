package app.miogram.feature.common

import app.miogram.data.telegram.SafetyRepository
import cn.ellerotta.core.model.ReportState
import cn.ellerotta.core.model.ReportStep
import cn.ellerotta.core.model.ReportTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class ReportFlow(private val safety: SafetyRepository, private val work: (suspend () -> Unit) -> Job) {
    private val state = MutableStateFlow<ReportState?>(null)

    val ui: StateFlow<ReportState?> = state.asStateFlow()

    fun open(target: ReportTarget) {
        state.value = ReportState(target, sending = true)
        send(target, null, "")
    }

    fun choose(id: String) {
        val current = state.value ?: return
        if (current.sending || current.step !is ReportStep.Choose) return
        send(current.target, id, "")
    }

    fun comment(text: String) {
        val current = state.value ?: return
        val step = current.step as? ReportStep.Comment ?: return
        if (current.sending || (!step.optional && text.isBlank())) return
        send(current.target, step.option, text)
    }

    fun close() {
        state.value = null
    }

    private fun send(target: ReportTarget, option: String?, comment: String) {
        state.update { it?.copy(sending = true) }
        work {
            try {
                val step = safety.report(target, option, comment)
                state.update { if (it?.target == target) it.copy(step = step, sending = false) else it }
            } catch (e: Exception) {
                state.update { if (it?.target != target) it else if (it.step == null) null else it.copy(sending = false) }
                throw e
            }
        }
    }
}
