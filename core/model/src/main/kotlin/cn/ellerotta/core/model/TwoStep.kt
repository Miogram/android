package cn.ellerotta.core.model

data class TwoStepStatus(
    val enabled: Boolean = false,
    val hint: String? = null,
    val hasRecovery: Boolean = false,
    val unconfirmedEmail: String? = null,
    val codeLength: Int? = null,
)

enum class TwoStepGoal {
    Create,

    Change,

    Remove,

    Email,
}

sealed interface TwoStepStep {
    data object Overview : TwoStepStep

    data class Current(val goal: TwoStepGoal) : TwoStepStep

    data object NewPassword : TwoStepStep

    data object Hint : TwoStepStep

    data class Email(val goal: TwoStepGoal) : TwoStepStep

    data class Code(val email: String, val length: Int) : TwoStepStep

    data class Recover(val email: String, val length: Int) : TwoStepStep
}

data class TwoStepState(
    val status: TwoStepStatus? = null,
    val step: TwoStepStep = TwoStepStep.Overview,
    val wrongCount: Int = 0,
    val saved: TwoStepSaved? = null,
)

enum class TwoStepSaved { Created, Changed, Removed, Email, Recovered }

const val TWO_STEP_CODE_LENGTH = 6
