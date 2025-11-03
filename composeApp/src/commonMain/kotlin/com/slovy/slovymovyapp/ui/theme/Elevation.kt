package com.slovy.slovymovyapp.ui.theme

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 elevation system.
 *
 * Five-level elevation hierarchy:
 * - level0 (0dp): Base level, no elevation
 * - level1 (1dp): Cards at rest, minimal separation
 * - level2 (3dp): Hover states, slightly raised components
 * - level3 (6dp): Bottom navigation, modals, floating components
 * - level4 (8dp): Dialogs, priority components
 * - level5 (12dp): Menus, tooltips, maximum elevation
 */
object AppElevation {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
    val level4: Dp = 8.dp
    val level5: Dp = 12.dp
}

/**
 * Extension functions for CardDefaults to easily apply elevation levels.
 */
@Composable
fun CardDefaults.elevationLevel0(): CardElevation = cardElevation(
    defaultElevation = AppElevation.level0,
    pressedElevation = AppElevation.level0,
    focusedElevation = AppElevation.level0,
    hoveredElevation = AppElevation.level0
)

@Composable
fun CardDefaults.elevationLevel1(): CardElevation = cardElevation(
    defaultElevation = AppElevation.level1,
    pressedElevation = AppElevation.level1,
    focusedElevation = AppElevation.level2,
    hoveredElevation = AppElevation.level2
)

@Composable
fun CardDefaults.elevationLevel2(): CardElevation = cardElevation(
    defaultElevation = AppElevation.level2,
    pressedElevation = AppElevation.level2,
    focusedElevation = AppElevation.level3,
    hoveredElevation = AppElevation.level3
)

@Composable
fun CardDefaults.elevationLevel3(): CardElevation = cardElevation(
    defaultElevation = AppElevation.level3,
    pressedElevation = AppElevation.level3,
    focusedElevation = AppElevation.level4,
    hoveredElevation = AppElevation.level4
)

@Composable
fun CardDefaults.elevationLevel4(): CardElevation = cardElevation(
    defaultElevation = AppElevation.level4,
    pressedElevation = AppElevation.level4,
    focusedElevation = AppElevation.level5,
    hoveredElevation = AppElevation.level5
)

@Composable
fun CardDefaults.elevationLevel5(): CardElevation = cardElevation(
    defaultElevation = AppElevation.level5,
    pressedElevation = AppElevation.level5,
    focusedElevation = AppElevation.level5,
    hoveredElevation = AppElevation.level5
)
