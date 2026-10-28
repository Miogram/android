package cn.ellerotta.core.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.MotionScheme

fun MotionScheme.expandFadeIn(): EnterTransition = expandVertically(fastSpatialSpec()) + fadeIn(fastEffectsSpec())

fun MotionScheme.shrinkFadeOut(): ExitTransition = shrinkVertically(fastSpatialSpec()) + fadeOut(fastEffectsSpec())

fun MotionScheme.crossfade(): ContentTransform =
    ContentTransform(fadeIn(fastEffectsSpec()), fadeOut(fastEffectsSpec()), sizeTransform = SizeTransform { _, _ -> fastSpatialSpec() })
