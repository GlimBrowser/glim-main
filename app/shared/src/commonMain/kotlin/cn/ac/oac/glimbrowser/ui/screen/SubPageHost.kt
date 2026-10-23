package cn.ac.oac.glimbrowser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import cn.ac.oac.glimbrowser.adfilter.AdFilterEngine
import cn.ac.oac.glimbrowser.data.GlimRepository
import cn.ac.oac.glimbrowser.logic.BrowserController
import cn.ac.oac.glimbrowser.logic.BrowserState
import cn.ac.oac.glimbrowser.logic.SubPage
import cn.ac.oac.glimbrowser.network.AiProfile
import cn.ac.oac.glimbrowser.ui.screen.pages.AboutPage
import cn.ac.oac.glimbrowser.ui.screen.pages.BookmarksPage
import cn.ac.oac.glimbrowser.ui.screen.pages.DownloadsPage
import cn.ac.oac.glimbrowser.ui.screen.pages.HistoryPage
import cn.ac.oac.glimbrowser.ui.screen.pages.PluginsPage
import cn.ac.oac.glimbrowser.ui.screen.pages.SettingsPage

/**
 * 子页面宿主：书签 / 历史 / 下载 / 插件 / 设置 / 关于（全屏，真实数据）。
 * 打开子页面时覆盖整个浏览器界面（含底部导航），返回时回到原页面。
 */
@Composable
fun SubPageHost(
    state: BrowserState,
    dispatch: (BrowserController.Event) -> Unit,
    repository: GlimRepository?,
    adFilter: AdFilterEngine,
    profile: AiProfile?,
    onOpenUrl: (String) -> Unit,
    nowMs: () -> Long,
) {
    val page = state.subPage ?: return
    // AI 设置页的"测试连接"：真实调用（已保存档案 + 安全区 Key）
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val aiTestResult = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val aiTesting = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // 在组合上下文读取 runtime（回调里不能读 CompositionLocal）
    val aiRuntime = cn.ac.oac.glimbrowser.ai.localAiRuntime.current
    val counts = remember(page, state.downloads.size) {
        runCatching {
            (repository?.recentHistory(500)?.size ?: 0) to (repository?.listBookmarks()?.size ?: 0)
        }.getOrDefault(0 to 0)
    }
    // 用全屏 Dialog 渲染，确保覆盖底部导航/侧栏（子页面是独立层级）
    Dialog(
        onDismissRequest = { dispatch(BrowserController.Event.CloseSubPage) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (page) {
            SubPage.BOOKMARKS -> BookmarksPage(
                repository = repository,
                currentUrl = state.currentTab?.url,
                currentTitle = state.currentTab?.title,
                onOpenUrl = onOpenUrl,
                onBack = { dispatch(BrowserController.Event.CloseSubPage) },
                nowMs = nowMs,
                onOpenFolders = { dispatch(BrowserController.Event.OpenSubPage(SubPage.FOLDERS)) },
            )
            SubPage.HISTORY -> HistoryPage(
                repository = repository,
                onOpenUrl = onOpenUrl,
                onBack = { dispatch(BrowserController.Event.CloseSubPage) },
            )
            SubPage.DOWNLOADS -> DownloadsPage(
                downloads = state.downloads,
                onOpenUrl = onOpenUrl,
                onRemove = { dispatch(BrowserController.Event.RemoveDownload(it)) },
                onClearFinished = { dispatch(BrowserController.Event.ClearDownloads) },
                onBack = { dispatch(BrowserController.Event.CloseSubPage) },
            )
            SubPage.PLUGINS -> PluginsPage(
                repository = repository,
                onBack = { dispatch(BrowserController.Event.CloseSubPage) },
            )
            SubPage.SETTINGS -> SettingsPage(
                searchEngine = state.searchEngine,
                onSetSearchEngine = { dispatch(BrowserController.Event.SetSearchEngine(it)) },
                adFilter = adFilter,
                themeMode = state.themeMode,
                onSetThemeMode = { dispatch(BrowserController.Event.SetThemeMode(it)) },
                desktopMode = state.desktopMode,
                onSetDesktopMode = { dispatch(BrowserController.Event.SetDesktopMode(it)) },
                onClearCache = { dispatch(BrowserController.Event.ClearCache) },
                forceDarkWeb = state.forceDarkWeb,
                onSetForceDarkWeb = { dispatch(BrowserController.Event.SetForceDarkWeb(it)) },
                aiConfigured = state.aiConfigured,
                docsEndpoint = profile?.baseUrl ?: "未填写地址",
                enabledHomeCards = state.enabledHomeCards,
                onToggleHomeCard = { id, on -> dispatch(BrowserController.Event.SetHomeCard(id, on)) },
                historyCount = counts.first,
                bookmarkCount = counts.second,
                onClearBrowsingData = {
                    runCatching { repository?.clearHistory() }
                },
                onOpenAiSetup = { dispatch(BrowserController.Event.AiEntry) },
                onOpenAiSettings = { dispatch(BrowserController.Event.OpenSubPage(SubPage.AI_SETTINGS)) },
                onOpenAbout = { dispatch(BrowserController.Event.OpenSubPage(SubPage.ABOUT)) },
                onBack = { dispatch(BrowserController.Event.CloseSubPage) },
            )
            SubPage.AI_SETTINGS -> cn.ac.oac.glimbrowser.ui.screen.pages.AiSettingsPage(
                prefs = state.aiPrefs,
                onChange = { dispatch(BrowserController.Event.SetAiPrefs(it)) },
                configured = state.aiConfigured,
                profileModel = profile?.model ?: "",
                onReconfigure = { dispatch(BrowserController.Event.ReconfigureAi) },
                onTestConnection = {
                    val rt = aiRuntime
                    val p = profile
                    if (rt == null || p == null) {
                        aiTestResult.value = "尚未配置端点与密钥"
                    } else if (aiTesting.value) {
                        // 防重复点击
                    } else {
                        aiTesting.value = true
                        aiTestResult.value = "测试中…"
                        scope.launch {
                            val r = runCatching { rt.testConnection(p) }
                                .getOrElse { cn.ac.oac.glimbrowser.network.ConnectivityResult(false, error = cn.ac.oac.glimbrowser.network.HttpErrorMapper.fromThrowable(it)) }
                            aiTesting.value = false
                            aiTestResult.value = if (r.success) {
                                "连接成功 · ${r.latencyMs ?: 0}ms" + (r.modelEcho?.let { " · 模型 $it" } ?: "")
                            } else {
                                "连接失败：" + (r.error?.userMessage ?: "未知错误")
                            }
                        }
                    }
                },
                testResult = aiTestResult.value,
                onBack = { dispatch(BrowserController.Event.CloseSubPage) },
            )
            SubPage.FOLDERS -> cn.ac.oac.glimbrowser.ui.screen.pages.FoldersPage(
                repository = repository,
                onBack = { dispatch(BrowserController.Event.CloseSubPage) },
            )
            SubPage.ABOUT -> AboutPage(onBack = { dispatch(BrowserController.Event.CloseSubPage) })
        }
    }
    }
}
