package cn.ac.oac.glimbrowser.logic

import cn.ac.oac.glimbrowser.ui.screen.ApiGuideEvent
import cn.ac.oac.glimbrowser.ui.screen.applyTo

import cn.ac.oac.glimbrowser.model.BrowserTab
import cn.ac.oac.glimbrowser.model.HomeCard
import cn.ac.oac.glimbrowser.model.QuickSite

/**
 * 浏览壳顶层状态（Phase 2 单一事实源）。
 *
 * 组合：标签管理（TabManager）+ 地址栏输入态 + 首页卡片开关 + 菜单/抽屉开合 + 无痕。
 * UI 层（App.kt / BrowserShell）只读取本状态并 dispatch 操作，不内嵌业务判断
 * （PRD V1.2 §2.3 原则：平台代码只做"执行"，业务判断在 shared）。
 *
 * Phase 2 范围（内存态，未接持久化/容器）：
 *  - 首页、浏览壳地址栏、标签管理、书签/历史/下载/设置入口骨架（PRD V1.0 §6.1 IA）
 *  - Phase 3 接 BrowserEngine 后由容器回调驱动 onLoading/onNavigated
 */
data class BrowserState(
    /** 标签管理子状态。 */
    val tabs: TabManagerState = TabManagerState(),

    /** 地址栏当前输入文本（编辑态）。 */
    val addressBarInput: String = "",

    /** 地址栏是否处于聚焦编辑态（聚焦时覆盖显示输入框 + 建议，PRD §3.1.1）。 */
    val addressBarFocused: Boolean = false,

    /** 首页卡片开关（PRD §3.5.3 可关卡片；默认全开，可在设置一键全关）。 */
    val enabledHomeCards: Set<String> = HomeCard.entries.filter { it.defaultEnabled }.map { it.id }.toSet(),

    /** 底部/侧边导航当前选中项（Compact 底部导航：首页/标签/菜单，原型 .tabbar）。 */
    val navDestination: NavDestination = NavDestination.HOME,

    /** 标签管理页是否展开（Compact 下"标签"进入全屏标签页视图）。 */
    val tabSwitcherOpen: Boolean = false,

    /** 菜单抽屉/面板是否展开（Compact 底部"菜单"→ 浮层；Medium/Expanded → 侧栏/面板）。 */
    val menuOpen: Boolean = false,

    /** AI 抽屉是否展开（Phase 5 接三步引导/摘要）。 */
    val aiDrawerOpen: Boolean = false,

    /** AI 是否已配置（✨ 未配置 → 三步引导；已配置 → 摘要 —— 附件 D.4 轻/重入口）。 */
    val aiConfigured: Boolean = false,

    /** 三步引导状态（附件 D.4）。 */
    val apiGuide: ApiGuideState = ApiGuideState(),

    /** 待重载标记（错误卡"重试"→ 容器观察到后 reload；消费后清零）。 */
    val pendingReload: Boolean = false,

    /** 全屏子页面（书签/历史/下载/插件/设置/关于）。 */
    val subPage: SubPage? = null,

    /** 下载任务（真实：由平台下载事件驱动；进程内列表）。 */
    val downloads: List<DownloadItem> = emptyList(),

    /** 桌面版网站（UA 切换；全局记忆，持久化）。 */
    val desktopMode: Boolean = false,

    /** 强制深色网页（实验，页面无深色样式时反转）；持久化。 */
    val forceDarkWeb: Boolean = false,

    /** 全屏搜索视图（原型 view-search；无"取消"文案，靠下拉/返回退出）。 */
    val searchOpen: Boolean = false,
    val searchQuery: String = "",

    /** AI 配置项（开关/超时/温度/语言等，持久化）。 */
    val aiPrefs: cn.ac.oac.glimbrowser.ai.AiPreferences = cn.ac.oac.glimbrowser.ai.AiPreferences(),

    /** 副作用请求信号（计数递增触发一次性动作；容器消费）。 */
    val systemBrowserRequest: Int = 0,
    val clearCacheRequest: Int = 0,

    /** 内核重建代数（渲染进程崩溃后自增，驱动 Shell 重建引擎）。 */
    val engineGeneration: Int = 0,

    /** 页内查找。 */
    val findOpen: Boolean = false,
    val findQuery: String = "",
    val findMatches: Int = 0,
    val findIndex: Int = 0,

    /** 当前页是否已收藏（地址栏 ☆ 状态，真实数据来自仓库查询）。 */
    val bookmarked: Boolean = false,

    /** 外观主题（真实切换：昼/夜/跟随/护眼，PRD V1.3 §3.5.3）。 */
    val themeMode: cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode = cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode.SYSTEM,

    /** 净化报告是否展开（原型注释 05：底部浮层，保留页面可见）。 */
    val purifyReportOpen: Boolean = false,

    /** 默认搜索引擎（PRD §3.1.1 多源搜索）。 */
    val searchEngine: SearchEngine = SearchEngine.DEFAULT,

    /**
     * 去广告引擎（PRD §3.3 默认去广告；默认内置 GlimBase 规则集，Phase 4 后接 CDN 订阅）。
     * 护盾计数 = adFilter.stats.currentPageCount（原型 .badge-shield）。
     */
    val adFilter: cn.ac.oac.glimbrowser.adfilter.AdFilterEngine =
        cn.ac.oac.glimbrowser.adfilter.AdFilterEngine(platformLabel = "Android 拦截"),
) {
    val currentTab: BrowserTab? get() = tabs.currentTab

    /** 当前是否停在新标签首页（glim://newtab 或空态无标签）。 */
    val onHome: Boolean
        get() = currentTab?.url == TabManager.NEW_TAB_HOME_URL || currentTab == null
}

/** 全局导航目的地（PRD V1.0 §6.1 全局信息架构）。 */
enum class NavDestination(val id: String, val label: String) {
    HOME("home", "首页"),
    TABS("tabs", "标签"),
    MENU("menu", "菜单"),
}

/**
 * 浏览壳操作分发（无副作用纯函数式，返回新 [BrowserState]）。
 * 组合 TabManager 与本地 UI 态变更；Phase 3 起平台副作用（加载页面、存密钥）由容器/平台层执行。
 */
class BrowserActions(
    private val tabManager: TabManager = TabManager(),
) {

    fun initial(): BrowserState = BrowserState(tabs = tabManager.initialState())

    // ===== 标签 =====

    fun newTab(state: BrowserState, url: String = TabManager.NEW_TAB_HOME_URL): BrowserState =
        state.copy(
            tabs = tabManager.open(state.tabs, url),
            navDestination = NavDestination.HOME,
            tabSwitcherOpen = false,
            menuOpen = false,
            addressBarInput = "",
            addressBarFocused = false,
        )

    fun selectTab(state: BrowserState, id: Long): BrowserState =
        state.copy(
            tabs = tabManager.select(state.tabs, id),
            tabSwitcherOpen = false,
            navDestination = NavDestination.HOME,
            addressBarInput = "",
            addressBarFocused = false,
        )

    fun closeTab(state: BrowserState, id: Long, nowMs: Long = 0L): BrowserState =
        state.copy(tabs = tabManager.close(state.tabs, id, nowMs))

    /** 标签分组（null=移出分组）。 */
    fun setTabGroup(state: BrowserState, id: Long, group: String?): BrowserState =
        state.copy(tabs = tabManager.setGroup(state.tabs, id, group))

    /** 标签搜索。 */
    fun setTabQuery(state: BrowserState, query: String): BrowserState =
        state.copy(tabs = tabManager.setQuery(state.tabs, query))

    /** 恢复最近关闭的标签。 */
    fun restoreClosedTab(state: BrowserState, closedAt: Long): BrowserState =
        state.copy(tabs = tabManager.restoreClosed(state.tabs, closedAt))

    fun clearClosedTabs(state: BrowserState): BrowserState =
        state.copy(tabs = tabManager.clearClosed(state.tabs))

    fun closeOtherTabs(state: BrowserState, keepId: Long): BrowserState =
        state.copy(tabs = tabManager.closeOthers(state.tabs, keepId))

    fun setIncognito(state: BrowserState, incognito: Boolean): BrowserState =
        state.copy(tabs = tabManager.setIncognito(state.tabs, incognito))

    // ===== 容器回调（Phase 3 接入后由平台层调用） =====

    fun onTabLoading(state: BrowserState, id: Long, isLoading: Boolean, progress: Float): BrowserState =
        state.copy(tabs = tabManager.onLoading(state.tabs, id, isLoading, progress))

    fun onTabNavigated(state: BrowserState, id: Long, url: String, title: String?): BrowserState =
        state.copy(tabs = tabManager.onNavigated(state.tabs, id, url, title))

    /** 内核导航能力（后退/前进按钮）。 */
    fun onNavCapability(state: BrowserState, id: Long, canGoBack: Boolean, canGoForward: Boolean): BrowserState =
        state.copy(tabs = tabManager.onNavCapability(state.tabs, id, canGoBack, canGoForward))

    /** 页面级错误（"网页访问报错"，108 修复）。 */
    fun onPageError(state: BrowserState, id: Long, url: String, description: String): BrowserState =
        state.copy(tabs = tabManager.onError(state.tabs, id, description))

    /** 清除当前标签错误（错误卡"重试"）。 */
    fun clearPageError(state: BrowserState, id: Long): BrowserState =
        state.copy(tabs = tabManager.clearError(state.tabs, id))

    // ===== 地址栏 =====

    fun onAddressBarChange(state: BrowserState, input: String): BrowserState =
        state.copy(addressBarInput = input, addressBarFocused = true)

    fun onAddressBarFocus(state: BrowserState, focused: Boolean): BrowserState =
        state.copy(
            addressBarFocused = focused,
            addressBarInput = if (focused) state.addressBarInput else "",
        )

    /**
     * 提交地址栏输入：解析后要么导航到 URL，要么发起搜索（两者均视为"导航到新地址"，
     * 由 Phase 3 容器真正 loadUrl；Phase 2 内存态直接更新当前标签 URL 供 UI 展示）。
     */
    fun submitAddressBar(
        state: BrowserState,
        queryEncoder: (String) -> String = { it },
    ): BrowserState {
        val resolution = resolveAddressBarInput(state.addressBarInput, state.searchEngine, queryEncoder)
        if (resolution.value.isBlank()) return onAddressBarFocus(state, false)
        val target = resolution.normalizedUrl ?: resolution.value
        val tabId = state.tabs.currentTabId ?: return state
        val navigated = state.copy(
            tabs = tabManager.onNavigated(state.tabs, tabId, target, title = null),
            addressBarFocused = false,
            addressBarInput = "",
        )
        return navigated.onHomeHandled()
    }

    /** 导航后回首页入口（搜索/URL 导航都不是首页）。 */
    private fun BrowserState.onHomeHandled(): BrowserState =
        copy(navDestination = NavDestination.HOME, tabSwitcherOpen = false, menuOpen = false)

    // ===== 导航 / 面板开合 =====

    fun navigateTo(state: BrowserState, destination: NavDestination): BrowserState = when (destination) {
        NavDestination.HOME -> state.copy(
            navDestination = NavDestination.HOME,
            tabSwitcherOpen = false,
            menuOpen = false,
        )
        NavDestination.TABS -> state.copy(
            navDestination = NavDestination.TABS,
            tabSwitcherOpen = true,
            menuOpen = false,
        )
        NavDestination.MENU -> state.copy(
            navDestination = NavDestination.MENU,
            menuOpen = true,
            tabSwitcherOpen = false,
        )
    }

    fun toggleMenu(state: BrowserState, open: Boolean): BrowserState =
        state.copy(menuOpen = open, tabSwitcherOpen = false, navDestination = if (open) NavDestination.MENU else NavDestination.HOME)

    fun toggleTabSwitcher(state: BrowserState, open: Boolean): BrowserState =
        state.copy(tabSwitcherOpen = open, menuOpen = false, navDestination = if (open) NavDestination.TABS else NavDestination.HOME)

    fun toggleAiDrawer(state: BrowserState, open: Boolean): BrowserState = state.copy(aiDrawerOpen = open)

    /** ✨ 入口：未配置 → 打开三步引导；已配置 → 开 AI 抽屉（附件 D.4 轻/重入口，PRD §3.2.4）。 */
    fun onAiEntry(state: BrowserState): BrowserState =
        if (state.aiConfigured) state.copy(aiDrawerOpen = true) else state.copy(apiGuide = ApiGuideActions.open(state.apiGuide))

    /** 三步引导事件分发（reduce 到 ApiGuideState）。 */
    fun onApiGuideEvent(state: BrowserState, event: cn.ac.oac.glimbrowser.ui.screen.ApiGuideEvent): BrowserState {
        val newGuide = event.applyTo(state.apiGuide)
        return state.copy(
            apiGuide = newGuide,
            aiConfigured = newGuide.step == ApiGuideState.Step.DONE || state.aiConfigured,
        )
    }

    /** 收藏当前页（书签真实落库由 dispatch 后置副作用完成）。 */
    fun bookmarkCurrent(state: BrowserState): BrowserState = state.copy(bookmarked = state.currentTab?.url != null)

    /** 去广告命中记账（主线程调用；后台线程只做只读匹配）。 */
    fun recordAdBlocked(state: BrowserState, url: String): BrowserState {
        val host = state.currentTab?.faviconDomain.orEmpty()
        state.adFilter.recordBlocked(url, host)
        return state
    }

    /** 渲染进程崩溃 → 代数 +1（Shell 重建该标签内核）。 */
    fun onRendererGone(state: BrowserState): BrowserState =
        state.copy(engineGeneration = state.engineGeneration + 1)

    /** 重新配置：打开三步引导并预填已有端点/模型（Key 需重填）。 */
    fun reconfigureGuide(state: BrowserState, baseUrl: String, model: String): BrowserState =
        state.copy(
            apiGuide = ApiGuideState(
                step = ApiGuideState.Step.STEP1,
                baseUrlInput = baseUrl,
                modelInput = model,
            ),
            aiDrawerOpen = false,
        )

    /** 打开搜索视图（清空上次输入）。 */
    fun openSearch(state: BrowserState): BrowserState = state.copy(searchOpen = true, searchQuery = "")

    /** 关闭搜索视图。 */
    fun closeSearch(state: BrowserState): BrowserState = state.copy(searchOpen = false, searchQuery = "")

    /** 搜索输入变化。 */
    fun setSearchQuery(state: BrowserState, q: String): BrowserState = state.copy(searchQuery = q)

    /** AI 配置更新（持久化在 dispatch 后置副作用）。 */
    fun setAiPrefs(state: BrowserState, prefs: cn.ac.oac.glimbrowser.ai.AiPreferences): BrowserState =
        state.copy(aiPrefs = prefs)

    /** 强制深色网页（实验；页面自身无深色样式时用滤镜反转，默认关）。 */
    fun setForceDarkWeb(state: BrowserState, enabled: Boolean): BrowserState =
        state.copy(forceDarkWeb = enabled)

    /** 桌面版网站开关（持久化在 dispatch 后置副作用）。 */
    fun setDesktopMode(state: BrowserState, enabled: Boolean): BrowserState =
        state.copy(desktopMode = enabled)

    /** 主题切换（持久化在 dispatch 后置副作用）。 */
    fun setThemeMode(state: BrowserState, mode: cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode): BrowserState =
        state.copy(themeMode = mode)

    /** 打开子页面（全屏）。 */
    fun openSubPage(state: BrowserState, page: SubPage): BrowserState =
        state.copy(subPage = page, menuOpen = false)

    fun closeSubPage(state: BrowserState): BrowserState = state.copy(subPage = null)

    /** 记录一次下载（真实事件驱动；重复 URL 更新状态）。 */
    fun onDownloadStarted(state: BrowserState, url: String, fileName: String, atEpochMs: Long): BrowserState {
        val id = "$url@$atEpochMs"
        val item = DownloadItem(id = id, url = url, fileName = fileName, state = DownloadState.RUNNING, startedAt = atEpochMs)
        return state.copy(downloads = state.downloads.filterNot { it.url == url } + item)
    }

    fun onDownloadFinished(state: BrowserState, url: String, success: Boolean): BrowserState =
        state.copy(downloads = state.downloads.map {
            if (it.url == url) it.copy(state = if (success) DownloadState.DONE else DownloadState.FAILED) else it
        })

    fun removeDownload(state: BrowserState, id: String): BrowserState =
        state.copy(downloads = state.downloads.filterNot { it.id == id })

    fun clearDownloads(state: BrowserState): BrowserState =
        state.copy(downloads = state.downloads.filter { it.state == DownloadState.RUNNING })

    // ===== 页内查找 =====

    fun openFind(state: BrowserState): BrowserState = state.copy(findOpen = true)

    fun closeFind(state: BrowserState): BrowserState =
        state.copy(findOpen = false, findQuery = "", findMatches = 0, findIndex = 0)

    fun setFindQuery(state: BrowserState, query: String): BrowserState =
        state.copy(findQuery = query)

    fun onFindResult(state: BrowserState, matches: Int, index: Int): BrowserState =
        state.copy(findMatches = matches, findIndex = index)

    /** 关闭引导浮层（保留配置进度）。 */
    fun closeApiGuide(state: BrowserState): BrowserState = state.copy(apiGuide = ApiGuideActions.close(state.apiGuide))

    fun togglePurifyReport(state: BrowserState, open: Boolean): BrowserState = state.copy(purifyReportOpen = open)

    // ===== 首页卡片开关（PRD §3.5.3 可在设置一键全关） =====

    fun setHomeCardEnabled(state: BrowserState, cardId: String, enabled: Boolean): BrowserState {
        val cards = state.enabledHomeCards.toMutableSet()
        if (enabled) cards += cardId else cards -= cardId
        return state.copy(enabledHomeCards = cards)
    }

    fun setAllHomeCardsEnabled(state: BrowserState, enabled: Boolean): BrowserState =
        state.copy(enabledHomeCards = if (enabled) HomeCard.entries.map { it.id }.toSet() else emptySet())

    fun setSearchEngine(state: BrowserState, engine: SearchEngine): BrowserState =
        state.copy(searchEngine = engine)
}


/** 全屏子页面（均为真实功能页）。 */
enum class SubPage(val title: String) {
    BOOKMARKS("书签"),
    HISTORY("历史"),
    DOWNLOADS("下载"),
    PLUGINS("插件"),
    SETTINGS("设置"),
    FOLDERS("文件夹"),
    AI_SETTINGS("AI 设置"),
    ABOUT("关于"),
}

/** 下载状态。 */
enum class DownloadState { RUNNING, DONE, FAILED }

/** 下载项（进程内会话；真实由平台下载事件填充）。 */
data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val state: DownloadState,
    val startedAt: Long,
)
