package cn.ac.oac.glimbrowser.window

/**
 * 多窗口控制器接口（PRD V1.3 附录 B：「共享 WindowController 接口；桌面真多窗口，移动栈式伪实现」）。
 *
 * 形态差异（PRD §3.5.2）：
 *  - 桌面（macOS/Windows）：真多窗口（newWindow 创建独立窗口实例；标签拖出成窗 = 新窗口接管标签）
 *  - 移动（Android/iOS/Pad）：栈式伪实现（同窗口内导航栈切换，[isNativeMultiWindow]=false）
 *
 * 平台实现（expect/actual 或 DI，PRD §2.4.2 同模式）：
 *  - 桌面 CMP(jvm)：Compose `Window{}` 多实例（Phase 6 桌面专项接入渲染层）
 *  - 移动：不创建真窗口，[newWindow] 退化为同窗栈式切换（返回 false 表示"已用伪实现处理"）
 */
interface WindowController {

    /** 当前平台是否支持真多窗口（桌面 true / 移动 false，PRD 附录 B）。 */
    val isNativeMultiWindow: Boolean

    /**
     * 新建窗口。
     * @param initialTabId 可选：迁移的标签 id（标签拖出成窗，PRD §3.5.2）
     * @return 窗口 id（真窗口）；伪实现返回 null 表示已在当前窗口栈式处理
     */
    fun newWindow(initialTabId: Long? = null): String?

    /** 关闭窗口（最后一个窗口按设置决定关应用/回首页，附件 E ⌘W 注）。 */
    fun closeWindow(windowId: String)

    /** 聚焦窗口。 */
    fun focusWindow(windowId: String)

    /** 当前窗口列表（真多窗口时 >1）。 */
    fun windowIds(): List<String>

    /**
     * 标签拖出成窗（PRD §3.5.2）：
     * 桌面 → 创建新窗口并迁移标签；移动 → 返回 null（无此交互，移动端用分屏/栈式替代）。
     */
    fun detachTab(tabId: Long, fromWindowId: String): String? = if (isNativeMultiWindow) newWindow(tabId) else null
}

/**
 * 移动端栈式伪实现（PRD 附录 B"移动栈式伪实现"）：不创建真窗口，记录一个虚拟栈。
 */
class StackWindowController : WindowController {
    override val isNativeMultiWindow: Boolean = false

    private var stackCounter = 0

    override fun newWindow(initialTabId: Long?): String? {
        // 伪实现：同窗栈式切换，返回 null（调用方据此走"回到该标签"路径而非真开窗）
        stackCounter++
        return null
    }

    override fun closeWindow(windowId: String) = Unit
    override fun focusWindow(windowId: String) = Unit
    override fun windowIds(): List<String> = listOf(SINGLE_WINDOW)
    override fun detachTab(tabId: Long, fromWindowId: String): String? = null

    companion object {
        const val SINGLE_WINDOW = "stack-0"
    }
}

/**
 * 桌面真多窗口控制台实现（Phase 6 接 CMP `Window{}` 实例渲染；此处为纯逻辑多窗口簿记，可单测）。
 * 期望注入 onWindowCreate/onWindowClose 回调（平台层创建/销毁真实窗口）。
 */
class DesktopWindowController(
    private val onWindowCreate: (String, Long?) -> Unit = { _, _ -> },
    private val onWindowClose: (String) -> Unit = {},
) : WindowController {
    override val isNativeMultiWindow: Boolean = true

    private val windows = LinkedHashMap<String, Long?>()   // windowId -> 迁移的 tabId
    private var seq = 0

    init {
        newWindow()
    }

    override fun newWindow(initialTabId: Long?): String? {
        val id = "win-${seq++}"
        windows[id] = initialTabId
        onWindowCreate(id, initialTabId)
        return id
    }

    override fun closeWindow(windowId: String) {
        // LinkedHashMap.remove 返回被移除的 value（Long? 可为 null）——用 containsKey 判断存在性，避免 null 值误判
        if (windows.containsKey(windowId)) {
            windows.remove(windowId)
            onWindowClose(windowId)
        }
    }

    override fun focusWindow(windowId: String) {
        // 焦点簿记（平台层在 onWindowCreate/焦点回调中实现；Phase 6 桌面专项）
    }

    override fun windowIds(): List<String> = windows.keys.toList()

    override fun detachTab(tabId: Long, fromWindowId: String): String? {
        // 真多窗口：拖出 = 新窗口携带该标签（PRD §3.5.2）
        return newWindow(tabId)
    }

    /** 窗口内迁移的 tab（测试/平台层查询）。 */
    fun tabIn(windowId: String): Long? = windows[windowId]
}
