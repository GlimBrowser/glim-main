package cn.ac.oac.glimbrowser.engine

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 浏览器内核抽象（PRD V1.2 §2.4.2 平台接口契约「BrowserEngine」）。
 *
 * 设计原则（PRD V1.2 §2.3）：
 *  - **最小能力集**：加载/导航/UA/Cookie/生命周期事件；超出部分用平台扩展接口，不做统一抽象重写。
 *  - **平台只做执行**：拦截、注入、密钥存取、WebView 事件桥接不含业务判断；业务判断在 shared 逻辑层。
 *  - 通过 expect/actual 或 DI 注入（本工程用 CompositionLocal 注入，Phase 6 接 Koin 后可换）。
 *
 * 各平台实现（PRD V1.3 §2.2.1）：
 *  - Android 系统 WebView（`shouldInterceptRequest`）
 *  - iOS/macOS WKWebView（`WKContentRuleList`）
 *  - Windows WebView2（`WebResourceRequested`）—— 桌面走 CMP Desktop(jvm)，Phase 3 jvm 实现用 JVM 可用内核
 *
 * Phase 3 覆盖：load/goBack/goForward/reload + 生命周期回调（progress/didNavigate/title）。
 * ⏳ Phase 4 拦截（RuleInterceptor）与 Phase 5 JsBridge 在此接口上扩展，本阶段不预留假方法。
 */
interface BrowserEngine {

    /** 加载 URL（含导航到首页伪 URL 时由实现决定是否拦截为本地内容）。 */
    fun loadUrl(url: String)

    /** 是否可后退（驱动底栏后退按钮可用态）。 */
    fun canGoBack(): Boolean

    /** 是否可前进。 */
    fun canGoForward(): Boolean

    fun goBack()

    fun goForward()

    fun reload()

    /** 当前页面标题（容器回填给标签）。 */
    fun currentTitle(): String?

    /** 当前页面 URL。 */
    fun currentUrl(): String?

    /** 挂载生命周期回调（由共享层实现，容器在平台事件到达时调用）。 */
    fun setListener(listener: Listener?)

    /** 页内查找（无内核的平台上为 no-op，UI 会显示不支持）。 */
    fun findInPage(query: String) {}

    /** 下一个匹配。 */
    fun findNext() {}

    /** 清除查找高亮。 */
    fun clearFind() {}

    /** 请求下载（由平台层处理；无内核时 no-op）。 */
    fun download(url: String) {}

    /** 桌面版网站（切换 UA；对当前页立即生效需 reload，由 UI 决定）。 */
    fun setDesktopMode(enabled: Boolean) {}

    /** 网页深色适配（跟随应用主题；Android 强制深色 / iOS 覆盖 prefers-color-scheme）。 */
    fun setDarkMode(enabled: Boolean) {}

    /**
     * 强制深色（实验）：页面本身没有深色样式时，用滤镜反转（图片二次反转）。
     * Android 有原生 force-dark，不需要；Apple 无原生能力，故以可选开关提供。
     */
    fun setForceDarkWeb(enabled: Boolean, appDark: Boolean) {}

    /** 清除本内核的缓存与 Cookie（隐私页"清除缓存"）。 */
    fun clearCache() {}

    /**
     * 读取当前页正文纯文本（AI 摘要/问答的真实上下文来源，PRD §3.2.2 上下文构建）。
     * 默认不支持（桌面/Web 未接内核时）；平台实现用 JS 注入取 `document.body.innerText`。
     * @param onResult 结果回调（null=失败/不支持）；主线程回调。
     */
    fun evaluatePageText(onResult: (String?) -> Unit) {
        onResult(null)
    }

    /**
     * 在当前页面执行用户脚本（插件执行入口）。
     * 平台实现须只暴露白名单桥（见 PluginSandbox），不得注入文件/系统能力。
     */
    fun evaluatePluginScript(scriptId: String, source: String, permissions: Set<String>) {}

    /**
     * 容器 → 共享层 的生命周期事件（PRD §3.1.2 加载进度/状态；共享层收到后经
     * [cn.ac.oac.glimbrowser.logic.BrowserController.Event] 落回状态，驱动 UI）。
     */
    interface Listener {
        fun onProgress(url: String, progress: Float) {}
        /** 页面级错误（DNS/断网/证书）→ UI 展示错误卡片 + 重试（"网页访问报错"提示，108）。 */
        fun onError(url: String, description: String) {}

        /** 页内查找结果（matches=总数，index=当前序号）。 */
        fun onFindResult(matches: Int, index: Int) {}

        /** 平台检测到下载（触发下载页记录与系统下载）。 */
        fun onDownloadStart(url: String, suggestedFileName: String) {}

        /** 下载结束（成功/失败）。 */
        fun onDownloadFinished(url: String, success: Boolean) {}

        /** 请求被去广告拦截（后台线程检测到 → 回主线程记账）。 */
        fun onAdBlocked(url: String) {}

        /** 页面渲染进程崩溃（重型页面 OOM 等）：需要重建该标签的内核。 */
        fun onRendererGone(url: String) {}
        fun onNavigationStateChanged(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {}
        fun onPageFinished(url: String, title: String?) {}
        fun onReceivedTitle(title: String?) {}
    }

    companion object {
        /** 首页伪 URL（与 [cn.ac.oac.glimbrowser.logic.TabManager.NEW_TAB_HOME_URL] 一致，避免循环依赖复制常量）。 */
        const val HOME_URL: String = "glim://newtab"
    }
}

/**
 * 容器实现注册点（CompositionLocal）：共享层 UI 通过 [localBrowserEngineFactory] 获取当前平台的引擎。
 * 默认 null → UI 显示 Phase 3 未接容器占位（防误以为能上网）。
 */
val localBrowserEngineFactory: ProvidableCompositionLocal<((BrowserEngine.Listener?, Boolean) -> BrowserEngine)?> =
    staticCompositionLocalOf { null }

/**
 * 平台引擎工厂（expect/actual）：返回该平台可用的 [BrowserEngine] 工厂，无内核平台返回 null。
 * [App] 通过 CompositionLocalProvider(localBrowserEngineFactory provides PlatformEngineFactory()) 注入。
 */
expect fun PlatformEngineFactory(): ((BrowserEngine.Listener?, Boolean) -> BrowserEngine)?

/** 当前去广告引擎（容器注入请求级/内容级拦截；null=未提供（默认关闭场景））——PRD §3.3.5 执行层接入点。 */
val localAdFilter: ProvidableCompositionLocal<cn.ac.oac.glimbrowser.adfilter.AdFilterEngine?> =
    staticCompositionLocalOf { null }
