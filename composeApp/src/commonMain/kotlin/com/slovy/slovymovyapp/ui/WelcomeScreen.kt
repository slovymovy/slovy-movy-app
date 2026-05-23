package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

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
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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

                OnboardingHeader(
                    title = stringResource(Res.string.common_app_name),
                    subtitle = stringResource(Res.string.welcome_tagline)
                )

                Spacer(modifier = Modifier.height(AppSpacing.xxl))

                SellingPointRow(
                    icon = Icons.Outlined.Search,
                    title = stringResource(Res.string.welcome_selling_point_translations_title),
                    description = stringResource(Res.string.welcome_selling_point_translations_description),
                    showDivider = false
                )

                SellingPointRow(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = stringResource(Res.string.welcome_selling_point_collection_title),
                    description = stringResource(Res.string.welcome_selling_point_collection_description),
                    showDivider = true
                )

                SellingPointRow(
                    icon = Icons.Outlined.Psychology,
                    title = stringResource(Res.string.welcome_selling_point_practice_title),
                    description = stringResource(Res.string.welcome_selling_point_practice_description),
                    showDivider = true
                )

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
                        text = stringResource(Res.string.welcome_cta_explore_now),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.xxl))
        }
    }
}

@Composable
private fun SellingPointRow(
    icon: ImageVector,
    title: String,
    description: String,
    showDivider: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(AppSpacing.md))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = AppSpacing.xs)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp
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
