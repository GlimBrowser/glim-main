package cn.ac.oac.glimbrowser.uiadaptive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PRD V1.3 §3.5.1「中面板栏宽度可拖拽 280–480dp」钳制策略单测。
 */
class PanelColumnPolicyTest {

    @Test
    fun clampLowerBound() {
        assertEquals(PanelColumnPolicy.DEFAULT_WIDTH_DP, PanelColumnPolicy.clamp(0))   // 非正 → 默认宽度
        assertEquals(PanelColumnPolicy.DEFAULT_WIDTH_DP, PanelColumnPolicy.clamp(-100)) // 防御式：非正 → 默认
        assertEquals(PanelColumnPolicy.MIN_WIDTH_DP, PanelColumnPolicy.clamp(200))     // 低于下限 → 280
        assertEquals(PanelColumnPolicy.MIN_WIDTH_DP, PanelColumnPolicy.clamp(280))     // 恰好下限
    }

    @Test
    fun clampWithinRange() {
        assertEquals(320, PanelColumnPolicy.clamp(320))
        assertEquals(400, PanelColumnPolicy.clamp(400))
        assertEquals(480, PanelColumnPolicy.clamp(480))     // 恰好上限
    }

    @Test
    fun clampUpperBound() {
        assertEquals(PanelColumnPolicy.MAX_WIDTH_DP, PanelColumnPolicy.clamp(500))     // 超上限 → 480
        assertEquals(PanelColumnPolicy.MAX_WIDTH_DP, PanelColumnPolicy.clamp(9999))
    }

    @Test
    fun defaultWidthIsWithinRange() {
        assertTrue(PanelColumnPolicy.DEFAULT_WIDTH_DP in PanelColumnPolicy.MIN_WIDTH_DP..PanelColumnPolicy.MAX_WIDTH_DP)
    }
}
