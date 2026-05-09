package com.slovy.slovymovyapp.ui.study

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.slovy.slovymovyapp.ui.theme.LocalIsDarkTheme

// Warm dark brown (rgb 31,22,8) — harmonises with linen/paper backgrounds in light theme;
// effectively invisible on dark surfaces, where elevation comes from surface.container alone.
internal val StudySessionCardShadowColor = Color(red = 31, green = 22, blue = 8, alpha = 255)

@Composable
internal fun colorsForRating(rating: StudyRating): Pair<Color, Color> {
    val dark = LocalIsDarkTheme.current
    return when (rating) {
        StudyRating.AGAIN -> if (dark) Color(0xFF4A2828) to Color(0xFFD4908F) else Color(0xFFF9DEDC) to Color(0xFFBA1A1A)
        StudyRating.HARD  -> if (dark) Color(0xFF4A3D20) to Color(0xFFD4B86A) else Color(0xFFF0E4D6) to Color(0xFF8B6914)
        StudyRating.GOOD  -> if (dark) Color(0xFF3A4530) to Color(0xFFAABD9A) else Color(0xFFE2E8D9) to Color(0xFF4A5D40)
        StudyRating.EASY  -> if (dark) Color(0xFF3D342A) to Color(0xFFF5EBD7) else Color(0xFFF5E6D8) to Color(0xFF3D2810)
    }
}
