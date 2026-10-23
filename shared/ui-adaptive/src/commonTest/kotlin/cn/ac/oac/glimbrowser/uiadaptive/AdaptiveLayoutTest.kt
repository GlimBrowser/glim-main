package cn.ac.oac.glimbrowser.uiadaptive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PRD V1.3 §3.5.1 导航模式推导 + 三栏/AI 停靠/密度策略单测（验收：000 §4 Phase 1）。
 */
class AdaptiveLayoutTest {

    @Test
    fun navigationModeMapping() {
        assertEquals(NavigationMode.BOTTOM_BAR, NavigationMode.fromWidthClass(WindowWidthClass.COMPACT))
        assertEquals(NavigationMode.DRAWER, NavigationMode.fromWidthClass(WindowWidthClass.MEDIUM))
        assertEquals(NavigationMode.THREE_PANE, NavigationMode.fromWidthClass(WindowWidthClass.EXPANDED))
    }

    @Test
    fun compactLayout() {
        val l = deriveAdaptiveLayout(390)
        assertEquals(WindowWidthClass.COMPACT, l.widthClass)
        assertEquals(NavigationMode.BOTTOM_BAR, l.navigationMode)
        assertFalse(l.showPanelColumn)
        assertFalse(l.showNavigationRail)
        assertEquals(AiPanelPresentation.BOTTOM_SHEET, l.aiPanelPresentation)
        assertEquals(UiDensity.COMFORTABLE, l.density)
    }

    @Test
    fun mediumLayout() {
        // Pad（768dp）按桌面三栏布局（用户要求：Pad 端布局同桌面端）
        val l = deriveAdaptiveLayout(768)
        assertEquals(WindowWidthClass.MEDIUM, l.widthClass)
        assertEquals(NavigationMode.THREE_PANE, l.navigationMode)
        assertTrue(l.showPanelColumn)
        assertTrue(l.showNavigationRail)
        assertEquals(AiPanelPresentation.DOCKED_HALF, l.aiPanelPresentation)
        assertEquals(UiDensity.COMPACT, l.density)
    }

    @Test
    fun expandedLayout() {
        val l = deriveAdaptiveLayout(1280)
        assertEquals(WindowWidthClass.EXPANDED, l.widthClass)
        assertEquals(NavigationMode.THREE_PANE, l.navigationMode)
        assertTrue(l.showPanelColumn)
        assertTrue(l.showNavigationRail)
        assertEquals(AiPanelPresentation.DOCKED_SIDE, l.aiPanelPresentation)  // 右侧常驻停靠
        assertEquals(UiDensity.COMPACT, l.density)                            // §3.5.3 紧凑密度
    }

    @Test
    fun widthClassOverloadMatchesIntOverload() {
        for (w in listOf(0, 390, 599, 600, 768, 999, 1000, 1024, 1600)) {
            assertEquals(deriveAdaptiveLayout(w), deriveAdaptiveLayout(WindowWidthClass.fromWidthDp(w)), "width=$w")
        }
    }
}
