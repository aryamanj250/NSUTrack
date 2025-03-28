import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A custom pull stretch animation effect to replace the SwipeRefresh
 */
@Composable
fun PullStretchEffect(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // States for drag gesture
    var offsetY by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }
    var maxOffset by remember { mutableStateOf(0f) }

    // Constants to control the animation
    val maxScale = 1.08f
    val resistanceFactor = 0.4f
    val animationDuration = 350

    Box(
        modifier = modifier
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .scale(scale)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { /* Optional: Haptic feedback here */ },
                    onDragEnd = {
                        // Return to normal position with animation
                        coroutineScope.launch {
                            animate(
                                initialValue = offsetY,
                                targetValue = 0f,
                                animationSpec = tween(animationDuration)
                            ) { value, _ ->
                                offsetY = value
                            }

                            animate(
                                initialValue = scale,
                                targetValue = 1f,
                                animationSpec = tween(animationDuration)
                            ) { value, _ ->
                                scale = value
                            }
                        }
                    },
                    onDragCancel = {
                        // Ensure animation also runs on cancel
                        coroutineScope.launch {
                            animate(
                                initialValue = offsetY,
                                targetValue = 0f,
                                animationSpec = tween(animationDuration)
                            ) { value, _ ->
                                offsetY = value
                            }

                            animate(
                                initialValue = scale,
                                targetValue = 1f,
                                animationSpec = tween(animationDuration)
                            ) { value, _ ->
                                scale = value
                            }
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()

                        // Only allow pulling down from top
                        if (offsetY >= 0) {
                            // Apply resistance as the user drags further
                            val newOffset = offsetY + dragAmount * resistanceFactor

                            if (newOffset >= 0) {
                                offsetY = newOffset

                                // Calculate scale based on drag amount
                                val dragProgress = (offsetY / maxOffset).coerceIn(0f, 1f)
                                scale = 1f + dragProgress * (maxScale - 1f)
                            }
                        }
                    }
                )
            }
            .onSizeChanged {
                // Calculate maximum offset based on component height
                maxOffset = with(density) { 100.dp.toPx() }
            }
    ) {
        content()
    }
}