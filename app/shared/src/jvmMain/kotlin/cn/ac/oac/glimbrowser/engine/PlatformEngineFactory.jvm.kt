package cn.ac.oac.glimbrowser.engine

/**
 * 桌面（JVM）网页内核：
 * - 默认 **JavaFX WebView**（已验证可渲染，稳定）
 * - `GLIM_DESKTOP_ENGINE=jcef` 时切到 **JCEF / Chromium**（实验；需要 CEF 原生库，首次下载约 118MB）
 *
 * 说明：JCEF 的窗口模式在 Compose Desktop 的 SwingPanel 里尚未跑通（页面不加载），
 * 需要改为离屏渲染（CefBrowserOsr + paint 回调）后才能作为默认内核。
 */
actual fun PlatformEngineFactory(): ((BrowserEngine.Listener?, Boolean) -> BrowserEngine)? {
    val useJcef = System.getenv("GLIM_DESKTOP_ENGINE").orEmpty().equals("jcef", ignoreCase = true)
    return { listener, isPrivate ->
        if (useJcef) JcefWebViewEngine(listener, isPrivate) else JavaFxWebViewEngine(listener, isPrivate)
    }
}
