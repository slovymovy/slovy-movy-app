package com.slovy.slovymovyapp.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 animation specifications.
 *
 * Uses Material motion easing curve: cubic-bezier(0.4, 0, 0.2, 1)
 * Duration scale:
 * - fast (200ms): Quick interactions, state changes
 * - normal (300ms): Standard transitions, theme changes
 * - slow (400ms): Complex animations, entrance effects
 */
object AppAnimations {
    // Duration constants
    const val fastDuration = 200
    const val normalDuration = 300
    const val slowDuration = 400

    // Material standard easing curve
    val standardEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    // Reusable animation specs
    val fastSpec = tween<Float>(durationMillis = fastDuration, easing = standardEasing)
    val normalSpec = tween<Float>(durationMillis = normalDuration, easing = standardEasing)
    val slowSpec = tween<Float>(durationMillis = slowDuration, easing = standardEasing)

    // Spring animation for natural motion
    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // Enter/Exit transitions
    fun fadeIn(duration: Int = fastDuration): EnterTransition = fadeIn(
        animationSpec = tween(durationMillis = duration, easing = standardEasing)
    )

    fun fadeOut(duration: Int = fastDuration): ExitTransition = fadeOut(
        animationSpec = tween(durationMillis = duration, easing = standardEasing)
    )

    fun expandVertically(duration: Int = normalDuration): EnterTransition = expandVertically(
        animationSpec = tween(durationMillis = duration, easing = standardEasing),
        expandFrom = Alignment.Top
    )

    fun shrinkVertically(duration: Int = normalDuration): ExitTransition = shrinkVertically(
        animationSpec = tween(durationMillis = duration, easing = standardEasing),
        shrinkTowards = Alignment.Top
    )

    fun slideInVertically(duration: Int = normalDuration): EnterTransition = slideInVertically(
        animationSpec = tween(durationMillis = duration, easing = standardEasing),
        initialOffsetY = { it / 4 }
    )

    fun slideOutVertically(duration: Int = normalDuration): ExitTransition = slideOutVertically(
        animationSpec = tween(durationMillis = duration, easing = standardEasing),
        targetOffsetY = { it / 4 }
    )

    // Combined transitions for common patterns
    fun fadeInWithSlide(duration: Int = normalDuration): EnterTransition =
        fadeIn(duration) + slideInVertically(duration)

    fun fadeOutWithSlide(duration: Int = normalDuration): ExitTransition =
        fadeOut(duration) + slideOutVertically(duration)
}

/**
 * Animatable elevation for hover effects.
 */
fun animateElevation(
    targetElevation: Dp,
    animationSpec: AnimationSpec<Dp> = tween(
        durationMillis = AppAnimations.fastDuration,
        easing = AppAnimations.standardEasing
    )
): Dp = targetElevation // In real usage, this would use animateDpAsState
