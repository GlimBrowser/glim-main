package cn.ac.oac.glimbrowser.platform

import android.app.Activity
import androidx.core.view.WindowCompat

/** 由 MainActivity 在 onCreate 注入。 */
var currentActivity: Activity? = null

actual fun setSystemBarsHidden(hidden: Boolean) {
    val activity = currentActivity ?: return
    runCatching {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
}

actual fun applySystemBarTheme(dark: Boolean) {
    val activity = currentActivity ?: return
    runCatching {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = !dark   // 浅色底 → 深色图标
        controller.isAppearanceLightNavigationBars = !dark
    }
}
