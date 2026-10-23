package cn.ac.oac.glimbrowser.engine

/** Kotlin/Wasm：js() 必须是单表达式，用外部函数包装。 */
@Suppress("UNUSED_PARAMETER")
private fun openWindow(url: String): Boolean = js("window.open(url, '_blank'), true")

actual fun openExternally(url: String): Boolean = openWindow(url)
