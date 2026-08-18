package com.slovy.slovymovyapp.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale for layout, padding and gaps.
 *
 * The scale is a 2dp rhythm in the tight range and a 4dp rhythm from [lg] up, which is what the
 * screens were already built on. The `…Plus` tokens are the intermediate steps: each one sits
 * between the token it is named after and the next one up.
 *
 * - xxs (2dp): hairline separation inside a component — badge insets, tight vertical gaps
 * - xs (4dp): minimal spacing, tight elements
 * - xsPlus (6dp): between xs and sm
 * - sm (8dp): small spacing, compact layouts
 * - smPlus (10dp): between sm and md
 * - md (12dp): medium spacing, related items
 * - mdPlus (14dp): between md and lg
 * - lg (16dp): large spacing, default padding
 * - lgPlus (20dp): between lg and xl
 * - xl (24dp): extra large, section spacing
 * - xlPlus (28dp): between xl and xxl
 * - xxl (32dp): XX large, major sections
 * - xxxl (48dp): XXX large, screen padding
 *
 * Reach for the nearest step rather than a literal. A raw `dp` value in a padding or gap is a
 * claim that no step fits, so it should be able to survive the question "why not the neighbour?".
 */
object AppSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val xsPlus: Dp = 6.dp
    val sm: Dp = 8.dp
    val smPlus: Dp = 10.dp
    val md: Dp = 12.dp
    val mdPlus: Dp = 14.dp
    val lg: Dp = 16.dp
    val lgPlus: Dp = 20.dp
    val xl: Dp = 24.dp
    val xlPlus: Dp = 28.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp
}
