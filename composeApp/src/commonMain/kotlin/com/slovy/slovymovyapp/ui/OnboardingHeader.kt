package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily

@Composable
fun OnboardingHeader(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(AppSpacing.sm))

        Text(
            text = subtitle,
            modifier = Modifier.widthIn(max = 300.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontStyle = FontStyle.Italic,
                lineHeight = 22.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
