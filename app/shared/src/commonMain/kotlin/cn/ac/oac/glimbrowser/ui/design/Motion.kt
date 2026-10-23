package cn.ac.oac.glimbrowser.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 入场动画（对应原型 .anim-1..5）：淡入 + 上移 8dp，按序延迟 70ms。
 * 仅在首次组合时播放一次。
 */
@Composable
fun Modifier.enterStagger(index: Int, distance: Int = 8): Modifier {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 420, delayMillis = index * 70),
        label = "enter-alpha-$index",
    )
    val dy by animateFloatAsState(
        targetValue = if (appeared) 0f else distance.toFloat(),
        animationSpec = tween(durationMillis = 460, delayMillis = index * 70),
        label = "enter-dy-$index",
    )
    return this
        .alpha(alpha)
        .graphicsLayer { translationY = dy.dp.toPx() }
}

/**
 * 卡片按压 spring 缩放（对应原型 `.qsite:active .tile{transform:scale(.92)}`、`.ai-btn` 等）。
 * 松手回弹使用 spring。
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 900f),
        label = "press-scale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}
