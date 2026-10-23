package cn.ac.oac.glimbrowser.engine

import cn.ac.oac.glimbrowser.ui.container.AndroidWebViewEngine

/** Android：系统 WebView 真实内核（PRD V1.3 §2.2.1）。isPrivate=无痕（禁用缓存/清 Cookie 由引擎处理）。 */
actual fun PlatformEngineFactory(): ((BrowserEngine.Listener?, Boolean) -> BrowserEngine)? = { listener, isPrivate ->
    AndroidWebViewEngine(isPrivate = isPrivate).apply { setListener(listener) }
}
