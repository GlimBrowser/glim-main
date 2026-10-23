package cn.ac.oac.glimbrowser.engine

import platform.UIKit.UIUserInterfaceStyle
import platform.WebKit.WKWebView

actual fun applyWebDarkMode(webView: WKWebView, dark: Boolean) {
    // NS_ENUM → Kotlin enum：UIUserInterfaceStyle.UIUserInterfaceStyleDark
    webView.overrideUserInterfaceStyle =
        if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
}
