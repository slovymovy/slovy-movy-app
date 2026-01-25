package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter

data class WelcomeUiState(
    val isStarting: Boolean = false
)

class WelcomeViewModel : ViewModel() {
    var state by mutableStateOf(WelcomeUiState())
        private set

    val scrollState = ScrollState(0)

    fun onGetStarted(onComplete: () -> Unit) {
        if (state.isStarting) return
        state = state.copy(isStarting = true)
        onComplete()
    }

    fun onError() {
        state = state.copy(isStarting = false)
    }
}

@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    onGetStarted: () -> Unit
) {
    WelcomeScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        onGetStarted = { viewModel.onGetStarted(onGetStarted) }
    )
}

@Composable
fun WelcomeScreenContent(
    state: WelcomeUiState = WelcomeUiState(),
    scrollState: ScrollState = ScrollState(0),
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(AppSpacing.xxxl))

                Text(
                    text = "Open Words",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                Text(
                    text = "An ad-free, supercharged dictionary\nfor serious learners.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(AppSpacing.xxl))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    SellingPointCard(
                        icon = Icons.Outlined.Lightbulb,
                        title = "Deep Word Insights",
                        description = "Build strong associations with word frequency, common phrases, and real-world examples."
                    )

                    SellingPointCard(
                        icon = Icons.Outlined.Favorite,
                        title = "Your Collection",
                        description = "Save words to your personal library and build your vocabulary locally."
                    )

                    SellingPointCard(
                        icon = Icons.Outlined.OfflinePin,
                        title = "Smart Offline Access",
                        description = "Download the core library to learn on the go. Essential data stays on your device."
                    )
                }

                Spacer(modifier = Modifier.height(AppSpacing.lg))
            }

            Spacer(modifier = Modifier.height(AppSpacing.lg))

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
                        text = "Explore Now",
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
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.lg),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(AppSpacing.lg))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
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
}

@Preview
@Composable
private fun WelcomeScreenPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WelcomeScreenContent()
    }
}

@Preview
@Composable
private fun WelcomeScreenLoadingPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WelcomeScreenContent(state = WelcomeUiState(isStarting = true))
    }
}
