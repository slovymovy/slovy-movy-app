package com.slovy.slovymovyapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private val VIEWBOX_REGEX = Regex("""viewBox=["']([^"']+)["']""")
private val WHITESPACE_SPLIT_REGEX = Regex("""[\s,]+""")
private val ELEMENT_REGEX = Regex(
    """<(path|circle|ellipse|line|rect)\b([^>]*?)(?:/>|>\s*</\1>)""",
    setOf(RegexOption.DOT_MATCHES_ALL),
)
private val ATTR_REGEX = Regex("""([\w-]+)=["']([^"']*)["']""")

/**
 * Parses simple monochrome currentColor SVG icons into [ImageVector].
 * Supports flat <path>, <circle>, <ellipse>, <line>, and <rect> elements. Attributes on the
 * root <svg> tag are used as defaults; elements may override them individually.
 * "currentColor" → Color.Black so that Icon() tinting works correctly.
 * Returns null when the SVG cannot be parsed or contains no renderable paths.
 */
fun parseSvgIcon(svg: String): ImageVector? {
    val viewBoxValues = VIEWBOX_REGEX.find(svg)
        ?.groupValues?.get(1)?.trim()?.split(WHITESPACE_SPLIT_REGEX)
        ?.mapNotNull { it.toFloatOrNull() }
        ?.takeIf { it.size == 4 } ?: return null
    val (_, _, vbWidth, vbHeight) = viewBoxValues

    val rootFill = svgRootAttr("fill", svg)
    val rootStroke = svgRootAttr("stroke", svg)
    val rootStrokeWidth = svgRootAttr("stroke-width", svg)?.toFloatOrNull() ?: 1f
    val rootLineCap = svgRootAttr("stroke-linecap", svg)
    val rootLineJoin = svgRootAttr("stroke-linejoin", svg)

    val builder = ImageVector.Builder(
        defaultWidth = vbWidth.dp,
        defaultHeight = vbHeight.dp,
        viewportWidth = vbWidth,
        viewportHeight = vbHeight,
    )

    var pathsAdded = 0
    for (match in ELEMENT_REGEX.findAll(svg)) {
        val tag = match.groupValues[1]
        val attrs = parseAttrs(match.groupValues[2])

        val fillStr = attrs["fill"] ?: rootFill
        val strokeStr = attrs["stroke"] ?: rootStroke
        val strokeWidth = attrs["stroke-width"]?.toFloatOrNull() ?: rootStrokeWidth
        val lineCap = toStrokeCap(attrs["stroke-linecap"] ?: rootLineCap)
        val lineJoin = toStrokeJoin(attrs["stroke-linejoin"] ?: rootLineJoin)

        val fill = if (fillStr.equals("currentColor", ignoreCase = true)) SolidColor(Color.Black) else null
        val stroke = if (strokeStr.equals("currentColor", ignoreCase = true)) SolidColor(Color.Black) else null

        if (fill == null && stroke == null) continue

        val pathData = when (tag) {
            "path" -> attrs["d"] ?: continue
            "circle" -> {
                val cx = attrs["cx"]?.toFloatOrNull() ?: continue
                val cy = attrs["cy"]?.toFloatOrNull() ?: continue
                val r = attrs["r"]?.toFloatOrNull() ?: continue
                ellipsePathData(cx, cy, r, r)
            }
            "ellipse" -> {
                val cx = attrs["cx"]?.toFloatOrNull() ?: continue
                val cy = attrs["cy"]?.toFloatOrNull() ?: continue
                val rx = attrs["rx"]?.toFloatOrNull() ?: continue
                val ry = attrs["ry"]?.toFloatOrNull() ?: continue
                ellipsePathData(cx, cy, rx, ry)
            }
            "line" -> {
                val x1 = attrs["x1"]?.toFloatOrNull() ?: continue
                val y1 = attrs["y1"]?.toFloatOrNull() ?: continue
                val x2 = attrs["x2"]?.toFloatOrNull() ?: continue
                val y2 = attrs["y2"]?.toFloatOrNull() ?: continue
                "M $x1 $y1 L $x2 $y2"
            }
            "rect" -> {
                val x = attrs["x"]?.toFloatOrNull() ?: continue
                val y = attrs["y"]?.toFloatOrNull() ?: continue
                val w = attrs["width"]?.toFloatOrNull() ?: continue
                val h = attrs["height"]?.toFloatOrNull() ?: continue
                val rx = attrs["rx"]?.toFloatOrNull() ?: 0f
                rectPathData(x, y, w, h, rx)
            }
            else -> continue
        }

        builder.addPath(
            pathData = addPathNodes(pathData),
            fill = fill,
            stroke = stroke,
            strokeLineWidth = strokeWidth,
            strokeLineCap = lineCap,
            strokeLineJoin = lineJoin,
        )
        pathsAdded++
    }

    return if (pathsAdded > 0) builder.build() else null
}

private fun svgRootAttr(attr: String, svg: String): String? =
    Regex("""<svg\b[^>]*\b$attr=["']([^"']+)["']""").find(svg)?.groupValues?.get(1)

private fun parseAttrs(s: String): Map<String, String> =
    ATTR_REGEX.findAll(s).associate { it.groupValues[1] to it.groupValues[2] }

private fun toStrokeCap(value: String?): StrokeCap = when (value) {
    "round" -> StrokeCap.Round
    "square" -> StrokeCap.Square
    else -> StrokeCap.Butt
}

private fun toStrokeJoin(value: String?): StrokeJoin = when (value) {
    "round" -> StrokeJoin.Round
    "bevel" -> StrokeJoin.Bevel
    else -> StrokeJoin.Miter
}

private fun ellipsePathData(cx: Float, cy: Float, rx: Float, ry: Float): String =
    "M ${cx - rx} $cy A $rx $ry 0 1 0 ${cx + rx} $cy A $rx $ry 0 1 0 ${cx - rx} $cy Z"

private fun rectPathData(x: Float, y: Float, w: Float, h: Float, rx: Float): String =
    if (rx <= 0f) "M $x $y H ${x + w} V ${y + h} H $x Z"
    else "M ${x + rx} $y H ${x + w - rx} A $rx $rx 0 0 1 ${x + w} ${y + rx} V ${y + h - rx} A $rx $rx 0 0 1 ${x + w - rx} ${y + h} H ${x + rx} A $rx $rx 0 0 1 $x ${y + h - rx} V ${y + rx} A $rx $rx 0 0 1 ${x + rx} $y Z"
