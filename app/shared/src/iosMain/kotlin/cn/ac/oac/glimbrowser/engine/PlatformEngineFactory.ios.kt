package cn.ac.oac.glimbrowser.engine

/** iOS：WKWebView 真实内核（PRD V1.3 §2.2.1；appleMain 共享 WkWebViewEngine）。 */
actual fun PlatformEngineFactory(): ((BrowserEngine.Listener?, Boolean) -> BrowserEngine)? = { listener, isPrivate ->
    WkWebViewEngine(onEvent = {}, isPrivate = isPrivate).apply { setListener(listener) }
}
