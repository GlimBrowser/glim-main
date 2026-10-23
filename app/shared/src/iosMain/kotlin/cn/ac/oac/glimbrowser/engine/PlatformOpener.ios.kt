package cn.ac.oac.glimbrowser.engine

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openExternally(url: String): Boolean {
    val nsUrl = NSURL(string = url) ?: return false
    return UIApplication.sharedApplication.openURL(nsUrl)
}
