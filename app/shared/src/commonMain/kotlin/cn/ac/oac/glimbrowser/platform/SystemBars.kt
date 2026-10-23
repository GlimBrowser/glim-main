package cn.ac.oac.glimbrowser.platform

/**
 * 系统栏（状态栏/导航栏）图标明暗跟随应用主题。
 */
expect fun applySystemBarTheme(dark: Boolean)

/**
 * 沉浸式浏览：隐藏/显示系统栏（状态栏 + 导航栏），滑动可临时唤出。
 * 只用于"网页下滑隐藏悬浮底栏"时同时隐藏系统栏，避免出现两条底部栏。
 */
expect fun setSystemBarsHidden(hidden: Boolean)
