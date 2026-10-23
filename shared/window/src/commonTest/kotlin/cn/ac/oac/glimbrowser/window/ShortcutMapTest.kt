package cn.ac.oac.glimbrowser.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 桌面快捷键映射单测（附件 E 桌面快捷键映射表逐条核对）。
 */
class ShortcutMapTest {

    @Test
    fun coreChordsMatchAttachmentE() {
        // 附件 E：⌘T 新标签 / ⌘N 新窗口 / ⌘W 关闭 / ⌘L 聚焦地址 / ⌘⇧N 无痕 / ⌘R 刷新 / ⌘⇧R 强刷 / ⌘F 查找
        assertEquals(AppAction.NEW_TAB, ShortcutMap.match(Chord("T", setOf(ModifierKey.META))))
        assertEquals(AppAction.NEW_WINDOW, ShortcutMap.match(Chord("N", setOf(ModifierKey.META))))
        assertEquals(AppAction.CLOSE_TAB, ShortcutMap.match(Chord("W", setOf(ModifierKey.META))))
        assertEquals(AppAction.FOCUS_ADDRESS, ShortcutMap.match(Chord("L", setFor(Meta()))))
        assertEquals(AppAction.NEW_INCOGNITO, ShortcutMap.match(Chord("N", setOf(ModifierKey.META, ModifierKey.SHIFT))))
        assertEquals(AppAction.RELOAD, ShortcutMap.match(Chord("R", setOf(ModifierKey.META))))
        assertEquals(AppAction.FORCE_RELOAD, ShortcutMap.match(Chord("R", setOf(ModifierKey.META, ModifierKey.SHIFT))))
        assertEquals(AppAction.FIND_IN_PAGE, ShortcutMap.match(Chord("F", setOf(ModifierKey.META))))
    }

    private fun setFor(@Suppress("UNUSED_PARAMETER") m: Meta): Set<ModifierKey> = setOf(ModifierKey.META)
    private class Meta

    @Test
    fun aiAndManagerChordsMatchAttachmentE() {
        assertEquals(AppAction.AI_DRAWER, ShortcutMap.match(Chord("A", setOf(ModifierKey.META, ModifierKey.SHIFT))))         // ⌘⇧A
        assertEquals(AppAction.AI_SUMMARIZE, ShortcutMap.match(Chord("S", setOf(ModifierKey.META, ModifierKey.SHIFT))))     // ⌘⇧S
        assertEquals(AppAction.OPEN_BOOKMARKS, ShortcutMap.match(Chord("B", setOf(ModifierKey.META, ModifierKey.ALT))))      // ⌘⌥B
        assertEquals(AppAction.OPEN_SETTINGS, ShortcutMap.match(Chord(",", setOf(ModifierKey.META))))                        // ⌘,
        // 标签切换 1-4（附件 E ⌘1…⌘8 子集）
        assertEquals(AppAction.SELECT_TAB_1, ShortcutMap.match(Chord("1", setOf(ModifierKey.META))))
        assertEquals(AppAction.SELECT_TAB_4, ShortcutMap.match(Chord("4", setOf(ModifierKey.META))))
        // 下一/上一标签（附件 E Ctrl+Tab / Ctrl+Shift+Tab）
        assertEquals(AppAction.NEXT_TAB, ShortcutMap.match(Chord("TAB", setOf(ModifierKey.CTRL))))
        assertEquals(AppAction.PREV_TAB, ShortcutMap.match(Chord("TAB", setOf(ModifierKey.CTRL, ModifierKey.SHIFT))))
    }

    @Test
    fun chordRenderIsStableAndSorted() {
        // 归一化渲染：修饰符顺序稳定（META+SHIFT 与 SHIFT+META 同串）
        val a = Chord("A", setOf(ModifierKey.META, ModifierKey.SHIFT))
        val b = Chord("A", setOf(ModifierKey.SHIFT, ModifierKey.META))
        assertEquals(a.render(), b.render())
        assertEquals("META+SHIFT+A", a.render())
        assertEquals("CTRL+TAB", Chord("TAB", setOf(ModifierKey.CTRL)).render())
    }

    @Test
    fun unboundChordReturnsNull() {
        assertNull(ShortcutMap.match(Chord("Z", setOf(ModifierKey.META, ModifierKey.ALT, ModifierKey.SHIFT))))
        assertNull(ShortcutMap.match(Chord("Q", emptySet())))
    }

    @Test
    fun chordForRoundTrips() {
        for (action in AppAction.entries) {
            val chord = ShortcutMap.chordFor(action)
            assertTrue(chord != null, "$action 必须有默认绑定（附件 E 覆盖全部动作）")
            assertEquals(action, ShortcutMap.match(chord!!), "$action 往返一致")
        }
    }

    @Test
    fun actionLookupById() {
        assertEquals(AppAction.AI_DRAWER, AppAction.byId("ai_drawer"))
        assertNull(AppAction.byId("nope"))
    }
}
