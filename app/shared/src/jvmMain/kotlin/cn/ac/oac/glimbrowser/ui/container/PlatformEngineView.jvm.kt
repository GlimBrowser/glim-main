package cn.ac.oac.glimbrowser.ui.container

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import cn.ac.oac.glimbrowser.engine.BrowserEngine
import cn.ac.oac.glimbrowser.engine.JavaFxWebViewEngine

/**
 * 桌面（JVM）actual：把 JavaFX WebView（JFXPanel）嵌进 Compose Desktop。
 */
@Composable
actual fun PlatformEngineView(engine: BrowserEngine, modifier: Modifier) {
    val fx = engine as? JavaFxWebViewEngine ?: return
    SwingPanel(
        modifier = modifier,
        background = androidx.compose.ui.graphics.Color.White,
        factory = { fx.panel },
    )
}
