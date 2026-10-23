package cn.ac.oac.glimbrowser.logic

import cn.ac.oac.glimbrowser.model.toDisplayDomain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 浏览壳状态控制器（Phase 2）—— 持有 [BrowserState] 并把 [BrowserActions] 的纯函数结果落回 Compose 状态。
 *
 * 设计：Actions 层保持纯函数（可单测，PRD V1.2 §2.3 原则 1），本类只做"取当前状态 → 求新状态 → 写回"，
 * 是 UI 与纯逻辑之间的唯一粘合点（等价轻量 ViewModel；Phase 5/6 接 Koin koinViewModel 后可平移）。
 *
 * 使用（App.kt）：
 *  ```
 *  val controller = remember { BrowserController() }
 *  BrowserShell(state = controller.state, dispatch = controller::dispatch, ...)
 *  ```
 */
class BrowserController(
    private val actions: BrowserActions = BrowserActions(),
    /** 本地持久化仓库（Phase 6；null=无持久化（测试默认））。 */
    private val repository: cn.ac.oac.glimbrowser.data.GlimRepository? = null,
    /** 平台安全区（红线7：明文 Key 只进 SecureKeyStore；null=无（测试/未接平台））。 */
    private val keyStore: cn.ac.oac.glimbrowser.securekey.SecureKeyStore? = null,
) {
    /** Compose 可观察状态（snapshot state；跨平台 commonMain 可用）。 */
    var state: BrowserState by mutableStateOf(actions.initial().also { st ->
        // PRD §3.3.2 L1「启动时本地加载、零配置」：启动即加载内置 GlimBase 规则（CDN 订阅热更留 Phase 4）
        st.adFilter.loadRules(BUILTIN_RULES, listName = "GlimBase")
    })
        private set

    init {
        // 启动即按持久化档案校准"已配置"状态（真实数据，非内存占位）
        runCatching {
            val hasProfile = repository?.aiProfiles()?.isNotEmpty() == true
            if (hasProfile && !state.aiConfigured) state = state.copy(aiConfigured = true)
            // 搜索引擎真实持久化恢复
            repository?.filterSetting("search_engine")?.let { id ->
                state = state.copy(searchEngine = cn.ac.oac.glimbrowser.logic.SearchEngine.byId(id))
            }
            repository?.filterSetting("desktop_mode")?.let { v ->
                state = state.copy(desktopMode = v == "true")
            }
            repository?.filterSetting("force_dark_web")?.let { v ->
                state = state.copy(forceDarkWeb = v == "true")
            }
            state = state.copy(aiPrefs = cn.ac.oac.glimbrowser.ai.AiPreferencesStore(repository).load())
            repository?.filterSetting("theme_mode")?.let { m ->
                runCatching {
                    state = state.copy(themeMode = cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode.valueOf(m))
                }
            }
        }
    }

    /** 统一 dispatch 入口：所有 UI 事件经 [Event] 封装，落回纯 Actions 求解；状态变化后执行副作用（持久化）。 */
    fun dispatch(event: Event) {
        val prevGuideStep = state.apiGuide.step
        // 重新配置需要读仓库 → 在 dispatch 层处理（reduce 保持纯函数）
        if (event is Event.ReconfigureAi) {
            val row = runCatching { repository?.aiProfile("default") }.getOrNull()
            state = actions.reconfigureGuide(state, row?.base_url ?: "", row?.model ?: "")
            return
        }
        state = event.reduce(actions, state)
        // ===== Phase 6 持久化副作用（dispatch 后置执行，保持 reduce 纯函数） =====
        onStateChanged(event, prevGuideStep)
    }

    /** 持久化副作用钩子（当前覆盖：AI 档案落库 + 历史记录；repository 为 null 时 no-op）。 */
    private fun onStateChanged(event: Event, prevGuideStep: cn.ac.oac.glimbrowser.logic.ApiGuideState.Step) {
        val repo = repository ?: return
        // 1) 三步引导 DONE → Key 入平台安全区 + 档案落库（仅 apiKeyRef；红线7）
        if (state.apiGuide.step == ApiGuideState.Step.DONE && prevGuideStep != ApiGuideState.Step.DONE) {
            val ref = cn.ac.oac.glimbrowser.securekey.SecureKeyStore.aiProfileRef("default")
            // Key 明文仅在此调用栈内出现：写安全区后即丢弃（不入 DB/日志，000 §2 红线 7/8）
            runCatching { keyStore?.put(ref, state.apiGuide.apiKeyInput) }
            runCatching {
                val g = state.apiGuide
                val id = "default"
                repo.upsertAiProfile(
                    cn.ac.oac.glimbrowser.data.Ai_profile_record(
                        id = id, name = "默认配置", base_url = g.baseUrlInput,
                        api_key_ref = ref,   // 引用（明文在 SecureKeyStore）
                        model = g.modelInput, timeout_seconds = 60, stream_enabled = 1,
                        summary_language = "auto",
                        last_connectivity_json = g.connectivity?.let { """{"success":${it.success},"latency":${it.latencyMs ?: 0}}""" },
                        capabilities_json = g.capabilities?.let { """{"json_object":${it.jsonObject},"stream":${it.stream}}""" },
                        updated_at = currentEpochMs(),
                    ),
                )
            }
        }
        // 1b) 收藏当前页（真实落库）
        if (event is Event.BookmarkCurrent) {
            val tab = state.currentTab ?: return
            if (tab.url.isNotBlank() && tab.url != cn.ac.oac.glimbrowser.logic.TabManager.NEW_TAB_HOME_URL) {
                runCatching { repo.addBookmark(tab.url, tab.title.ifBlank { tab.faviconDomain }, folder = "", atEpochMs = currentEpochMs()) }
            }
        }
        // 1c) 搜索引擎持久化
        if (event is Event.SetSearchEngine) {
            runCatching { repo.putFilterSetting("search_engine", event.engine.id, currentEpochMs()) }
        }
        if (event is Event.SetThemeMode) {
            runCatching { repo.putFilterSetting("theme_mode", event.mode.name, currentEpochMs()) }
        }
        if (event is Event.SetDesktopMode) {
            runCatching { repo.putFilterSetting("desktop_mode", event.enabled.toString(), currentEpochMs()) }
        }
        if (event is Event.SetForceDarkWeb) {
            runCatching { repo.putFilterSetting("force_dark_web", event.enabled.toString(), currentEpochMs()) }
        }
        if (event is Event.SetAiPrefs) {
            runCatching { cn.ac.oac.glimbrowser.ai.AiPreferencesStore(repo).save(event.prefs) }
        }
        if (event is Event.CloseTab || event is Event.TabNavigated) {
            // 收藏状态同步（地址栏 ☆）
            runCatching {
                val url = state.currentTab?.url
                val marked = url != null && repo.listBookmarks().any { it.url == url }
                if (marked != state.bookmarked) state = state.copy(bookmarked = marked)
            }
        }

        // 2) 页面导航 → 历史（无痕不入史，PRD §3.1.3；首启首页不记）
        if (event is Event.TabNavigated && !state.tabs.incognito && event.url != cn.ac.oac.glimbrowser.logic.TabManager.NEW_TAB_HOME_URL) {
            runCatching {
                val host = event.url.toHostForHistory()
                repo.recordVisit(event.url, event.title ?: "", host, currentEpochMs())
            }
        }
    }

    /** epoch ms（可注入便于测试）。 */
    var currentEpochMs: () -> Long = { 0L }

    /**
     * UI 事件（封闭集合：新增入口时加枚举值 + reduce 分支，编译期防漏）。
     * 每个 case 对应一个纯 [BrowserActions] 调用；queryEncoder 用 [encodeUrlQuery]（common 实现）。
     */
    sealed interface Event {
        fun reduce(actions: BrowserActions, state: BrowserState): BrowserState

        data class AddressBarInput(val text: String) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.onAddressBarChange(state, text)
        }

        data class AddressBarFocus(val focused: Boolean) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.onAddressBarFocus(state, focused)
        }

        data object SubmitAddressBar : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) =
                actions.submitAddressBar(state, queryEncoder = { it.encodeUrlQuery() })
        }

        data class Navigate(val destination: NavDestination) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.navigateTo(state, destination)
        }

        data object NewTab : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.newTab(state)
        }

        data class OpenUrlInNewTab(val url: String) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.newTab(state, url)
        }

        data class SelectTab(val id: Long) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.selectTab(state, id)
        }

        data class CloseTab(val id: Long) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) =
                actions.closeTab(state, id, kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds)
        }

        /** 标签分组（null=移出分组）。 */
        data class SetTabGroup(val id: Long, val group: String?) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.setTabGroup(state, id, group)
        }

        /** 标签搜索词。 */
        data class SetTabQuery(val query: String) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.setTabQuery(state, query)
        }

        /** 恢复最近关闭的标签（回收站）。 */
        data class RestoreClosedTab(val closedAt: Long) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.restoreClosedTab(state, closedAt)
        }

        data object ClearClosedTabs : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.clearClosedTabs(state)
        }

        data class CloseOtherTabs(val keepId: Long) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.closeOtherTabs(state, keepId)
        }

        data class SetIncognito(val on: Boolean) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.setIncognito(state, on)
        }

        data class ToggleMenu(val open: Boolean) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.toggleMenu(state, open)
        }

        data class ToggleTabSwitcher(val open: Boolean) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.toggleTabSwitcher(state, open)
        }

        /** ✨ 入口（未配置→三步引导；已配置→抽屉，附件 D.4）。 */
        data object AiEntry : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.onAiEntry(state)
        }

        /** 三步引导事件（reduce 到 ApiGuideState）。 */
        data class ApiGuide(val event: cn.ac.oac.glimbrowser.ui.screen.ApiGuideEvent) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.onApiGuideEvent(state, event)
        }

        /** 关闭三步引导浮层。 */
        data object CloseApiGuide : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.closeApiGuide(state)
        }

        data class ToggleAiDrawer(val open: Boolean) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.toggleAiDrawer(state, open)
        }

        data class TogglePurifyReport(val open: Boolean) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.togglePurifyReport(state, open)
        }

        data class SetHomeCard(val cardId: String, val enabled: Boolean) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) =
                actions.setHomeCardEnabled(state, cardId, enabled)
        }

        data object CloseAllHomeCards : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.setAllHomeCardsEnabled(state, false)
        }

        data object OpenAllHomeCards : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.setAllHomeCardsEnabled(state, true)
        }

        /** 误杀闭环：本页暂停拦截（PRD §3.3.3 流程 B）。 */
        data object PauseSiteFilter : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState): BrowserState {
                state.adFilter.pauseSite(state.currentTab?.faviconDomain ?: return state)
                return state  // 状态本身不变（引擎内部状态），UI 经 adFilter.stats/settings 读取
            }
        }

        /** 恢复该站点拦截。 */
        data object ResumeSiteFilter : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState): BrowserState {
                state.adFilter.resumeSite(state.currentTab?.faviconDomain ?: return state)
                return state
            }
        }

        data class SetSearchEngine(val engine: SearchEngine) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.setSearchEngine(state, engine)
        }

        // ===== 容器回调（Phase 3 平台层调用） =====

        data class TabLoading(val id: Long, val loading: Boolean, val progress: Float) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) =
                actions.onTabLoading(state, id, loading, progress)
        }

        data class TabNavigated(val id: Long, val url: String, val title: String?) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) =
                actions.onTabNavigated(state, id, url, title)
        }

        /** 内核导航能力回填（后退/前进按钮态）。 */
        data class NavCapability(val id: Long, val canGoBack: Boolean, val canGoForward: Boolean) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) =
                actions.onNavCapability(state, id, canGoBack, canGoForward)
        }

        /** 后退请求（UI 手势/按钮 → Shell 用引擎池消费执行 engine.goBack()）。 */
        data object RequestBack : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = state   // 副作用由 Shell 消费
        }

        /** 前进请求。 */
        data object RequestForward : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = state
        }

        /** 页面级错误（"网页访问报错"，108）：容器 onReceivedError/onError 回传。 */
        data class PageError(val id: Long, val url: String, val description: String) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) =
                actions.onPageError(state, id, url, description)
        }

        /** 收藏当前页（仓库落库；已存在则忽略）。 */
        data object BookmarkCurrent : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.bookmarkCurrent(state)
        }

        /** 桌面版网站（UA 切换；副作用：容器对当前引擎应用并 reload）。 */
        data class SetDesktopMode(val enabled: Boolean) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.setDesktopMode(state, enabled)
        }

        /** 用系统浏览器打开当前页（计数递增 → 容器 LaunchedEffect 消费）。 */
        data object OpenInSystemBrowser : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) =
                state.copy(systemBrowserRequest = state.systemBrowserRequest + 1)
        }

        /** 清除缓存与 Cookie（计数递增 → 容器消费）。 */
        data object ClearCache : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) =
                state.copy(clearCacheRequest = state.clearCacheRequest + 1)
        }

        /** 渲染进程崩溃（容器回传）→ 重建内核。 */
        data object RendererGone : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.onRendererGone(state)
        }

        /** 去广告命中（容器在主线程回传，记入护盾与净化统计）。 */
        data class AdBlocked(val url: String) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.recordAdBlocked(state, url)
        }

        /** 重新配置 AI：打开引导（预填端点/模型；由 dispatch 从仓库取档案）。 */
        data object ReconfigureAi : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = state
        }

        /** AI 配置更新（开关/超时/温度/语言等）。 */
        data class SetAiPrefs(val prefs: cn.ac.oac.glimbrowser.ai.AiPreferences) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.setAiPrefs(state, prefs)
        }

        /** 强制深色网页（实验，持久化）。 */
        data class SetForceDarkWeb(val enabled: Boolean) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.setForceDarkWeb(state, enabled)
        }

        /** 主题切换（护眼等，持久化）。 */
        data class SetThemeMode(val mode: cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.setThemeMode(state, mode)
        }

        /** 打开全屏子页面。 */
        data class OpenSubPage(val page: cn.ac.oac.glimbrowser.logic.SubPage) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.openSubPage(state, page)
        }

        data object CloseSubPage : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.closeSubPage(state)
        }

        /** 下载事件（平台 DownloadListener 回传）。 */
        data class DownloadStarted(val url: String, val fileName: String) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) =
                actions.onDownloadStarted(state, url, fileName, kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds)
        }

        data class DownloadFinished(val url: String, val success: Boolean) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) =
                actions.onDownloadFinished(state, url, success)
        }

        data class RemoveDownload(val id: String) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.removeDownload(state, id)
        }

        data object ClearDownloads : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.clearDownloads(state)
        }

        /** 页内查找。 */
        data object OpenFind : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.openFind(state)
        }

        data object CloseFind : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.closeFind(state)
        }

        data class SetFindQuery(val query: String) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.setFindQuery(state, query)
        }

        data class FindResult(val matches: Int, val index: Int) : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) = actions.onFindResult(state, matches, index)
        }

        /** 容器已消费 pendingReload → 清标记。 */
        data object ConsumeReload : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState) =
                if (state.pendingReload) state.copy(pendingReload = false) else state
        }

        /** 错误卡"重试"：清除错误并重新加载当前 URL（引擎由容器 loadUrl）。 */
        data object RetryPage : Event {
            override fun reduce(actions: BrowserActions, state: BrowserState): BrowserState {
                val id = state.tabs.currentTabId ?: return state
                val cleared = actions.clearPageError(state, id)
                // 通过"改写 URL 为同 URL 再还原"无副作用；实际重载交给容器：url 不变 → 容器 reload()
                return cleared.copy(pendingReload = true)
            }
        }
    }
}


/**
 * 内置 GlimBase 规则集（Phase 4 占位样本；真实 EasyList/自维护清单随 CDN 接入，PRD §3.3.2 L1 本地加载）。
 * 红线：L1 默认全开、零配置（PRD V1.1 §3.3.2 默认值红线）。
 */
private val BUILTIN_RULES: String = """
! GlimBase builtin (Phase 4 sample; CDN subscription hot-update in Phase 4, PRD §3.3.3)
||ads.example.com^
||tracker.io^${'$'}third-party
||doubleclick.net^
||googlesyndication.com^
##.ad-banner
##.sponsored-post
#@#.ad-banner
""".trimIndent()


/** URL → 历史 host（复用 model 层域名提取）。 */
private fun String.toHostForHistory(): String = this.toDisplayDomain()
