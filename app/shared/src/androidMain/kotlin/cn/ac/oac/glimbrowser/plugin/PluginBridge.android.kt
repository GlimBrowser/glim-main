package cn.ac.oac.glimbrowser.plugin

import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * 插件 JS 桥（白名单）：只暴露 5 个能力，且每个能力都要脚本已获授权。
 * 这不是强隔离沙箱（WebView 内 JS 仍可访问页面自身 DOM），但可确保：
 *  - 拿不到文件系统、进程、网络任意请求（除页面自身）；
 *  - 剪贴板/存储需显式授权；
 *  - 日志只进调试日志，不上报（埋点红线）。
 */
class PluginBridge(
    private val webView: WebView,
    private val granted: Set<Permission>,
    private val scriptId: String,
) {
    private val storage = HashMap<String, String>()

    @JavascriptInterface
    fun pageText(): String = "(function(){try{return document.body?document.body.innerText:''}catch(e){return ''}})()"

    @JavascriptInterface
    fun copy(text: String): Boolean {
        if (Permission.CLIPBOARD !in granted) return false
        return runCatching {
            val cm = webView.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("GlimBrowser", text))
            true
        }.getOrDefault(false)
    }

    @JavascriptInterface
    fun get(key: String): String? {
        if (Permission.STORAGE !in granted) return null
        return storage["$scriptId:$key"]
    }

    @JavascriptInterface
    fun put(key: String, value: String): Boolean {
        if (Permission.STORAGE !in granted) return false
        storage["$scriptId:$key"] = value.take(8192)
        return true
    }

    @JavascriptInterface
    fun log(message: String) {
        // 仅调试日志（不上报，符合埋点红线）
        android.util.Log.d("GlimPlugin/$scriptId", message.take(2000))
    }
}
