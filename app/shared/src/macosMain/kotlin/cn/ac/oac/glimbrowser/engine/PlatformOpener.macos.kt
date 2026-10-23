package cn.ac.oac.glimbrowser.engine

import platform.Foundation.NSURL
import platform.AppKit.NSWorkspace

actual fun openExternally(url: String): Boolean {
    val nsUrl = NSURL(string = url) ?: return false
    return NSWorkspace.sharedWorkspace.openURL(nsUrl)
}
