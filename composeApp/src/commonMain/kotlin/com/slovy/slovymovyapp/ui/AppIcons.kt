package com.slovy.slovymovyapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes as parsePathNodes
import androidx.compose.ui.unit.dp

// Stroke color is black so Icon() tint overrides it correctly. Do not render these via Image().
private val strokeRound = SolidColor(Color.Black)
private val fillBlack = SolidColor(Color.Black)

internal val SpeakerVector: ImageVector = ImageVector.Builder(
    name = "Speaker",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    addPath(
        pathData = parsePathNodes("M11 5L6 9H2v6h4l5 4V5z"),
        fill = null,
        stroke = strokeRound,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    )
    addPath(
        pathData = parsePathNodes("M19.1 5a9 9 0 0 1 0 14M15.5 8.5a5 5 0 0 1 0 7"),
        fill = null,
        stroke = strokeRound,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    )
}.build()

internal val SpeakerOffVector: ImageVector = ImageVector.Builder(
    name = "SpeakerOff",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    addPath(
        pathData = parsePathNodes("M11 5L6 9H2v6h4l5 4V5z"),
        fill = null,
        stroke = strokeRound,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    )
    addPath(
        pathData = parsePathNodes("M22 9l-6 6M16 9l6 6"),
        fill = null,
        stroke = strokeRound,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    )
}.build()

internal val StatsFilledVector: ImageVector = ImageVector.Builder(
    name = "StatsFilled",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    addPath(
        pathData = parsePathNodes("M3 13h4v8H3z M10 8h4v13h-4z M17 3h4v18h-4z"),
        fill = fillBlack
    )
}.build()

internal val StatsOutlineVector: ImageVector = ImageVector.Builder(
    name = "StatsOutline",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    addPath(
        pathData = parsePathNodes("M3 13h4v8H3z M10 8h4v13h-4z M17 3h4v18h-4z"),
        fill = null,
        stroke = strokeRound,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    )
}.build()
