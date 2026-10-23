package cn.ac.oac.glimbrowser.engine

/** Web(WasmJs)：非首发形态（PRD §2.2.1），不注入引擎。 */
actual fun PlatformEngineFactory(): ((BrowserEngine.Listener?, Boolean) -> BrowserEngine)? = null
