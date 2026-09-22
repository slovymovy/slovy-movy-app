package com.slovy.slovymovyapp.ui.study

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The five format marks on the explainer, drawn as strokes of one weight so they sit as a set
 * next to each other. Material's arrows are filled glyphs and the app's speaker is a solid
 * silhouette; mixed with a line circle they read as three different weights.
 */
internal object StudyExplainerIcons {
    private const val STROKE = 1.75f

    val WordToTranslation: ImageVector by lazy {
        lineIcon("StudyExplainerWordToTranslation") {
            moveTo(5f, 12f)
            lineTo(19f, 12f)
            moveTo(13f, 6f)
            lineTo(19f, 12f)
            lineTo(13f, 18f)
        }
    }

    val TranslationToWord: ImageVector by lazy {
        lineIcon("StudyExplainerTranslationToWord") {
            moveTo(19f, 12f)
            lineTo(5f, 12f)
            moveTo(11f, 6f)
            lineTo(5f, 12f)
            lineTo(11f, 18f)
        }
    }

    /** A baseline rule with two short ascenders: the blank in a sentence. */
    val FillTheGap: ImageVector by lazy {
        lineIcon("StudyExplainerFillTheGap") {
            moveTo(5f, 11f)
            lineTo(5f, 17f)
            lineTo(19f, 17f)
            lineTo(19f, 11f)
        }
    }

    val Listen: ImageVector by lazy {
        lineIcon("StudyExplainerListen") {
            moveTo(4f, 9.5f)
            lineTo(7.5f, 9.5f)
            lineTo(12.5f, 5.5f)
            lineTo(12.5f, 18.5f)
            lineTo(7.5f, 14.5f)
            lineTo(4f, 14.5f)
            close()
            moveTo(16f, 9f)
            arcTo(4.6f, 4.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 16f, y1 = 15f)
        }
    }

    val SourceOnly: ImageVector by lazy {
        lineIcon("StudyExplainerSourceOnly") {
            moveTo(12f, 5f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 12f, y1 = 19f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 12f, y1 = 5f)
        }
    }

    private fun lineIcon(name: String, pathData: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 18.dp,
            defaultHeight = 18.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
                pathBuilder = pathData,
            )
        }.build()
}
