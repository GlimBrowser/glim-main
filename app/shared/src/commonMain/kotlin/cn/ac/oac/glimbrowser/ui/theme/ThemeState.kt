package cn.ac.oac.glimbrowser.ui.theme

/**
 * 当前应用是否深色（供非 Composable 上下文读取，如 WebView 创建时决定底色）。
 * 由 GlimTheme 在组合时写入，避免 WebView 首帧白闪。
 */
object ThemeState {
    var isDark: Boolean = false
        internal set
}
