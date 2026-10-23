package cn.ac.oac.glimbrowser.engine

actual fun openExternally(url: String): Boolean {
    js("window.open(url, '_blank')")
    return true
}
