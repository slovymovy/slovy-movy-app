package com.slovy.slovymovyapp.ui.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/**
 * Staged entrance choreography for the study session-complete reward screen.
 *
 * Ports the CSS keyframe timeline from `DEV_HANDOFF_REWARD.md` §8: every animated element starts
 * hidden and rises/scales into place once on mount, then holds. When [RewardEntrance.animate] is
 * false (reduced-motion, previews, print) every element is rendered at its finished state instantly,
 * which is the entire reduced-motion strategy — there is no separate timeline to maintain.
 */

// Easings from §8b. The back-ease overshoots past 1f to supply a single bounce for the medallion.
private val StandardEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val BackEase = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
internal val ConfettiEase = CubicBezierEasing(0.3f, 0.7f, 0.4f, 1f)
internal val ShiftBarEase = StandardEase

// Stagger constants (§8b) used by the data-driven elements (confetti, bars, deltas).
internal const val ShiftBarBaseDelayMillis = 720
internal const val ShiftBarRowStaggerMillis = 90
internal const val ShiftBarDurationMillis = 780
internal const val ShiftDeltaExtraDelayMillis = 140
internal const val ShiftDeltaDurationMillis = 560
internal const val MedallionCountUpDelayMillis = 820
internal const val MedallionCountUpDurationMillis = 720
internal const val ConfettiBaseDelayMillis = 840
internal const val ConfettiPieceStaggerMillis = 55
internal const val ConfettiDurationMillis = 720

private enum class RewardKeyframe { RISE, HERO, OTTER, POP }

/** The CSS-keyframe driven elements from §8b (data-driven ones are handled at their call sites). */
internal enum class RewardElement(
    val delayMillis: Int,
    val durationMillis: Int,
    private val easing: Easing,
    private val keyframe: RewardKeyframe,
) {
    OTTER(delayMillis = 60, durationMillis = 620, easing = StandardEase, keyframe = RewardKeyframe.OTTER),
    HEADLINE(delayMillis = 230, durationMillis = 520, easing = StandardEase, keyframe = RewardKeyframe.RISE),
    STATS(delayMillis = 360, durationMillis = 520, easing = StandardEase, keyframe = RewardKeyframe.RISE),
    HERO_TILE(delayMillis = 470, durationMillis = 580, easing = StandardEase, keyframe = RewardKeyframe.HERO),
    MEDALLION(delayMillis = 700, durationMillis = 600, easing = BackEase, keyframe = RewardKeyframe.POP),
    ACTION_BAR(delayMillis = 1140, durationMillis = 480, easing = StandardEase, keyframe = RewardKeyframe.RISE);

    val easingValue: Easing get() = easing

    /** Applies the keyframe's transform for an eased progress value in 0..1. */
    fun applyKeyframe(scope: GraphicsLayerScope, progress: Float) = with(scope) {
        alpha = progress.coerceIn(0f, 1f)
        when (keyframe) {
            RewardKeyframe.RISE -> translationY = (1f - progress) * 14.dp.toPx()
            RewardKeyframe.HERO -> {
                translationY = (1f - progress) * 18.dp.toPx()
                val s = lerp(0.965f, 1f, progress)
                scaleX = s
                scaleY = s
            }

            RewardKeyframe.OTTER -> {
                // Bottom-anchored monotonic grow-in (§8d): scales out of its seat, never bobs.
                transformOrigin = TransformOrigin(0.5f, 1f)
                val s = lerp(0.95f, 1f, progress)
                scaleX = s
                scaleY = s
            }

            RewardKeyframe.POP -> {
                val s = lerp(0.82f, 1f, progress)
                scaleX = s
                scaleY = s
            }
        }
    }
}

internal data class RewardEntrance(val animate: Boolean)

internal val LocalRewardEntrance = compositionLocalOf { RewardEntrance(animate = false) }

/** Eased 0..1 entrance progress for [element], started once when this enters composition. */
@Composable
private fun rememberElementProgress(element: RewardElement): State<Float> {
    val animate = LocalRewardEntrance.current.animate
    val anim = remember(element) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(element, animate) {
        if (animate) {
            anim.snapTo(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = element.durationMillis,
                    delayMillis = element.delayMillis,
                    easing = element.easingValue,
                ),
            )
        } else {
            anim.snapTo(1f)
        }
    }
    return anim.asState()
}

/** Wraps an element in its §8 entrance transform; a no-op (finished state) when not animating. */
@Composable
internal fun Modifier.rewardEntrance(element: RewardElement): Modifier {
    val progress = rememberElementProgress(element)
    return graphicsLayer { element.applyKeyframe(this, progress.value) }
}

/**
 * Ticks an integer 0 -> [target] (§8e). Renders [target] instantly when not animating. Negative
 * targets count down through negatives so callers can format a signed value.
 */
@Composable
internal fun rememberCountUp(target: Int, delayMillis: Int, durationMillis: Int): State<Int> {
    val animate = LocalRewardEntrance.current.animate
    val anim = remember(target) { Animatable((if (animate) 0 else target).toFloat()) }
    LaunchedEffect(target, animate) {
        if (animate) {
            anim.snapTo(0f)
            anim.animateTo(
                targetValue = target.toFloat(),
                animationSpec = tween(durationMillis, delayMillis, LinearEasing),
            )
        } else {
            anim.snapTo(target.toFloat())
        }
    }
    return remember { derivedStateOf { anim.value.roundToInt() } }
}

/** Whether the platform requests reduced motion; gates the whole reward entrance (§8a). */
@Composable
expect fun rememberReduceMotion(): Boolean
