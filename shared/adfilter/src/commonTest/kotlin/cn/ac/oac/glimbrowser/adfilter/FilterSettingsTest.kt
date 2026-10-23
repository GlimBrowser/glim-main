package cn.ac.oac.glimbrowser.adfilter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 开关状态机单测（PRD V1.0 §5.1 + V1.1 §3.3.3 默认值红线 + §3.3.3 误杀闭环）。
 */
class FilterSettingsTest {

    @Test
    fun defaultIsFullyOn_redLine() {
        // PRD V1.1 §3.3.2 默认值红线：L1–L3 首启即启用、全局默认 ON
        val s = FilterSettings()
        assertTrue(s.globalEnabled)
        assertTrue(s.layerEnabled[FilterSettings.Layer.L1] == true)
        assertTrue(s.layerEnabled[FilterSettings.Layer.L2] == true)
        assertTrue(s.layerEnabled[FilterSettings.Layer.L3] == true)
        assertFalse(s.layerEnabled[FilterSettings.Layer.L4] == true)  // L4 用户规则按需
        assertEquals(FilterSettings.SiteMode.NORMAL, s.effectiveModeFor("any.site"))
        assertTrue(s.shouldBlockNetwork("any.site", isTrackerRequest = false))
    }

    @Test
    fun pauseSiteBlocksAll_noKillSwitch() {
        val s = FilterSettings().pauseSite("broken.site")
        assertFalse(s.shouldBlockNetwork("broken.site", isTrackerRequest = false))
        assertFalse(s.shouldBlockNetwork("broken.site", isTrackerRequest = true))
        assertFalse(s.shouldCosmeticHide("broken.site"))
        // 其它站点不受影响
        assertTrue(s.shouldBlockNetwork("other.site", isTrackerRequest = false))
    }

    @Test
    fun trackersOnlyModeKeepsTrackerBlocking() {
        // PRD §5.1：站点白名单 → "仅追踪器拦截"
        val s = FilterSettings().withSiteMode("news.site", FilterSettings.SiteMode.TRACKERS_ONLY)
        assertTrue(s.shouldBlockNetwork("news.site", isTrackerRequest = true))
        assertFalse(s.shouldBlockNetwork("news.site", isTrackerRequest = false))
    }

    @Test
    fun globalOffDisablesEverything() {
        val s = FilterSettings().withGlobalEnabled(false).pauseSite("x.com").resumeSite("x.com")
        assertFalse(s.shouldBlockNetwork("x.com", isTrackerRequest = true))
        assertFalse(s.shouldCosmeticHide("x.com"))
    }

    @Test
    fun layerL1OffStopsNetworkBlocking() {
        val s = FilterSettings().withLayer(FilterSettings.Layer.L1, false)
        assertFalse(s.shouldBlockNetwork("a.site", isTrackerRequest = false))
        // L3 仍在 → cosmetic 仍隐藏（shouldCosmeticHide: L1||L3）
        assertTrue(s.shouldCosmeticHide("a.site"))
    }

    @Test
    fun siteModeOperationsArePureAndReversible() {
        var s = FilterSettings()
        s = s.pauseSite("a.com")
        assertEquals(FilterSettings.SiteMode.DISABLED, s.siteModes["a.com"])
        s = s.resumeSite("a.com")
        assertFalse(s.siteModes.containsKey("a.com"))   // NORMAL 从表中移除（不存冗余项）
        assertEquals(FilterSettings.SiteMode.NORMAL, s.effectiveModeFor("a.com"))
    }

    @Test
    fun incognitoStillFiltersByDefault() {
        assertTrue(FilterSettings().incognitoStillFilters)  // 无痕只影响历史/缓存，不影响净化（PRD §3.2.5/§5.2 语义）
    }
}
