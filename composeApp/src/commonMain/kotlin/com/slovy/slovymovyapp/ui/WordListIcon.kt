package com.slovy.slovymovyapp.ui

import com.slovy.slovymovyapp.ui.listdetail.WordListEmblem

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter

/**
 * Renders a server-delivered monochrome SVG list icon tinted with [fgColor]. Decoding goes
 * through Coil's SVG decoder (registered on the singleton image loader in `App`); when the
 * list has no icon or the SVG fails to decode, the hand-drawn [WordListEmblem] is shown
 * instead.
 */
@Composable
internal fun WordListIcon(iconSvg: String?, fgColor: Color, modifier: Modifier) {
    if (iconSvg == null) {
        WordListEmblem(fgColor = fgColor, modifier = modifier)
        return
    }
    // Coil compares request models by equals, which is identity for arrays — encode once
    // per SVG so recompositions don't restart the request.
    val model = remember(iconSvg) { iconSvg.encodeToByteArray() }
    val painter = rememberAsyncImagePainter(model)
    val state by painter.state.collectAsState()
    if (state is AsyncImagePainter.State.Error) {
        WordListEmblem(fgColor = fgColor, modifier = modifier)
    } else {
        Image(
            painter = painter,
            contentDescription = null,
            colorFilter = ColorFilter.tint(fgColor),
            modifier = modifier,
        )
    }
}
