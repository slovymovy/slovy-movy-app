package com.slovy.slovymovyapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview

/**
 * Indeterminate spinner used in place of the Material 3 [androidx.compose.material3.CircularProgressIndicator].
 *
 * The Material spinner is driven by `rememberInfiniteTransition`, which Compose collapses to a
 * single static frame when the platform reports reduced motion (iOS Reduce Motion arrives as
 * `MotionDurationScale == 0`), leaving a frozen arc. Progress feedback is functional rather than
 * decorative — native iOS keeps `UIActivityIndicatorView` spinning under Reduce Motion — so this
 * spinner derives its rotation directly from the frame clock, which the duration scale cannot
 * collapse.
 */
@Composable
fun SpinningProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    trackColor: Color = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
) {
    var rotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startMillis = withFrameMillis { it }
        while (true) {
            withFrameMillis { frameMillis ->
                rotation = ((frameMillis - startMillis) % ROTATION_MILLIS) * 360f / ROTATION_MILLIS
            }
        }
    }
    Canvas(modifier.progressSemantics().size(SPINNER_DIAMETER)) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2
        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
        if (trackColor != Color.Transparent && trackColor != Color.Unspecified) {
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
        drawArc(
            color = color,
            startAngle = rotation - 90f,
            sweepAngle = SWEEP_DEGREES,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
    }
}

// Matches the Material 3 circular indicator's default 40dp diameter.
private val SPINNER_DIAMETER = 40.dp
private const val ROTATION_MILLIS = 1000
private const val SWEEP_DEGREES = 270f

@Preview
@Composable
private fun SpinningProgressIndicatorPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpinningProgressIndicator()
            SpinningProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpinningProgressIndicator(
                modifier = Modifier.size(24.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
