package cn.ac.oac.glimbrowser.ui.container

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import cn.ac.oac.glimbrowser.engine.BrowserEngine

/**
 * Android actual —— 系统 WebView 真实容器（PRD V1.3 §2.2.1 主力首发平台）。
 *
 * 实现要点（PRD V1.2 §2.4.2 BrowserEngine Android 要点：系统 WebView + 事件桥接，不含业务判断）：
 *  - [AndroidView] 承载 WebView，配置 WebViewClient/WebChromeClient 回调进度与导航状态
 *  - 事件经 [BrowserEngine.Listener] 回传共享层（进度 → TabLoading，导航 → TabNavigated）
 *  - ⏳ Phase 4：`shouldInterceptRequest` 请求级拦截在此接入（PRD §3.3.5 Android 完整能力）
 *  - ⏳ Phase 5：`addJavascriptInterface` JsBridge 白名单方法在此接入
 *
 * @param engine 由 [cn.ac.oac.glimbrowser.engine.AndroidWebViewEngine] 创建（持 WebView 引用）
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformEngineView(engine: BrowserEngine, modifier: Modifier) {
    // 请求级拦截注入（PRD §3.3.5 Android 完整能力；去广告默认启用 L1-L3）
    val adFilter = cn.ac.oac.glimbrowser.engine.localAdFilter.current
    val webEngine = engine as? AndroidWebViewEngine
    if (webEngine == null) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier,
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            androidx.compose.material3.Text("网页视图初始化失败，请重试")
        }
        return
    }

    // 注入拦截回调：**后台线程只做只读匹配**（不碰统计 HashMap，避免并发崩溃）；
    // 命中后回主线程记账（护盾/净化统计）。
    webEngine.requestInterceptor = { url, pageHost ->
        val hit = adFilter?.matchOnly(url, pageHost) != null
        if (hit) webEngine.postAdBlocked(url)
        hit
    }
    // 网页深色适配由 Shell 依据应用主题调用 setDarkMode（见 BrowserShell 的 theme LaunchedEffect）

    AndroidView(
        modifier = modifier,
        factory = { context ->
            // 池化复用：引擎已持 WebView（切换标签回来）→ 先脱离旧 parent 再复用（保留滚动/历史/缓存，108 修复）
            webEngine.existingWebView()?.also { wv ->
                (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            } ?: newWebView(context, webEngine)
        },
        update = { wv ->
            // 复用场景：WebView 原在另一 parent（上一个标签的 AndroidView）→ re-parent 挂到当前槽位
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            if (wv.parent == null && wv !== lastAttached) {
                // 交由 Compose AndroidView 内部管理（factory 返回即挂载）
            }
        },
    )
}

private var lastAttached: android.view.View? = null

private fun newWebView(context: android.content.Context, webEngine: AndroidWebViewEngine): WebView =
    WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // 深色适配：WebView 底色先跟随（避免首帧白闪），细节由 setDarkMode 控制
        setBackgroundColor(if (cn.ac.oac.glimbrowser.ui.theme.ThemeState.isDark) 0xFF101413.toInt() else 0xFFFAF8F3.toInt())
        // 滚动回调：供悬浮底栏下滑隐藏（View 级 API，WebView 滚动时触发）
        setOnScrollChangeListener { _, _, scrollY, _, _ ->
            webEngine.notifyScroll(scrollY)
        }
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        // 视口/缩放：现代站点通常需要这两项，否则页面按 980px 缩得很小
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.loadsImagesAutomatically = true
        // 媒体：允许非手势触发的播放（部分站点依赖；不影响系统媒体控件）
        settings.mediaPlaybackRequiresUserGesture = false
        // 混合内容：HTTPS 页面里的 http 子资源按"兼容模式"加载（否则大量老图床/脚本空白）
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        // 弹窗一律在当前 WebView 内处理（不新开系统窗口）
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.cacheMode = if (webEngine.isPrivateEngine) android.webkit.WebSettings.LOAD_NO_CACHE
                             else android.webkit.WebSettings.LOAD_DEFAULT   // 无痕不落缓存（PRD §3.1.3）
        settings.userAgentString = settings.userAgentString + " GlimBrowser/1.0"   // UA 标识（报告/站点兼容）
        // 手势：双指缩放 + 视觉反馈（PRD §3.1.2 手势适配）
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        // 同窗口内新开链接（target=_blank）→ 当前 WebView 内加载（避免静默丢页）
        settings.setSupportMultipleWindows(false)
        runCatching {
            val cm = android.webkit.CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(this, true)
        }
        webViewClient = webEngine.client
        webChromeClient = webEngine.chromeClient
        // 下载：交给平台层（DownloadManager）并进入下载页
        setDownloadListener { url, _ /* userAgent */, _ /* contentDisposition */, mimetype, _ /* contentLength */ ->
            webEngine.handleDownload(url, mimetype)
        }
        webEngine.attach(this)
    }

/**
 * Android WebView 引擎实现（PRD V1.2 §2.4.2）。
 * 由平台层（androidApp 或 DI）创建并注入 [cn.ac.oac.glimbrowser.engine.localBrowserEngineFactory]。
 */
class AndroidWebViewEngine(
    /** 无痕标签（PRD §3.1.3）：禁缓存/不写磁盘缓存；Cookie 交由平台会话隔离。 */
    private val isPrivate: Boolean = false,
) : BrowserEngine {

    private var webView: WebView? = null
    private var listener: BrowserEngine.Listener? = null

    /** 页面滚动 → UI（悬浮底栏下滑隐藏）。 */
    internal fun notifyScroll(scrollY: Int) {
        listener?.onScroll(scrollY)
    }

    /**
     * 请求级拦截（PRD §3.3.5 Android 完整能力）：由容器注入 adfilter.shouldIntercept。
     * 返回 true → WebViewClient 返回空响应（"已拦截"，去广告 L1–L3 默认启用，PRD §3.3.2）。
     */
    var requestInterceptor: ((url: String, pageHost: String) -> Boolean)? = null

    /**
     * 页面级错误（DNS 失败/断网）→ 事件（"网页访问报错"透明提示 + 重试，108 修复）。
     * 平台：WebViewClient.onReceivedError(main frame) → listener.onError。
     */
    /** 后台线程检测到拦截 → 切主线程通知（不在此处改任何共享可变状态）。 */
    internal fun postAdBlocked(url: String) {
        webView?.post { listener?.onAdBlocked(url) }
    }

    override fun setDarkMode(enabled: Boolean) {
        val wv = webView ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            wv.settings.forceDark = if (enabled) android.webkit.WebSettings.FORCE_DARK_ON
                                    else android.webkit.WebSettings.FORCE_DARK_OFF
        }
        // 同时告知页面 color-scheme（部分站点据此切换样式）
        wv.evaluateJavascript(
            "(function(){try{document.documentElement.style.colorScheme='${if (enabled) "dark" else "light"}';}catch(e){}})()",
            null,
        )
    }

    internal fun notifyError(url: String, description: String) {
        // 隐藏内核自带错误页（Chromium "Webpage not available"），由共享层错误卡呈现
        webView?.visibility = android.view.View.INVISIBLE
        listener?.onError(url, description)
    }

    internal val client: WebViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            val url = request?.url?.toString() ?: return null
            if (request.isForMainFrame) return null   // 主文档不拦（只拦子资源/广告，PRD §3.3.2 请求级）
            val pageHost = view?.url?.let { runCatching { java.net.URI(it).host }.getOrNull() } ?: ""
            return if (requestInterceptor?.invoke(url, pageHost) == true) {
                // 拦截：返回空响应（广告资源不下发）
                WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
            } else null
        }

        override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
            // 安全默认：证书异常一律阻止，不"忽略继续"；给用户明确说明而不是白屏
            handler?.cancel()
            val url = error?.url ?: view?.url.orEmpty()
            notifyError(url, "安全连接失败：该网站证书不受信任（或已过期），已阻止访问")
        }

        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
            // 重型页面 OOM / 渲染进程崩溃：返回 true 阻止应用崩溃，并请求重建该标签内核
            val url = view?.url.orEmpty()
            runCatching { view?.destroy() }
            webView = null
            listener?.onRendererGone(url)
            return true
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
            super.onReceivedError(view, request, error)
            // 部分机型在 provisional 失败时会传 null request；一律按主文档处理（minSdk 26 无兼容负担）
            if (request == null || request.isForMainFrame) {
                val url = request?.url?.toString() ?: view?.url.orEmpty()
                notifyError(url, friendlyError(error?.errorCode ?: 0, error?.description?.toString()))
            }
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            view?.visibility = android.view.View.VISIBLE   // 重试/新导航时恢复显示
            listener?.onProgress(url ?: "", 0f)
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            url?.let { listener?.onNavigationStateChanged(it, view?.title, view?.canGoBack() == true, view?.canGoForward() == true) }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            if (url != null) listener?.onPageFinished(url, view?.title)
        }
    }

    internal val chromeClient: WebChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            listener?.onProgress(view?.url ?: "", newProgress / 100f)
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            listener?.onReceivedTitle(title)
        }
    }

    internal fun attach(wv: WebView) {
        webView = wv
    }

    /** 池化复用：已创建的 WebView（标签切回时返回原实例；切走后 AndroidView 释放但实例保留于引擎）。 */
    internal fun existingWebView(): WebView? = webView

    /** 平台视图 detach 时不销毁 WebView（池语义：引擎持有实例常驻，直到标签关闭 remove）。 */
    internal fun detach() { /* 保留 webView 引用供复用（修复"缓存丢失"） */ }

    override fun loadUrl(url: String) {
        if (url == BrowserEngine.HOME_URL) return  // 首页由共享 UI 渲染，容器不加载伪 URL
        webView?.loadUrl(url)
    }

    override fun canGoBack(): Boolean = webView?.canGoBack() == true
    override fun canGoForward(): Boolean = webView?.canGoForward() == true
    override fun goBack() { webView?.goBack() }
    override fun goForward() { webView?.goForward() }
    override fun reload() { webView?.reload() }
    override fun currentTitle(): String? = webView?.title
    override fun currentUrl(): String? = webView?.url
    /** 无痕标志（视图层创建 WebView 时读缓存策略）。 */
    internal val isPrivateEngine: Boolean get() = isPrivate

    override fun setListener(listener: BrowserEngine.Listener?) { this.listener = listener }

    /** 下载：通知共享层 + 交给系统 DownloadManager（真实落盘，通知栏可见）。 */
    internal fun handleDownload(url: String, mimeType: String?) {
        val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "download" }
        listener?.onDownloadStart(url, name)
        runCatching {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setMimeType(mimeType)
            val dm = webView?.context?.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
            dm?.enqueue(request)
        }
    }

    override fun download(url: String) {
        handleDownload(url, null)
    }

    private val mobileUa: String by lazy { webView?.settings?.userAgentString ?: "" }
    private var desktop = false

    override fun setDesktopMode(enabled: Boolean) {
        desktop = enabled
        val wv = webView ?: return
        val base = wv.settings.userAgentString.substringBefore(" GlimBrowser/")
        val engineBase = when {
            base.contains("Android") && base.contains("Mobile") -> base.replace(" Mobile", "")
            else -> base
        }
        wv.settings.userAgentString = if (enabled) "$engineBase GlimBrowser/1.0" else "$base GlimBrowser/1.0"
        wv.reload()
    }

    override fun clearCache() {
        val wv = webView ?: return
        // 1) Service Worker / CacheStorage（WebView.clearCache 不覆盖，需在页面内清理）
        wv.evaluateJavascript(
            """
            (async function(){
              try {
                if (window.caches && caches.keys) {
                  var keys = await caches.keys();
                  for (var i = 0; i < keys.length; i++) { await caches.delete(keys[i]); }
                }
                if (navigator.serviceWorker && navigator.serviceWorker.getRegistrations) {
                  var regs = await navigator.serviceWorker.getRegistrations();
                  for (var j = 0; j < regs.length; j++) { await regs[j].unregister(); }
                }
                try { localStorage.clear(); sessionStorage.clear(); } catch(e) {}
              } catch(e) {}
              return true;
            })();
            """.trimIndent(),
            null,
        )
        // 2) 原生缓存 / 历史 / Cookie / WebStorage
        wv.clearCache(true)
        wv.clearHistory()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        android.webkit.WebStorage.getInstance().deleteAllData()
        android.webkit.WebViewDatabase.getInstance(wv.context).clearFormData()
    }

    /**
     * 插件脚本执行：注入白名单桥（Glim）+ 在 (function(){...})() 内执行脚本源码。
     * 每次执行重建桥（按脚本授权集），避免跨脚本串权。
     */
    override fun evaluatePluginScript(scriptId: String, source: String, permissions: Set<String>) {
        val wv = webView ?: return
        val granted = permissions.mapNotNull { runCatching { cn.ac.oac.glimbrowser.plugin.Permission.valueOf(it) }.getOrNull() }.toSet()
        runCatching {
            wv.addJavascriptInterface(
                cn.ac.oac.glimbrowser.plugin.PluginBridge(wv, granted, scriptId),
                "Glim_$scriptId".replace("-", "_"),
            )
        }
        val bridgeName = "Glim_" + scriptId.replace("-", "_")
        val wrapped = """
            (function(){
              try {
                var Glim = window['$bridgeName'];
                (function(Glim){ $source })(Glim);
              } catch (e) { console.error('[Glim] plugin error', e); }
            })();
        """.trimIndent()
        wv.evaluateJavascript(wrapped, null)
    }

    /** 页内查找：WebView 原生 findAllAsync + 高亮，结果回调共享层。 */
    override fun findInPage(query: String) {
        val wv = webView ?: return
        if (query.isBlank()) {
            wv.clearMatches()
            listener?.onFindResult(0, 0)
            return
        }
        wv.findAllAsync(query)
        wv.setFindListener { total, current, _ ->
            listener?.onFindResult(total, current)
        }
    }

    override fun findNext() {
        webView?.findNext(true)
    }

    override fun clearFind() {
        webView?.clearMatches()
    }

    /** 正文抽取（AI 上下文真实来源：document.body.innerText，PRD §3.2.2）。 */
    override fun evaluatePageText(onResult: (String?) -> Unit) {
        val wv = webView
        if (wv == null) { onResult(null); return }
        wv.evaluateJavascript("(function(){try{return document.body?document.body.innerText:''}catch(e){return ''}})()") { raw ->
            onResult(decodeJsString(raw))
        }
    }
}

/** WebViewClient 错误码 → 用户能看懂的说明（不暴露 ERR_ 常量）。 */
private fun friendlyError(code: Int, fallback: String?): String = when {
    // 明文 HTTP 被网络安全策略拦截（我们默认只放行本机）
    fallback?.contains("CLEARTEXT", ignoreCase = true) == true ->
        "该网站使用不安全的 http 连接，已被安全策略阻止。请改用 https 访问。"
    else -> when (code) {
    android.webkit.WebViewClient.ERROR_HOST_LOOKUP -> "找不到该网站，请检查网址是否正确、网络是否可用"
    android.webkit.WebViewClient.ERROR_CONNECT -> "无法连接到该网站（服务器拒绝或端口不通）"
    android.webkit.WebViewClient.ERROR_TIMEOUT -> "连接超时，请稍后重试"
    android.webkit.WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> "安全连接失败（证书不受信任），已阻止访问"
    android.webkit.WebViewClient.ERROR_UNKNOWN -> "网页加载失败，请重试"
    android.webkit.WebViewClient.ERROR_UNSUPPORTED_SCHEME -> "不支持的链接类型"
    android.webkit.WebViewClient.ERROR_IO -> "网络读写失败，请重试"
    else -> fallback?.takeIf { it.isNotBlank() } ?: "网页加载失败，请重试"
    }
}

/** evaluateJavascript 返回 JSON 字符串字面量 → 原始文本（去引号/反转义）。 */
internal fun decodeJsString(raw: String?): String? {
    if (raw == null || raw == "null") return null
    val t = raw.trim()
    if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
        val body = t.substring(1, t.length - 1)
        return body.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\")
    }
    return t
}
