package cn.ac.oac.glimbrowser.ui.container

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.engine.BrowserEngine
import cn.ac.oac.glimbrowser.logic.BrowserController
import cn.ac.oac.glimbrowser.logic.TabManager

/**
 * 浏览容器 —— 渲染**外部池化**的 [engine]（不在此创建引擎）。
 *
 * 设计演进（用户反馈「网页切换/缓存丢失/多标签不顺畅」修复，108 文档）：
 *  - 引擎改为 **每标签一个、常驻复用**（[EnginePool] 在 Shell 层 remember），切换标签 = 换挂载的
 *    平台视图（同一 WebView/WKWebView 实例 re-parent），**页面状态（滚动/历史/缓存/Cookie）保留**，
 *    不再销毁重建 → 不重复整页加载（消除"报错感/缓存丢失"）
 *  - 仅当 UI 明确导航到**新 URL**（[activeUrl] 与 [engine.currentUrl] 不同）才 loadUrl
 *  - 关闭标签 → EnginePool 移除该引擎（销毁原生视图）
 *
 * 事件闭环不变：平台容器 → [BrowserEngine.Listener] → dispatch → [BrowserState]。
 */
@Composable
fun BrowserContainer(
    activeTabId: Long?,
    activeUrl: String,
    engine: BrowserEngine?,
    dispatch: (BrowserController.Event) -> Unit,
    pendingReload: Boolean = false,
    /** 渲染进程崩溃时回调（Shell 负责移除并重建该标签引擎）。 */
    onRendererGone: () -> Unit = {},
    /** 页面滚动回调（悬浮底栏隐藏）。 */
    onScroll: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    homeContent: @Composable () -> Unit,
) {
    if (activeTabId == null || activeUrl == TabManager.NEW_TAB_HOME_URL) {
        Box(modifier = modifier.fillMaxSize()) { homeContent() }
        return
    }

    // 仓库（插件执行需要读安装记录；在组合上下文取，避免在回调里读 CompositionLocal）
    val pluginRepository = cn.ac.oac.glimbrowser.ai.localRepository.current
    val onRendererGoneCb = onRendererGone
    val onScrollCb = onScroll

    // 监听器按当前标签绑定（切换标签时重绑到该标签的引擎；池化后引擎可能为 null——占位）
    DisposableEffect(engine, activeTabId) {
        val e = engine
        if (e != null && activeTabId != null) {
            e.setListener(object : BrowserEngine.Listener {
                override fun onProgress(url: String, progress: Float) {
                    dispatch(BrowserController.Event.TabLoading(activeTabId, loading = progress < 1f, progress = progress))
                }

                override fun onPageFinished(url: String, title: String?) {
                    dispatch(BrowserController.Event.TabNavigated(activeTabId, url, title))
                    dispatch(BrowserController.Event.TabLoading(activeTabId, loading = false, progress = 1f))
                    // 插件：页面加载完成 → 执行匹配且已授权的用户脚本（真实注入）
                    cn.ac.oac.glimbrowser.plugin.PluginRunner.runForPage(
                        repository = pluginRepository,
                        engine = e,
                        host = hostOf(url),
                    )
                }

                override fun onNavigationStateChanged(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
                    dispatch(BrowserController.Event.TabNavigated(activeTabId, url, title))
                    dispatch(BrowserController.Event.NavCapability(activeTabId, canGoBack, canGoForward))
                }

                override fun onFindResult(matches: Int, index: Int) {
                    dispatch(BrowserController.Event.FindResult(matches, index))
                }

                override fun onDownloadStart(url: String, suggestedFileName: String) {
                    dispatch(BrowserController.Event.DownloadStarted(url, suggestedFileName))
                }

                override fun onDownloadFinished(url: String, success: Boolean) {
                    dispatch(BrowserController.Event.DownloadFinished(url, success))
                }

                override fun onAdBlocked(url: String) {
                    // 主线程：统计 + 护盾计数（后台线程只做只读匹配）
                    dispatch(BrowserController.Event.AdBlocked(url))
                }

                override fun onScroll(scrollY: Int) {
                    onScrollCb(scrollY)
                }

                override fun onRendererGone(url: String) {
                    // 重建该标签内核（pool 由 Shell 持有 → 通过回调处理）
                    onRendererGoneCb()
                }

                override fun onError(url: String, description: String) {
                    dispatch(BrowserController.Event.PageError(activeTabId, url, description))
                }

                override fun onReceivedTitle(title: String?) {
                    title?.let { dispatch(BrowserController.Event.TabNavigated(activeTabId, e.currentUrl() ?: activeUrl, it)) }
                }
            })
        }
        onDispose {
            e?.setListener(null)
        }
    }

    // 错误卡"重试"：容器消费 pendingReload → engine.reload()（同 URL 不走 loadUrl 分支）
    var reloadSeen by remember { mutableStateOf(false) }
    DisposableEffect(engine, pendingReload) {
        if (engine != null && pendingReload && !reloadSeen) {
            engine.reload()
            reloadSeen = true
            dispatch(BrowserController.Event.ConsumeReload)   // 清标记（防止再次触发）
        }
        if (!pendingReload) reloadSeen = false
        onDispose { }
    }

    // 仅在"UI 导航目标 ≠ 引擎当前页"时加载（池化后切标签不触发 → 不重载页面，保留缓存与状态）
    DisposableEffect(engine, activeUrl) {
        val e = engine
        if (e != null && activeUrl != TabManager.NEW_TAB_HOME_URL && e.currentUrl() != activeUrl) {
            e.loadUrl(activeUrl)
        }
        onDispose { }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val e = engine
        if (e != null) {
            PlatformEngineView(engine = e, modifier = Modifier.fillMaxSize())
        } else {
            NotConnectedPlaceholder(url = activeUrl)
        }
    }
}

/** 平台内核渲染挂点（expect/actual）。 */
@Composable
expect fun PlatformEngineView(engine: BrowserEngine, modifier: Modifier)

/** 平台未接入引擎时的占位（诚实提示）。 */
@Composable
private fun NotConnectedPlaceholder(url: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "此平台的网页渲染尚未开放\n\n地址：$url",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** URL → host（common 代码不依赖 java.net；手写解析，失败返回原串由作用域匹配兜底）。 */
private fun hostOf(url: String): String {
    val afterScheme = url.substringAfter("://", url)
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    return authority.substringAfter('@').substringBefore(':').ifBlank { url }
}
