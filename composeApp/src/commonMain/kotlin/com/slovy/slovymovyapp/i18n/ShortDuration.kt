package com.slovy.slovymovyapp.i18n

import org.jetbrains.compose.resources.StringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.duration_unit_hours
import slovymovyapp.composeapp.generated.resources.duration_unit_minutes

data class ShortDurationPart(
    val value: Int,
    val unit: StringResource,
)

object ShortDuration {

    fun parts(totalMinutes: Int): List<ShortDurationPart> {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours <= 0 -> listOf(ShortDurationPart(totalMinutes, Res.string.duration_unit_minutes))
            minutes == 0 -> listOf(ShortDurationPart(hours, Res.string.duration_unit_hours))
            else -> listOf(
                ShortDurationPart(hours, Res.string.duration_unit_hours),
                ShortDurationPart(minutes, Res.string.duration_unit_minutes),
            )
        }
    }

    fun uiText(totalMinutes: Int): UiText = UiText.Joined(
        items = parts(totalMinutes).flatMap { part ->
            listOf(UiText.Plain(part.value.toString()), UiText.Resource(part.unit))
        },
        separator = " ",
    )
}
