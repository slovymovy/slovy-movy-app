package com.slovy.slovymovyapp.ui.word

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.data.remote.NameType
import com.slovy.slovymovyapp.data.remote.SenseFrequency
import com.slovy.slovymovyapp.ui.theme.LocalIsDarkTheme

@Composable
internal fun colorsForLevel(level: LearnerLevel): Pair<Color, Color> {
    val isDark = LocalIsDarkTheme.current
    return when (level) {
        // Warm earth-tone progression: approachable → mature
        LearnerLevel.A1 -> if (isDark) {
            Color(0xFF3D4A36) to Color(0xFFB8C4A8) // Muted olive bg / Sage text
        } else {
            Color(0xFFE2E8D8) to Color(0xFF4A5D3A) // Light sage / Deeper olive
        }

        LearnerLevel.A2 -> if (isDark) {
            Color(0xFF354030) to Color(0xFFAABD9A) // Deep forest bg / Light sage
        } else {
            Color(0xFFDDE4D5) to Color(0xFF4A5D40) // Warm sage / Forest
        }

        LearnerLevel.B1 -> if (isDark) {
            Color(0xFF3A404A) to Color(0xFFB0B8C4) // Dark slate bg / Light steel
        } else {
            Color(0xFFE0E4E8) to Color(0xFF4A5666) // Dusty slate / Steel
        }

        LearnerLevel.B2 -> if (isDark) {
            Color(0xFF2E3640) to Color(0xFFA0AAB8) // Charcoal bg / Muted silver
        } else {
            Color(0xFFD8DDE3) to Color(0xFF3D4A5A) // Muted steel / Charcoal
        }

        LearnerLevel.C1 -> if (isDark) {
            Color(0xFF4A3D20) to Color(0xFFD4B86A) // Deep bronze bg / Warm gold
        } else {
            Color(0xFFF0E4D6) to Color(0xFF8B6914) // Warm amber / Bronze
        }

        LearnerLevel.C2 -> if (isDark) {
            Color(0xFF4A3438) to Color(0xFFCAADB2) // Deep mauve bg / Dusty rose
        } else {
            Color(0xFFEBDDD8) to Color(0xFF7A4A50) // Dusty rose / Mauve
        }
    }
}

@Composable
internal fun colorsForFrequency(f: SenseFrequency): Pair<Color, Color> {
    val isDark = LocalIsDarkTheme.current
    return when (f) {
        // Muted earth tones: natural materials feel
        SenseFrequency.HIGH -> if (isDark) {
            Color(0xFF3A4530) to Color(0xFFAABD9A) // Deep sage bg / Light sage
        } else {
            Color(0xFFE2E8D9) to Color(0xFF4A5D40) // Warm sage / Forest
        }

        SenseFrequency.MIDDLE -> if (isDark) {
            Color(0xFF3E3E3A) to Color(0xFFD5D0C8) // Neutral gray-taupe bg / Muted cream
        } else {
            Color(0xFFE2DEDA) to Color(0xFF4A4540) // Neutral warm gray bg / Soft charcoal
        }

        SenseFrequency.LOW -> if (isDark) {
            Color(0xFF443830) to Color(0xFFCAAA98) // Deep clay bg / Warm terracotta
        } else {
            Color(0xFFEBE0D6) to Color(0xFF7A5A45) // Warm clay / Terracotta
        }

        SenseFrequency.VERY_LOW -> if (isDark) {
            Color(0xFF38363A) to Color(0xFFB0ADA8) // Deep stone bg / Light stone
        } else {
            Color(0xFFE5E3E0) to Color(0xFF5A5650) // Warm gray / Stone
        }
    }
}

@Composable
internal fun colorsForNameType(nameType: NameType): Pair<Color, Color> {
    val isDark = LocalIsDarkTheme.current
    return when (nameType) {
        NameType.NO -> if (isDark) {
            Color(0xFF3A3A3A) to Color(0xFFB0B0B0)
        } else {
            Color(0xFFE5E3E0) to Color(0xFF5A5650)
        }

        NameType.PERSON_NAME -> if (isDark) {
            Color(0xFF4A3438) to Color(0xFFCAADB2)
        } else {
            Color(0xFFEBDDD8) to Color(0xFF7A4A50)
        }

        NameType.PLACE_NAME -> if (isDark) {
            Color(0xFF2E403D) to Color(0xFF9ABAB5)
        } else {
            Color(0xFFDDE8E6) to Color(0xFF3D5A55)
        }

        NameType.GEOGRAPHICAL_FEATURE -> if (isDark) {
            Color(0xFF2D4044) to Color(0xFF9AC0C6)
        } else {
            Color(0xFFDCE8EA) to Color(0xFF3D5860)
        }

        NameType.ORGANIZATION_NAME -> if (isDark) {
            Color(0xFF363A4A) to Color(0xFFA8B0C8)
        } else {
            Color(0xFFE2E4EC) to Color(0xFF4A5070)
        }

        NameType.FICTIONAL_NAME -> if (isDark) {
            Color(0xFF403848) to Color(0xFFBAACC4)
        } else {
            Color(0xFFE8E2EC) to Color(0xFF5A4A68)
        }

        NameType.HISTORICAL_NAME -> if (isDark) {
            Color(0xFF4A4030) to Color(0xFFD0B890)
        } else {
            Color(0xFFF0E8DA) to Color(0xFF7A6030)
        }

        NameType.EVENT_NAME -> if (isDark) {
            Color(0xFF4A4228) to Color(0xFFD0C090)
        } else {
            Color(0xFFF0EBD8) to Color(0xFF6B5830)
        }

        NameType.WORK_OF_ART_NAME -> if (isDark) {
            Color(0xFF4A3D28) to Color(0xFFD4B888)
        } else {
            Color(0xFFF0E6D6) to Color(0xFF7A5A28)
        }

        NameType.LANGUAGE_NAME -> if (isDark) {
            Color(0xFF30404A) to Color(0xFF98B8C8)
        } else {
            Color(0xFFDEE8ED) to Color(0xFF3D5868)
        }

        NameType.ETHNIC_GROUP_NAME -> if (isDark) {
            Color(0xFF483440) to Color(0xFFC8A0B4)
        } else {
            Color(0xFFECE0E6) to Color(0xFF6A4058)
        }

        NameType.DEITY_OR_RELIGIOUS_NAME -> if (isDark) {
            Color(0xFF443850) to Color(0xFFC0A8D0)
        } else {
            Color(0xFFEAE4F0) to Color(0xFF5A4070)
        }

        NameType.RELIGION_OR_PHILOSOPHY_NAME -> if (isDark) {
            Color(0xFF3A3848) to Color(0xFFB4ACC4)
        } else {
            Color(0xFFE6E4EC) to Color(0xFF4A4568)
        }

        NameType.ASTRONOMICAL_NAME -> if (isDark) {
            Color(0xFF32384A) to Color(0xFFA0AAC4)
        } else {
            Color(0xFFE2E4EC) to Color(0xFF3D4568)
        }

        NameType.TITLE_OR_HONORIFIC_NAME -> if (isDark) {
            Color(0xFF4A4428) to Color(0xFFD8C880)
        } else {
            Color(0xFFF2ECD4) to Color(0xFF6B5A18)
        }

        NameType.BRAND_OR_PRODUCT_NAME -> if (isDark) {
            Color(0xFF4A3440) to Color(0xFFCC9EB0)
        } else {
            Color(0xFFEEDEE6) to Color(0xFF7A4058)
        }

        NameType.TECHNOLOGY_OR_SOFTWARE_NAME -> if (isDark) {
            Color(0xFF2E4040) to Color(0xFF98BAB8)
        } else {
            Color(0xFFDCE8E6) to Color(0xFF3D5855)
        }

        NameType.GAME_OR_SPORT_NAME -> if (isDark) {
            Color(0xFF344538) to Color(0xFFA0C0A4)
        } else {
            Color(0xFFE0EAE2) to Color(0xFF406048)
        }

        NameType.IDEOLOGY_OR_MOVEMENT_NAME -> if (isDark) {
            Color(0xFF4A4028) to Color(0xFFD4BC80)
        } else {
            Color(0xFFF0EAD4) to Color(0xFF7A5A18)
        }

        NameType.MYTHOLOGICAL_OR_ASTROLOGICAL_ENTITY -> if (isDark) {
            Color(0xFF3C3048) to Color(0xFFB8A0C8)
        } else {
            Color(0xFFE8E0F0) to Color(0xFF503870)
        }

        NameType.DOCUMENT_OR_PROGRAM_NAME -> if (isDark) {
            Color(0xFF363A40) to Color(0xFFA8B0B8)
        } else {
            Color(0xFFE4E6E8) to Color(0xFF4A5058)
        }

        NameType.OTHER -> if (isDark) {
            Color(0xFF38363A) to Color(0xFFB0ADA8)
        } else {
            Color(0xFFE5E3E0) to Color(0xFF5A5650)
        }
    }
}

/** Warm dusty rose used for favorite heart indicators across the UI. */
internal val FavoriteAccentColor = Color(0xFFC46060)

/**
 * Colors for synonyms badges - warm sage tones indicating similarity/harmony
 */
@Composable
internal fun colorsForSynonyms(): Pair<Color, Color> {
    val isDark = LocalIsDarkTheme.current
    return if (isDark) {
        Color(0xFF3D4A36) to Color(0xFFB8C4A8) // Muted olive bg / Sage text
    } else {
        Color(0xFFE2E8D8) to Color(0xFF4A5D3A) // Light sage / Deeper olive
    }
}

/**
 * Colors for antonyms badges - warm dusty rose tones indicating contrast
 */
@Composable
internal fun colorsForAntonyms(): Pair<Color, Color> {
    val isDark = LocalIsDarkTheme.current
    return if (isDark) {
        Color(0xFF4A3438) to Color(0xFFCAADB2) // Deep mauve bg / Dusty rose
    } else {
        Color(0xFFEBDDD8) to Color(0xFF7A4A50) // Light dusty rose / Mauve
    }
}

internal val ExpandMoreVector: ImageVector = ImageVector.Builder(
    name = "ExpandableChevronDown",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(12f, 16f)
        lineTo(5.5f, 9.5f)
        lineTo(6.91f, 8.09f)
        lineTo(12f, 13.17f)
        lineTo(17.09f, 8.09f)
        lineTo(18.5f, 9.5f)
        close()
    }
}.build()

internal val ExpandLessVector: ImageVector = ImageVector.Builder(
    name = "ExpandableChevronUp",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(5.5f, 14.5f)
        lineTo(6.91f, 15.91f)
        lineTo(12f, 10.83f)
        lineTo(17.09f, 15.91f)
        lineTo(18.5f, 14.5f)
        lineTo(12f, 8.0f)
        close()
    }
}.build()
