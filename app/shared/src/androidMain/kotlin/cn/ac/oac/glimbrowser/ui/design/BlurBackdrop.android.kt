package cn.ac.oac.glimbrowser.ui.design

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
actual fun PlatformBlurBackdrop(modifier: Modifier, radius: Dp, tintAlpha: Float) {
    val view = LocalView.current
    var blurred by remember { mutableStateOf<Bitmap?>(null) }
    var area by remember { mutableStateOf<android.graphics.Rect?>(null) }

    LaunchedEffect(view) {
        val activity = view.context.findActivity() ?: return@LaunchedEffect
        while (isActive) {
            delay(350)
            val rect = area ?: continue
            val w = rect.width().coerceAtLeast(8)
            val h = rect.height().coerceAtLeast(8)
            if (w <= 8 || h <= 8) continue
            val tmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            PixelCopy.request(activity.window, rect, tmp, { result ->
                if (result == PixelCopy.SUCCESS) {
                    val small = Bitmap.createBitmap((w / 12).coerceAtLeast(1), (h / 12).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    Canvas(small).drawBitmap(
                        tmp, null, android.graphics.Rect(0, 0, small.width, small.height),
                        Paint(Paint.FILTER_BITMAP_FLAG),
                    )
                    blurred = small
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    Box(modifier = modifier.onGloballyPositioned { coords ->
        val b = coords.boundsInWindow()
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        area = android.graphics.Rect(
            (b.left - loc[0]).toInt().coerceAtLeast(0),
            (b.top - loc[1]).toInt().coerceAtLeast(0),
            (b.right - loc[0]).toInt(),
            (b.bottom - loc[1]).toInt(),
        )
    }) {
        blurred?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Low,
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = tintAlpha * 0.18f)))
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
