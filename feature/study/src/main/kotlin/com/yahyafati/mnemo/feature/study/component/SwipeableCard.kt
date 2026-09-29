package com.yahyafati.mnemo.feature.study.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.yahyafati.mnemo.feature.study.R
import kotlinx.coroutines.launch

/**
 * Horizontal swipe to answer: left for Again, right for Good. Past 30% of the width the card flies
 * off and the callback fires; otherwise it springs back.
 *
 * The offset lives in an [Animatable] read only inside `graphicsLayer`, so dragging redraws the
 * card without recomposing it. [content] gets the same offset as a fraction of the width (-1…1)
 * for swipe hints. Key this composable by card so each card starts centered.
 *
 * Swiping isn't available to screen readers, so the same two answers are offered as custom
 * accessibility actions.
 */
@Composable
internal fun SwipeableCard(
    enabled: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(swipeFraction: () -> Float) -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(1) }
    val left by rememberUpdatedState(onSwipeLeft)
    val right by rememberUpdatedState(onSwipeRight)
    val againLabel = stringResource(R.string.feature_study_swipe_again)
    val goodLabel = stringResource(R.string.feature_study_swipe_good)

    Box(
        modifier = modifier
            .semantics {
                if (enabled) {
                    customActions = listOf(
                        CustomAccessibilityAction(againLabel) { left(); true },
                        CustomAccessibilityAction(goodLabel) { right(); true },
                    )
                }
            }
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .graphicsLayer {
                translationX = offset.value
                rotationZ = offset.value / width * MAX_ROTATION
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val threshold = width * SWIPE_THRESHOLD
                            when {
                                offset.value > threshold -> {
                                    offset.animateTo(width * 1.5f, tween(FLY_OFF_MS))
                                    right()
                                }
                                offset.value < -threshold -> {
                                    offset.animateTo(-width * 1.5f, tween(FLY_OFF_MS))
                                    left()
                                }
                                else -> offset.animateTo(0f, spring())
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + dragAmount) }
                }
            },
    ) {
        content { (offset.value / width).coerceIn(-1f, 1f) }
    }
}

private const val SWIPE_THRESHOLD = 0.3f
private const val MAX_ROTATION = 8f
private const val FLY_OFF_MS = 160
