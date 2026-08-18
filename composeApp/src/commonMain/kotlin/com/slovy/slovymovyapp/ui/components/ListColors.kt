package com.slovy.slovymovyapp.ui.components

import androidx.compose.ui.graphics.Color

/**
 * The list's stable saturated brand hue (terracotta, teal, …), derived from the same
 * id→color assignment as [vibrantColorsForList] so a list keeps its color across the
 * redesign. Reused as the icon tint and badge accent over the calm card field.
 */
internal fun listHue(id: String): Color = vibrantColorsForList(id, isDark = false).first

internal fun vibrantColorsForList(id: String, isDark: Boolean): Pair<Color, Color> {
    var h = 0L
    for (i in id.indices) {
        h = (h + id[i].code.toLong() * (i + 1)) and 0xFFFFFFFFL
    }
    // light (bg, fg) to dark (bg, fg) — 8 slots from the design palette
    val slots = listOf(
        Pair(Color(0xFFC46A3D), Color(0xFFFFF0E8)) to Pair(Color(0xFFA55530), Color(0xFFFFE4D4)), // 0 Terracotta
        Pair(Color(0xFFD4A03A), Color(0xFFFFF6EE)) to Pair(Color(0xFFA87A1E), Color(0xFFFFF0DC)), // 1 Mustard
        Pair(Color(0xFF3D7A7A), Color(0xFFE8F5F5)) to Pair(Color(0xFF2E5E5E), Color(0xFFD0ECEC)), // 2 Teal
        Pair(Color(0xFF7A4A6E), Color(0xFFF5E8F2)) to Pair(Color(0xFF5C3854), Color(0xFFEDD6E8)), // 3 Plum
        Pair(Color(0xFF6E7C3D), Color(0xFFF0F5E0)) to Pair(Color(0xFF525C2E), Color(0xFFE4EED0)), // 4 Olive
        Pair(Color(0xFFA04A28), Color(0xFFFFF1E8)) to Pair(Color(0xFF7A3A20), Color(0xFFFFE4D4)), // 5 Rust
        Pair(Color(0xFF8B6E7C), Color(0xFFFBF4F8)) to Pair(Color(0xFF6A5360), Color(0xFFF2E2EA)), // 6 Mauve
        Pair(Color(0xFF3A4A5C), Color(0xFFE8EEF5)) to Pair(Color(0xFF2A3848), Color(0xFFD4DCE8)), // 7 Ink
    )
    val (light, dark) = slots[(h % 8L).toInt()]
    return if (isDark) dark else light
}
