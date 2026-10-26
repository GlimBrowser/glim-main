package cn.ac.oac.glimbrowser.engine

/** 桌面（JVM）：JavaFX WebView 真实内核。 */
actual fun PlatformEngineFactory(): ((BrowserEngine.Listener?, Boolean) -> BrowserEngine)? =
    { listener, isPrivate -> JavaFxWebViewEngine(listener, isPrivate) }
