package cn.ac.oac.glimbrowser.engine

import platform.AppKit.NSAppearance
import platform.AppKit.NSAppearanceNameAqua
import platform.AppKit.NSAppearanceNameDarkAqua
import platform.WebKit.WKWebView

actual fun applyWebDarkMode(webView: WKWebView, dark: Boolean) {
    webView.appearance = NSAppearance.appearanceNamed(
        if (dark) NSAppearanceNameDarkAqua else NSAppearanceNameAqua,
    )
}
