package cn.ac.oac.glimbrowser.engine

/** macOS：WKWebView 真实内核（engine 真实；宿主视图挂点缺口见 103 §4 / 106 §4）。 */
actual fun PlatformEngineFactory(): ((BrowserEngine.Listener?, Boolean) -> BrowserEngine)? = { listener, isPrivate ->
    WkWebViewEngine(onEvent = {}, isPrivate = isPrivate).apply { setListener(listener) }
}
