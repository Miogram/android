package cn.ellerotta.core.ui.components.profile

import cn.ellerotta.core.model.TwoStepGoal
import cn.ellerotta.core.model.TwoStepSaved
import cn.ellerotta.core.model.TwoStepState
import cn.ellerotta.core.model.TwoStepStatus
import cn.ellerotta.core.model.TwoStepStep

internal object TwoStepPreviewData {
    val off = TwoStepState(status = TwoStepStatus())
    val on = TwoStepState(status = TwoStepStatus(enabled = true, hint = "the first pet", hasRecovery = true), saved = TwoStepSaved.Changed)
    val onWithoutEmail = TwoStepState(status = TwoStepStatus(enabled = true))
    val pendingEmail = TwoStepState(status = TwoStepStatus(enabled = true, hasRecovery = false, unconfirmedEmail = "i***@e*****.com", codeLength = 6))
    val current = TwoStepState(status = on.status, step = TwoStepStep.Current(TwoStepGoal.Change))
    val currentWrong = current.copy(wrongCount = 1)
    val newPassword = TwoStepState(status = off.status, step = TwoStepStep.NewPassword)
    val hint = TwoStepState(status = off.status, step = TwoStepStep.Hint)
    val email = TwoStepState(status = off.status, step = TwoStepStep.Email(TwoStepGoal.Create))
    val code = TwoStepState(status = pendingEmail.status, step = TwoStepStep.Code("i***@e*****.com", 6))
    val recover = TwoStepState(status = on.status, step = TwoStepStep.Recover("i***@e*****.com", 6))
}
