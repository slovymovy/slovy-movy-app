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
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.data.remote.SenseFrequency
import com.slovy.slovymovyapp.data.remote.TraitType

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
internal fun colorsForFrequency(f: SenseFrequency): Pair<Color, Color> = when (f) {
    SenseFrequency.HIGH -> Color(0xFFDFF6DD) to Color(0xFF1C5E20)
    SenseFrequency.MIDDLE -> Color(0xFFFFF1C5) to Color(0xFF6C4A00)
    SenseFrequency.LOW -> Color(0xFFFFE0B2) to Color(0xFF8C4513)
    SenseFrequency.VERY_LOW -> Color(0xFFE7E9F0) to Color(0xFF3F4856)
}

@Composable
internal fun colorsForPos(pos: PartOfSpeech): Pair<Color, Color> = when (pos) {
    PartOfSpeech.NOUN -> Color(0xFFE3F2FD) to Color(0xFF0D47A1)
    PartOfSpeech.VERB -> Color(0xFFE8F5E9) to Color(0xFF1B5E20)
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