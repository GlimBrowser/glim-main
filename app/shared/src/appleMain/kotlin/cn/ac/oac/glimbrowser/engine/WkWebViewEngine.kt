package cn.ac.oac.glimbrowser.engine

import cn.ac.oac.glimbrowser.plugin.Permission
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile
import platform.WebKit.WKWebView

/**
 * WKWebView 引擎实现（iOS/macOS 共用，PRD V1.2 §2.4.2 BrowserEngine iOS/macOS 要点：WKWebView）。
 *
 * 设计（PRD V1.3 §2.2.1：iOS/macOS 内核同为 WKWebView，规则产物可复用 → 引擎实现也共享于 appleMain）：
 *  - 持 [WKWebView] 引用，导航/进度事件由 [NavRelay]（WKNavigationDelegate actual，见同包 AppleEngineView）回传
 *  - 进度：didStartProvisional=0 / didFinish=1（KVO 细化进度留 Phase 4，PRD §3.1.2）
 *  - ⏳ Phase 4：WKContentRuleList 注入（PRD §3.3.5 iOS/macOS 内容级拦截）
 *  - ⏳ Phase 5：WKScriptMessageHandler JsBridge
 *
 * @param onEvent 引擎 → 共享层（由 [BrowserEngine.Listener] 承接，经 dispatch 落回状态）
 */
@OptIn(ExperimentalForeignApi::class)
class WkWebViewEngine(
    private val onEvent: (Event) -> Unit,
    /** 无痕（PRD §3.1.3）：视图层用 ephemeral WKWebsiteDataStore（不落 Cookie/缓存）。 */
    val isPrivate: Boolean = false,
) : BrowserEngine {

    /** 容器创建后注入（AppleEngineView 创建 WKWebView 并 attach）。 */
    var webView: WKWebView? = null
        private set

    private var listener: BrowserEngine.Listener? = null

    /** 引擎内部事件（与 [BrowserEngine.Listener] 1:1，多一层便于转发解耦）。 */
    sealed interface Event {
        data class Progress(val url: String, val value: Float) : Event
        data class Finished(val url: String, val title: String?) : Event
        data class Title(val value: String?) : Event
        data class State(val url: String, val title: String?, val canGoBack: Boolean, val canGoForward: Boolean) : Event
    }

    /** 下载开始/结束（由 WKDownloadDelegate 回调）。 */
    internal fun notifyDownloadStart(url: String, name: String) {
        listener?.onDownloadStart(url, name)
    }

    internal fun notifyDownloadFinished(url: String, success: Boolean) {
        listener?.onDownloadFinished(url, success)
    }

    internal fun attach(wv: WKWebView) {
        webView = wv
    }

    internal fun detach() {
        webView = null
    }

    /** 由 [platform.WebKit.WKNavigationDelegate] 回调（AppleEngineView 内的 NavRelay 调用）。 */
    internal fun handle(event: Event) {
        when (event) {
            is Event.Progress -> listener?.onProgress(event.url, event.value)
            is Event.Finished -> listener?.onPageFinished(event.url, event.title)
            is Event.Title -> listener?.onReceivedTitle(event.value)
            is Event.State -> listener?.onNavigationStateChanged(event.url, event.title, event.canGoBack, event.canGoForward)
        }
        onEvent(event)
    }

    override fun loadUrl(url: String) {
        if (url == BrowserEngine.HOME_URL) return
        val nsUrl = NSURL(string = url) ?: return
        webView?.loadRequest(NSURLRequest(uRL = nsUrl))
    }

    override fun canGoBack(): Boolean = webView?.canGoBack() ?: false
    override fun canGoForward(): Boolean = webView?.canGoForward() ?: false
    override fun goBack() { webView?.goBack() }
    override fun goForward() { webView?.goForward() }
    override fun reload() { webView?.reload() }
    override fun currentTitle(): String? = webView?.title
    override fun currentUrl(): String? = webView?.URL?.absoluteString
    override fun setListener(listener: BrowserEngine.Listener?) { this.listener = listener }

    override fun setDarkMode(enabled: Boolean) {
        val wv = webView ?: return
        applyWebDarkMode(wv, enabled)
        // 同步 color-scheme（影响滚动条/表单控件/部分站点样式），并通知页面
        wv.evaluateJavaScript(
            "(function(){try{document.documentElement.style.colorScheme='${if (enabled) "dark" else "light"}';}catch(e){}})()",
        ) { _, _ -> }
    }

    override fun setForceDarkWeb(enabled: Boolean, appDark: Boolean) {
        val wv = webView ?: return
        val css = if (!enabled || !appDark) {
            "(function(){var s=document.getElementById('glim-force-dark');if(s)s.remove();return true;})()"
        } else {
            """
            (function(){
              try {
                if (document.getElementById('glim-force-dark')) return true;
                var s = document.createElement('style');
                s.id = 'glim-force-dark';
                s.textContent = 'html{background:#101413 !important;filter:invert(1) hue-rotate(180deg);}' +
                                'img,video,picture,canvas,svg,iframe,embed,object{filter:invert(1) hue-rotate(180deg);}';
                document.documentElement.appendChild(s);
                return true;
              } catch(e) { return false; }
            })()
            """.trimIndent()
        }
        wv.evaluateJavaScript(css) { _, _ -> }
    }

    override fun setDesktopMode(enabled: Boolean) {
        val wv = webView ?: return
        wv.customUserAgent = if (enabled) {
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15 GlimBrowser/1.0"
        } else null
        wv.reload()
    }

    override fun clearCache() {
        platform.WebKit.WKWebsiteDataStore.defaultDataStore().removeDataOfTypes(
            platform.WebKit.WKWebsiteDataStore.allWebsiteDataTypes(),
            modifiedSince = platform.Foundation.NSDate.dateWithTimeIntervalSince1970(0.0),
        ) { }
    }

    /**
     * 插件脚本执行（iOS/macOS）：WKWebView 无 addJavascriptInterface，
     * 因此在 JS 侧构造受限 Glim 对象：只把已授权的方法挂上（未授权调用直接抛错），
     * 剪贴板/存储走 JS 自身能力（不触达原生文件系统）。
     */
    override fun evaluatePluginScript(scriptId: String, source: String, permissions: Set<String>) {
        val wv = webView ?: return
        val canClip = Permission.CLIPBOARD.name in permissions
        val canStore = Permission.STORAGE.name in permissions
        val wrapped = """
            (function(){
              try {
                var storeKey = 'glim_plugin_$scriptId';
                var Glim = {
                  pageText: function(){ return (document.body ? document.body.innerText : ''); },
                  log: function(m){ console.log('[Glim]', m); }
                };
                ${if (canClip) "Glim.copy = function(t){ return navigator.clipboard ? (navigator.clipboard.writeText(t), true) : false; };" else ""}
                ${if (canStore) "Glim.get = function(k){ try { return localStorage.getItem(storeKey + ':' + k); } catch(e) { return null; } };" else ""}
                ${if (canStore) "Glim.put = function(k, v){ try { localStorage.setItem(storeKey + ':' + k, String(v).slice(0, 8192)); return true; } catch(e) { return false; } };" else ""}
                (function(Glim){ $source })(Glim);
              } catch (e) { console.error('[Glim] plugin error', e); }
            })();
        """.trimIndent()
        wv.evaluateJavaScript(wrapped) { _, _ -> }
    }

    /** 页内查找（iOS 无原生高亮 API：用 JS 统计命中数并滚动到首个命中，结果如实回传）。 */
    override fun findInPage(query: String) {
        lastQuery = query
        findIndex = 0
        val wv = webView ?: return
        if (query.isBlank()) { listener?.onFindResult(0, 0); return }
        wv.evaluateJavaScript(highlightJs(query)) { result, _ ->
            val n = (result as? Number)?.toInt() ?: 0
            findIndex = 0
            if (n > 0) wv.evaluateJavaScript(scrollToMatchJs(0)) { _, _ -> }
            listener?.onFindResult(n, if (n > 0) 1 else 0)
        }
    }

    override fun findNext() {
        val q = lastQuery ?: return
        val wv = webView ?: return
        wv.evaluateJavaScript("(function(){return document.querySelectorAll('mark[data-glim-find]').length;})()") { result, _ ->
            val total = (result as? Number)?.toInt() ?: 0
            if (total <= 0) { listener?.onFindResult(0, 0); return@evaluateJavaScript }
            findIndex = (findIndex + 1) % total
            wv.evaluateJavaScript(scrollToMatchJs(findIndex)) { _, _ -> }
            listener?.onFindResult(total, findIndex + 1)
        }
    }

    private var findIndex = 0

    /** 高亮实现：清除旧标记 → 用 TreeWalker 包 <mark>（保留 DOM 文本，不改内容）。 */
    private fun highlightJs(query: String): String {
        val q = query.replace("\\", "\\\\").replace("'", "\\'")
        return """
            (function(){
              try {
                var q = 'Q_PLACEHOLDER';
                if (!q) return 0;
                // 清除旧高亮（把 mark 还原成文本）
                document.querySelectorAll('mark[data-glim-find]').forEach(function(m){
                  var t = document.createTextNode(m.textContent);
                  m.parentNode.replaceChild(t, m);
                });
                document.body.normalize();
                var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
                var texts = [], n;
                while (n = walker.nextNode()) {
                  var p = n.parentNode && n.parentNode.nodeName;
                  if (p === 'SCRIPT' || p === 'STYLE' || p === 'NOSCRIPT') continue;
                  if (n.nodeValue && n.nodeValue.toLowerCase().indexOf(q.toLowerCase()) >= 0) texts.push(n);
                }
                var lower = q.toLowerCase(), count = 0;
                texts.forEach(function(node){
                  var val = node.nodeValue, low = val.toLowerCase(), idx = low.indexOf(lower);
                  if (idx < 0) return;
                  var frag = document.createDocumentFragment(), last = 0;
                  while (idx >= 0) {
                    if (idx > last) frag.appendChild(document.createTextNode(val.slice(last, idx)));
                    var m = document.createElement('mark');
                    m.setAttribute('data-glim-find', '1');
                    m.style.background = '#ffe066';
                    m.textContent = val.slice(idx, idx + q.length);
                    frag.appendChild(m);
                    count++;
                    last = idx + q.length;
                    idx = low.indexOf(lower, last);
                  }
                  if (last < val.length) frag.appendChild(document.createTextNode(val.slice(last)));
                  node.parentNode.replaceChild(frag, node);
                });
                return count;
              } catch(e) { return 0; }
            })()
        """.trimIndent().replace("Q_PLACEHOLDER", q)
    }

    private fun scrollToMatchJs(index: Int): String =
        "(function(){var m=document.querySelectorAll('mark[data-glim-find]')[$index]; if(m){m.scrollIntoView({block:'center'});} return true;})()"

    override fun clearFind() {
        webView?.evaluateJavaScript(
            "(function(){document.querySelectorAll('mark[data-glim-find]').forEach(function(m){var t=document.createTextNode(m.textContent);m.parentNode.replaceChild(t,m);});document.body.normalize();return true;})()"
        ) { _, _ -> }
        listener?.onFindResult(0, 0)
    }

    /**
     * 应用内下载（iOS/macOS）：后台队列同步拉取 → 写入 Documents/Downloads → 通知下载页。
     */
    override fun download(url: String) {
        val nsUrl = NSURL(string = url) ?: return
        val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "download" }
        notifyDownloadStart(url, name)
        platform.darwin.dispatch_async(platform.darwin.dispatch_get_global_queue(-2, 0u)) {
            val data = platform.Foundation.NSData.dataWithContentsOfURL(nsUrl)
            var ok = false
            if (data != null) {
                val dir = platform.Foundation.NSSearchPathForDirectoriesInDomains(
                    platform.Foundation.NSDocumentDirectory, platform.Foundation.NSUserDomainMask, true,
                ).firstOrNull() as? String
                if (dir != null) {
                    val folder = "$dir/Downloads"
                    platform.Foundation.NSFileManager.defaultManager.createDirectoryAtPath(folder, true, null, null)
                    ok = data.writeToFile("$folder/$name", true)
                }
            }
            platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
                notifyDownloadFinished(url, ok)
            }
        }
    }

    private var lastQuery: String? = null

    /** 正文抽取（AI 上下文真实来源：document.body.innerText，PRD §3.2.2）。 */
    override fun evaluatePageText(onResult: (String?) -> Unit) {
        val wv = webView
        if (wv == null) { onResult(null); return }
        wv.evaluateJavaScript("(function(){try{return document.body?document.body.innerText:''}catch(e){return ''}})()") { result, _ ->
            onResult(result as? String)
        }
    }
}
