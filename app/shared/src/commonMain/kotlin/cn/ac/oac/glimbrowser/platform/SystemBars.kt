package cn.ac.oac.glimbrowser.platform

/**
 * 系统栏（状态栏/导航栏）图标明暗跟随应用主题。
 * edge-to-edge 下若不同步，会出现"深色主题 + 深色图标"看不见系统状态的问题。
 */
expect fun applySystemBarTheme(dark: Boolean)
