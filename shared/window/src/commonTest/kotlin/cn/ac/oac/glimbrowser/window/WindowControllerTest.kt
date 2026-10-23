package cn.ac.oac.glimbrowser.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 多窗口控制器单测（PRD V1.3 附录 B：桌面真多窗口 / 移动栈式伪实现 + §3.5.2 标签拖出成窗）。
 */
class WindowControllerTest {

    @Test
    fun mobileStackControllerIsPseudo() {
        val c = StackWindowController()
        assertFalse(c.isNativeMultiWindow)
        assertNull(c.newWindow())                      // 伪实现：不建真窗
        assertEquals(listOf(StackWindowController.SINGLE_WINDOW), c.windowIds())
        assertNull(c.detachTab(1L, StackWindowController.SINGLE_WINDOW))  // 移动端无拖出
    }

    @Test
    fun desktopCreatesRealWindows() {
        val created = ArrayList<Pair<String, Long?>>()
        val closed = ArrayList<String>()
        val c = DesktopWindowController(onWindowCreate = { id, tab -> created.add(id to tab) }, onWindowClose = { closed.add(it) })
        assertTrue(c.isNativeMultiWindow)
        assertEquals(1, c.windowIds().size)             // init 已开主窗
        val w2 = c.newWindow()
        assertNotNull(w2)
        assertEquals(2, c.windowIds().size)
        assertEquals(2, created.size)
        c.closeWindow(w2!!)
        assertEquals(1, c.windowIds().size)
        assertEquals(listOf(w2), closed)
    }

    @Test
    fun detachTabOpensNewWindowCarryingTab() {
        // PRD §3.5.2 标签拖出成窗
        val c = DesktopWindowController()
        val main = c.windowIds().first()
        val w2 = c.detachTab(tabId = 42L, fromWindowId = main)
        assertNotNull(w2)
        assertEquals(42L, c.tabIn(w2!!))               // 新窗携带被拖出的标签
        assertEquals(2, c.windowIds().size)
        // 主窗未被移除（标签被"迁移"语义由调用方处理簿记，此处窗口本身保留）
        assertTrue(c.windowIds().contains(main))
    }

    @Test
    fun closeWindowIsIdempotentSafe() {
        val c = DesktopWindowController()
        val w = c.windowIds().first()
        c.closeWindow(w)
        c.closeWindow(w)                               // 重复关闭不抛
        assertTrue(c.windowIds().isEmpty())
    }
}
