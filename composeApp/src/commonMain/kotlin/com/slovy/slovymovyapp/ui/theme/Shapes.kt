package com.slovy.slovymovyapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 shape system from Figma design.
 *
 * Shape scale:
 * - extraSmall (4dp): Small chips, badges
 * - small (8dp): Buttons, small cards
 * - medium (12dp): Medium cards, dialogs
 * - large (16dp): Large cards
 * - extraLarge (28dp): Primary cards (Figma standard)
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
