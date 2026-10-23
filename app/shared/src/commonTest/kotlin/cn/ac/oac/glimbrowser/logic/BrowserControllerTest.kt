package cn.ac.oac.glimbrowser.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * BrowserController（状态持有 + Event 分发）单测 —— Phase 2 关键防回归：
 * 确保 dispatch 真正落回 state（此前 UI 层曾有"丢弃返回状态"的 bug 类，见 102 文档）。
 */
class BrowserControllerTest {

    @Test
    fun initialIsOnHomeWithOneTab() {
        val c = BrowserController()
        assertTrue(c.state.onHome)
        assertEquals(1, c.state.tabs.tabs.size)
        assertFalse(c.state.menuOpen)
    }

    @Test
    fun dispatchAddressBarSubmitMovesOffHomeAndPersists() {
        val c = BrowserController()
        c.dispatch(BrowserController.Event.AddressBarInput("example.com/x"))
        assertEquals("example.com/x", c.state.addressBarInput)
        assertTrue(c.state.addressBarFocused)
        c.dispatch(BrowserController.Event.SubmitAddressBar)
        assertFalse(c.state.addressBarFocused)
        assertEquals("", c.state.addressBarInput)
        assertEquals("https://example.com/x", c.state.currentTab?.url)   // 百分号编码器 + https 补全
        assertFalse(c.state.onHome)
    }

    @Test
    fun dispatchSearchPersistsEncodedQuery() {
        val c = BrowserController()
        c.dispatch(BrowserController.Event.AddressBarInput("kmp 多平台"))
        c.dispatch(BrowserController.Event.SubmitAddressBar)
        val url = c.state.currentTab!!.url
        assertTrue(url.startsWith("https://www.baidu.com/"))
        assertTrue(url.contains("kmp%20"))  // encodeUrlQuery 生效（空格→%20）
        assertFalse(url.contains(" "))      // 不残留裸空格
    }

    @Test
    fun dispatchNewTabAndSelectAndClose() {
        val c = BrowserController()
        c.dispatch(BrowserController.Event.NewTab)
        assertEquals(2, c.state.tabs.tabs.size)
        val firstId = c.state.tabs.tabs.first().id
        c.dispatch(BrowserController.Event.Navigate(NavDestination.TABS))
        assertTrue(c.state.tabSwitcherOpen)
        c.dispatch(BrowserController.Event.SelectTab(firstId))
        assertEquals(firstId, c.state.tabs.currentTabId)
        assertFalse(c.state.tabSwitcherOpen)   // selectTab 回首页并关切换器
        c.dispatch(BrowserController.Event.CloseTab(firstId))
        assertEquals(1, c.state.tabs.tabs.size)
    }

    @Test
    fun dispatchOpenUrlInNewTabJumpsHomeWithUrl() {
        val c = BrowserController()
        c.dispatch(BrowserController.Event.Navigate(NavDestination.TABS))
        c.dispatch(BrowserController.Event.OpenUrlInNewTab("https://news.example.cn/tech"))
        assertTrue(c.state.tabSwitcherOpen.not())
        assertEquals("news.example.cn", c.state.currentTab?.faviconDomain)
        assertFalse(c.state.onHome)
    }

    @Test
    fun dispatchMenuAndIncognito() {
        val c = BrowserController()
        c.dispatch(BrowserController.Event.ToggleMenu(true))
        assertTrue(c.state.menuOpen)
        assertEquals(NavDestination.MENU, c.state.navDestination)
        c.dispatch(BrowserController.Event.SetIncognito(true))
        assertTrue(c.state.tabs.incognito)
        c.dispatch(BrowserController.Event.ToggleMenu(false))
        assertFalse(c.state.menuOpen)
    }

    @Test
    fun dispatchHomeCardToggles() {
        val c = BrowserController()
        c.dispatch(BrowserController.Event.CloseAllHomeCards)
        assertTrue(c.state.enabledHomeCards.isEmpty())
        c.dispatch(BrowserController.Event.SetHomeCard("privacy", true))
        assertEquals(setOf("privacy"), c.state.enabledHomeCards)
        c.dispatch(BrowserController.Event.OpenAllHomeCards)
        assertEquals(2, c.state.enabledHomeCards.size)
    }

    @Test
    fun dispatchContainerCallbacksUpdateTab() {
        val c = BrowserController()
        val id = c.state.tabs.currentTabId!!
        c.dispatch(BrowserController.Event.TabNavigated(id, "https://tech.example.com/article", "技术文章"))
        assertEquals("技术文章", c.state.currentTab?.title)
        assertEquals("tech.example.com", c.state.currentTab?.faviconDomain)
        c.dispatch(BrowserController.Event.TabLoading(id, loading = true, progress = 0.5f))
        assertTrue(c.state.currentTab!!.isLoading)
        assertEquals(0.5f, c.state.currentTab!!.progress)
    }

    @Test
    fun dispatchSearchEngineSwitch() {
        val c = BrowserController()
        c.dispatch(BrowserController.Event.SetSearchEngine(SearchEngine.GOOGLE))
        c.dispatch(BrowserController.Event.AddressBarInput("hello"))
        c.dispatch(BrowserController.Event.SubmitAddressBar)
        assertTrue(c.state.currentTab!!.url.startsWith("https://www.google.com/"))
    }
}
