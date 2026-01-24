package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.slovy.slovymovyapp.ui.theme.AppSpacing

data class WelcomeUiState(
    val isStarting: Boolean = false
)

class WelcomeViewModel : ViewModel() {
    var state by mutableStateOf(WelcomeUiState())
        private set

    fun onGetStarted(onComplete: () -> Unit) {
        if (state.isStarting) return
        state = state.copy(isStarting = true)
        onComplete()
    }
}

@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    onGetStarted: () -> Unit
) {
    WelcomeScreenContent(
        state = viewModel.state,
        onGetStarted = { viewModel.onGetStarted(onGetStarted) }
    )
}

@Composable
fun WelcomeScreenContent(
    state: WelcomeUiState = WelcomeUiState(),
    onGetStarted: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.15f))

            // Welcome title
            Text(
                text = "Welcome to",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(AppSpacing.xs))

            Text(
                text = "Open Words",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.weight(0.1f))

            // Selling points
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
            ) {
                SellingPointCard(
                    title = "Built for learners",
                    description = "Meanings, word frequency, examples, and rich data to build real associations"
                )

                SellingPointCard(
                    title = "Build your vocabulary",
                    description = "Save words to favorites and grow your personal collection"
                )

                SellingPointCard(
                    title = "Lives on your device",
                    description = "Download once, use anywhere — no internet needed"
                )
            }

            Spacer(modifier = Modifier.weight(0.15f))

            // Get Started button
            Button(
                onClick = onGetStarted,
                enabled = !state.isStarting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (state.isStarting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Get Started",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.xxxl))
        }
    }
}

@Composable
private fun SellingPointCard(
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(AppSpacing.lg),
        horizontalArrangement = Arrangement.Start
    ) {
        // Accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )

        Spacer(modifier = Modifier.width(AppSpacing.lg))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(AppSpacing.xs))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WelcomeScreenPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WelcomeScreenContent()
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WelcomeScreenLoadingPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WelcomeScreenContent(state = WelcomeUiState(isStarting = true))
    }
}
