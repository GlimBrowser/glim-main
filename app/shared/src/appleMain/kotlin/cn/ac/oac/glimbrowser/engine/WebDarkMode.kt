package cn.ac.oac.glimbrowser.engine

import platform.WebKit.WKWebView

/**
 * 网页深色适配（平台相关）：
 *  - iOS：`WKWebView.overrideUserInterfaceStyle` → 页面 `prefers-color-scheme` 变为 dark
 *  - macOS：`NSView.appearance = NSAppearanceNameDarkAqua`
 * 两者都无法强制"没有深色样式的站点"变暗（WKWebView 无 force-dark），这是与 Android 的差异，已在文档标注。
 */
expect fun applyWebDarkMode(webView: WKWebView, dark: Boolean)
