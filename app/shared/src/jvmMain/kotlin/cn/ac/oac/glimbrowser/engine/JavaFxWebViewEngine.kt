package cn.ac.oac.glimbrowser.engine

import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import javax.swing.SwingUtilities

/**
 * 桌面（JVM）网页内核：**JavaFX WebView**。
 *
 * - 真实渲染 HTML/CSS/JS（WebKit），支持前进/后退/刷新/标题/进度/错误
 * - 所有 WebView/WebEngine 调用都切到 JavaFX Application Thread（`Platform.runLater`）
 * - 通过 `JFXPanel`（Swing 组件）嵌进 Compose Desktop 的 `SwingPanel`
 *
 * 能力边界（如实）：
 *  - 无请求级拦截（去广告在桌面不生效；规则引擎仍可用于统计）
 *  - 查找只做计数 + `window.find` 定位，不做 DOM 高亮
 *  - 下载交给系统浏览器（JavaFX WebView 无下载 API）
 */
class JavaFxWebViewEngine(
    initialListener: BrowserEngine.Listener? = null,
    private val isPrivate: Boolean = false,
) : BrowserEngine {

    private var listener: BrowserEngine.Listener? = initialListener
    private var webView: WebView? = null
    private var webEngine: javafx.scene.web.WebEngine? = null

    @Volatile private var lastUrl: String = ""
    @Volatile private var lastTitle: String? = null
    @Volatile private var canBack: Boolean = false
    @Volatile private var canForward: Boolean = false
    @Volatile private var desktopMode: Boolean = false

    init {
        // 必须先初始化 JavaFX Toolkit，否则任何 Platform.runLater 都会抛
        // IllegalStateException: Toolkit not initialized（引擎可能先于视图被调用）
        ensureToolkitStarted()
    }

    /** Swing 容器（由 SwingPanel 使用）。 */
    val panel: JFXPanel by lazy {
        JFXPanel().also { p ->
            ensureToolkitStarted()
            Platform.runLater {
                val view = WebView()
                val eng = view.engine
                webView = view
                webEngine = eng
                eng.userAgent = if (desktopMode) DESKTOP_UA else DEFAULT_UA
                wire(eng, view)
                p.scene = Scene(view)
            }
        }
    }

    private fun ensureToolkitStarted() {
        if (toolkitStarted) return
        synchronized(toolkitLock) {
            if (toolkitStarted) return
            // JFXPanel 构造会启动 JavaFX Toolkit（并完成 Platform 初始化）
            JFXPanel()
            runCatching { Platform.setImplicitExit(false) }
            toolkitStarted = true
        }
    }

    /**
     * 回调统一切到 AWT 事件线程（Compose Desktop 的组合/渲染线程）。
     * 若直接在 JavaFX 线程里 dispatch，会与 Compose 线程并发访问状态与 SQLDelight 连接 → 闪退。
     */
    private fun notifyAwt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }

    private fun fx(block: () -> Unit) {
        ensureToolkitStarted()
        if (Platform.isFxApplicationThread()) block() else Platform.runLater(block)
    }

    private fun wire(eng: javafx.scene.web.WebEngine, view: WebView) {
        eng.locationProperty().addListener { _, _, v -> v?.let { lastUrl = it } }
        eng.titleProperty().addListener { _, _, v ->
            lastTitle = v
            notifyAwt { listener?.onReceivedTitle(v) }
        }
        eng.loadWorker.progressProperty().addListener { _, _, p ->
            notifyAwt { listener?.onProgress(lastUrl, p.toFloat()) }
        }
        eng.loadWorker.stateProperty().addListener { _, _, s ->
            when (s) {
                Worker.State.SUCCEEDED -> {
                    lastUrl = eng.location ?: lastUrl
                    notifyAwt { listener?.onProgress(lastUrl, 1f) }
                    notifyAwt { listener?.onPageFinished(lastUrl, eng.title) }
                }
                Worker.State.FAILED -> {
                    val msg = eng.loadWorker.exception?.message ?: "网页加载失败"
                    notifyAwt { listener?.onError(lastUrl, msg) }
                }
                else -> Unit
            }
        }
        // 历史可前进/后退（只能在 FX 线程读，缓存给 UI 线程）
        eng.history.currentIndexProperty().addListener { _, _, _ -> updateNavFlags(eng) }
        eng.history.entries.addListener(
            javafx.collections.ListChangeListener<javafx.scene.web.WebHistory.Entry> { _ -> updateNavFlags(eng) },
        )
        eng.locationProperty().addListener { _, _, _ -> updateNavFlags(eng) }
        // 滚动（供悬浮栏；桌面不依赖，保持接口完整）
        view.setOnScroll { e -> notifyAwt { listener?.onScroll(e.deltaY.toInt()) } }
    }

    override fun loadUrl(url: String) {
        if (url == BrowserEngine.HOME_URL) return
        lastUrl = url
        fx { webEngine?.load(url) }
    }

    override fun canGoBack(): Boolean = canBack

    override fun canGoForward(): Boolean = canForward

    /** 只在 FX 线程调用：刷新前进/后退可用态。 */
    private fun updateNavFlags(eng: javafx.scene.web.WebEngine) {
        val h = eng.history
        canBack = h.currentIndex > 0
        canForward = h.currentIndex < h.entries.size - 1
    }

    override fun goBack() {
        fx {
            val h = webEngine?.history
            if (h != null && h.currentIndex > 0) h.go(-1)
        }
    }

    override fun goForward() {
        fx {
            val h = webEngine?.history
            if (h != null && h.currentIndex < h.entries.size - 1) h.go(1)
        }
    }

    override fun reload() = fx { webEngine?.reload() }

    override fun currentTitle(): String? = lastTitle

    override fun currentUrl(): String? = lastUrl

    override fun setListener(listener: BrowserEngine.Listener?) { this.listener = listener }

    override fun findInPage(query: String) {
        val q = query.trim()
        lastFindQuery = q
        if (q.isEmpty()) { notifyAwt { listener?.onFindResult(0, 0) }; return }
        fx {
            val text = (webEngine?.executeScript("document.body?document.body.innerText:''") as? String).orEmpty()
            val count = if (text.isEmpty()) 0 else Regex(Regex.escape(q), RegexOption.IGNORE_CASE).findAll(text).count()
            // 定位到第一个匹配（无 DOM 高亮）
            webEngine?.executeScript("window.find(${q.js()}, false, false, true, false, true, false)")
            notifyAwt { listener?.onFindResult(count, if (count > 0) 1 else 0) }
        }
    }

    override fun findNext() {
        fx {
            val q = lastFindQuery
            if (q.isNotBlank()) webEngine?.executeScript("window.find(${q.js()})")
        }
    }

    override fun clearFind() {
        fx { webEngine?.executeScript("window.getSelection()?.removeAllRanges()") }
    }

    private var lastFindQuery: String = ""

    override fun download(url: String) {
        notifyAwt { listener?.onDownloadStart(url, url.substringAfterLast('/').ifBlank { "download" }) }
        openExternally(url)
        notifyAwt { listener?.onDownloadFinished(url, true) }
    }

    override fun setDesktopMode(enabled: Boolean) {
        desktopMode = enabled
        fx { webEngine?.userAgent = if (enabled) DESKTOP_UA else DEFAULT_UA }
    }

    override fun setDarkMode(enabled: Boolean) {
        fx {
            webEngine?.executeScript(
                "(function(){try{document.documentElement.style.colorScheme='${if (enabled) "dark" else "light"}';}catch(e){}})()",
            )
        }
    }

    override fun setForceDarkWeb(enabled: Boolean, appDark: Boolean) {
        fx {
            val js = if (!enabled || !appDark) {
                "(function(){var s=document.getElementById('glim-force-dark');if(s)s.remove();})()"
            } else {
                "(function(){if(document.getElementById('glim-force-dark'))return;" +
                    "var s=document.createElement('style');s.id='glim-force-dark';" +
                    "s.textContent='html{background:#101413!important;filter:invert(1) hue-rotate(180deg);}' +" +
                    "'img,video,picture,canvas,svg,iframe{filter:invert(1) hue-rotate(180deg);}';" +
                    "document.documentElement.appendChild(s);})()"
            }
            webEngine?.executeScript(js)
        }
    }

    override fun clearCache() {
        fx {
            webEngine?.executeScript(
                "(function(){try{caches.keys().then(function(k){k.forEach(function(n){caches.delete(n)})});" +
                    "if(navigator.serviceWorker)serviceWorker.getRegistrations().then(function(r){r.forEach(function(x){x.unregister()})});" +
                    "localStorage.clear();sessionStorage.clear();}catch(e){}})()",
            )
        }
    }

    override fun evaluatePageText(onResult: (String?) -> Unit) {
        fx {
            val text = runCatching {
                webEngine?.executeScript("document.body?document.body.innerText:''") as? String
            }.getOrNull()
            onResult(text)
        }
    }

    override fun evaluatePluginScript(scriptId: String, source: String, permissions: Set<String>) {
        fx {
            val bridge = """
                (function(){
                  var Glim = {
                    pageText: function(){ return document.body ? document.body.innerText : ''; },
                    copy: function(t){ try { navigator.clipboard.writeText(t); } catch(e) {} },
                    get: function(k){ try { return localStorage.getItem('glim.'+k); } catch(e) { return null; } },
                    put: function(k,v){ try { localStorage.setItem('glim.'+k, String(v)); } catch(e) {} },
                    log: function(){ try { console.log.apply(console, arguments); } catch(e) {} }
                  };
                  try { (function(Glim){ $source })(Glim); } catch (e) { console.error('plugin $scriptId', e); }
                })()
            """.trimIndent()
            webEngine?.executeScript(bridge)
        }
    }

    private fun String.js(): String = "'" + replace("\\", "\\\\").replace("'", "\\'") + "'"

    companion object {
        @Volatile private var toolkitStarted = false
        private val toolkitLock = Any()
        private const val DEFAULT_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36 GlimBrowser/0.0.1"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36 GlimBrowser/0.0.1 Desktop"
    }
}
