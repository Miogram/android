package app.miogram.feature.profile

import app.miogram.data.telegram.TwoStepRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.TWO_STEP_CODE_LENGTH
import cn.ellerotta.core.model.TwoStepGoal
import cn.ellerotta.core.model.TwoStepSaved
import cn.ellerotta.core.model.TwoStepState
import cn.ellerotta.core.model.TwoStepStatus
import cn.ellerotta.core.model.TwoStepStep
import cn.ellerotta.ktproto.session.RpcException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class TwoStepViewModel @Inject constructor(private val repository: TwoStepRepository) : WorkViewModel() {
    private val stateFlow = MutableStateFlow(TwoStepState(status = repository.storedStatus()))

    val state: StateFlow<TwoStepState> = stateFlow.asStateFlow()

    private var goal = TwoStepGoal.Create
    private var current: String? = null
    private var password: String? = null
    private var hint: String? = null

    init {
        refresh(stateFlow.value.status != null) { loadStatus() }
    }

    private suspend fun loadStatus() {
        val status = repository.status()
        stateFlow.update { it.copy(status = status) }
    }

    fun start(goal: TwoStepGoal) {
        this.goal = goal
        forget()
        go(if (goal == TwoStepGoal.Create) TwoStepStep.NewPassword else TwoStepStep.Current(goal))
    }

    fun submitCurrent(password: String) = rejecting {
        repository.verify(password)
        current = password
        when (goal) {
            TwoStepGoal.Remove -> finish(repository.remove(password), TwoStepSaved.Removed)
            TwoStepGoal.Change -> go(TwoStepStep.NewPassword)
            else -> go(TwoStepStep.Email(TwoStepGoal.Email))
        }
    }

    fun submitNewPassword(password: String) {
        this.password = password
        go(TwoStepStep.Hint)
    }

    fun submitHint(hint: String) = work {
        this.hint = hint.trim().takeIf { it.isNotEmpty() }
        val new = password ?: return@work
        if (goal == TwoStepGoal.Create) {
            go(TwoStepStep.Email(TwoStepGoal.Create))
        } else {
            finish(repository.change(current.orEmpty(), new, this.hint), TwoStepSaved.Changed)
        }
    }

    fun submitEmail(email: String?) = work {
        val status = if (goal == TwoStepGoal.Create) {
            repository.create(password.orEmpty(), hint, email?.trim()?.takeIf { it.isNotEmpty() })
        } else {
            repository.setRecoveryEmail(current.orEmpty(), email.orEmpty().trim())
        }
        if (status.unconfirmedEmail != null) {
            stateFlow.update { it.copy(status = status, step = TwoStepStep.Code(status.unconfirmedEmail.orEmpty(), status.codeLength ?: TWO_STEP_CODE_LENGTH), wrongCount = 0, saved = null) }
        } else {
            finish(status, if (goal == TwoStepGoal.Create) TwoStepSaved.Created else TwoStepSaved.Email)
        }
    }

    fun submitCode(code: String) = rejecting {
        val status = repository.confirmEmail(code.trim())
        finish(status, if (goal == TwoStepGoal.Create) TwoStepSaved.Created else TwoStepSaved.Email)
    }

    fun enterPendingCode() {
        val status = stateFlow.value.status ?: return
        goal = if (status.enabled) TwoStepGoal.Email else TwoStepGoal.Create
        go(TwoStepStep.Code(status.unconfirmedEmail.orEmpty(), status.codeLength ?: TWO_STEP_CODE_LENGTH))
    }

    fun resendCode() = work { repository.resendCode() }

    fun cancelEmail() = work {
        val status = repository.cancelEmail()
        forget()
        stateFlow.update { it.copy(status = status, step = TwoStepStep.Overview, wrongCount = 0) }
    }

    fun forgot() = work {
        val pattern = repository.requestRecovery()
        go(TwoStepStep.Recover(pattern, TWO_STEP_CODE_LENGTH))
    }

    fun submitRecovery(code: String) = rejecting {
        finish(repository.recover(code.trim()), TwoStepSaved.Recovered)
    }

    fun back(): Boolean {
        when (val step = stateFlow.value.step) {
            TwoStepStep.Overview -> return false
            TwoStepStep.Hint -> go(TwoStepStep.NewPassword)
            is TwoStepStep.Email -> if (step.goal == TwoStepGoal.Create) go(TwoStepStep.Hint) else leave()
            else -> leave()
        }
        return true
    }

    private fun leave() {
        forget()
        stateFlow.update { it.copy(step = TwoStepStep.Overview, wrongCount = 0) }
    }

    private fun go(step: TwoStepStep) = stateFlow.update { it.copy(step = step, wrongCount = 0, saved = null) }

    private fun finish(status: TwoStepStatus, saved: TwoStepSaved) {
        forget()
        stateFlow.update { it.copy(status = status, step = TwoStepStep.Overview, wrongCount = 0, saved = saved) }
    }

    private fun forget() {
        current = null
        password = null
        hint = null
    }

    private fun rejecting(block: suspend () -> Unit) = work {
        try {
            block()
        } catch (e: RpcException) {
            if (e.errorMessage !in REJECTED && !e.errorMessage.startsWith("CODE_")) throw e
            stateFlow.update { it.copy(wrongCount = it.wrongCount + 1) }
        }
    }

    private companion object {
        val REJECTED = setOf("PASSWORD_HASH_INVALID", "EMAIL_HASH_EXPIRED", "CODE_INVALID")
    }
}
