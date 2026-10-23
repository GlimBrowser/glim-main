package cn.ac.oac.glimbrowser.logic

import cn.ac.oac.glimbrowser.model.BrowserTab
import cn.ac.oac.glimbrowser.model.toDisplayDomain

/**
 * 标签页管理器（PRD V1.0 §3.1.3 标签页管理，Phase 2 覆盖：多标签 20+、当前/后台、关闭恢复）。
 *
 * 纯 Kotlin 不可变状态 + 纯函数迁移（`with*` 返回新状态），便于单测与后续接入
 * ViewModel/Store（PRD V1.2 §2.3 关键设计原则 1：逻辑全部下沉 shared）。
 *
 * 覆盖范围说明（对齐 PRD 优先级，本阶段实现 P0 项的内存态骨架）：
 *  - ✅ 多标签（≥20）创建/切换/关闭
 *  - ✅ 当前标签索引维护（关闭当前标签时选中相邻）
 *  - ⏳ 标签分组/拖拽整理/命名（PRD P1，Phase 6）
 *  - ⏳ 标签回收站（误关 24h 恢复，PRD P1，Phase 6）
 *  - ⏳ 后台标签冻结（P0，依赖 Phase 3 容器 onTrimMemory，本阶段仅记录字段）
 */
data class TabManagerState(
    val tabs: List<BrowserTab> = emptyList(),
    val currentTabId: Long? = null,
    /** 无痕模式总开关（PRD §3.1.3 隐私标签：新标签是否默认无痕）。 */
    val incognito: Boolean = false,
    /** 最近关闭的标签（回收站，最多 20 条；可恢复）。 */
    val closedTabs: List<ClosedTab> = emptyList(),
    /** 标签搜索词（标签页过滤）。 */
    val tabQuery: String = "",
) {
    val currentTab: BrowserTab? get() = tabs.firstOrNull { it.id == currentTabId }

    val currentIndex: Int get() = tabs.indexOfFirst { it.id == currentTabId }
}

/**
 * 标签管理操作（单入口，返回新状态；无副作用，线程无关）。
 * id 分配策略：单调递增，会话内唯一；Phase 6 持久化时改由数据层发号。
 */
class TabManager(
    private var nextId: Long = 1L,
    /** PRD V1.0 §3.1.3「支持 20+ 标签」；防御式上限，超出拒绝并返回原状态（UI 侧提示）。 */
    val maxTabs: Int = 30,
) {

    fun initialState(url: String? = null): TabManagerState {
        val first = newTab(url ?: NEW_TAB_HOME_URL, isPrivate = false)
        return TabManagerState(tabs = listOf(first), currentTabId = first.id)
    }

    /** 新建标签并置为当前（PRD：新标签默认普通模式；incognito 参数用于无痕入口）。 */
    fun open(state: TabManagerState, url: String = NEW_TAB_HOME_URL, background: Boolean = false): TabManagerState {
        if (state.tabs.size >= maxTabs) return state
        val tab = newTab(url, isPrivate = state.incognito)
        val tabs = state.tabs + tab
        return state.copy(
            tabs = tabs,
            currentTabId = if (background) state.currentTabId else tab.id,
        )
    }

    /** 切换当前标签；id 不存在时原样返回（幂等防御）。 */
    fun select(state: TabManagerState, id: Long): TabManagerState =
        if (state.tabs.any { it.id == id }) state.copy(currentTabId = id) else state

    /**
     * 关闭标签（PRD §3.1.3）：
     *  - 关闭非当前标签 → 当前不变
     *  - 关闭当前标签 → 选中右侧相邻；无右侧则左侧；全关 → 返回空态（UI 显示"新标签"入口）
     */
    fun close(state: TabManagerState, id: Long, nowMs: Long = 0L): TabManagerState {
        val idx = state.tabs.indexOfFirst { it.id == id }
        if (idx < 0) return state
        val closed = state.tabs[idx]
        val tabs = state.tabs.filterNot { it.id == id }
        val newCurrent = when {
            state.currentTabId != id -> state.currentTabId
            tabs.isEmpty() -> null
            else -> tabs.getOrNull(idx)?.id ?: tabs.lastOrNull()?.id
        }
        // 无痕标签关闭后不进入回收站（不残留痕迹）
        val closedTabs = if (closed.isPrivate) state.closedTabs else (listOf(
            ClosedTab(url = closed.url, title = closed.title, group = closed.group, closedAt = nowMs, isPrivate = false)
        ) + state.closedTabs).take(20)
        return state.copy(tabs = tabs, currentTabId = newCurrent, closedTabs = closedTabs)
    }

    /** 分组设置（同组名；null=移出分组）。 */
    fun setGroup(state: TabManagerState, id: Long, group: String?): TabManagerState =
        mapTab(state, id) { it.copy(group = group?.takeIf { g -> g.isNotBlank() }) }

    /** 标签搜索词。 */
    fun setQuery(state: TabManagerState, query: String): TabManagerState = state.copy(tabQuery = query)

    /** 恢复回收站里的标签（重新打开并移出回收站）。 */
    fun restoreClosed(state: TabManagerState, closedAt: Long): TabManagerState {
        val item = state.closedTabs.firstOrNull { it.closedAt == closedAt } ?: return state
        val rest = state.closedTabs.filterNot { it.closedAt == closedAt }
        val tab = newTab(item.url, isPrivate = false).copy(group = item.group, title = item.title)
        return state.copy(tabs = state.tabs + tab, currentTabId = tab.id, closedTabs = rest)
    }

    fun clearClosed(state: TabManagerState): TabManagerState = state.copy(closedTabs = emptyList())

    /** 关闭除 id 外全部（菜单"关闭其他标签"，桌面/大屏常用）。 */
    fun closeOthers(state: TabManagerState, keepId: Long): TabManagerState {
        val keep = state.tabs.filter { it.id == keepId }
        if (keep.isEmpty()) return state
        return state.copy(tabs = keep, currentTabId = keepId)
    }

    /** 容器回填加载状态（Phase 3 容器回调入口；进度钳制 0..1）。 */
    fun onLoading(state: TabManagerState, id: Long, isLoading: Boolean, progress: Float = 0f): TabManagerState =
        mapTab(state, id) { it.copy(isLoading = isLoading, progress = progress.coerceIn(0f, 1f)) }

    /** 容器回填标题/URL（页面跳转后由平台层调用）。 */
    fun onNavigated(state: TabManagerState, id: Long, url: String, title: String? = null): TabManagerState =
        mapTab(state, id) {
            it.copy(
                url = url,
                faviconDomain = url.toDisplayDomain(),
                title = title ?: it.title,
                lastError = null,   // 导航推进 → 清错误（重试成功/新页面）
            )
        }

    /** 内核导航能力回填（后退/前进按钮可用态）。 */
    fun onNavCapability(state: TabManagerState, id: Long, canGoBack: Boolean, canGoForward: Boolean): TabManagerState =
        mapTab(state, id) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }

    /** 页面级错误落回（"网页访问报错"卡片，108）；onNavigated 成功时清空。 */
    fun onError(state: TabManagerState, id: Long, error: String): TabManagerState =
        mapTab(state, id) { it.copy(lastError = error, isLoading = false, progress = 1f) }

    /** 清除错误（重试/导航成功）。 */
    fun clearError(state: TabManagerState, id: Long): TabManagerState =
        mapTab(state, id) { it.copy(lastError = null) }

    /** 无痕模式切换（影响后续 open 的默认 isPrivate；不改变已存在标签）。 */
    fun setIncognito(state: TabManagerState, incognito: Boolean): TabManagerState =
        state.copy(incognito = incognito)

    private fun mapTab(state: TabManagerState, id: Long, transform: (BrowserTab) -> BrowserTab): TabManagerState {
        val idx = state.tabs.indexOfFirst { it.id == id }
        if (idx < 0) return state
        val tabs = state.tabs.toMutableList()
        tabs[idx] = transform(tabs[idx])
        return state.copy(tabs = tabs)
    }

    private fun newTab(url: String, isPrivate: Boolean): BrowserTab = BrowserTab(
        id = nextId++,
        title = if (url == NEW_TAB_HOME_URL) NEW_TAB_TITLE else url.toDisplayDomain(),
        url = url,
        isPrivate = isPrivate,
    )

    companion object {
        /** 新标签页占位 URL（首页由共享 UI 渲染，不真正加载；Phase 3 容器拦截该伪 URL 显示首页）。 */
        const val NEW_TAB_HOME_URL: String = "glim://newtab"
        const val NEW_TAB_TITLE: String = "新标签页"
    }
}

/** 回收站条目（最近关闭的标签）。 */
data class ClosedTab(
    val url: String,
    val title: String,
    val group: String?,
    val closedAt: Long,
    val isPrivate: Boolean = false,
)
