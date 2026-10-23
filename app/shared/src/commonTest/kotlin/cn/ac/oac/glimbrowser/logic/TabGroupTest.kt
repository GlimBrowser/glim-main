package cn.ac.oac.glimbrowser.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 标签分组 / 回收站 / 搜索。 */
class TabGroupTest {

    @Test
    fun closeTabGoesToTrashAndCanRestore() {
        val c = BrowserController()
        c.dispatch(BrowserController.Event.OpenUrlInNewTab("https://a.example/1"))
        val id = c.state.currentTab!!.id
        c.dispatch(BrowserController.Event.CloseTab(id))
        assertEquals(1, c.state.tabs.closedTabs.size)
        val closedAt = c.state.tabs.closedTabs.first().closedAt
        c.dispatch(BrowserController.Event.RestoreClosedTab(closedAt))
        assertTrue(c.state.tabs.tabs.any { it.url == "https://a.example/1" })
        assertTrue(c.state.tabs.closedTabs.isEmpty())
    }

    @Test
    fun incognitoTabNotStoredInTrash() {
        val c = BrowserController()
        c.dispatch(BrowserController.Event.SetIncognito(true))
        c.dispatch(BrowserController.Event.NewTab)
        val id = c.state.currentTab!!.id
        assertTrue(c.state.currentTab!!.isPrivate)
        c.dispatch(BrowserController.Event.CloseTab(id))
        assertTrue(c.state.tabs.closedTabs.isEmpty())
    }

    @Test
    fun groupAssignAndClear() {
        val c = BrowserController()
        val id = c.state.currentTab!!.id
        c.dispatch(BrowserController.Event.SetTabGroup(id, "工作"))
        assertEquals("工作", c.state.currentTab!!.group)
        c.dispatch(BrowserController.Event.SetTabGroup(id, "  "))
        assertNull(c.state.currentTab!!.group)
    }

    @Test
    fun tabQueryFiltersNothingButStoresQuery() {
        val c = BrowserController()
        c.dispatch(BrowserController.Event.SetTabQuery("kmp"))
        assertEquals("kmp", c.state.tabs.tabQuery)
        c.dispatch(BrowserController.Event.SetTabQuery(""))
        assertEquals("", c.state.tabs.tabQuery)
    }

    @Test
    fun clearClosedEmptiesTrash() {
        val c = BrowserController()
        c.dispatch(BrowserController.Event.OpenUrlInNewTab("https://b.example/"))
        c.dispatch(BrowserController.Event.CloseTab(c.state.currentTab!!.id))
        assertNotNull(c.state.tabs.closedTabs.firstOrNull())
        c.dispatch(BrowserController.Event.ClearClosedTabs)
        assertTrue(c.state.tabs.closedTabs.isEmpty())
    }
}
