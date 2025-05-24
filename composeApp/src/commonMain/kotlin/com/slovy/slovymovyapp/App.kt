package com.slovy.slovymovyapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.compose_multiplatform

// Data class to represent card content
data class CardItem(
    val id: Int,
    val title: String,
    val description: String,
    val showImage: Boolean = true,
    val color: Color = Color(0xFF6200EE)
)

@Composable
fun SwipeableCard(
    cardItem: CardItem,
    onSwiped: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // Threshold for considering a swipe as complete (in pixels)
    val swipeThreshold = 300f

    // Rotation angle based on horizontal offset
    val rotation by animateFloatAsState(targetValue = (offsetX / 60).coerceIn(-10f, 10f))

    // Determine card color based on swipe direction
    val cardColor = when {
        offsetX > 50 -> Color.Green.copy(alpha = 0.8f) // Approved (right swipe)
        offsetX < -50 -> Color.Red.copy(alpha = 0.8f)  // Cancelled (left swipe)
        else -> cardItem.color
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(300.dp)
                .height(400.dp)
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .rotate(rotation)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            // Check if swipe threshold is reached
                            if (offsetX > swipeThreshold) {
                                // Swiped right (approved)
                                onSwiped(true)
                            } else if (offsetX < -swipeThreshold) {
                                // Swiped left (cancelled)
                                onSwiped(false)
                            } else {
                                // Reset position if not swiped enough
                                offsetX = 0f
                                offsetY = 0f
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    )
                },
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = cardItem.title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Show image if enabled for this card
                if (cardItem.showImage) {
                    Image(
                        painterResource(Res.drawable.compose_multiplatform),
                        contentDescription = "Card Image",
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .fillMaxWidth(0.7f)
                    )
                }

                Text(
                    text = cardItem.description,
                    style = MaterialTheme.typography.bodyLarge
                )

                // Swipe direction indicators
                when {
                    offsetX > 50 -> Text(
                        "APPROVED",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.Green,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    offsetX < -50 -> Text(
                        "CANCELLED",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.Red,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
@Preview
fun App() {
    // Sample cards
    val cardItems = remember {
        listOf(
            CardItem(
                id = 1, 
                title = "Swipe Cards Demo", 
                description = "Swipe right to approve or left to cancel. Try it now!",
                showImage = true,
                color = Color(0xFF6200EE)
            ),
            CardItem(
                id = 2, 
                title = "Multiplatform App", 
                description = "This app works on Android, iOS, and Desktop platforms using Kotlin Multiplatform and Compose.",
                showImage = true,
                color = Color(0xFF03DAC5)
            ),
            CardItem(
                id = 3, 
                title = "No Image Card", 
                description = "This card doesn't display an image to show the flexibility of the card design.",
                showImage = false,
                color = Color(0xFFE91E63)
            ),
            CardItem(
                id = 4, 
                title = "Gesture Detection", 
                description = "The cards use Compose's gesture detection to track swipe movements and animations for visual feedback.",
                showImage = true,
                color = Color(0xFF009688)
            ),
            CardItem(
                id = 5, 
                title = "Final Card", 
                description = "This is the last card in the deck. After swiping, you'll see a restart button.",
                showImage = true,
                color = Color(0xFF673AB7)
            )
        )
    }

    // Current card index
    var currentCardIndex by remember { mutableStateOf(0) }

    // Track last swipe action
    var lastSwipeApproved by remember { mutableStateOf<Boolean?>(null) }

    // Whether all cards have been swiped
    val allCardsProcessed = currentCardIndex >= cardItems.size

    MaterialTheme {
        Column(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App title
            Text(
                text = "Slovy Movy Card Swiper",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )

            // Instructions or feedback
            if (lastSwipeApproved != null) {
                Text(
                    text = if (lastSwipeApproved == true) "Last card: APPROVED ✅" else "Last card: CANCELLED ❌",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (lastSwipeApproved == true) Color.Green else Color.Red,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                Text(
                    text = "Swipe cards left to cancel or right to approve",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Card or completion message
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (!allCardsProcessed) {
                    // Show current card
                    SwipeableCard(
                        cardItem = cardItems[currentCardIndex],
                        onSwiped = { approved ->
                            // Update last swipe action
                            lastSwipeApproved = approved
                            // Move to next card
                            currentCardIndex++
                        }
                    )
                } else {
                    // All cards processed
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "All cards processed!",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Button(
                            onClick = { 
                                currentCardIndex = 0
                                lastSwipeApproved = null
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Restart")
                        }
                    }
                }
            }
        }
    }
}
