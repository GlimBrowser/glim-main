package cn.ac.oac.glimbrowser.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
actual fun PlatformBlurBackdrop(modifier: Modifier, radius: Dp, tintAlpha: Float) {
    Box(modifier = modifier) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = tintAlpha * 0.12f)))
    }
}
