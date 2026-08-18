package com.slovy.slovymovyapp.ui.search

import com.slovy.slovymovyapp.ui.components.listHue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.data.lists.WordList
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.resources.pluralStringResource
import slovymovyapp.composeapp.generated.resources.*
import com.slovy.slovymovyapp.ui.WordListIcon

@Composable
internal fun ListCard(
    list: WordList,
    featured: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val localeCode = Locale.current.language
    val title = list.title[localeCode] ?: list.title["en"] ?: list.id
    val subtitle = list.subtitle[localeCode] ?: list.subtitle["en"] ?: ""
    val badge = list.labels[localeCode]?.firstOrNull() ?: list.labels["en"]?.firstOrNull()
    val wordCount = list.senseIds.size
    // The list's stable brand color; it now lives only in the icon and accents, while the
    // card field becomes a quiet wash of the same hue.
    val hue = listHue(list.id)
    val fieldColor = if (isDark) {
        mixColors(over = hue, base = MaterialTheme.colorScheme.surfaceContainer, overFraction = 0.09f)
    } else {
        mixColors(over = hue, base = MaterialTheme.colorScheme.background, overFraction = 0.09f)
    }
    val iconColor = if (isDark) {
        mixColors(over = hue, base = Color(0xFFFFFFFF), overFraction = 0.55f)
    } else {
        mixColors(over = hue, base = Color(0xFF2D2620), overFraction = 0.80f)
    }
    // Contrast-safe accent for the small (10sp) badge text: a darkened hue on the pale
    // light field, a lightened hue on the dark field — keeps the brand tint while passing
    // AA. The vivid [iconColor] is fine for the 38dp icon but too light/dark for small text.
    val badgeTextColor = if (isDark) {
        mixColors(over = Color(0xFFFFFFFF), base = hue, overFraction = 0.55f)
    } else {
        mixColors(over = Color(0xFF2D2620), base = hue, overFraction = 0.55f)
    }
    val titleFontSize = if (featured) 18.sp else 17.sp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(fieldColor)
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClickLabel = title, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.mdPlus),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xsPlus),
            ) {
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(hue.copy(alpha = 0.14f))
                            .border(0.5.dp, hue.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs)
                    ) {
                        Text(
                            text = badge.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            color = badgeTextColor,
                        )
                    }
                }
                Text(
                    text = title,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    lineHeight = (titleFontSize.value * 1.2f).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        fontFamily = MaterialTheme.serifFontFamily,
                        lineHeight = (13f * 1.38f).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = pluralStringResource(Res.plurals.search_list_word_count, wordCount, wordCount),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = AppSpacing.xxs),
                )
            }

            WordListIcon(
                iconSvg = list.iconSvg,
                fgColor = iconColor,
                modifier = Modifier.size(38.dp),
            )
        }
    }
}
/** Linear per-channel blend of [overFraction] of [over] onto [base]. */
private fun mixColors(over: Color, base: Color, overFraction: Float): Color = Color(
    red = base.red * (1f - overFraction) + over.red * overFraction,
    green = base.green * (1f - overFraction) + over.green * overFraction,
    blue = base.blue * (1f - overFraction) + over.blue * overFraction,
)
