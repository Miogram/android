package cn.ellerotta.core.ui.components.profile

import androidx.compose.runtime.Composable
import cn.ellerotta.core.model.TwoStepState
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme
import cn.ellerotta.core.ui.components.profile.TwoStepPreviewData as Data

@Composable
private fun Screen(state: TwoStepState, busy: Boolean = false) = PreviewTheme { TwoStepScreen(state, busy, TwoStepActions()) }

@MioPreviews
@Composable
internal fun TwoStepLoadingPreview() = Screen(TwoStepState(), busy = true)

@MioPreviews
@Composable
internal fun TwoStepOffPreview() = Screen(Data.off)

@MioPreviews
@Composable
internal fun TwoStepOnPreview() = Screen(Data.on)

@MioPreviews
@Composable
internal fun TwoStepOnWithoutEmailPreview() = Screen(Data.onWithoutEmail)

@MioPreviews
@Composable
internal fun TwoStepPendingEmailPreview() = Screen(Data.pendingEmail)

@MioPreviews
@Composable
internal fun TwoStepCurrentPreview() = Screen(Data.current)

@MioPreviews
@Composable
internal fun TwoStepCurrentWrongPreview() = Screen(Data.currentWrong)

@MioPreviews
@Composable
internal fun TwoStepNewPasswordPreview() = Screen(Data.newPassword)

@MioPreviews
@Composable
internal fun TwoStepHintPreview() = Screen(Data.hint)

@MioPreviews
@Composable
internal fun TwoStepEmailPreview() = Screen(Data.email)

@MioPreviews
@Composable
internal fun TwoStepCodePreview() = Screen(Data.code, busy = false)

@MioPreviews
@Composable
internal fun TwoStepRecoverPreview() = Screen(Data.recover)
