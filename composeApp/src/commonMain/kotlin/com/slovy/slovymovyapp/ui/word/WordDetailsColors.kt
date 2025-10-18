package com.slovy.slovymovyapp.ui.word

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.*

@Composable
internal fun colorsForLevel(level: LearnerLevel): Pair<Color, Color> = when (level) {
    LearnerLevel.A1 -> Color(0xFFE0F7D4) to Color(0xFF215732)
    LearnerLevel.A2 -> Color(0xFFBFEBD7) to Color(0xFF0F4C3C)
    LearnerLevel.B1 -> Color(0xFFCCE3FF) to Color(0xFF0F3D7A)
    LearnerLevel.B2 -> Color(0xFFB7D2FF) to Color(0xFF0F3566)
    LearnerLevel.C1 -> Color(0xFFFFE2C6) to Color(0xFF7A3E00)
    LearnerLevel.C2 -> Color(0xFFFBD0D9) to Color(0xFF7A1232)
}

@Composable
internal fun getFrequencyColor(zipfFrequency: Float): Pair<Color, Color> {
    return when {
        zipfFrequency >= 4.0f -> Color(0xFFDFF6DD) to Color(0xFF1E7D23)
        zipfFrequency >= 3.0f -> Color(0xFFFFF1C5) to Color(0xFF6C4A00)
        zipfFrequency >= 2.0f -> Color(0xFFFFE2C6) to Color(0xFF7A3E00)
        else -> Color(0xFFE7E9F0) to Color(0xFF3F4856)
    }
}

@Composable
internal fun colorsForFrequency(f: SenseFrequency): Pair<Color, Color> = when (f) {
    SenseFrequency.HIGH -> Color(0xFFDFF6DD) to Color(0xFF1C5E20)
    SenseFrequency.MIDDLE -> Color(0xFFFFF1C5) to Color(0xFF6C4A00)
    SenseFrequency.LOW -> Color(0xFFFFE0B2) to Color(0xFF8C4513)
    SenseFrequency.VERY_LOW -> Color(0xFFE7E9F0) to Color(0xFF3F4856)
}

@Composable
internal fun colorsForPos(pos: PartOfSpeech): Pair<Color, Color> = when (pos) {
    PartOfSpeech.NOUN -> Color(0xFFE3F2FD) to Color(0xFF0D47A1)
    PartOfSpeech.VERB -> Color(0xFFE8F5E9) to Color(0xFF5E4D1B)
    PartOfSpeech.ADJECTIVE -> Color(0xFFFFF3E0) to Color(0xFFEF6C00)
    PartOfSpeech.ADVERB -> Color(0xFFF3E5F5) to Color(0xFF6A1B9A)
    PartOfSpeech.PRONOUN -> Color(0xFFFFEBEE) to Color(0xFFC62828)
    PartOfSpeech.PREPOSITION -> Color(0xFFE0F7FA) to Color(0xFF006064)
    PartOfSpeech.CONJUNCTION -> Color(0xFFEDE7F6) to Color(0xFF4527A0)
    PartOfSpeech.INTERJECTION -> Color(0xFFFFFDE7) to Color(0xFFF9A825)
    PartOfSpeech.DETERMINER -> Color(0xFFF1F8E9) to Color(0xFF33691E)
    PartOfSpeech.NUMERAL -> Color(0xFFE0F2F1) to Color(0xFF004D40)
    PartOfSpeech.ARTICLE -> Color(0xFFE8EAF6) to Color(0xFF283593)
    PartOfSpeech.NAME -> Color(0xFFFFEBE9) to Color(0xFF7A1232)
}

@Composable
internal fun colorsForNameType(nameType: NameType): Pair<Color, Color> = when (nameType) {
    NameType.NO -> Color(0xFFE0E0E0) to Color(0xFF424242)
    NameType.PERSON_NAME -> Color(0xFFFFEBE9) to Color(0xFF7A1232)
    NameType.PLACE_NAME -> Color(0xFFE0F2F1) to Color(0xFF004D40)
    NameType.GEOGRAPHICAL_FEATURE -> Color(0xFFE0F7FA) to Color(0xFF006064)
    NameType.ORGANIZATION_NAME -> Color(0xFFE8EAF6) to Color(0xFF283593)
    NameType.FICTIONAL_NAME -> Color(0xFFF3E5F5) to Color(0xFF6A1B9A)
    NameType.HISTORICAL_NAME -> Color(0xFFFFF3E0) to Color(0xFFEF6C00)
    NameType.EVENT_NAME -> Color(0xFFFFF1C5) to Color(0xFF6C4A00)
    NameType.WORK_OF_ART_NAME -> Color(0xFFFFE2C6) to Color(0xFF7A3E00)
    NameType.LANGUAGE_NAME -> Color(0xFFE1F5FE) to Color(0xFF01579B)
    NameType.ETHNIC_GROUP_NAME -> Color(0xFFFCE4EC) to Color(0xFF880E4F)
    NameType.DEITY_OR_RELIGIOUS_NAME -> Color(0xFFF9E7FA) to Color(0xFF7B1FA2)
    NameType.RELIGION_OR_PHILOSOPHY_NAME -> Color(0xFFEDE7F6) to Color(0xFF512DA8)
    NameType.ASTRONOMICAL_NAME -> Color(0xFFE8EAF6) to Color(0xFF1A237E)
    NameType.TITLE_OR_HONORIFIC_NAME -> Color(0xFFFFF9C4) to Color(0xFFF57F17)
    NameType.BRAND_OR_PRODUCT_NAME -> Color(0xFFFFE0E9) to Color(0xFFC2185B)
    NameType.TECHNOLOGY_OR_SOFTWARE_NAME -> Color(0xFFE0F2F1) to Color(0xFF00695C)
    NameType.GAME_OR_SPORT_NAME -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
    NameType.IDEOLOGY_OR_MOVEMENT_NAME -> Color(0xFFFFF8E1) to Color(0xFFF57C00)
    NameType.MYTHOLOGICAL_OR_ASTROLOGICAL_ENTITY -> Color(0xFFF3E5F5) to Color(0xFF4A148C)
    NameType.DOCUMENT_OR_PROGRAM_NAME -> Color(0xFFECEFF1) to Color(0xFF37474F)
    NameType.OTHER -> Color(0xFFE7E9F0) to Color(0xFF3F4856)
}

@Composable
internal fun colorsForTraitType(traitType: TraitType): Pair<Color, Color> = when (traitType) {
    TraitType.DATED -> Color(0xFFFFE0B2) to Color(0xFF8C4513)
    TraitType.COLLOQUIAL -> Color(0xFFE3F2FD) to Color(0xFF0D47A1)
    TraitType.OBSOLETE -> Color(0xFFE7E9F0) to Color(0xFF3F4856)
    TraitType.DIALECTAL -> Color(0xFFE0F7FA) to Color(0xFF006064)
    TraitType.ARCHAIC -> Color(0xFFFFF3E0) to Color(0xFFEF6C00)
    TraitType.REGIONAL -> Color(0xFFF1F8E9) to Color(0xFF33691E)
    TraitType.SLANG -> Color(0xFFFFEBEE) to Color(0xFFC62828)
    TraitType.FORM -> Color(0xFFF3E5F5) to Color(0xFF6A1B9A)
    TraitType.SURNAME -> Color(0xFFFFEBE9) to Color(0xFF7A1232)
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

internal val ArrowForwardVector: ImageVector = ImageVector.Builder(
    name = "ArrowForward",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(12f, 4f)
        lineTo(10.59f, 5.41f)
        lineTo(16.17f, 11f)
        lineTo(4f, 11f)
        lineTo(4f, 13f)
        lineTo(16.17f, 13f)
        lineTo(10.59f, 18.59f)
        lineTo(12f, 20f)
        lineTo(20f, 12f)
        close()
    }
}.build()