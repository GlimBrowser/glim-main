package cn.ac.oac.glimbrowser.uiadaptive

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * PRD V1.3 §3.5.1 Size Class 边界值单测（验收：000 §4 Phase 1「SizeClass 单测通过」）。
 */
class WindowSizeClassTest {

    @Test
    fun compactBoundary() {
        assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.fromWidthDp(0))
        assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.fromWidthDp(390))   // 手机竖屏典型
        assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.fromWidthDp(599))   // <600 上界
    }

    @Test
    fun mediumBoundary() {
        assertEquals(WindowWidthClass.MEDIUM, WindowWidthClass.fromWidthDp(600))    // 恰好 600
        assertEquals(WindowWidthClass.MEDIUM, WindowWidthClass.fromWidthDp(768))    // Pad 竖屏典型
        assertEquals(WindowWidthClass.MEDIUM, WindowWidthClass.fromWidthDp(999))    // <1000 上界
    }

    @Test
    fun expandedBoundary() {
        assertEquals(WindowWidthClass.EXPANDED, WindowWidthClass.fromWidthDp(1000)) // 恰好 1000
        assertEquals(WindowWidthClass.EXPANDED, WindowWidthClass.fromWidthDp(1024)) // Pad 横屏典型
        assertEquals(WindowWidthClass.EXPANDED, WindowWidthClass.fromWidthDp(1600)) // 桌面主窗口
    }

    @Test
    fun defensiveNegativeWidthTreatedAsCompact() {
        assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.fromWidthDp(-1))
        assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.fromWidthDp(Int.MIN_VALUE))
    }
}
