package cn.ac.oac.glimbrowser.uiadaptive

/**
 * 窗口宽度分级（PRD V1.3 §3.5.1）：以 WindowSizeClass 为唯一布局判定依据，
 * 形态差异由窗口尺寸自然驱动（Pad 横屏 ≈ 桌面窄窗，自动复用桌面布局）。
 *
 * 宽度参考（dp）：
 *  - Compact  ：< 600dp      → 底部导航 + 全屏栈式页面（手机竖屏）
 *  - Medium   ：600–1000dp   → 侧边抽屉导航；AI 面板可停靠半屏（Pad 竖屏/手机横屏/桌面窄窗）
 *  - Expanded ：> 1000dp     → 三栏：导航栏｜标签/面板栏｜内容区（Pad 横屏/桌面主窗口）
 */
enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
    ;

    companion object {
        /** Compact 上限（不含），与 PRD §3.5.1「< 600dp」一致。 */
        const val COMPACT_MAX_WIDTH_EXCLUSIVE_DP: Int = 600

        /** Medium 上限（不含），与 PRD §3.5.1「600–1000dp」一致（Expanded 为 >1000dp）。 */
        const val MEDIUM_MAX_WIDTH_EXCLUSIVE_DP: Int = 1000

        /**
         * 由窗口宽度（dp）推导 SizeClass。
         * @param widthDp 窗口可用宽度，单位 dp；负值按 0 处理（防御式，不影响正常调用）。
         */
        fun fromWidthDp(widthDp: Int): WindowWidthClass = when {
            widthDp < COMPACT_MAX_WIDTH_EXCLUSIVE_DP -> COMPACT
            widthDp < MEDIUM_MAX_WIDTH_EXCLUSIVE_DP -> MEDIUM
            else -> EXPANDED
        }
    }
}
