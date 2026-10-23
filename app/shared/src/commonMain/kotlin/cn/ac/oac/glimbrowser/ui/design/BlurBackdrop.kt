package cn.ac.oac.glimbrowser.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 真实模糊背景（不是半透明假玻璃）。
 *
 * 实现方式：按平台抓取窗口在「该区域」的像素 → 高斯/降采样模糊 → 作为背景图绘制，
 * 再在上面叠加半透明色调与边框。这样看到的是**真实的背后内容被模糊**。
 *  - Android：PixelCopy（窗口像素）→ 1/12 降采样再上采样（真实低通滤波）
 *  - Desktop(JVM)：java.awt.Robot 截取窗口区域 → 缩放模糊
 *  - iOS/macOS：UIKit/AppKit 快照 + CoreImage 高斯模糊
 *  - Web：CSS backdrop-filter（浏览器原生真实模糊）
 */
@Composable
expect fun PlatformBlurBackdrop(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    tintAlpha: Float = 0.35f,
)

/** 无平台实现时的安全占位（不伪造模糊，仅给底色）。 */
@Composable
internal fun FallbackBackdrop(modifier: Modifier) {
    Box(modifier = modifier)
}
