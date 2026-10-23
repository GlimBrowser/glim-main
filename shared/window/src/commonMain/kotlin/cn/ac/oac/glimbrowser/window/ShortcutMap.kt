package cn.ac.oac.glimbrowser.window

/**
 * 桌面快捷键映射（附件 E「桌面快捷键映射表（草案）」；PRD 附录 B：「共享映射表（Cmd/Ctrl 归一化）；平台层仅转发」）。
 *
 * 归一化：跨平台 Action 码 → 键序列；桌面平台层把物理按键（⌘/Ctrl/⌥/⇧ + key）翻译成 [Shortcut.Chord] 转发。
 * 移动端不消费本表（手势体系另列，PRD §3.5.2）。
 */
enum class AppAction(val id: String, val label: String) {
    NEW_TAB("new_tab", "新标签"),
    NEW_WINDOW("new_window", "新窗口"),
    CLOSE_TAB("close_tab", "关闭标签"),
    FOCUS_ADDRESS("focus_address", "聚焦地址栏"),
    NEW_INCOGNITO("new_incognito", "无痕窗口"),
    SELECT_TAB_1("select_tab_1", "切换标签 1"),
    SELECT_TAB_2("select_tab_2", "切换标签 2"),
    SELECT_TAB_3("select_tab_3", "切换标签 3"),
    SELECT_TAB_4("select_tab_4", "切换标签 4"),
    NEXT_TAB("next_tab", "下一标签"),
    PREV_TAB("prev_tab", "上一标签"),
    RELOAD("reload", "刷新"),
    FORCE_RELOAD("force_reload", "强制刷新"),
    FIND_IN_PAGE("find_in_page", "查找页内"),
    AI_DRAWER("ai_drawer", "AI 抽屉"),
    AI_SUMMARIZE("ai_summarize", "AI 摘要当前页"),
    OPEN_BOOKMARKS("open_bookmarks", "书签管理器"),
    OPEN_HISTORY("open_history", "历史"),
    OPEN_SETTINGS("open_settings", "设置"),
    ;

    companion object {
        fun byId(id: String): AppAction? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 按键修饰归一化（PRD 附录 B：Cmd/Ctrl 归一化 —— 平台层报告 metaOnMac/ctrlOnWin 后统一成本语）。
 */
enum class ModifierKey { CTRL, ALT, SHIFT, META }

/** 归一化键序列（如 CTRL+SHIFT+A / META+L；META 在 Windows 映射自 Ctrl 由平台层转换）。 */
data class Chord(val key: String, val modifiers: Set<ModifierKey> = emptySet()) {
    fun render(): String = (modifiers.sortedBy { it.name }.joinToString("+") { it.name } + "+" + key).trim('+')
}

/**
 * 快捷键映射表（附件 E 草案；`*` 平台：`mod` = macOS ⌘ / Windows Ctrl）。
 * 表值为归一化 Chord；key 用大写字母/功能键名（F5、TAB 等）。
 */
object ShortcutMap {
    private val table: Map<AppAction, Chord> = mapOf(
        AppAction.NEW_TAB to Chord("T", setOf(ModifierKey.META)),                       // ⌘T / Ctrl+T
        AppAction.NEW_WINDOW to Chord("N", setOf(ModifierKey.META)),                   // ⌘N / Ctrl+N
        AppAction.CLOSE_TAB to Chord("W", setOf(ModifierKey.META)),                    // ⌘W / Ctrl+W
        AppAction.FOCUS_ADDRESS to Chord("L", setOf(ModifierKey.META)),                // ⌘L / Ctrl+L
        AppAction.NEW_INCOGNITO to Chord("N", setOf(ModifierKey.META, ModifierKey.SHIFT)),   // ⌘⇧N
        AppAction.SELECT_TAB_1 to Chord("1", setOf(ModifierKey.META)),
        AppAction.SELECT_TAB_2 to Chord("2", setOf(ModifierKey.META)),
        AppAction.SELECT_TAB_3 to Chord("3", setOf(ModifierKey.META)),
        AppAction.SELECT_TAB_4 to Chord("4", setOf(ModifierKey.META)),
        AppAction.NEXT_TAB to Chord("TAB", setOf(ModifierKey.CTRL)),                   // Ctrl+Tab（macOS 为 ⌃Tab，Ctrl 语义同）
        AppAction.PREV_TAB to Chord("TAB", setOf(ModifierKey.CTRL, ModifierKey.SHIFT)),
        AppAction.RELOAD to Chord("R", setOf(ModifierKey.META)),
        AppAction.FORCE_RELOAD to Chord("R", setOf(ModifierKey.META, ModifierKey.SHIFT)),
        AppAction.FIND_IN_PAGE to Chord("F", setOf(ModifierKey.META)),
        AppAction.AI_DRAWER to Chord("A", setOf(ModifierKey.META, ModifierKey.SHIFT)),  // ⌘⇧A
        AppAction.AI_SUMMARIZE to Chord("S", setOf(ModifierKey.META, ModifierKey.SHIFT)), // ⌘⇧S
        AppAction.OPEN_BOOKMARKS to Chord("B", setOf(ModifierKey.META, ModifierKey.ALT)),  // ⌘⌥B
        AppAction.OPEN_HISTORY to Chord("H", setOf(ModifierKey.META)),                  // ⌘Y / Ctrl+H（macOS ⌘H 由表外注释说明差异）
        AppAction.OPEN_SETTINGS to Chord(",", setOf(ModifierKey.META)),                 // ⌘,
    )

    /** 归一化 Chord → Action（平台层转发的查表入口）。 */
    fun match(chord: Chord): AppAction? = table.entries.firstOrNull { it.value == chord }?.key

    /** Action → 默认 Chord（设置页展示/自定义重绑，PRD §3.5.3 设置）。 */
    fun chordFor(action: AppAction): Chord? = table[action]

    fun all(): Map<AppAction, Chord> = table.toMap()
}
