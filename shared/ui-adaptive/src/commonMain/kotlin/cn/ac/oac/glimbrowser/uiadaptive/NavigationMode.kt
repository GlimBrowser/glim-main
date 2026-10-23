package cn.ac.oac.glimbrowser.uiadaptive

/**
 * 导航模式（PRD V1.3 §3.5.1 表：Size Class → 导航模式 → 典型形态）。
 *
 * | Size Class | 导航模式                                   | 典型形态                     |
 * |------------|--------------------------------------------|------------------------------|
 * | Compact    | 底部导航 + 全屏栈式页面                     | 手机竖屏                     |
 * | Medium     | 侧边抽屉导航；AI 面板可停靠半屏             | Pad 竖屏、手机横屏、桌面窄窗 |
 * | Expanded   | 三栏：导航栏｜标签/面板栏｜内容区           | Pad 横屏、桌面主窗口         |
 */
enum class NavigationMode {
    /** Compact：底部导航 + 全屏栈式页面。 */
    BOTTOM_BAR,

    /** Medium：侧边抽屉导航（可收起为图标轨），AI 面板可停靠半屏。 */
    DRAWER,

    /** Expanded：三栏（左导航栏｜中面板栏｜内容区，PRD V1.3 §3.5.1 Expanded 三栏细则）。 */
    THREE_PANE,
    ;

    companion object {
        /** 由 SizeClass 推导导航模式（纯函数，可单测）。 */
        fun fromWidthClass(widthClass: WindowWidthClass): NavigationMode = when (widthClass) {
            WindowWidthClass.COMPACT -> BOTTOM_BAR
            WindowWidthClass.MEDIUM -> DRAWER
            WindowWidthClass.EXPANDED -> THREE_PANE
        }
    }
}
