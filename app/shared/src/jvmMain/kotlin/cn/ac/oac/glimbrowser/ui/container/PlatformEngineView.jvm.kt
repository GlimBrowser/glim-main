package cn.ac.oac.glimbrowser.ui.container

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import cn.ac.oac.glimbrowser.engine.BrowserEngine
import cn.ac.oac.glimbrowser.engine.JavaFxWebViewEngine
import cn.ac.oac.glimbrowser.engine.JcefWebViewEngine

/**
 * 桌面（JVM）actual：Chromium（JCEF）优先，JavaFX 作为回退引擎。
 * 两者都是 Swing/AWT 组件，通过 Compose Desktop 的 SwingPanel 嵌入。
 */
@Composable
actual fun PlatformEngineView(engine: BrowserEngine, modifier: Modifier) {
    when (engine) {
        is JcefWebViewEngine -> SwingPanel(
            modifier = modifier,
            background = Color.White,
            factory = { engine.panel },
        )
        is JavaFxWebViewEngine -> SwingPanel(
            modifier = modifier,
            background = Color.White,
            factory = { engine.panel },
        )
        else -> Unit
    }
}
