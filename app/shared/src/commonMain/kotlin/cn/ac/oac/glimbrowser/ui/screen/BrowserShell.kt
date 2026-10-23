package cn.ac.oac.glimbrowser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.ac.oac.glimbrowser.logic.BrowserController
import cn.ac.oac.glimbrowser.logic.TabManager
import cn.ac.oac.glimbrowser.ui.PlatformBackHandler
import cn.ac.oac.glimbrowser.ui.container.BrowserContainer
import cn.ac.oac.glimbrowser.ui.container.rememberEnginePool
import cn.ac.oac.glimbrowser.logic.BrowserState
import cn.ac.oac.glimbrowser.logic.NavDestination
import cn.ac.oac.glimbrowser.model.MenuItem
import cn.ac.oac.glimbrowser.model.MenuItemIcon
import cn.ac.oac.glimbrowser.ui.theme.GlimTokens
import cn.ac.oac.glimbrowser.uiadaptive.AiPanelPresentation
import cn.ac.oac.glimbrowser.uiadaptive.NavigationMode
import cn.ac.oac.glimbrowser.uiadaptive.WindowWidthClass
import cn.ac.oac.glimbrowser.uiadaptive.deriveAdaptiveLayout

/**
 * 浏览壳（Phase 2 顶层 UI）—— 按 [deriveAdaptiveLayout] 结果渲染（PRD V1.3 §3.5.1 自适应）。
 *
 * | SizeClass | 导航模式 | 本实现布局 |
 * |-----------|----------|-----------|
 * | Compact   | BOTTOM_BAR | 单列内容 + 底部导航（首页/标签/菜单，原型 .tabbar）；菜单以底部浮层 Sheet 呈现 |
 * | MEDIUM    | DRAWER    | 同 Compact 结构 + 左侧图标轨（收起态抽屉语义），此处先渲染图标轨 |
 * | EXPANDED  | THREE_PANE| 左导航图标轨 + 中面板栏（标签列表）+ 内容区；AI 右侧停靠 |
 *
 * 依赖注入（Phase 2 内存态）：
 *  - [state] / [actions] 由 App.kt 持有（remember），本组件只渲染 + dispatch
 *  - [now] 时钟展示（Phase 3 接平台时钟）
 */
@Composable
fun BrowserShell(
    state: BrowserState,
    dispatch: (BrowserController.Event) -> Unit,
    widthDp: Int,
    now: HomeClock,
    modifier: Modifier = Modifier,
    onMenuItemClick: (MenuItem) -> Unit = {},
) {
    val layout = deriveAdaptiveLayout(widthDp)
    val onHome = state.onHome && !state.addressBarFocused

    when (layout.navigationMode) {
        NavigationMode.BOTTOM_BAR -> CompactShell(
            state = state, dispatch = dispatch, layoutWidthClass = layout.widthClass,
            now = now, onHome = onHome, modifier = modifier, onMenuItemClick = onMenuItemClick,
        )
        NavigationMode.DRAWER, NavigationMode.THREE_PANE -> ExpandedShell(
            state = state, dispatch = dispatch, layout = layout,
            now = now, onHome = onHome, modifier = modifier, onMenuItemClick = onMenuItemClick,
        )
    }
}

// ================= Compact（底部导航） =================

@Composable
private fun CompactShell(
    state: BrowserState,
    dispatch: (BrowserController.Event) -> Unit,
    layoutWidthClass: WindowWidthClass,
    now: HomeClock,
    onHome: Boolean,
    modifier: Modifier,
    onMenuItemClick: (MenuItem) -> Unit,
) {
    // 每标签引擎池（切换不销毁 → 页面状态/缓存保留，108 修复）
    val pool = rememberEnginePool(aliveIds = state.tabs.tabs.map { it.id }.toSet())
    // 仓库（书签/历史/插件/设置面板的真实数据源）
    val panelRepository = cn.ac.oac.glimbrowser.ai.localRepository.current

    // 网页深色适配：跟随应用主题（护眼/浅色=浅，深色=深，跟随系统则读系统）
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val webDark = when (state.themeMode) {
        cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode.DARK -> true
        cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode.LIGHT, cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode.EYE_CARE -> false
        cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode.SYSTEM -> systemDark
    }
    androidx.compose.runtime.LaunchedEffect(webDark, state.tabs.currentTabId) {
        val e = pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)
        e?.setDarkMode(webDark)
        e?.setForceDarkWeb(state.forceDarkWeb, webDark)
    }

    // 桌面版 UA 应用（状态变化 → 当前引擎 setDesktopMode + reload）
    androidx.compose.runtime.LaunchedEffect(state.desktopMode) {
        pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.setDesktopMode(state.desktopMode)
    }
    // 用系统浏览器打开当前页
    androidx.compose.runtime.LaunchedEffect(state.systemBrowserRequest) {
        if (state.systemBrowserRequest > 0) {
            state.currentTab?.url?.takeIf { it.startsWith("http") }?.let { cn.ac.oac.glimbrowser.engine.openExternally(it) }
        }
    }
    // 清除缓存请求
    androidx.compose.runtime.LaunchedEffect(state.clearCacheRequest) {
        if (state.clearCacheRequest > 0) {
            pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.clearCache()
        }
    }

    // 带副作用的导航请求（后退/前进/重载）→ 直接消费引擎（不进 reducer；PRD §2.3 平台只执行）
    val navDispatch: (BrowserController.Event) -> Unit = { event ->
        when (event) {
            is BrowserController.Event.RequestBack -> pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.goBack()
            is BrowserController.Event.RequestForward -> pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.goForward()
            else -> dispatch(event)
        }
    }

    // 系统返回（手势/键）分层语义：浮层 > 内核历史 > 回首页（PRD §3.1.2 手势 + 108 用户反馈）
    PlatformBackHandler(enabled = true) {
        val tab = state.tabs.currentTab
        when {
            state.searchOpen -> dispatch(BrowserController.Event.CloseSearch)
            state.subPage != null -> dispatch(BrowserController.Event.CloseSubPage)
            state.findOpen -> dispatch(BrowserController.Event.CloseFind)
            state.menuOpen -> dispatch(BrowserController.Event.ToggleMenu(false))
            state.tabSwitcherOpen -> dispatch(BrowserController.Event.ToggleTabSwitcher(false))
            state.apiGuide.step != cn.ac.oac.glimbrowser.logic.ApiGuideState.Step.WELCOME -> dispatch(BrowserController.Event.CloseApiGuide)
            state.aiDrawerOpen -> dispatch(BrowserController.Event.ToggleAiDrawer(false))
            state.addressBarFocused -> dispatch(BrowserController.Event.AddressBarFocus(false))
            tab != null && tab.lastError != null -> dispatch(BrowserController.Event.RetryPage)
            tab != null && tab.canGoBack -> pool.engineFor(tab.id, tab.isPrivate)?.goBack()
            else -> dispatch(BrowserController.Event.Navigate(cn.ac.oac.glimbrowser.logic.NavDestination.HOME))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.tabSwitcherOpen -> TabSwitcherScreen(
                    tabs = state.tabs.tabs,
                    currentTabId = state.tabs.currentTabId,
                    onTabClick = { dispatch(BrowserController.Event.SelectTab(it)) },
                    onTabClose = { dispatch(BrowserController.Event.CloseTab(it)) },
                    onCloseOthers = { dispatch(BrowserController.Event.CloseOtherTabs(it)) },
                    onNewTab = { dispatch(BrowserController.Event.NewTab) },
                    query = state.tabs.tabQuery,
                    onQueryChange = { dispatch(BrowserController.Event.SetTabQuery(it)) },
                    onSetGroup = { id, g -> dispatch(BrowserController.Event.SetTabGroup(id, g)) },
                    closedTabs = state.tabs.closedTabs,
                    onRestoreClosed = { at -> dispatch(BrowserController.Event.RestoreClosedTab(at)) },
                    onClearClosed = { dispatch(BrowserController.Event.ClearClosedTabs) },
                    modifier = Modifier.fillMaxSize(),
                )
                state.searchOpen -> SearchOverlay(
                    query = state.searchQuery,
                    onQueryChange = { dispatch(BrowserController.Event.SetSearchQuery(it)) },
                    onSubmit = { q ->
                        val r = cn.ac.oac.glimbrowser.logic.resolveAddressBarInput(
                            q, cn.ac.oac.glimbrowser.logic.SearchEngine.GOOGLE,
                        )
                        val target = when (r.intent) {
                            cn.ac.oac.glimbrowser.logic.AddressBarIntent.NAVIGATE -> r.normalizedUrl ?: r.value
                            else -> r.value
                        }
                        if (target.isNotBlank()) {
                            dispatch(BrowserController.Event.OpenUrlInNewTab(target))
                            dispatch(BrowserController.Event.CloseSearch)
                        }
                    },
                    onClose = { dispatch(BrowserController.Event.CloseSearch) },
                    repository = panelRepository,
                    modifier = Modifier.fillMaxSize(),
                )
                onHome -> HomeScreen(
                    now = now,
                    enabledCardIds = state.enabledHomeCards,
                    onSearchBarClick = { dispatch(BrowserController.Event.OpenSearch) },
                    onQuickSiteClick = { site -> dispatch(BrowserController.Event.OpenUrlInNewTab(site.url)) },
                    onAiCardClick = { dispatch(BrowserController.Event.AiEntry) },
                    onAiClick = { dispatch(BrowserController.Event.AiEntry) },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> BrowserScreen(
                    tab = state.currentTab,
                    shieldCount = state.adFilter.stats.currentPageCount,
                    onAddressBarClick = { dispatch(BrowserController.Event.AddressBarFocus(true)) },
                    onShieldClick = { dispatch(BrowserController.Event.TogglePurifyReport(true)) },
                    onAiClick = { dispatch(BrowserController.Event.AiEntry) },
                    onBack = { navDispatch(BrowserController.Event.RequestBack) },
                    onForward = { navDispatch(BrowserController.Event.RequestForward) },
                    onRetry = { dispatch(BrowserController.Event.RetryPage) },
                    bookmarked = state.bookmarked,
                    onBookmarkClick = { dispatch(BrowserController.Event.BookmarkCurrent) },
                    incognito = state.tabs.incognito,
                    modifier = Modifier.fillMaxSize(),
                    body = { m ->
                        // Phase 3：真实平台内核容器（Android WebView / iOS WKWebView；PRD V1.2 §2.4.2）
                        BrowserContainer(
                            activeTabId = state.tabs.currentTabId,
                            activeUrl = state.currentTab?.url ?: TabManager.NEW_TAB_HOME_URL,
                            engine = pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true),
                            pendingReload = state.pendingReload,
                            onRendererGone = {
                                pool.remove(state.tabs.currentTabId ?: -1)
                                dispatch(BrowserController.Event.RendererGone)
                            },
                            dispatch = dispatch,
                            modifier = m,
                            homeContent = {
                                val bmSites = remember(panelRepository, state.tabs.currentTabId) {
                                    runCatching { panelRepository?.listBookmarks().orEmpty().take(6).mapIndexed { i, bm ->
                                        cn.ac.oac.glimbrowser.model.QuickSite(
                                            id = "bm-$i", label = bm.title.ifBlank { bm.url },
                                            url = bm.url, letter = (bm.title.ifBlank { bm.url }).take(1).ifBlank { "·" },
                                        )
                                    } }.getOrElse { emptyList() }
                                }
                                HomeScreen(
                                    now = now,
                                    enabledCardIds = state.enabledHomeCards,
                                    quickSites = bmSites,
                                    todayBlocked = state.adFilter.stats.todayCount,
                                    blockedSeries = state.adFilter.stats.categoryCounts().values.toList(),
                                    aiConfigured = state.aiConfigured,
                                    onAiCardClick = { dispatch(BrowserController.Event.AiEntry) },
                                    onSearchBarClick = { dispatch(BrowserController.Event.AddressBarFocus(true)) },
                                    onQuickSiteClick = { site -> dispatch(BrowserController.Event.OpenUrlInNewTab(site.url)) },
                                    onAddSiteClick = { dispatch(BrowserController.Event.AddressBarFocus(true)) },
                                    onMoreSitesClick = { dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.BOOKMARKS)) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                        )
                    },
                )
            }

            // 地址栏聚焦编辑态（Phase 2 简化：覆盖一层输入提示；Phase 3 接真实输入框 + 建议列表）
            if (state.addressBarFocused) {
                AddressBarEditorOverlay(
                    input = state.addressBarInput,
                    onInputChange = { dispatch(BrowserController.Event.AddressBarInput(it)) },
                    onSubmit = { dispatch(BrowserController.Event.SubmitAddressBar) },
                    onCancel = { dispatch(BrowserController.Event.AddressBarFocus(false)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 三步引导（真实测试连接）
            if (state.apiGuide.step != cn.ac.oac.glimbrowser.logic.ApiGuideState.Step.WELCOME) {
                cn.ac.oac.glimbrowser.ui.screen.ApiGuideHost(
                    state = state.apiGuide,
                    runtime = cn.ac.oac.glimbrowser.ai.localAiRuntime.current,
                    onEvent = { dispatch(BrowserController.Event.ApiGuide(it)) },
                    onClose = { dispatch(BrowserController.Event.CloseApiGuide) },
                )
            }

            // 净化报告浮层（PRD §3.3.3；真实统计来自 AdFilterEngine，红线6 按端标注）
            if (state.purifyReportOpen) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)).clickable {
                        dispatch(BrowserController.Event.TogglePurifyReport(false))
                    },
                )
                Box(modifier = Modifier.fillMaxSize().padding(top = 160.dp), contentAlignment = Alignment.BottomCenter) {
                    cn.ac.oac.glimbrowser.ui.screen.PurifyReportSheet(
                        adFilter = state.adFilter,
                        currentHost = state.currentTab?.faviconDomain,
                        onClose = { dispatch(BrowserController.Event.TogglePurifyReport(false)) },
                        onToggleSite = { host, enable ->
                            dispatch(if (enable) BrowserController.Event.ResumeSiteFilter else BrowserController.Event.PauseSiteFilter)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 菜单浮层（Compact：底部 Sheet，原型 .drawer 55%）
            if (state.menuOpen) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)).clickable { dispatch(BrowserController.Event.ToggleMenu(false)) })
                Box(modifier = Modifier.fillMaxSize().padding(top = 180.dp), contentAlignment = Alignment.BottomCenter) {
                    MenuSheet(
                        entries = DefaultMenuEntries,
                        incognito = state.tabs.incognito,
                        onItemClick = { item ->
                            dispatch(BrowserController.Event.ToggleMenu(false))
                            when (item.id) {
                                "bookmarks" -> dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.BOOKMARKS))
                                "folders" -> dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.FOLDERS))
                                "history" -> dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.HISTORY))
                                "downloads" -> dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.DOWNLOADS))
                                "plugins" -> dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.PLUGINS))
                                "settings" -> dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.SETTINGS))
                                "about" -> dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.ABOUT))
                                "find" -> dispatch(BrowserController.Event.OpenFind)
                                "desktop_mode" -> {
                                    dispatch(BrowserController.Event.SetDesktopMode(!state.desktopMode))
                                }
                                "open_system" -> dispatch(BrowserController.Event.OpenInSystemBrowser)
                                "clear_cache" -> dispatch(BrowserController.Event.ClearCache)
                                "new_tab" -> dispatch(BrowserController.Event.NewTab)
                                "incognito" -> { dispatch(BrowserController.Event.SetIncognito(true)); dispatch(BrowserController.Event.NewTab) }
                                else -> onMenuItemClick(item)
                            }
                        },
                        onIncognitoToggle = { dispatch(BrowserController.Event.SetIncognito(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // 页内查找条（真实命中数）
        if (state.findOpen) {
            cn.ac.oac.glimbrowser.ui.screen.FindInPageBar(
                query = state.findQuery,
                matches = state.findMatches,
                index = state.findIndex,
                onQueryChange = { q ->
                    dispatch(BrowserController.Event.SetFindQuery(q))
                    pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.findInPage(q)
                },
                onPrev = {
                    pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.let { e ->
                        e.findInPage(state.findQuery)
                    }
                },
                onNext = { pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.findNext() },
                onClose = {
                    pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.clearFind()
                    dispatch(BrowserController.Event.CloseFind)
                },
            )
        }

        // 全屏子页面（书签/历史/下载/插件/设置/关于）
        val subProfile = cn.ac.oac.glimbrowser.ai.resolveAiProfile(
            state.apiGuide,
            runCatching { panelRepository?.aiProfile("default") }.getOrNull(),
        )
        cn.ac.oac.glimbrowser.ui.screen.SubPageHost(
            state = state,
            dispatch = dispatch,
            repository = panelRepository,
            adFilter = state.adFilter,
            profile = subProfile,
            onOpenUrl = { url ->
                dispatch(BrowserController.Event.CloseSubPage)
                dispatch(BrowserController.Event.OpenUrlInNewTab(url))
            },
            nowMs = { kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds },
        )

        // AI 抽屉（真实 BYOK 链路；未配置→三步引导）
        cn.ac.oac.glimbrowser.ui.screen.AiDrawerHost(
            visible = state.aiDrawerOpen,
            runtime = cn.ac.oac.glimbrowser.ai.localAiRuntime.current,
            repository = panelRepository,
            guide = state.apiGuide,
            engine = pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true),
            onRequestGuide = { dispatch(BrowserController.Event.AiEntry) },
            onRequestAiSettings = {
                dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.AI_SETTINGS))
            },
            prefs = state.aiPrefs,
            incognito = state.tabs.incognito,
            pageUrl = state.currentTab?.url,
            onClose = { dispatch(BrowserController.Event.ToggleAiDrawer(false)) },
        )

        // 底部标签条（原型 V3.1：位于内容与底部导航之间）
        if (!state.searchOpen && !onHome) {
            cn.ac.oac.glimbrowser.ui.design.FloatingTabStrip(
                tabs = state.tabs.tabs,
                currentTabId = state.tabs.currentTabId,
                onSelect = { dispatch(BrowserController.Event.SelectTab(it)) },
                onClose = { dispatch(BrowserController.Event.CloseTab(it)) },
                onNewTab = { dispatch(BrowserController.Event.NewTab) },
                compact = true,
            )
        }

        // 底部导航（原型 .tabbar：首页/标签/菜单，选中玉青）
        BottomNavBar(
            current = state.navDestination,
            tabCount = state.tabs.tabs.size,
            onSelect = { dispatch(BrowserController.Event.Navigate(it)) },
        )
    }
}

/** 底部导航栏（PRD §6.1 首页/标签/菜单 三入口；原型 .tabbar）。 */
@Composable
private fun BottomNavBar(current: NavDestination, tabCount: Int, onSelect: (NavDestination) -> Unit) {
    // 原型 V3.1 .bottom-nav：毛玻璃底 + 三入口（图标 + 文字）+ Home Indicator
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
        cn.ac.oac.glimbrowser.ui.design.PlatformBlurBackdrop(
            modifier = Modifier.matchParentSize(),
            radius = 22.dp,
            tintAlpha = 0.30f,
        )
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.matchParentSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
        )
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth().height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                NavBarItem("home", "首页", active = current == NavDestination.HOME) { onSelect(NavDestination.HOME) }
                NavBarItem("tabs", if (tabCount > 1) "标签 $tabCount" else "标签", active = current == NavDestination.TABS) { onSelect(NavDestination.TABS) }
                NavBarItem("menu", "菜单", active = current == NavDestination.MENU) { onSelect(NavDestination.MENU) }
            }
            Spacer(Modifier.height(6.dp))
            // Home Indicator（原型 .home-indicator）
            Box(
                modifier = Modifier
                    .width(134.dp)
                    .height(5.dp)
                    .clip(GlimTokens.RadiusPill)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)),
            )
        }
    }
}

@Composable
private fun NavBarItem(kind: String, label: String, active: Boolean, onClick: () -> Unit) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .clip(GlimTokens.Radius12)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(GlimTokens.RadiusXs)
                .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            NavIcon(kind, color)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            color = color,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** 线性图标（纯 Canvas，避免引入图标库）。 */
@Composable
private fun NavIcon(kind: String, color: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.8.dp.toPx()
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = sw,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        when (kind) {
            "home" -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.16f, h * 0.46f)
                    lineTo(w * 0.5f, h * 0.16f)
                    lineTo(w * 0.84f, h * 0.46f)
                    lineTo(w * 0.84f, h * 0.84f)
                    lineTo(w * 0.60f, h * 0.84f)
                    lineTo(w * 0.60f, h * 0.60f)
                    lineTo(w * 0.40f, h * 0.60f)
                    lineTo(w * 0.40f, h * 0.84f)
                    lineTo(w * 0.16f, h * 0.84f)
                    close()
                }
                drawPath(p, color, style = stroke)
            }
            "tabs" -> {
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.10f, h * 0.28f),
                    size = androidx.compose.ui.geometry.Size(w * 0.80f, h * 0.58f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    style = stroke,
                )
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.30f, h * 0.28f), androidx.compose.ui.geometry.Offset(w * 0.30f, h * 0.16f), sw)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.30f, h * 0.16f), androidx.compose.ui.geometry.Offset(w * 0.70f, h * 0.16f), sw)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.70f, h * 0.16f), androidx.compose.ui.geometry.Offset(w * 0.70f, h * 0.28f), sw)
            }
            else -> {
                listOf(0.24f, 0.5f, 0.76f).forEach { x ->
                    drawCircle(color, radius = 1.6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * x, h * 0.5f))
                }
            }
        }
    }
}

// ================= 地址栏编辑覆盖层（Phase 2 简化） =================

/**
 * 地址栏聚焦编辑态（PRD §3.1.1：聚焦后展开输入 + 建议；Phase 2 仅输入 + 提交/取消，
 * 搜索联想与拼音匹配 P1 留待后续；提交逻辑复用 [cn.ac.oac.glimbrowser.logic.resolveAddressBarInput]）。
 */
@Composable
private fun AddressBarEditorOverlay(
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(GlimTokens.RadiusPill)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.primary, GlimTokens.RadiusPill)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        if (input.isEmpty()) {
                            Text("搜索或输入网址", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium)
                        }
                        inner()
                    }
                },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "前往",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(GlimTokens.RadiusPill).clickable(onClick = onSubmit).padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "输入网址直接打开，输入文字用搜索引擎（可在设置里切换）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = "支持搜索联想与快捷搜索，敬请期待",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

// ================= Expanded（三栏 / 图标轨 + 内容） =================

@Composable
private fun ExpandedShell(
    state: BrowserState,
    dispatch: (BrowserController.Event) -> Unit,
    layout: cn.ac.oac.glimbrowser.uiadaptive.AdaptiveLayout,
    now: HomeClock,
    onHome: Boolean,
    modifier: Modifier,
    onMenuItemClick: (MenuItem) -> Unit,
) {
    // 每标签引擎池（切换不销毁 → 页面状态/缓存保留，108 修复）
    val pool = rememberEnginePool(aliveIds = state.tabs.tabs.map { it.id }.toSet())
    // 仓库（书签/历史/插件/设置面板的真实数据源）
    val panelRepository = cn.ac.oac.glimbrowser.ai.localRepository.current

    // 网页深色适配：跟随应用主题（护眼/浅色=浅，深色=深，跟随系统则读系统）
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val webDark = when (state.themeMode) {
        cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode.DARK -> true
        cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode.LIGHT, cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode.EYE_CARE -> false
        cn.ac.oac.glimbrowser.ui.theme.GlimThemeMode.SYSTEM -> systemDark
    }
    androidx.compose.runtime.LaunchedEffect(webDark, state.tabs.currentTabId) {
        val e = pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)
        e?.setDarkMode(webDark)
        e?.setForceDarkWeb(state.forceDarkWeb, webDark)
    }

    // 桌面版 UA 应用（状态变化 → 当前引擎 setDesktopMode + reload）
    androidx.compose.runtime.LaunchedEffect(state.desktopMode) {
        pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.setDesktopMode(state.desktopMode)
    }
    // 用系统浏览器打开当前页
    androidx.compose.runtime.LaunchedEffect(state.systemBrowserRequest) {
        if (state.systemBrowserRequest > 0) {
            state.currentTab?.url?.takeIf { it.startsWith("http") }?.let { cn.ac.oac.glimbrowser.engine.openExternally(it) }
        }
    }
    // 清除缓存请求
    androidx.compose.runtime.LaunchedEffect(state.clearCacheRequest) {
        if (state.clearCacheRequest > 0) {
            pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.clearCache()
        }
    }

    // 带副作用的导航请求（后退/前进/重载）→ 直接消费引擎（不进 reducer；PRD §2.3 平台只执行）
    val navDispatch: (BrowserController.Event) -> Unit = { event ->
        when (event) {
            is BrowserController.Event.RequestBack -> pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.goBack()
            is BrowserController.Event.RequestForward -> pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.goForward()
            else -> dispatch(event)
        }
    }

    // 系统返回（手势/键）分层语义：浮层 > 内核历史 > 回首页（PRD §3.1.2 手势 + 108 用户反馈）
    PlatformBackHandler(enabled = true) {
        val tab = state.tabs.currentTab
        when {
            state.searchOpen -> dispatch(BrowserController.Event.CloseSearch)
            state.subPage != null -> dispatch(BrowserController.Event.CloseSubPage)
            state.findOpen -> dispatch(BrowserController.Event.CloseFind)
            state.menuOpen -> dispatch(BrowserController.Event.ToggleMenu(false))
            state.tabSwitcherOpen -> dispatch(BrowserController.Event.ToggleTabSwitcher(false))
            state.apiGuide.step != cn.ac.oac.glimbrowser.logic.ApiGuideState.Step.WELCOME -> dispatch(BrowserController.Event.CloseApiGuide)
            state.aiDrawerOpen -> dispatch(BrowserController.Event.ToggleAiDrawer(false))
            state.addressBarFocused -> dispatch(BrowserController.Event.AddressBarFocus(false))
            tab != null && tab.lastError != null -> dispatch(BrowserController.Event.RetryPage)
            tab != null && tab.canGoBack -> pool.engineFor(tab.id, tab.isPrivate)?.goBack()
            else -> dispatch(BrowserController.Event.Navigate(cn.ac.oac.glimbrowser.logic.NavDestination.HOME))
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        // 左导航图标轨（PRD §3.5.1：可折叠为图标轨）
                // 浮动标签条（Safari 风格；手机仅当前标签，桌面/Pad 全量）
        cn.ac.oac.glimbrowser.ui.design.FloatingTabStrip(
            tabs = state.tabs.tabs,
            currentTabId = state.tabs.currentTabId,
            onSelect = { dispatch(BrowserController.Event.SelectTab(it)) },
            onClose = { dispatch(BrowserController.Event.CloseTab(it)) },
            onNewTab = { dispatch(BrowserController.Event.NewTab) },
            compact = false,
        )

        // 页内查找条（真实命中数）
        if (state.findOpen) {
            cn.ac.oac.glimbrowser.ui.screen.FindInPageBar(
                query = state.findQuery,
                matches = state.findMatches,
                index = state.findIndex,
                onQueryChange = { q ->
                    dispatch(BrowserController.Event.SetFindQuery(q))
                    pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.findInPage(q)
                },
                onPrev = {
                    pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.let { e ->
                        e.findInPage(state.findQuery)
                    }
                },
                onNext = { pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.findNext() },
                onClose = {
                    pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true)?.clearFind()
                    dispatch(BrowserController.Event.CloseFind)
                },
            )
        }

        // 全屏子页面（书签/历史/下载/插件/设置/关于）
        val subProfile = cn.ac.oac.glimbrowser.ai.resolveAiProfile(
            state.apiGuide,
            runCatching { panelRepository?.aiProfile("default") }.getOrNull(),
        )
        cn.ac.oac.glimbrowser.ui.screen.SubPageHost(
            state = state,
            dispatch = dispatch,
            repository = panelRepository,
            adFilter = state.adFilter,
            profile = subProfile,
            onOpenUrl = { url ->
                dispatch(BrowserController.Event.CloseSubPage)
                dispatch(BrowserController.Event.OpenUrlInNewTab(url))
            },
            nowMs = { kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds },
        )

        // AI 抽屉（真实 BYOK 链路；未配置→三步引导）
        cn.ac.oac.glimbrowser.ui.screen.AiDrawerHost(
            visible = state.aiDrawerOpen,
            runtime = cn.ac.oac.glimbrowser.ai.localAiRuntime.current,
            repository = panelRepository,
            guide = state.apiGuide,
            engine = pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true),
            onRequestGuide = { dispatch(BrowserController.Event.AiEntry) },
            onRequestAiSettings = {
                dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.AI_SETTINGS))
            },
            prefs = state.aiPrefs,
            incognito = state.tabs.incognito,
            pageUrl = state.currentTab?.url,
            onClose = { dispatch(BrowserController.Event.ToggleAiDrawer(false)) },
        )

            NavigationRail(
                current = state.navDestination,
                onSelect = { dispatch(BrowserController.Event.Navigate(it)) },
                modifier = Modifier.fillMaxHeight().width(if (layout.showNavigationRail) 188.dp else 0.dp),
        )

        // 中面板栏（标签列表，PRD §3.5.1：宽度可拖拽 280–480dp；Phase 2 固定默认宽）
        if (layout.showPanelColumn) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(cn.ac.oac.glimbrowser.uiadaptive.PanelColumnPolicy.DEFAULT_WIDTH_DP.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
            ) {
                TabSwitcherScreen(
                    tabs = state.tabs.tabs,
                    currentTabId = state.tabs.currentTabId,
                    onTabClick = { dispatch(BrowserController.Event.SelectTab(it)) },
                    onTabClose = {},
                    onCloseOthers = { dispatch(BrowserController.Event.CloseOtherTabs(it)) },
                    onNewTab = { dispatch(BrowserController.Event.NewTab) },
                    query = state.tabs.tabQuery,
                    onQueryChange = { dispatch(BrowserController.Event.SetTabQuery(it)) },
                    onSetGroup = { id, g -> dispatch(BrowserController.Event.SetTabGroup(id, g)) },
                    closedTabs = state.tabs.closedTabs,
                    onRestoreClosed = { at -> dispatch(BrowserController.Event.RestoreClosedTab(at)) },
                    onClearClosed = { dispatch(BrowserController.Event.ClearClosedTabs) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 内容区
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when {
                state.searchOpen -> SearchOverlay(
                    query = state.searchQuery,
                    onQueryChange = { dispatch(BrowserController.Event.SetSearchQuery(it)) },
                    onSubmit = { q ->
                        val r = cn.ac.oac.glimbrowser.logic.resolveAddressBarInput(
                            q, cn.ac.oac.glimbrowser.logic.SearchEngine.GOOGLE,
                        )
                        val target = when (r.intent) {
                            cn.ac.oac.glimbrowser.logic.AddressBarIntent.NAVIGATE -> r.normalizedUrl ?: r.value
                            else -> r.value
                        }
                        if (target.isNotBlank()) {
                            dispatch(BrowserController.Event.OpenUrlInNewTab(target))
                            dispatch(BrowserController.Event.CloseSearch)
                        }
                    },
                    onClose = { dispatch(BrowserController.Event.CloseSearch) },
                    repository = panelRepository,
                    modifier = Modifier.fillMaxSize(),
                )
                onHome -> HomeScreen(
                    now = now,
                    enabledCardIds = state.enabledHomeCards,
                    onSearchBarClick = { dispatch(BrowserController.Event.OpenSearch) },
                    onQuickSiteClick = { site -> dispatch(BrowserController.Event.OpenUrlInNewTab(site.url)) },
                    onAiCardClick = { dispatch(BrowserController.Event.AiEntry) },
                    onAiClick = { dispatch(BrowserController.Event.AiEntry) },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> BrowserScreen(
                    tab = state.currentTab,
                    shieldCount = state.adFilter.stats.currentPageCount,
                    onAddressBarClick = { dispatch(BrowserController.Event.AddressBarFocus(true)) },
                    onShieldClick = { dispatch(BrowserController.Event.TogglePurifyReport(true)) },
                    onAiClick = { dispatch(BrowserController.Event.AiEntry) },
                    onBack = { navDispatch(BrowserController.Event.RequestBack) },
                    onForward = { navDispatch(BrowserController.Event.RequestForward) },
                    onRetry = { dispatch(BrowserController.Event.RetryPage) },
                    bookmarked = state.bookmarked,
                    onBookmarkClick = { dispatch(BrowserController.Event.BookmarkCurrent) },
                    incognito = state.tabs.incognito,
                    modifier = Modifier.fillMaxSize(),
                    body = { m ->
                        // Phase 3：真实平台内核容器（Android WebView / iOS WKWebView；PRD V1.2 §2.4.2）
                        BrowserContainer(
                            activeTabId = state.tabs.currentTabId,
                            activeUrl = state.currentTab?.url ?: TabManager.NEW_TAB_HOME_URL,
                            engine = pool.engineFor(state.tabs.currentTabId, state.currentTab?.isPrivate == true),
                            pendingReload = state.pendingReload,
                            onRendererGone = {
                                pool.remove(state.tabs.currentTabId ?: -1)
                                dispatch(BrowserController.Event.RendererGone)
                            },
                            dispatch = dispatch,
                            modifier = m,
                            homeContent = {
                                val bmSites = remember(panelRepository, state.tabs.currentTabId) {
                                    runCatching { panelRepository?.listBookmarks().orEmpty().take(6).mapIndexed { i, bm ->
                                        cn.ac.oac.glimbrowser.model.QuickSite(
                                            id = "bm-$i", label = bm.title.ifBlank { bm.url },
                                            url = bm.url, letter = (bm.title.ifBlank { bm.url }).take(1).ifBlank { "·" },
                                        )
                                    } }.getOrElse { emptyList() }
                                }
                                HomeScreen(
                                    now = now,
                                    enabledCardIds = state.enabledHomeCards,
                                    quickSites = bmSites,
                                    todayBlocked = state.adFilter.stats.todayCount,
                                    blockedSeries = state.adFilter.stats.categoryCounts().values.toList(),
                                    aiConfigured = state.aiConfigured,
                                    onAiCardClick = { dispatch(BrowserController.Event.AiEntry) },
                                    onSearchBarClick = { dispatch(BrowserController.Event.AddressBarFocus(true)) },
                                    onQuickSiteClick = { site -> dispatch(BrowserController.Event.OpenUrlInNewTab(site.url)) },
                                    onAddSiteClick = { dispatch(BrowserController.Event.AddressBarFocus(true)) },
                                    onMoreSitesClick = { dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.BOOKMARKS)) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                        )
                    },
                )
            }
            if (state.addressBarFocused) {
                AddressBarEditorOverlay(
                    input = state.addressBarInput,
                    onInputChange = { dispatch(BrowserController.Event.AddressBarInput(it)) },
                    onSubmit = { dispatch(BrowserController.Event.SubmitAddressBar) },
                    onCancel = { dispatch(BrowserController.Event.AddressBarFocus(false)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (state.apiGuide.step != cn.ac.oac.glimbrowser.logic.ApiGuideState.Step.WELCOME) {
                cn.ac.oac.glimbrowser.ui.screen.ApiGuideHost(
                    state = state.apiGuide,
                    runtime = cn.ac.oac.glimbrowser.ai.localAiRuntime.current,
                    onEvent = { dispatch(BrowserController.Event.ApiGuide(it)) },
                    onClose = { dispatch(BrowserController.Event.CloseApiGuide) },
                )
            }
            // 净化报告浮层（真实统计；Expanded 半宽）
            if (state.purifyReportOpen) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)).clickable {
                        dispatch(BrowserController.Event.TogglePurifyReport(false))
                    },
                )
                Box(modifier = Modifier.fillMaxSize().padding(top = 160.dp), contentAlignment = Alignment.BottomCenter) {
                    cn.ac.oac.glimbrowser.ui.screen.PurifyReportSheet(
                        adFilter = state.adFilter,
                        currentHost = state.currentTab?.faviconDomain,
                        onClose = { dispatch(BrowserController.Event.TogglePurifyReport(false)) },
                        onToggleSite = { host, enable ->
                            dispatch(if (enable) BrowserController.Event.ResumeSiteFilter else BrowserController.Event.PauseSiteFilter)
                        },
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }
            }

            if (state.menuOpen) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)))
                Box(modifier = Modifier.fillMaxSize().padding(top = 180.dp), contentAlignment = Alignment.BottomCenter) {
                    MenuSheet(
                        entries = DefaultMenuEntries,
                        incognito = state.tabs.incognito,
                        onItemClick = { item ->
                            dispatch(BrowserController.Event.ToggleMenu(false))
                            when (item.id) {
                                "bookmarks" -> dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.BOOKMARKS))
                                "folders" -> dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.FOLDERS))
                                "history" -> dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.HISTORY))
                                "downloads" -> dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.DOWNLOADS))
                                "plugins" -> dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.PLUGINS))
                                "settings" -> dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.SETTINGS))
                                "about" -> dispatch(BrowserController.Event.OpenSubPage(cn.ac.oac.glimbrowser.logic.SubPage.ABOUT))
                                "find" -> dispatch(BrowserController.Event.OpenFind)
                                "desktop_mode" -> {
                                    dispatch(BrowserController.Event.SetDesktopMode(!state.desktopMode))
                                }
                                "open_system" -> dispatch(BrowserController.Event.OpenInSystemBrowser)
                                "clear_cache" -> dispatch(BrowserController.Event.ClearCache)
                                "new_tab" -> dispatch(BrowserController.Event.NewTab)
                                "incognito" -> { dispatch(BrowserController.Event.SetIncognito(true)); dispatch(BrowserController.Event.NewTab) }
                                else -> onMenuItemClick(item)
                            }
                        },
                        onIncognitoToggle = { dispatch(BrowserController.Event.SetIncognito(it)) },
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }
            }
        }

        // AI 右侧停靠（PRD §3.5.1 Expanded / 原型 ai-dock；点击进真实 AI 抽屉）
        if (layout.aiPanelPresentation == AiPanelPresentation.DOCKED_SIDE) {
            AiDock(onOpen = { dispatch(BrowserController.Event.AiEntry) }, modifier = Modifier.fillMaxHeight().width(270.dp))
        }
    }
}

/** 左导航图标轨（Expanded，PRD §3.5.1）。 */
@Composable
private fun NavigationRail(
    current: NavDestination,
    onSelect: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 玻璃侧边栏（组件库 Sidebar）：分组标题 + 图标 + 文字 + 计数
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = cn.ac.oac.glimbrowser.ui.theme.GlimTokens.GlassAlphaMedium))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        RailGroup("浏览")
        RailItem("⌂", "首页", active = current == NavDestination.HOME) { onSelect(NavDestination.HOME) }
        RailItem("▦", "标签", active = current == NavDestination.TABS) { onSelect(NavDestination.TABS) }
        Spacer(modifier = Modifier.height(6.dp))
        RailGroup("智能")
        RailItem("✦", "AI 助手", active = false) { onSelect(NavDestination.MENU) }
        RailItem("⋯", "菜单", active = current == NavDestination.MENU) { onSelect(NavDestination.MENU) }
        Spacer(modifier = Modifier.weight(1f))
        RailItem("⚙", "设置", active = false) { onSelect(NavDestination.MENU) }
    }
}

@Composable
private fun RailGroup(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun RailItem(glyph: String, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** AI 停靠栏（点击打开 AI 抽屉，真实链路）。 */
@Composable
private fun AiDock(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.background).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)).padding(16.dp),
    ) {
        Text(text = "AI DOCK — 摘要", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "打开 AI 助手：摘要本页、与页面问答。\n\n还没配置 AI 服务时会带你完成设置。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier.clip(GlimTokens.RadiusPill).background(MaterialTheme.colorScheme.primary).clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp),
        ) { Text("打开 AI 抽屉", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary) }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "⌘/Ctrl ⇧ A 唤起", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

// ================= 默认菜单项（Phase 2 IA，PRD §6.1） =================

/** 默认菜单入口（书签/历史/下载/插件/设置 + 无痕/新标签/桌面模式占位）。 */
val DefaultMenuEntries: List<MenuItem> = listOf(
    MenuItem("bookmarks", "书签", "已收藏页面（本地）", MenuItemIcon.BOOKMARK),
    MenuItem("folders", "书签文件夹", "新建 / 改名 / 删除", MenuItemIcon.BOOKMARK),
    MenuItem("history", "历史", "最近访问（无痕不记录）", MenuItemIcon.HISTORY),
    MenuItem("downloads", "下载", "系统下载任务与文件", MenuItemIcon.DOWNLOAD),
    MenuItem("plugins", "插件", "用户脚本与授权", MenuItemIcon.PLUGIN),
    MenuItem("settings", "设置", "搜索、去广告、外观、隐私", MenuItemIcon.SETTINGS),
    MenuItem("about", "关于", "版本与隐私说明", MenuItemIcon.SETTINGS),
    MenuItem("find", "页内查找", "在当前页面搜索文字", MenuItemIcon.SETTINGS),
    MenuItem("desktop_mode", "桌面版网站", "切换电脑版页面", MenuItemIcon.DESKTOP_MODE),
    MenuItem("open_system", "用系统浏览器打开", "交给系统默认浏览器", MenuItemIcon.SETTINGS),
    MenuItem("clear_cache", "清除缓存", "清除页面缓存与 Cookie", MenuItemIcon.SETTINGS),
    MenuItem("new_tab", "新标签", "", MenuItemIcon.NEW_TAB),
    MenuItem("incognito", "无痕新标签", "不记录历史与缓存", MenuItemIcon.INCOGNITO),
)
