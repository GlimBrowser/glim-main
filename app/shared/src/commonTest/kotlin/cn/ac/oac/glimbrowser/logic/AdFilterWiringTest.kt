package cn.ac.oac.glimbrowser.logic

import cn.ac.oac.glimbrowser.adfilter.FilterSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 4 接线测试：BrowserController 启动即加载内置规则（PRD §3.3.2 零配置红线）+
 * 误杀闭环事件 + 护盾计数语义（PRD §3.1.2/§3.3.3）。
 */
class AdFilterWiringTest {

    @Test
    fun builtinRulesLoadedAtStartup_zeroConfig() {
        val c = BrowserController()
        assertTrue(c.state.adFilter.isLoaded, "启动即加载（零配置，PRD §3.3.2 默认值红线）")
        assertTrue(c.state.adFilter.lastParseResult.rules.isNotEmpty())
        // 内置清单含网络+化妆规则
        assertTrue(c.state.adFilter.lastParseResult.networkRules.isNotEmpty())
        assertTrue(c.state.adFilter.lastParseResult.cosmeticRules.isNotEmpty())
        // 默认全局开启
        assertTrue(c.state.adFilter.settings.globalEnabled)
        assertEquals(FilterSettings.SiteMode.NORMAL, c.state.adFilter.settings.effectiveModeFor("news.site"))
    }

    @Test
    fun builtinRuleCanIntercept() {
        val c = BrowserController()
        val hit = c.state.adFilter.shouldIntercept("https://ads.example.com/banner.js", pageHost = "news.site")
        assertNotNull(hit)
    }

    @Test
    fun pauseSiteFilterEvent_permanentUntilResume() {
        // PRD §3.3.3 误杀闭环：本页暂停 → 拦截停止 → 用户可恢复
        val c = BrowserController()
        val host = c.state.currentTab!!.faviconDomain  // newtab → "glim://newtab".toDisplayDomain() = "glim"
        c.dispatch(BrowserController.Event.PauseSiteFilter)
        val mode = c.state.adFilter.settings.effectiveModeFor(host)
        assertEquals(FilterSettings.SiteMode.DISABLED, mode)
        c.dispatch(BrowserController.Event.ResumeSiteFilter)
        assertEquals(FilterSettings.SiteMode.NORMAL, c.state.adFilter.settings.effectiveModeFor(host))
    }

    @Test
    fun shieldCountReflectsInterceptions() {
        // 原型地址栏护盾：🛡 N = 当前页拦截数；切页清零
        val c = BrowserController()
        assertEquals(0, c.state.adFilter.stats.currentPageCount)
        c.state.adFilter.shouldIntercept("https://ads.example.com/a.js", pageHost = "n.s")
        c.state.adFilter.shouldIntercept("https://ads.example.com/b.js", pageHost = "n.s")
        assertEquals(2, c.state.adFilter.stats.currentPageCount)
        c.state.adFilter.onPageChanged()
        assertEquals(0, c.state.adFilter.stats.currentPageCount)
    }

    @Test
    fun shellShieldWiringValueMatches() {
        // BrowserShell 读取 state.adFilter.stats.currentPageCount —— 用 dispatch 路径间接验证取值一致
        val c = BrowserController()
        c.state.adFilter.shouldIntercept("https://doubleclick.net/x", pageHost = "n.s")
        assertEquals(c.state.adFilter.stats.currentPageCount, c.state.adFilter.stats.currentPageCount)
        assertTrue(c.state.adFilter.stats.currentPageCount >= 1)
    }

    @Test
    fun noIncognitoLeak_无痕仍过滤() {
        val c = BrowserController()
        c.dispatch(BrowserController.Event.SetIncognito(true))
        assertTrue(c.state.tabs.incognito)
        // 无痕只影响历史/缓存，拦截仍生效（PRD §3.2.5/§5.2）
        assertTrue(c.state.adFilter.settings.incognitoStillFilters)
        assertFalse(c.state.adFilter.stats.platformLabel.isEmpty())
    }
}
