package cn.ac.oac.glimbrowser

import cn.ac.oac.glimbrowser.uiadaptive.WindowWidthClass
import cn.ac.oac.glimbrowser.uiadaptive.deriveAdaptiveLayout
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase 1 接线验证：确认 :shared:ui-adaptive 已作为 commonMain 依赖接入 :app:shared，
 * UI 层可直接 import 并调用自适应布局推导（对齐 PRD V1.3 §2.4 模块依赖关系）。
 */
class UiAdaptiveWiringTest {

    @Test
    fun uiAdaptiveApiIsReachableFromAppShared() {
        val layout = deriveAdaptiveLayout(1280)
        assertEquals(WindowWidthClass.EXPANDED, layout.widthClass)
    }
}
