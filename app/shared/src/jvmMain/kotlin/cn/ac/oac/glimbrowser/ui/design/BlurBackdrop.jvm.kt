package cn.ac.oac.glimbrowser.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage

@Composable
actual fun PlatformBlurBackdrop(modifier: Modifier, radius: Dp, tintAlpha: Float) {
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var rect by remember { mutableStateOf<Rectangle?>(null) }
    LaunchedEffect(Unit) {
        val robot = runCatching { Robot() }.getOrNull() ?: return@LaunchedEffect
        while (isActive) {
            delay(400)
            val r = rect ?: continue
            if (r.width <= 4 || r.height <= 4) continue
            runCatching {
                val shot: BufferedImage = robot.createScreenCapture(r)
                val sw = (r.width / 12).coerceAtLeast(1)
                val sh = (r.height / 12).coerceAtLeast(1)
                val small = BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB)
                small.createGraphics().apply {
                    drawImage(shot, 0, 0, sw, sh, null)
                    dispose()
                }
                image = small.toComposeImageBitmap()
            }
        }
    }
    Box(modifier = modifier.onGloballyPositioned { c ->
        val b = c.boundsInWindow()
        rect = Rectangle(b.left.toInt(), b.top.toInt(), b.width.toInt().coerceAtLeast(1), b.height.toInt().coerceAtLeast(1))
    }) {
        image?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = tintAlpha * 0.18f)))
    }
}
