package com.example.buddy.ui.chat

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp

@Composable
internal fun BoxScope.BubbleEdgeLongPress(
    onLongPress: (() -> Unit)?,
    horizontal: Dp,
    vertical: Dp
) {
    if (onLongPress == null) return
    val latestOnLongPress = rememberUpdatedState(onLongPress)
    val gesture = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress != null && !longPress.isConsumed) {
                longPress.consume()
                latestOnLongPress.value?.invoke()
            }
        }
    }
    Box(Modifier.matchParentSize()) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(vertical).then(gesture))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(vertical).then(gesture))
        Box(Modifier.align(Alignment.CenterStart).width(horizontal).fillMaxHeight().padding(vertical = vertical).then(gesture))
        Box(Modifier.align(Alignment.CenterEnd).width(horizontal).fillMaxHeight().padding(vertical = vertical).then(gesture))
    }
}
