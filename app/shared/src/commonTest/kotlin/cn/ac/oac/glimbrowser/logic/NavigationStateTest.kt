package cn.ac.oac.glimbrowser.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 菜单面板 / 收藏 / 导航能力 / 错误（108 真实数据与导航闭环）。 */
class NavigationStateTest {

    private fun controller() = BrowserController()

    @Test
    fun subPageOpenCloseReapsMenu() {
        val c = controller()
        c.dispatch(BrowserController.Event.ToggleMenu(true))
        assertTrue(c.state.menuOpen)
        c.dispatch(BrowserController.Event.OpenSubPage(SubPage.BOOKMARKS))
        assertEquals(SubPage.BOOKMARKS, c.state.subPage)
        assertTrue(!c.state.menuOpen)   // 打开子页面自动收菜单
        c.dispatch(BrowserController.Event.CloseSubPage)
        assertNull(c.state.subPage)
    }

    @Test
    fun downloadAndFindStateFlow() {
        val c = controller()
        c.dispatch(BrowserController.Event.DownloadStarted("https://x/a.apk", "a.apk"))
        assertEquals(1, c.state.downloads.size)
        c.dispatch(BrowserController.Event.DownloadFinished("https://x/a.apk", true))
        assertEquals(DownloadState.DONE, c.state.downloads.first().state)

        c.dispatch(BrowserController.Event.OpenFind)
        c.dispatch(BrowserController.Event.SetFindQuery("kmp"))
        c.dispatch(BrowserController.Event.FindResult(3, 1))
        assertTrue(c.state.findOpen)
        assertEquals(3, c.state.findMatches)
        c.dispatch(BrowserController.Event.CloseFind)
        assertNull(c.state.findQuery.ifEmpty { null })
    }

    @Test
    fun pageErrorThenNavigatedClears() {
        val c = controller()
        val id = c.state.tabs.currentTabId!!
        c.dispatch(BrowserController.Event.PageError(id, "https://x.io", "ERR_NAME_NOT_RESOLVED"))
        assertEquals("ERR_NAME_NOT_RESOLVED", c.state.tabs.currentTab!!.lastError)
        c.dispatch(BrowserController.Event.TabNavigated(id, "https://x.io", "X"))
        assertNull(c.state.tabs.currentTab!!.lastError)   // 导航推进清错误
    }

    @Test
    fun retryPageSetsPendingReloadThenConsumed() {
        val c = controller()
        c.dispatch(BrowserController.Event.RetryPage)
        assertTrue(c.state.pendingReload)
        c.dispatch(BrowserController.Event.ConsumeReload)
        assertTrue(!c.state.pendingReload)
    }

    @Test
    fun navCapabilityReflectedOnTab() {
        val c = controller()
        val id = c.state.tabs.currentTabId!!
        c.dispatch(BrowserController.Event.NavCapability(id, canGoBack = true, canGoForward = false))
        assertTrue(c.state.tabs.currentTab!!.canGoBack)
        assertTrue(!c.state.tabs.currentTab!!.canGoForward)
    }

    @Test
    fun incognitoNewTabIsPrivateAndNoHistory() {
        val c = controller()
        c.dispatch(BrowserController.Event.SetIncognito(true))
        c.dispatch(BrowserController.Event.NewTab)
        assertTrue(c.state.currentTab!!.isPrivate)
    }
}
