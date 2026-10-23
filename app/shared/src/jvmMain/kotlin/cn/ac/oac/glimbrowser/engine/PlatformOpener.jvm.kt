package cn.ac.oac.glimbrowser.engine

import java.awt.Desktop
import java.net.URI

actual fun openExternally(url: String): Boolean = runCatching {
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(URI(url)); true
    } else false
}.getOrDefault(false)
