package cn.ac.oac.glimbrowser.logic

import cn.ac.oac.glimbrowser.model.HomeCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 浏览壳顶层状态分发单测（Phase 2：IA 导航、地址栏提交、首页卡片开关）。
 */
class BrowserStateTest {

    private val actions = BrowserActions()

    @Test
    fun initialStateOnHome() {
        val s = actions.initial()
        assertTrue(s.onHome)
        assertEquals(NavDestination.HOME, s.navDestination)
        assertFalse(s.tabSwitcherOpen)
        assertFalse(s.menuOpen)
    }

    @Test
    fun submitSearchNavigatesAwayFromHome() {
        var s = actions.initial()
        s = actions.onAddressBarChange(s, "hello world")
        s = actions.submitAddressBar(s)   // 默认编码器恒等
        assertFalse(s.onHome)
        assertTrue(s.tabs.currentTab!!.url.startsWith("https://www.baidu.com/"))  // 默认引擎百度
        assertFalse(s.addressBarFocused)
        assertEquals("", s.addressBarInput)
    }

    @Test
    fun submitUrlSetsNormalizedHttps() {
        var s = actions.initial()
        s = actions.onAddressBarChange(s, "example.com/a")
        s = actions.submitAddressBar(s)
        assertEquals("https://example.com/a", s.tabs.currentTab!!.url)
        assertFalse(s.onHome)
    }

    @Test
    fun navigateToTabsOpensTabSwitcher() {
        var s = actions.initial()
        s = actions.navigateTo(s, NavDestination.TABS)
        assertTrue(s.tabSwitcherOpen)
        assertEquals(NavDestination.TABS, s.navDestination)
        assertFalse(s.menuOpen)
    }

    @Test
    fun navigateToMenuClosesTabSwitcher() {
        var s = actions.initial()
        s = actions.navigateTo(s, NavDestination.TABS)
        s = actions.navigateTo(s, NavDestination.MENU)
        assertTrue(s.menuOpen)
        assertFalse(s.tabSwitcherOpen)
    }

    @Test
    fun newTabFromTabSwitcherReturnsHome() {
        var s = actions.initial()
        s = actions.navigateTo(s, NavDestination.TABS)
        s = actions.newTab(s)
        assertFalse(s.tabSwitcherOpen)
        assertEquals(NavDestination.HOME, s.navDestination)
        assertEquals(2, s.tabs.tabs.size)
        assertTrue(s.onHome)   // 新标签落首页
    }

    @Test
    fun homeCardToggles() {
        var s = actions.initial()
        assertTrue(HomeCard.entries.all { it.id in s.enabledHomeCards })  // 默认全开
        s = actions.setHomeCardEnabled(s, HomeCard.AI.id, enabled = false)
        assertFalse(HomeCard.AI.id in s.enabledHomeCards)
        assertTrue(HomeCard.PRIVACY.id in s.enabledHomeCards)
        s = actions.setAllHomeCardsEnabled(s, enabled = false)
        assertTrue(s.enabledHomeCards.isEmpty())
        s = actions.setAllHomeCardsEnabled(s, enabled = true)
        assertEquals(HomeCard.entries.size, s.enabledHomeCards.size)
    }

    @Test
    fun aiDrawerAndPurifyToggleIndependently() {
        var s = actions.initial()
        s = actions.toggleAiDrawer(s, true)
        assertTrue(s.aiDrawerOpen)
        assertFalse(s.purifyReportOpen)
        s = actions.togglePurifyReport(s, true)
        assertTrue(s.aiDrawerOpen && s.purifyReportOpen)
    }

    @Test
    fun selectTabClosesSwitcher() {
        var s = actions.initial()
        s = actions.newTab(s)
        val firstId = s.tabs.tabs.first().id
        s = actions.toggleTabSwitcher(s, true)
        s = actions.selectTab(s, firstId)
        assertFalse(s.tabSwitcherOpen)
        assertEquals(firstId, s.tabs.currentTabId)
    }

    @Test
    fun containerCallbackPropagatesToState() {
        var s = actions.initial()
        val id = s.tabs.currentTabId!!
        s = actions.onTabNavigated(s, id, "https://news.example.cn/tech", title = "科技新闻")
        assertEquals("科技新闻", s.tabs.currentTab!!.title)
        assertEquals("news.example.cn", s.tabs.currentTab!!.faviconDomain)
        s = actions.onTabLoading(s, id, isLoading = true, progress = 0.42f)
        assertEquals(0.42f, s.tabs.currentTab!!.progress)
        assertTrue(s.tabs.currentTab!!.isLoading)
    }
}
