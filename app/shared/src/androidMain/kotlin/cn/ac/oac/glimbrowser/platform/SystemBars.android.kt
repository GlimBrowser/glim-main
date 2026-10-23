package cn.ac.oac.glimbrowser.platform

import android.app.Activity
import androidx.core.view.WindowCompat

/** 由 MainActivity 在 onCreate 注入。 */
var currentActivity: Activity? = null

actual fun applySystemBarTheme(dark: Boolean) {
    val activity = currentActivity ?: return
    runCatching {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = !dark   // 浅色底 → 深色图标
        controller.isAppearanceLightNavigationBars = !dark
    }
}
