package cn.ac.oac.glimbrowser.engine

import me.friwi.jcefmaven.CefAppBuilder
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.callback.CefStringVisitor
import java.awt.BorderLayout
import java.io.File
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * 桌面（JVM）Chromium 内核：JCEF（Java Chromium Embedded Framework）。
 *
 * - 真实 Chromium 渲染（与现代 Chrome 同一内核），比 JavaFX WebView 兼容性更好
 * - 首次启动会把 CEF 原生库下载到 `~/.glimbrowser/cef`（约 100MB，需要网络；之后离线可用）
 * - 页面文本通过 cefQuery 消息路由回传（AI 摘要/问答真实可用）
 * - 下载交给系统浏览器（未接 CEF 下载回调）
 */
class JcefWebViewEngine(
    initialListener: BrowserEngine.Listener? = null,
    private val isPrivate: Boolean = false,
) : BrowserEngine {

    private var listener: BrowserEngine.Listener? = initialListener

    /** Swing 容器：先显示状态，Chromium 就绪后放入浏览器组件。 */
    val panel: JPanel = JPanel(BorderLayout()).apply {
        add(JLabel("正在初始化 Chromium…（首次启动需下载内核）"), BorderLayout.CENTER)
    }

    @Volatile private var browser: CefBrowser? = null
    @Volatile private var lastUrl: String = ""
    @Volatile private var lastTitle: String? = null
    @Volatile private var canBack = false
    @Volatile private var canForward = false
    @Volatile private var pendingTextResult: ((String?) -> Unit)? = null
    private var findQuery: String = ""
    /** 内核尚未就绪时先缓存目标 URL（初始化是异步的，UI 会立刻 loadUrl）。 */
    @Volatile private var pendingUrl: String? = null
    private var ready = false

    init {
        Thread({
            runCatching { initCef() }.onFailure { t ->
                SwingUtilities.invokeLater {
                    panel.removeAll()
                    panel.add(JLabel("Chromium 初始化失败：${t.message ?: t.javaClass.simpleName}（已回退到 JavaFX）"), BorderLayout.CENTER)
                    panel.revalidate()
                }
            }
        }, "glim-cef-init").apply { isDaemon = true }.start()
    }

    private fun initCef() {
        val builder = CefAppBuilder().apply {
            setInstallDir(File(System.getProperty("user.home"), ".glimbrowser/cef"))
            addJcefArgs("--disable-gpu-compositing")
        }
        val app: CefApp = builder.build()
        val client: CefClient = app.createClient()

        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(b: CefBrowser?, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
                canBack = canGoBack
                canForward = canGoForward
                b?.url?.let { lastUrl = it }
                notifyAwt { listener?.onProgress(lastUrl, if (isLoading) 0.35f else 1f) }
            }

            override fun onLoadEnd(b: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                b?.url?.let { lastUrl = it }
                notifyAwt {
                    listener?.onProgress(lastUrl, 1f)
                    listener?.onPageFinished(lastUrl, lastTitle)
                }
                // 调试：GLIM_DEBUG_TEXT=/tmp/x.txt 时把页面正文写文件（用于自动化验收）
                System.getenv("GLIM_DEBUG_TEXT")?.let { path ->
                    b?.getText(object : CefStringVisitor {
                        override fun visit(text: String?) {
                            runCatching { java.io.File(path).writeText(text.orEmpty()) }
                        }
                    })
                }
            }

            override fun onLoadError(
                b: CefBrowser?,
                frame: CefFrame?,
                errorCode: CefLoadHandler.ErrorCode?,
                errorText: String?,
                failedUrl: String?,
            ) {
                if (frame != null && !frame.isMain) return
                notifyAwt { listener?.onError(failedUrl ?: lastUrl, errorText ?: "网页加载失败（$errorCode）") }
            }
        })

        client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onTitleChange(b: CefBrowser?, title: String?) {
                lastTitle = title
                notifyAwt { listener?.onReceivedTitle(title) }
            }

            override fun onAddressChange(b: CefBrowser?, frame: CefFrame?, url: String?) {
                if (url != null) lastUrl = url
            }
        })

        val b = client.createBrowser("about:blank", false, false)
        runCatching { b.createImmediately() }
        browser = b
        ready = true
        pendingUrl?.let { target ->
            pendingUrl = null
            lastUrl = target
            b.loadURL(target)
        }
        SwingUtilities.invokeLater {
            panel.removeAll()
            b.uiComponent?.let { panel.add(it, BorderLayout.CENTER) }
            panel.revalidate()
            panel.repaint()
        }
    }

    private fun notifyAwt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }

    private fun jsEscape(s: String): String = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"

    override fun loadUrl(url: String) {
        if (url == BrowserEngine.HOME_URL) return
        lastUrl = url
        val b = browser
        if (b != null) b.loadURL(url) else pendingUrl = url
    }

    override fun canGoBack(): Boolean = canBack
    override fun canGoForward(): Boolean = canForward
    override fun goBack() { browser?.goBack() }
    override fun goForward() { browser?.goForward() }
    override fun reload() { browser?.reload() }
    override fun currentTitle(): String? = lastTitle
    override fun currentUrl(): String? = lastUrl
    override fun setListener(listener: BrowserEngine.Listener?) { this.listener = listener }

    override fun findInPage(query: String) {
        findQuery = query
        val b = browser ?: return
        val q = query
        b.getText(object : CefStringVisitor {
            override fun visit(text: String?) {
                val count = if (q.isBlank() || text.isNullOrEmpty()) 0
                            else Regex(Regex.escape(q), RegexOption.IGNORE_CASE).findAll(text).count()
                notifyAwt { listener?.onFindResult(count, if (count > 0) 1 else 0) }
            }
        })
        b.find(query, true, false, true)
    }

    override fun findNext() {
        if (findQuery.isNotBlank()) browser?.find(findQuery, true, false, true)
    }

    override fun clearFind() {
        browser?.stopFinding(true)
        findQuery = ""
    }

    override fun download(url: String) {
        notifyAwt { listener?.onDownloadStart(url, url.substringAfterLast('/').ifBlank { "download" }) }
        openExternally(url)
        notifyAwt { listener?.onDownloadFinished(url, true) }
    }

    override fun setDesktopMode(enabled: Boolean) {
        // CEF 的 UA 需在建进程前设置；运行时用 JS 标记
        browser?.executeJavaScript(
            "document.documentElement.dataset.glimDesktop='${if (enabled) "1" else "0"}';", "glim://ua", 0,
        )
    }

    override fun setDarkMode(enabled: Boolean) {
        browser?.executeJavaScript(
            "(function(){try{document.documentElement.style.colorScheme='${if (enabled) "dark" else "light"}';}catch(e){}})()",
            "glim://dark", 0,
        )
    }

    override fun setForceDarkWeb(enabled: Boolean, appDark: Boolean) {
        val js = if (!enabled || !appDark) {
            "(function(){var s=document.getElementById('glim-force-dark');if(s)s.remove();})()"
        } else {
            "(function(){if(document.getElementById('glim-force-dark'))return;var s=document.createElement('style');" +
                "s.id='glim-force-dark';s.textContent='html{background:#101413!important;filter:invert(1) hue-rotate(180deg);}' +" +
                "'img,video,picture,canvas,svg,iframe{filter:invert(1) hue-rotate(180deg);}';document.documentElement.appendChild(s);})()"
        }
        browser?.executeJavaScript(js, "glim://force-dark", 0)
    }

    override fun clearCache() {
        browser?.executeJavaScript(
            "(function(){try{caches.keys().then(function(k){k.forEach(function(n){caches.delete(n)})});" +
                "if(navigator.serviceWorker)serviceWorker.getRegistrations().then(function(r){r.forEach(function(x){x.unregister()})});" +
                "localStorage.clear();sessionStorage.clear();}catch(e){}})()",
            "glim://clear", 0,
        )
    }

    override fun evaluatePageText(onResult: (String?) -> Unit) {
        val b = browser
        if (b == null) { onResult(null); return }
        b.getText(object : CefStringVisitor {
            override fun visit(text: String?) {
                notifyAwt { onResult(text) }
            }
        })
    }

    override fun evaluatePluginScript(scriptId: String, source: String, permissions: Set<String>) {
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
        browser?.executeJavaScript(bridge, "glim://plugin", 0)
    }
}
