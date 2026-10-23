package cn.ac.oac.glimbrowser.logic

import cn.ac.oac.glimbrowser.model.toDisplayDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PRD V1.0 §3.1.3 标签页管理单测（Phase 2 P0：多标签 20+、当前/后台、关闭相邻选中）。
 */
class TabManagerTest {

    private fun tm() = TabManager()

    @Test
    fun initialStateHasSingleNewTab() {
        val s = tm().initialState()
        assertEquals(1, s.tabs.size)
        assertEquals(s.tabs.first().id, s.currentTabId)
        assertEquals(TabManager.NEW_TAB_HOME_URL, s.currentTab?.url)
        assertFalse(s.tabs.first().isPrivate)
    }

    @Test
    fun openManyTabsUpToTwentyPlus() {
        val m = tm()
        var s = m.initialState()
        // PRD §3.1.3「支持 20+ 标签」：开到 21 个仍正常
        repeat(20) { s = m.open(s, url = "https://example.com/$it") }
        assertEquals(21, s.tabs.size)
        assertEquals(s.tabs.last().id, s.currentTabId)
        assertTrue(s.tabs.size >= 20)
    }

    @Test
    fun openInCapReturnsUnchanged() {
        val m = TabManager(maxTabs = 3)
        var s = m.initialState()
        s = m.open(s, "https://a.com")
        s = m.open(s, "https://b.com")
        assertEquals(3, s.tabs.size)
        val before = s
        s = m.open(s, "https://c.com")  // 超上限 → 拒绝
        assertEquals(before, s)
    }

    @Test
    fun backgroundOpenKeepsCurrentTab() {
        val m = tm()
        var s = m.initialState()
        val firstCurrent = s.currentTabId
        s = m.open(s, "https://background.example", background = true)
        assertEquals(firstCurrent, s.currentTabId)
        assertEquals(2, s.tabs.size)
    }

    @Test
    fun selectSwitchesCurrent() {
        val m = tm()
        var s = m.initialState()
        s = m.open(s, "https://second.example")
        val second = s.tabs.last().id
        val first = s.tabs.first().id
        s = m.select(s, first)
        assertEquals(first, s.currentTabId)
        s = m.select(s, second)
        assertEquals(second, s.currentTabId)
    }

    @Test
    fun selectUnknownIdIsNoop() {
        val m = tm()
        val s = m.initialState()
        assertEquals(s, m.select(s, id = 999L))
    }

    @Test
    fun closeCurrentSelectsRightNeighbor() {
        val m = tm()
        var s = m.initialState()
        s = m.open(s, "https://b.example")
        s = m.open(s, "https://c.example")   // 顺序 a,b,c；当前 c
        val a = s.tabs[0].id
        val b = s.tabs[1].id
        val c = s.tabs[2].id
        s = m.select(s, b)                    // 当前 = b（中间）
        s = m.close(s, b)
        assertEquals(2, s.tabs.size)
        assertEquals(c, s.currentTabId)       // 关中间 → 选右侧
        assertTrue(s.tabs.any { it.id == a })
        assertTrue(s.tabs.any { it.id == c })
    }

    @Test
    fun closeCurrentAtEndSelectsLeftNeighbor() {
        val m = tm()
        var s = m.initialState()
        s = m.open(s, "https://b.example")
        val a = s.tabs[0].id
        val b = s.tabs[1].id
        s = m.select(s, b)                    // 当前 = b（最右）
        s = m.close(s, b)
        assertEquals(a, s.currentTabId)       // 关最右 → 选左侧
    }

    @Test
    fun closeBackgroundTabKeepsCurrent() {
        val m = tm()
        var s = m.initialState()
        s = m.open(s, "https://b.example")
        val a = s.tabs[0].id
        val current = s.currentTabId
        s = m.close(s, a)                     // 关非当前
        assertEquals(current, s.currentTabId)
        assertEquals(1, s.tabs.size)
    }

    @Test
    fun closeAllTabsYieldsEmptyState() {
        val m = tm()
        var s = m.initialState()
        s = m.close(s, s.tabs.first().id)
        assertTrue(s.tabs.isEmpty())
        assertNull(s.currentTabId)
        assertNull(s.currentTab)
    }

    @Test
    fun closeUnknownIsNoop() {
        val m = tm()
        val s = m.initialState()
        assertEquals(s, m.close(s, 4242L))
    }

    @Test
    fun closeOthersKeepsOnlyTarget() {
        val m = tm()
        var s = m.initialState()
        s = m.open(s, "https://b.example")
        s = m.open(s, "https://c.example")
        val keep = s.tabs.first().id
        s = m.closeOthers(s, keep)
        assertEquals(1, s.tabs.size)
        assertEquals(keep, s.currentTabId)
    }

    @Test
    fun incognitoNewTabsArePrivate() {
        val m = tm()
        var s = m.initialState()
        s = m.setIncognito(s, true)
        s = m.open(s, "https://secret.example")
        assertTrue(s.tabs.last().isPrivate)
        assertFalse(s.tabs.first().isPrivate)  // 既有标签不变
    }

    @Test
    fun onNavigatedUpdatesUrlDomainAndTitleFallback() {
        val m = tm()
        var s = m.initialState()
        val id = s.tabs.first().id
        s = m.onNavigated(s, id, "https://tech.example.com/kmp-browser", title = null)
        val t = s.tabs.first()
        assertEquals("https://tech.example.com/kmp-browser", t.url)
        assertEquals("tech.example.com", t.faviconDomain)
        assertEquals(TabManager.NEW_TAB_TITLE, t.title)  // title=null 保留原值
    }

    @Test
    fun onLoadingClampsProgress() {
        val m = tm()
        var s = m.initialState()
        val id = s.tabs.first().id
        s = m.onLoading(s, id, isLoading = true, progress = 1.7f)
        assertEquals(1f, s.tabs.first().progress)
        assertTrue(s.tabs.first().isLoading)
        s = m.onLoading(s, id, isLoading = false, progress = -0.2f)
        assertEquals(0f, s.tabs.first().progress)
        assertFalse(s.tabs.first().isLoading)
    }
}
